package com.example.test.data.remote

import com.google.gson.Gson
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

/** A small, documented fixture grammar. This is not the commercial parser. */
object DemoListingParser {
    const val SAMPLE = "İSTANBUL -> ANKARA | TIR | 20 ton | 18000 TL | 0000 000 00 01\nBURSA -> İZMİR | KAMYON | 8 ton | 9000 TL | 0000 000 00 01"
    private val cities = listOf("İSTANBUL","ANKARA","İZMİR","BURSA","ANTALYA","KONYA","ADANA","MERSİN","KOCAELİ","SAMSUN","TEKİRDAĞ","TRABZON").associateBy(::normalizedDemoText)
    fun parse(text: String): List<Ilan> {
        if(text.length !in 5..8000)throw ListingUserError("İlan metni 5–8000 karakter olmalı.")
        val offers=text.lineSequence().mapNotNull { line ->
            val parts=line.split('|').map(String::trim)
            val route=parts[0].split(Regex("->|→"));if(route.size!=2)return@mapNotNull null
            val from=cities[normalizedDemoText(route[0])]?:return@mapNotNull null
            val to=cities[normalizedDemoText(route[1])]?:return@mapNotNull null
            if(from==to)return@mapNotNull null
            val details=parts.drop(1).joinToString(" | ")
            val phone=Regex("(?<!\\d)0000[ -]*000[ -]*00[ -]*[0-9]{2}(?!\\d)").find(details)?.value?.let(::normalizeManualPhone)
            val fields=mapOf("gonderici_il" to from,"alici_il" to to,
                "arac_tipi" to Regex("\\b(TIR|KAMYONET|KAMYON|PANELVAN)\\b").find(details.uppercase(Locale.ROOT))?.value,
                "tonaj" to Regex("\\b[0-9]{1,2}(?:[.,][0-9]{1,2})?\\s*ton\\b",RegexOption.IGNORE_CASE).find(details)?.value,
                "fiyat" to Regex("([0-9]{1,3}(?:\\.[0-9]{3})+|[0-9]{1,7})\\s*(?:TL|TRY)",RegexOption.IGNORE_CASE).find(details)?.groupValues?.get(1)?.replace(".",""),
                "telefon" to phone,"yuk_cinsi" to "DEMO")
            Gson().fromJson(Gson().toJson(fields),Ilan::class.java)
        }.toList()
        if(offers.isEmpty())throw ListingUserError("ŞEHİR -> ŞEHİR biçiminde örnek ilan gir.")
        if(offers.size>20)throw ListingUserError("Tek seferde en fazla 20 kart hazırla.")
        return offers
    }
}
internal fun normalizedDemoText(value:String):String=Normalizer.normalize(value,Normalizer.Form.NFKD)
    .replace(Regex("\\p{M}"),"").lowercase(Locale.ROOT).replace('ı','i').trim().replace(Regex("\\s+")," ")
private fun identity(ilan:Ilan):String=ilan.editableFields().toSortedMap().entries.joinToString("\u0000"){(key,value)->
    val comparable=if(key=="fiyat")value.replace(Regex("[.\\s]|(?i)TL|TRY|₺"),"")else value
    "$key=${normalizedDemoText(comparable)}"
}
private data class Publication(val time:Long,val count:Int)
private data class DemoListingState(val version:Int=1,var sequence:Long=0,val listings:MutableList<SharedListing> = mutableListOf(),val publications:MutableList<Publication> = mutableListOf())

/** Device-local simulation, with no networking or authentication responsibilities. */
class DemoListingEngine private constructor(private val state:DemoListingState,private val clock:()->Long) {
    constructor(clock:()->Long=System::currentTimeMillis):this(DemoListingState(),clock)
    private val drafts=mutableMapOf<String,ListingDraft>()
    private val confirmed=mutableMapOf<String,ListingFeed>()
    @Synchronized fun list():List<SharedListing>{prune();return state.listings.sortedByDescending{it.createdAtMillis}}
    @Synchronized fun preview(text:String):ListingDraft {
        prune()
        if(drafts.size>=150)throw ListingUserError("Çok fazla açık önizleme var. Biraz bekle.")
        val cards=DemoListingParser.parse(text).mapIndexed{index,ilan->ListingPreviewCard(index,ilan,ilan.listingText())}
        return ListingDraft(UUID.randomUUID().toString(),cards,clock()+15*60000).also{drafts[it.draftId]=it}
    }
    @Synchronized fun publish(draft:ListingDraft,selected:List<Int>):ListingFeed {
        prune();confirmed[draft.draftId]?.let{return it}
        if(!drafts.containsKey(draft.draftId))throw ListingUserError("Önizleme süresi doldu.")
        if(selected.isEmpty()||selected.distinct().size!=selected.size||selected.any{it !in drafts.getValue(draft.draftId).cards.indices})throw ListingUserError("Kart seçimini kontrol et.")
        val selectedCards=selected.map{index->draft.cards.firstOrNull{it.index==index}?.ilan?:throw ListingUserError("Geçersiz kart.")}
        selectedCards.forEach(::validate)
        val seen=state.listings.map{identity(it.ilan)}.toMutableSet()
        val unique=selectedCards.filter{seen.add(identity(it))}
        if(state.publications.sumOf{it.count}+unique.size>150)throw ListingUserError("Dakikada en fazla 150 ilan.")
        if(state.listings.size+unique.size>1000)throw ListingUserError("Demo listesi dolu. Eski ilanları kaldır.")
        val now=clock()
        val added=unique.map{ilan->SharedListing((++state.sequence).toString(),ilan,ilan.listingText(),now,now+86400000,true,now)}
        state.listings.addAll(added);state.publications.add(Publication(now,added.size))
        val result=ListingFeed(added,selected.size-added.size);confirmed[draft.draftId]=result
        return result
    }
    @Synchronized fun update(item:SharedListing,fields:Map<String,String>){
        prune();val index=state.listings.indexOfFirst{it.id==item.id}
        if(index<0)throw ListingUserError("İlan artık listede değil.")
        val current=state.listings[index]
        if(current.updatedAtMillis!=item.updatedAtMillis)throw ListingUserError("İlan değişti. Yeniden aç.")
        if(fields.keys.any{it !in current.ilan.editableFields()})throw ListingUserError("Geçersiz alan.")
        val ilan=current.ilan.withListingFields(fields);validate(ilan)
        if(state.listings.any{it.id!=item.id&&identity(it.ilan)==identity(ilan)})throw ListingUserError("Bu ilan zaten listede.")
        state.listings[index]=current.copy(ilan=ilan,displayText=ilan.listingText(),updatedAtMillis=maxOf(clock(),(current.updatedAtMillis?:current.createdAtMillis)+1))
    }
    @Synchronized fun remove(id:String){state.listings.removeAll{it.id==id}}
    @Synchronized fun clear(){state.listings.clear();state.publications.clear();drafts.clear();confirmed.clear()}
    @Synchronized fun snapshot():String {prune();return Gson().toJson(state)}
    private fun validate(ilan:Ilan){
        if(ilan.gondericiIl.isNullOrBlank()||ilan.aliciIl.isNullOrBlank())throw ListingUserError("Alış ve teslim illerini doldur.")
        if(ilan.editableFields().values.any{it.length>100})throw ListingUserError("Alan en fazla 100 karakter olabilir.")
        if(normalizeManualPhone(ilan.telefon.orEmpty())==null)throw ListingUserError("Demo numarası gir: 0000 000 00 01.")
    }
    private fun prune(){val now=clock();state.listings.removeAll{it.expiresAtMillis<=now};state.publications.removeAll{it.time<=now-60000};
        val expired=drafts.filterValues{it.expiresAtMillis<=now}.keys;expired.forEach{drafts.remove(it);confirmed.remove(it)}}
    companion object {
        fun restore(json:String?,clock:()->Long=System::currentTimeMillis):DemoListingEngine {
            val state=runCatching{Gson().fromJson(json,DemoListingState::class.java)}.getOrNull()
            val valid=runCatching { state?.takeIf{it.version==1&&it.sequence>=0&&it.listings.size<=1000&&it.publications.size<=1000&&it.listings.all{item->item.id.toLongOrNull()?.let{id->id>0&&id<=it.sequence}==true&&item.mine&&normalizeManualPhone(item.ilan.telefon.orEmpty())!=null}} }.getOrNull()
            return DemoListingEngine(valid?:DemoListingState(),clock)
        }
    }
}

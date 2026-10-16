package com.example.test.data.remote

import android.content.Context
import com.example.test.data.local.LoadOfferEntity
import com.example.test.notification.LoadOfferMapper
import com.google.gson.annotations.SerializedName
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ListingUserError(message: String): IOException(message)
const val SHARED_LISTING_SOURCE = "Demo ilanları"
data class ListingPreviewCard(val index: Int, val ilan: Ilan, @SerializedName("display_text") val displayText: String)
data class ListingDraft(@SerializedName("draft_id") val draftId: String, val cards: List<ListingPreviewCard>, @SerializedName("expires_at_millis") val expiresAtMillis: Long)
data class SharedListing(val id: String, val ilan: Ilan, @SerializedName("display_text") val displayText: String,
    @SerializedName("created_at_millis") val createdAtMillis: Long, @SerializedName("expires_at_millis") val expiresAtMillis: Long, val mine: Boolean, @SerializedName("updated_at_millis") val updatedAtMillis: Long? = null)
data class ListingFeed(val listings: List<SharedListing> = emptyList(), @SerializedName("already_published_count") val alreadyPublishedCount: Int = 0)
data class ListingRemoval(val removed: Boolean = false)

fun SharedListing.toLoadOrNull(): LoadOfferEntity? {
    val numericId = id.toLongOrNull()?.takeIf { it > 0 } ?: return null
    return LoadOfferMapper.fromIlanOrNull(ilan, displayText, SHARED_LISTING_SOURCE, "Yükio kullanıcısı", createdAtMillis)
        ?.copy(id = -numericId)
}


/** Offline adapter: no network, installation tokens, purchase or production endpoint. */
object ManualListingsRepository {
    private lateinit var engine: DemoListingEngine
    private var persist: (() -> Unit)? = null
    private val items=MutableStateFlow<List<SharedListing>>(emptyList())
    val listings=items.asStateFlow()
    val error=MutableStateFlow<String?>(null).asStateFlow()
    fun initialize(context: Context) {
        val prefs=context.getSharedPreferences("demo_listings_v44",Context.MODE_PRIVATE)
        engine=DemoListingEngine.restore(prefs.getString("state",null))
        persist={prefs.edit().putString("state",engine.snapshot()).apply()}
        items.value=engine.list()
    }
    private fun changed(){items.value=engine.list();persist?.invoke()}
    suspend fun preview(text:String):ListingDraft=engine.preview(text)
    suspend fun publish(draft:ListingDraft,selected:List<Int>):ListingFeed {
        val result=engine.publish(draft,selected);changed();return result
    }
    suspend fun update(item:SharedListing,fields:Map<String,String>){engine.update(item,fields);changed()}
    suspend fun remove(id:String){engine.remove(id);changed()}
    suspend fun refresh(){changed()}
    fun clear(){engine.clear();changed()}
}

/** Deliberately non-diallable fixture numbers; real contact numbers are rejected. */
fun normalizeManualPhone(value:String):String? {
    if(!Regex("^[0-9 ()-]+$").matches(value))return null
    return value.filter(Char::isDigit).takeIf{Regex("000000000[0-9]{2}").matches(it)&&it.takeLast(2)!="00"}
}
fun Ilan.editableFields(): Map<String,String> = mapOf(
    "gonderici_il" to gondericiIl.orEmpty(), "gonderici_ilce" to gondericiIlce.orEmpty(),
    "alici_il" to aliciIl.orEmpty(), "alici_ilce" to aliciIlce.orEmpty(),
    "arac_tipi" to aracTipi.orEmpty(), "dorse_tipi" to dorseTipi.orEmpty(),
    "tonaj" to tonaj.orEmpty(), "fiyat" to fiyat.orEmpty(), "yuk_cinsi" to yukCinsi.orEmpty(), "telefon" to telefon.orEmpty(),
)
fun Ilan.withListingFields(fields: Map<String,String>): Ilan {
    val updatedStops = routeStops?.toMutableList()
    for ((role,city,district) in listOf(Triple("ALIS","gonderici_il","gonderici_ilce"),Triple("TESLIM","alici_il","alici_ilce"))) {
        val index = updatedStops?.indexOfFirst { it.role == role } ?: -1
        if (index >= 0) updatedStops!![index] = updatedStops[index].copy(city=fields[city] ?: updatedStops[index].city,district=fields[district] ?: updatedStops[index].district)
    }
    return copy(gondericiIl=fields["gonderici_il"] ?: gondericiIl,gondericiIlce=fields["gonderici_ilce"] ?: gondericiIlce,
        aliciIl=fields["alici_il"] ?: aliciIl,aliciIlce=fields["alici_ilce"] ?: aliciIlce,
        aracTipi=fields["arac_tipi"] ?: aracTipi,dorseTipi=fields["dorse_tipi"] ?: dorseTipi,
        aracTipiKanonik=if(fields["arac_tipi"] != null && fields["arac_tipi"] != aracTipi)null else aracTipiKanonik,
        dorseTipiKanonik=if(fields["dorse_tipi"] != null && fields["dorse_tipi"] != dorseTipi)null else dorseTipiKanonik,
        tonaj=fields["tonaj"] ?: tonaj,agirlik=if(fields["tonaj"] != null && fields["tonaj"] != tonaj)null else agirlik,
        fiyat=fields["fiyat"] ?: fiyat,yukCinsi=fields["yuk_cinsi"] ?: yukCinsi,telefon=fields["telefon"] ?: telefon,routeStops=updatedStops)
}
fun Ilan.listingText(): String = listOf(
    "${listOfNotNull(gondericiIlce,gondericiIl).filter{it.isNotBlank()}.joinToString(" / ")} → ${listOfNotNull(aliciIlce,aliciIl).filter{it.isNotBlank()}.joinToString(" / ")}",
    listOfNotNull(aracTipi,dorseTipi,tonaj?.takeIf{it.isNotBlank()}?.let{if(Regex("ton|kg|teker",RegexOption.IGNORE_CASE).containsMatchIn(it))it else "$it ton"},yukCinsi,fiyat?.takeIf{it.isNotBlank()}?.let{if(Regex("TL|TRY|₺|LIRA",RegexOption.IGNORE_CASE).containsMatchIn(it))it else "$it TL"}).filter{it.isNotBlank()}.joinToString(" · "),
    telefon?.takeIf{it.isNotBlank()}?.let{"İletişim: $it"} ?: "Telefon eklenmeli",
).joinToString("\n")

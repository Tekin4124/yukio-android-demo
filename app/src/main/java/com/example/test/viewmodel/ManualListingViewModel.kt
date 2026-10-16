package com.example.test.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.remote.ListingUserError
import com.example.test.data.remote.normalizeManualPhone
import com.example.test.data.remote.withListingFields
import com.example.test.data.remote.listingText
import com.example.test.data.remote.ListingDraft
import com.example.test.data.remote.ManualListingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManualListingState(
    val text: String = "", val phone: String = "", val draft: ListingDraft? = null,
    val selected: Set<Int> = emptySet(), val busy: Boolean = false,
    val error: String? = null, val publishedCount: Int? = null, val duplicateCount: Int = 0,
)
class ManualListingViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(ManualListingState(text=saved["text"]?:"",phone=saved["phone"]?:""))
    val state = _state.asStateFlow()
    fun text(value: String) { if(_state.value.busy)return;saved["text"]=value.take(8000);_state.update{it.copy(text=value.take(8000),draft=null,selected=emptySet(),error=null)} }
    fun phone(value: String) { if(_state.value.busy)return;saved["phone"]=value.take(32);_state.update{it.copy(phone=value.take(32),error=null)} }
    fun toggle(index: Int) { if(_state.value.busy)return;_state.update { it.copy(selected=if(index in it.selected)it.selected-index else it.selected+index) } }
    fun edit() { if(!_state.value.busy)_state.update{it.copy(draft=null,selected=emptySet(),error=null)} }
    fun reset() { saved["text"]="";saved["phone"]="";_state.value=ManualListingState() }
    fun preview() {
        val input=_state.value;if(input.busy)return
        if(input.text.trim().length<5){_state.update{it.copy(error="İlan metnini gir.")};return}
        _state.update{it.copy(busy=true,error=null)}
        viewModelScope.launch {
            try {
                val draft=ManualListingsRepository.preview(input.text)
                _state.update{it.copy(draft=draft,phone="",selected=draft.cards.map{c->c.index}.toSet())}
            }catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){_state.update{it.copy(error=if(error is ListingUserError)error.message else "Sunucuya ulaşılamadı. Kartları yeniden hazırlamayı dene.")}}
            finally{_state.update{it.copy(busy=false)}}
        }
    }
    fun editCard(index: Int, fields: Map<String,String>) {
        if(_state.value.busy)return
        _state.update { current -> current.copy(draft=current.draft?.copy(cards=current.draft.cards.map { card ->
            if(card.index==index){val ilan=card.ilan.withListingFields(fields);card.copy(ilan=ilan,displayText=ilan.listingText())}else card
        }),error=null) }
    }
    fun applyMissingPhone() {
        val number=normalizeManualPhone(_state.value.phone)
        if(number==null){_state.update{it.copy(error="Demo numarası gir: 0000 000 00 01.")};return}
        _state.value.draft?.cards?.filter{it.index in _state.value.selected && normalizeManualPhone(it.ilan.telefon.orEmpty())==null}
            ?.forEach{editCard(it.index,mapOf("telefon" to number))}
    }
    fun publish() {
        val input=_state.value;val draft=input.draft?:return
        if(input.busy||input.selected.isEmpty())return
        if(draft.cards.any{it.index in input.selected && normalizeManualPhone(it.ilan.telefon.orEmpty())==null}){
            _state.update{it.copy(error="Telefonu bulunamayan seçili kartlara numara ekle.")};return
        }
        _state.update{it.copy(busy=true,error=null)}
        viewModelScope.launch {
            try {
                val listings=ManualListingsRepository.publish(draft,input.selected.sorted())
                saved["text"]="";saved["phone"]=""
                _state.update{it.copy(publishedCount=listings.listings.size,duplicateCount=listings.alreadyPublishedCount,draft=null,text="",phone="")}
            }catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){_state.update{it.copy(error=if(error is ListingUserError)error.message else "Yayın yanıtı alınamadı. Aynı önizlemeden tekrar deneyebilirsin.")}}
            finally{_state.update{it.copy(busy=false)}}
        }
    }
}

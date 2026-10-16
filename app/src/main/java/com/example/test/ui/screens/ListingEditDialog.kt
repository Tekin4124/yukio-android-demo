package com.example.test.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.test.data.remote.Ilan
import com.example.test.data.remote.editableFields
import com.example.test.data.remote.normalizeManualPhone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ListingEditDialog(initial: Ilan, published: Boolean, onDismiss: () -> Unit, onSave: suspend (Map<String,String>) -> String?) {
    var values by remember(initial) { mutableStateOf(initial.editableFields()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    fun save() {
        val phone=normalizeManualPhone(values["telefon"].orEmpty())
        if(values["gonderici_il"].isNullOrBlank()||values["alici_il"].isNullOrBlank()){error="Alış ve teslim illerini doldur.";return}
        if(phone==null){error="Demo numarası gir: 0000 000 00 01.";return}
        busy=true;error=null
        scope.launch{
            try{
                val issue=onSave(values.mapValues{it.value.trim()} + ("telefon" to phone))
                if(issue==null)onDismiss()else error=issue
            }catch(cancelled:CancellationException){throw cancelled}
            catch(_:Exception){error="Değişiklikler kaydedilemedi. Tekrar dene."}
            finally{busy=false}
        }
    }
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("İlanı düzenle")},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            val fields=listOf("gonderici_il" to "Alış ili","gonderici_ilce" to "Alış ilçesi","alici_il" to "Teslim ili","alici_ilce" to "Teslim ilçesi","arac_tipi" to "Araç tipi","dorse_tipi" to "Dorse tipi","tonaj" to "Tonaj / ağırlık","fiyat" to "Fiyat (TL)","yuk_cinsi" to "Yük cinsi","telefon" to "Demo telefon (0000 000 00 01)")
            for((key,label) in fields) OutlinedTextField(value=values[key].orEmpty(),onValueChange={values=values+(key to it.take(if(key=="telefon")32 else 80));error=null},
                enabled=!busy,label={Text(label)},singleLine=true,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),
                keyboardOptions=KeyboardOptions(keyboardType=when(key){"telefon"->KeyboardType.Phone;"fiyat"->KeyboardType.Decimal;else->KeyboardType.Text}))
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        confirmButton={TextButton(enabled=!busy,onClick={save()}){Text(if(busy)"Kaydediliyor…" else "Kaydet")}},
        dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Vazgeç")}},
    )
}

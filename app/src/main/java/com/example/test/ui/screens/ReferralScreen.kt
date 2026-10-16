@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.test.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.test.data.remote.DemoReferral

@Composable
fun ReferralScreen(onBack:()->Unit){
    val context=LocalContext.current
    val prefs=remember{context.getSharedPreferences("demo_referral",Context.MODE_PRIVATE)}
    val demo=remember{DemoReferral(prefs.getBoolean("activated",false))}
    var activated by remember{mutableStateOf(demo.activated)}
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var input by rememberSaveable{mutableStateOf("")}
    var error by remember{mutableStateOf<String?>(null)}
    Scaffold(topBar={TopAppBar(title={Text("Arkadaşını davet et")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Geri")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
            Text("Demo · Gerçek premium hakkı vermez",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TabRow(selectedTabIndex=tab){Tab(selected=tab==0,onClick={tab=0},text={Text("Kodum")});Tab(selected=tab==1,onClick={tab=1},text={Text("Kod kullan")})}
            if(tab==0){
                Text(DemoReferral.OWN_CODE,style=MaterialTheme.typography.headlineMedium)
                Text("İkinize de 14 gün",style=MaterialTheme.typography.titleLarge)
                if(activated)Text("Sen: +14 gün · Arkadaşın: +14 gün (simülasyon)")
            }else{
                OutlinedTextField(value=input,onValueChange={input=it.take(32);error=null},singleLine=true,label={Text("Referans kodu")},placeholder={Text(DemoReferral.FRIEND_CODE)},modifier=Modifier.fillMaxWidth(),enabled=!activated)
                Button(enabled=!activated&&input.isNotBlank(),onClick={error=demo.activate(input);activated=demo.activated;if(activated)prefs.edit().putBoolean("activated",true).apply()},modifier=Modifier.fillMaxWidth()){Text(if(activated)"Etkinleştirildi" else "Etkinleştir")}
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                if(activated)Text("İki örnek hesaba da 14 gün eklendi.")
            }
        }
    }
}

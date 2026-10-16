@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.test.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test.data.remote.Ilan
import com.example.test.data.remote.SharedListing
import com.example.test.data.remote.ListingUserError
import com.example.test.data.remote.normalizeManualPhone
import com.example.test.data.remote.ManualListingsRepository
import com.example.test.notification.LoadOfferMapper
import com.example.test.ui.format.displayRoute
import com.example.test.data.local.displayTonnage
import com.example.test.viewmodel.ManualListingViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

private data class EditingListing(val ilan: Ilan, val index: Int? = null, val listing: SharedListing? = null)

@Composable
fun ManualListingScreen(onBack: () -> Unit, viewModel: ManualListingViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listings by ManualListingsRepository.listings.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    var tab by rememberSaveable{mutableIntStateOf(0)}
    var editing by remember{mutableStateOf<EditingListing?>(null)}
    var deleting by remember{mutableStateOf<String?>(null)}
    var removing by remember{mutableStateOf(false)}
    var removalError by remember{mutableStateOf<String?>(null)}
    BackHandler(enabled=state.busy||removing){}
    LaunchedEffect(Unit){ManualListingsRepository.refresh()}
    editing?.let { target ->
        ListingEditDialog(initial=target.ilan,published=target.listing!=null,onDismiss={editing=null},onSave={fields ->
            if(target.index!=null){viewModel.editCard(target.index,fields);null}
            else {
                try{ManualListingsRepository.update(target.listing!!,fields);null}
                catch(cancelled:CancellationException){throw cancelled}
                catch(error:Exception){if(error is ListingUserError)error.message else "İlan güncellenemedi. Tekrar dene."}
            }
        })
    }
    if(deleting!=null) AlertDialog(
        onDismissRequest={if(!removing)deleting=null}, title={Text("İlan kaldırılsın mı?")},
        text={Text("İlan bu cihazın demo listesinden kaldırılacak.")},
        confirmButton={TextButton(enabled=!removing,onClick={
            val id=deleting?:return@TextButton;removing=true
            scope.launch{try{ManualListingsRepository.remove(id);deleting=null;removalError=null}
                catch(cancelled:CancellationException){throw cancelled}
                catch(e:Exception){removalError="İlan kaldırılamadı. Tekrar dene.";deleting=null}
                finally{removing=false}}
        }){Text(if(removing)"Kaldırılıyor…" else "Kaldır")}},
        dismissButton={TextButton(enabled=!removing,onClick={deleting=null}){Text("Vazgeç")}},
    )
    Scaffold(topBar={TopAppBar(title={Text("Demo ilanları")},navigationIcon={IconButton(enabled=!state.busy,onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Geri")}})}){padding->
        Column(Modifier.fillMaxSize().padding(padding).imePadding(),horizontalAlignment=Alignment.CenterHorizontally){
            Row(Modifier.widthIn(max=600.dp).fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                val mineCount=listings.count{it.mine}
                for(index in 0..1){
                    val selected=tab==index
                    Surface(onClick={tab=index},enabled=!state.busy&&!removing,
                        modifier=Modifier.weight(1f),shape=RoundedCornerShape(14.dp),
                        color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor=if(selected)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant){
                        Row(Modifier.padding(vertical=14.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                            if(index==0){Icon(Icons.Default.Add,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp))}
                            Text(if(index==0)"Yeni ilan" else "İlanlarım",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
                            if(index==1&&mineCount>0){Spacer(Modifier.width(8.dp));Text("$mineCount",style=MaterialTheme.typography.labelMedium)}
                        }
                    }
                }
            }
            key(tab){
            Column(Modifier.widthIn(max=600.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                if(tab==0){
                if(state.publishedCount!=null){
                    Icon(Icons.Default.CheckCircle,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(36.dp))
                    Text(if(state.publishedCount==0)"Bu ilanlar demo listesinde" else "${state.publishedCount} ilan demo listesinde",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    if(state.duplicateCount>0 && state.publishedCount!=0) Text("${state.duplicateCount} tekrar atlandı",color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick=onBack,modifier=Modifier.fillMaxWidth()){Text("İlan listesine dön")}
                    TextButton(onClick={tab=1}){Text("İlanlarım")}
                    TextButton(onClick=viewModel::reset){Text("Yeni ilan ekle")}
                }else if(state.draft==null){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                        Text("Yalnız cihazda",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={viewModel.text(com.example.test.data.remote.DemoListingParser.SAMPLE)},enabled=!state.busy){Text("Örnek ekle")}
                    }
                    OutlinedTextField(value=state.text,onValueChange=viewModel::text,enabled=!state.busy,
                        placeholder={Text("ŞEHİR -> ŞEHİR | ARAÇ | TONAJ | FİYAT")},
                        minLines=8,maxLines=14,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp))
                    Button(onClick=viewModel::preview,enabled=!state.busy&&state.text.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){
                        if(state.busy){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(8.dp))}
                        Text(if(state.busy)"Kartlar hazırlanıyor…" else "Kartları hazırla")
                    }
                }else{
                    Text("Kartları kontrol et",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                    for(card in state.draft!!.cards){
                        val load=LoadOfferMapper.fromIlanOrNull(card.ilan,card.displayText,"Yükio ilanları","Sen")
                        if(load!=null) Surface(onClick={editing=EditingListing(card.ilan,index=card.index)},enabled=!state.busy,shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),color=MaterialTheme.colorScheme.surfaceContainer){
                            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                Checkbox(checked=card.index in state.selected,enabled=!state.busy,onCheckedChange={viewModel.toggle(card.index)})
                                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
                                    Text(load.displayRoute(),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                                    Text(listOfNotNull(load.vehicleType,load.displayTonnage(),load.priceTry?.let{"%,d ₺".format(it)}).joinToString(" · "))
                                    card.ilan.yukCinsi?.let{Text(it,style=MaterialTheme.typography.bodyMedium)}
                                    Text(if(normalizeManualPhone(card.ilan.telefon.orEmpty())==null) "Telefon ekle" else "${card.ilan.telefon}",style=MaterialTheme.typography.bodySmall,
                                        color=if(normalizeManualPhone(card.ilan.telefon.orEmpty())==null)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Düzenle",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    val missingPhones=state.draft!!.cards.count { it.index in state.selected && normalizeManualPhone(it.ilan.telefon.orEmpty())==null }
                    if(missingPhones>0){
                        Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainer){
                            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                                Text("$missingPhones kartta telefon bulunamadı",fontWeight=FontWeight.SemiBold)
                                OutlinedTextField(value=state.phone,onValueChange=viewModel::phone,label={Text("Demo telefon: 0000 000 00 01")},
                                    singleLine=true,enabled=!state.busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),modifier=Modifier.fillMaxWidth())
                                OutlinedButton(onClick=viewModel::applyMissingPhone,enabled=!state.busy&&state.phone.isNotBlank(),modifier=Modifier.fillMaxWidth()){Text("Eksik telefonlara uygula")}
                            }
                        }
                    }
                    TextButton(onClick=viewModel::edit,enabled=!state.busy){Text("Metne dön")}
                    Text("Yalnız bu cihazda · 24 saat",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick=viewModel::publish,enabled=!state.busy&&state.selected.isNotEmpty()&&missingPhones==0,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){
                        Text(if(state.busy)"Yayınlanıyor…" else "Onayla ve ${state.selected.size} ilanı yayınla")
                    }
                }
                state.error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                removalError?.let{Text(it,color=MaterialTheme.colorScheme.error)}
                }
                val mine=listings.filter{it.mine}
                if(tab==1 && mine.isEmpty()){
                    Text("Henüz demo ilanın yok.",style=MaterialTheme.typography.titleMedium)

                    OutlinedButton(onClick={tab=0}){Text("İlan ekle")}
                }
                if(tab==1 && mine.isNotEmpty()){
                    mine.forEach{item->
                        val load=LoadOfferMapper.fromIlanOrNull(item.ilan,item.displayText,"Yükio ilanları","Sen")
                        Surface(onClick={editing=EditingListing(item.ilan,listing=item)},enabled=!removing&&!state.busy,
                            shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainer){
                            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                    Text("Cihazda",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.weight(1f))
                                    Icon(Icons.Default.Edit,"Düzenle",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(load?.displayRoute()?:"${item.ilan.gondericiIl} → ${item.ilan.aliciIl}",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
                                if(load!=null) Text(listOfNotNull(load.vehicleType,load.displayTonnage(),load.priceTry?.let{"%,d ₺".format(it)}).joinToString(" · "),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                    Text(item.ilan.telefon.orEmpty(),style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    IconButton(onClick={deleting=item.id},enabled=!removing&&!state.busy,modifier=Modifier.size(40.dp)){
                                        Icon(Icons.Default.DeleteOutline,"Yayından kaldır",Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            }
        }
    }
}

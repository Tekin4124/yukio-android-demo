package com.example.test.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test.data.local.AppDatabase
import com.example.test.data.remote.LoadParserApi
import com.example.test.data.remote.ParseResult
import com.example.test.notification.LoadOfferMapper
import com.example.test.util.PiiSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Modernized local end-to-end demo dialog with improved typography and Material3 styling. */
@Composable
fun DemoInputDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var text by remember {
        mutableStateOf(
            "İSTANBUL -> ANKARA | TIR | 20 ton | 18000 TL\nBURSA -> İZMİR | KAMYON | 8 ton | 9000 TL"
        )
    }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Demo İlan Simülatörü",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Aşağıdaki örnek metinleri simüle ederek uygulamanın ayrıştırma, filtre ve liste akışını canlı test edebilirsiniz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Yerel backend ve adb reverse (port 3000) gereklidir.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 8000) text = it },
                    label = { Text("Örnek Yük İlanları") },
                    placeholder = { Text("Şehir -> Şehir | Araç | Tonaj | Fiyat") },
                    enabled = !busy,
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                    )
                )

                if (status.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                onClick = {
                    busy = true
                    status = ""
                    scope.launch {
                        try {
                            val safeText = withContext(Dispatchers.IO) {
                                PiiSanitizer.sanitize(text).cleanText
                            }
                            when (val result = withContext(Dispatchers.IO) { LoadParserApi.parseIlan(safeText) }) {
                                is ParseResult.Success -> {
                                    val saved = withContext(Dispatchers.IO) {
                                        val dao = AppDatabase.getInstance(context).loadOfferDao()
                                        var count = 0
                                        result.ilanlar.forEach { ilan ->
                                            val entity = LoadOfferMapper.fromIlanOrNull(
                                                ilan = ilan,
                                                rawMessage = safeText,
                                                sourceGroup = "Yerel Demo",
                                                fallbackSender = "Demo"
                                            )
                                            if (entity != null && dao.insertDistinct(entity) != -1L) count++
                                        }
                                        count
                                    }
                                    isError = false
                                    status = "$saved yeni ilan eklendi! Listeye dönerek inceleyebilirsiniz."
                                }
                                is ParseResult.NoLogisticContent -> {
                                    isError = true
                                    status = "Uygun ilan bulunamadı. Şehir ve '->' ok formatını kontrol edin."
                                }
                                else -> {
                                    isError = true
                                    status = "Yerel sunucuya bağlanılamadı. Port 3000 ve adb reverse bağlantısını kontrol edin."
                                }
                            }
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("İşleniyor…")
                } else {
                    Text("İlanları Gönder")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                onClick = onDismiss
            ) {
                Text("Kapat")
            }
        }
    )
}

package com.example.test.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConsentScreen(onContinue: (productAnalyticsEnabled: Boolean, parserSamplesEnabled: Boolean) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Yükio Demo", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(20.dp))
            Text("Örnek ilanlarla ekranları, filtreleri ve araç profilini deneyebilirsin. " +
                "Demo için WhatsApp izni, API anahtarı veya ödeme gerekmez.")
            Spacer(Modifier.height(16.dp))
            Text("Bildirim erişimini ayrıca açarsan WhatsApp bildirimleri cihazda işlenir; " +
                "maskelenmiş metin yalnız bilgisayarında çalıştırdığın yerel demo sunucusuna gönderilir. " +
                "Demo gerçek Yükio parser'ını içermez. AI, telemetri, örnek toplama ve satın alma kapalıdır. " +
                "Denemelerde sentetik mesaj kullan; maskeleme bütün kişisel verileri yakalamayabilir.")
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onContinue(false, false) }) { Text("Demoyu aç") }
        }
    }
}

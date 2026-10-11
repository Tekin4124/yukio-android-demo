package com.example.test.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.test.notification.NotificationHealthStatus
import com.example.test.notification.installedWhatsAppApps
import com.example.test.notification.launchWhatsApp
import com.example.test.notification.openBatteryOptimizationSettings
import com.example.test.notification.openNotificationListenerSettings
import com.example.test.notification.openOwnNotificationSettings
import com.example.test.notification.openWhatsAppNotificationSettings
import com.example.test.notification.readNotificationHealthStatus
import com.example.test.telemetry.ContentlessTelemetry
import com.example.test.telemetry.TelemetryEvents

@Composable
fun NotificationHealthScreen(
    onFinish: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember(context) {
        mutableStateOf(readNotificationHealthStatus(context))
    }
    var previewChecked by remember { mutableStateOf(false) }
    var groupsChecked by remember { mutableStateOf(false) }
    val whatsAppApps = remember(context) { installedWhatsAppApps(context) }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                status = readNotificationHealthStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri"
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bildirim sağlığı",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "İlanların eksiksiz yakalanması için bu kontrolleri bir kez tamamla.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AutomaticHealthCheck(
                title = "WhatsApp bildirim erişimi",
                detail = if (status.listenerEnabled) {
                    "Açık; Yükio gelen WhatsApp bildirimlerini görebilir."
                } else {
                    "Kapalı; bu durumda hiçbir ilan yakalanamaz."
                },
                healthy = status.listenerEnabled,
                icon = Icons.Default.NotificationsActive,
                actionLabel = if (status.listenerEnabled) "Ayarı aç" else "Erişimi aç",
                onAction = {
                    ContentlessTelemetry.record(
                        context,
                        TelemetryEvents.funnel("notification_access_opened")
                    )
                    openNotificationListenerSettings(context)
                }
            )

            AutomaticHealthCheck(
                title = "Pil kısıtlaması",
                detail = if (status.batteryUnrestricted) {
                    "Kısıtlanmamış; dinleyicinin arka planda kesilme riski daha düşük."
                } else {
                    "Pil optimizasyonu dinleyiciyi uyutabilir. Listede Yükio'yu kısıtlanmamış yap."
                },
                healthy = status.batteryUnrestricted,
                icon = Icons.Default.BatterySaver,
                actionLabel = if (status.batteryUnrestricted) "Ayarı aç" else "Pil ayarına git",
                onAction = { openBatteryOptimizationSettings(context) }
            )

            AutomaticHealthCheck(
                title = "Yükio eşleşme bildirimleri",
                detail = if (status.appNotificationsEnabled) {
                    "Açık; aracına uygun yük bulunduğunda bildirim gösterilebilir."
                } else {
                    "Kapalı; ilan yakalansa bile Yükio sana haber veremez."
                },
                healthy = status.appNotificationsEnabled,
                icon = Icons.Default.NotificationsActive,
                actionLabel = "Bildirim ayarı",
                onAction = { openOwnNotificationSettings(context) }
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "WhatsApp içi kontroller",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Android bu iki ayarı Yükio'ya göstermiyor; WhatsApp'ta elle doğrulaman gerekiyor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ManualCheckRow(
                        checked = previewChecked,
                        onCheckedChange = { previewChecked = it },
                        text = "Bildirim içeriği/önizlemesi açık."
                    )
                    ManualCheckRow(
                        checked = groupsChecked,
                        onCheckedChange = { groupsChecked = it },
                        text = "İlan grupları sessize alınmamış ve grup bildirimleri açık."
                    )
                    whatsAppApps.forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    openWhatsAppNotificationSettings(context, app.packageName)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${app.label} bildirimi")
                            }
                            OutlinedButton(
                                onClick = { launchWhatsApp(context, app.packageName) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("${app.label}'ı aç")
                            }
                        }
                    }
                    if (whatsAppApps.isEmpty()) {
                        Text(
                            text = "Bu cihazda WhatsApp paketi bulunamadı.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (!status.automaticChecksPassed) {
                Text(
                    text = "Otomatik kontrollerin hepsi yeşil değil. Şimdilik geçebilirsin; ana ekranda uyarı görünmeye devam eder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onFinish,
                enabled = previewChecked && groupsChecked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Kontrolleri tamamladım")
            }
            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Şimdilik geç")
            }
        }
    }
}

@Composable
private fun AutomaticHealthCheck(
    title: String,
    detail: String,
    healthy: Boolean,
    icon: ImageVector,
    actionLabel: String,
    onAction: () -> Unit
) {
    val statusColor = if (healthy) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = statusColor)
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (healthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = statusColor
                )
            }
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ManualCheckRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

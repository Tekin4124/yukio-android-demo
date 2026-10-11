package com.example.test.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.FileProvider
import com.example.test.BuildConfig
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.ParseDebugLogEntity
import com.example.test.util.AuditExporter
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val PhonePanelSurface = Color(0xFF101314)
private val PhonePanelCard = Color(0xFF171B1D)
private val PhonePanelCell = Color(0xFF0A0D0E)
private val PhonePanelBorder = Color(0xFF2D363A)
private val PhonePanelMuted = Color(0xFF9AA4AA)
private val PhonePrettyGson = GsonBuilder().setPrettyPrinting().create()
private const val PHONE_REVIEW_LIMIT = 1_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParseDebugPanelScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val database = remember(context) { AppDatabase.getInstance(context.applicationContext) }
    val dao = remember(database) { database.parseDebugLogDao() }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val logs by remember(dao) { dao.getRecentFlow(PHONE_REVIEW_LIMIT) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var onlyUnreviewed by rememberSaveable { mutableStateOf(true) }
    val unreviewedCount = logs.count { it.reviewStatus == ParseDebugLogEntity.REVIEW_UNREVIEWED }
    val correctCount = logs.count { it.reviewStatus == ParseDebugLogEntity.REVIEW_CORRECT }
    val incorrectCount = logs.count { it.reviewStatus == ParseDebugLogEntity.REVIEW_INCORRECT }
    val uncertainCount = logs.count { it.reviewStatus == ParseDebugLogEntity.REVIEW_UNCERTAIN }
    val visibleLogs = if (onlyUnreviewed) {
        logs.filter { it.reviewStatus == ParseDebugLogEntity.REVIEW_UNREVIEWED }
    } else {
        logs
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Parse Panel", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Bekleyen $unreviewedCount · Doğru $correctCount · Yanlış $incorrectCount · Belirsiz $uncertainCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                },
                actions = {
                    if (BuildConfig.DEBUG) {
                        TextButton(onClick = { onlyUnreviewed = !onlyUnreviewed }) {
                            Text(if (onlyUnreviewed) "Tümü" else "Bekleyen")
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val file = AuditExporter.export(
                                        context = context.applicationContext,
                                        db = database
                                    )
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Audit JSON: ${file.absolutePath}",
                                        actionLabel = "Paylaş",
                                        withDismissAction = true
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        shareAuditFile(context, file)
                                    }
                                }
                            }
                        ) {
                            Text("Audit JSON Export")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = PhonePanelSurface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (visibleLogs.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = PhonePanelCard,
                        border = BorderStroke(1.dp, PhonePanelBorder)
                    ) {
                        Text(
                            text = if (logs.isEmpty()) {
                                "Henüz parse log yok. WhatsApp'tan gelen mesajlar burada ham metin ve parser sonucu ile görünür."
                            } else {
                                "Bekleyen kayıt kalmadı. Tümü düğmesinden önceki kararlarını görebilirsin."
                            },
                            modifier = Modifier.padding(16.dp),
                            color = PhonePanelMuted
                        )
                    }
                }
            } else {
                items(visibleLogs, key = { it.id }) { log ->
                    PhoneParseLogCard(
                        log = log,
                        onReview = { status, note, reviewedAt ->
                            scope.launch {
                                dao.updateReview(
                                    id = log.id,
                                    status = status,
                                    note = note,
                                    reviewedAt = reviewedAt
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun shareAuditFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Audit JSON Paylaş"))
}

@Composable
private fun PhoneParseLogCard(
    log: ParseDebugLogEntity,
    onReview: (status: String, note: String?, reviewedAt: Long?) -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale("tr", "TR")) }
    val createdText = remember(log.createdAt) { timeFormat.format(Date(log.createdAt)) }
    val jsonText = remember(log.apiResponseJson) { prettyJson(log.apiResponseJson) }
    val horizontalScroll = rememberScrollState()
    var reviewNote by rememberSaveable(log.id, log.reviewNote) {
        mutableStateOf(log.reviewNote.orEmpty())
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = PhonePanelCard),
        border = BorderStroke(1.dp, PhonePanelBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.sourceGroup,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = createdText,
                        style = MaterialTheme.typography.labelSmall,
                        color = PhonePanelMuted
                    )
                }
                StatusPill(log.status)
            }

            Text(
                text = "Manuel karar: ${reviewLabel(log.reviewStatus)}",
                style = MaterialTheme.typography.labelMedium,
                color = reviewColor(log.reviewStatus),
                fontWeight = FontWeight.SemiBold
            )

            HorizontalDivider(color = PhonePanelBorder)

            Row(
                modifier = Modifier.horizontalScroll(horizontalScroll),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PhoneDebugCell("Gelen WhatsApp", log.rawMessage)
                PhoneDebugCell("Temizlenmis", log.sanitizedMessage)
                PhoneDebugCell("AI JSON", jsonText)
            }

            if (!log.error.isNullOrBlank()) {
                Text(
                    text = "Hata: ${log.error}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB4AB)
                )
            }

            HorizontalDivider(color = PhonePanelBorder)

            Text(
                text = "Parser doğrulaması",
                style = MaterialTheme.typography.labelMedium,
                color = PhonePanelMuted,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = reviewNote,
                onValueChange = { reviewNote = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Not (hangi alan yanlış, neden?)") },
                minLines = 1,
                maxLines = 3
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = {
                        onReview(
                            ParseDebugLogEntity.REVIEW_CORRECT,
                            reviewNote.trim().takeIf { it.isNotEmpty() },
                            System.currentTimeMillis()
                        )
                    }
                ) { Text("Doğru") }
                TextButton(
                    onClick = {
                        onReview(
                            ParseDebugLogEntity.REVIEW_INCORRECT,
                            reviewNote.trim().takeIf { it.isNotEmpty() },
                            System.currentTimeMillis()
                        )
                    }
                ) { Text("Parser yanlış") }
                TextButton(
                    onClick = {
                        onReview(
                            ParseDebugLogEntity.REVIEW_UNCERTAIN,
                            reviewNote.trim().takeIf { it.isNotEmpty() },
                            System.currentTimeMillis()
                        )
                    }
                ) { Text("Belirsiz") }
                if (log.reviewStatus != ParseDebugLogEntity.REVIEW_UNREVIEWED) {
                    TextButton(
                        onClick = {
                            reviewNote = ""
                            onReview(ParseDebugLogEntity.REVIEW_UNREVIEWED, null, null)
                        }
                    ) { Text("Kararı kaldır") }
                }
            }
        }
    }
}

private fun reviewLabel(status: String): String = when (status) {
    ParseDebugLogEntity.REVIEW_CORRECT -> "Doğru"
    ParseDebugLogEntity.REVIEW_INCORRECT -> "Parser yanlış"
    ParseDebugLogEntity.REVIEW_UNCERTAIN -> "Belirsiz"
    else -> "Bekliyor"
}

private fun reviewColor(status: String): Color = when (status) {
    ParseDebugLogEntity.REVIEW_CORRECT -> Color(0xFF9AD8B3)
    ParseDebugLogEntity.REVIEW_INCORRECT -> Color(0xFFFFB4AB)
    ParseDebugLogEntity.REVIEW_UNCERTAIN -> Color(0xFFFFD38A)
    else -> PhonePanelMuted
}

@Composable
private fun PhoneDebugCell(title: String, text: String) {
    val verticalScroll = rememberScrollState()
    Column(
        modifier = Modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = PhonePanelMuted,
            fontWeight = FontWeight.SemiBold
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(PhonePanelCell, RoundedCornerShape(6.dp))
                .verticalScroll(verticalScroll)
                .padding(10.dp)
        ) {
            SelectionContainer {
                Text(
                    text = text.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when {
        status.contains("dropped", ignoreCase = true) -> Color(0xFFFFB4AB)
        status.contains("success", ignoreCase = true) -> Color(0xFF9AD8B3)
        status.contains("retry", ignoreCase = true) -> Color(0xFFFFD38A)
        status.contains("dead", ignoreCase = true) -> Color(0xFFFFB4AB)
        status.contains("ignored", ignoreCase = true) -> Color(0xFFAEC6FF)
        else -> Color(0xFFD0C5FF)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1
        )
    }
}

private fun prettyJson(raw: String?): String {
    if (raw.isNullOrBlank()) return "JSON yok"
    return runCatching {
        PhonePrettyGson.toJson(JsonParser().parse(raw))
    }.getOrElse { raw }
}

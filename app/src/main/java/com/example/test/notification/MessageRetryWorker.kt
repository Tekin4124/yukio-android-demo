package com.example.test.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.test.BuildConfig
import com.example.test.billing.EntitlementRuntime
import com.example.test.data.ConsentStore
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.FailedMessage
import com.example.test.data.local.ParseDebugLogDao
import com.example.test.data.local.ParseDebugLogEntity
import com.example.test.data.local.WhatsappSourceDecision
import com.example.test.data.remote.Ilan
import com.example.test.data.remote.LoadParserApi
import com.example.test.data.remote.ParseResult
import com.example.test.data.remote.ParserSampleFeedback
import com.example.test.util.LogisticSourceFilter
import com.example.test.util.PiiSanitizer
import com.example.test.telemetry.ContentlessTelemetry
import com.example.test.telemetry.ParseTelemetryResult
import com.example.test.telemetry.TelemetryEvents
import com.example.test.telemetry.TelemetryMilestone
import kotlinx.coroutines.flow.first
import timber.log.Timber

class MessageRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(applicationContext)
        val failedMessageDao = database.failedMessageDao()
        val loadOfferDao = database.loadOfferDao()
        val parseDebugLogDao = database.parseDebugLogDao()
        val now = System.currentTimeMillis()

        if (!ConsentStore(applicationContext).isAccepted.first()) {
            failedMessageDao.deleteAll()
            Timber.tag(TAG).d("Onay yok, retry kuyrugu temizlendi")
            return Result.success()
        }

        if (!NotificationManagerCompat.getEnabledListenerPackages(applicationContext).contains(applicationContext.packageName)) {
            failedMessageDao.deleteAll()
            Timber.tag(TAG).d("Bildirim erisimi yok, retry kuyrugu temizlendi")
            return Result.success()
        }

        // Bekleyen kimliksiz sample etkileşimlerini fırsattan istifade gönder;
        // bu tercih ücretli erişimden bağımsızdır ve kullanıcı geri bildirimi
        // abonelik süresi bitti diye outbox'ta kilitli kalmamalıdır.
        ParserSampleFeedback.flushPending(applicationContext)

        if (!EntitlementRuntime.hasAccessOrDefault()) {
            Timber.tag(TAG).d("Entitlement yok, retry ertelendi")
            return Result.success()
        }

        // Retry edilen parse istekleri sample taşımaz: aynı mesaj ikinci kez
        // kalıcı örnek olarak yüklenmez.
        failedMessageDao.getAll(20).forEach { failedMessage ->
            val neededMinutes = backoffMinutesFor(failedMessage.retryCount)
            if (now - failedMessage.lastRetryTime < neededMinutes * 60_000L) {
                return@forEach
            }

            // Listener ile AYNI kaynak karari: once kullanicinin elle verdigi
            // ALLOW/BLOCK kararina bak, yoksa anahtar kelime filtresine dus.
            // (Eski hali sadece kelime filtresine bakiyordu ve elle izin verilen
            // ama adinda lojistik kelime gecmeyen gruplarin mesajlarini siliyordu.)
            if (!isAllowedWhatsappSource(database, failedMessage.groupName)) {
                Timber.tag(TAG).d("Lojistik kaynak degil, retry silindi")
                failedMessageDao.delete(failedMessage)
                return@forEach
            }

            if (failedMessage.retryCount >= MAX_RETRY_COUNT) {
                recordDeadLetterLog(parseDebugLogDao, failedMessage)
                failedMessageDao.delete(failedMessage)
                return@forEach
            }

            val parseStartedAtNanos = System.nanoTime()
            val parseResult = LoadParserApi.parseIlan(failedMessage.messageText)
            recordParseTelemetry(parseResult, parseStartedAtNanos)
            when (val result = parseResult) {
                is ParseResult.Success -> {
                    val devicePhones = PiiSanitizer.decodePhones(failedMessage.devicePhones)
                    val deviceNames = PiiSanitizer.decodeNames(failedMessage.deviceNames)
                    var savedOfferCount = 0
                    var skippedOfferCount = 0
                    val notificationGate = MessageNotificationGate()
                    result.ilanlar.forEach { ilan ->
                        val ilanWithPii = restoreMaskedPii(
                            ilan = ilan,
                            devicePhone = failedMessage.devicePhone,
                            devicePhones = devicePhones,
                            deviceNames = deviceNames
                        )
                        val entity = LoadOfferMapper.fromIlanOrNull(
                            ilan = ilanWithPii,
                            rawMessage = failedMessage.messageText,
                            sourceGroup = failedMessage.groupName,
                            fallbackSender = WhatsappNotificationPrivacy.HIDDEN_SENDER,
                            createdAt = failedMessage.timestamp
                        ) ?: run {
                            skippedOfferCount++
                            return@forEach
                        }
                        if (entity.phone.isNotBlank()) {
                            val updatedPhoneCount = loadOfferDao.updateMissingPhoneForRawMessage(
                                rawMessage = entity.rawMessage,
                                phone = entity.phone
                            )
                            if (updatedPhoneCount > 0) {
                                savedOfferCount += updatedPhoneCount
                                return@forEach
                            }
                        }
                        val insertedId = loadOfferDao.insertDistinct(entity)
                        if (insertedId == -1L) {
                            skippedOfferCount++
                        } else {
                            savedOfferCount++
                            // Listener yolundaki davranışla aynı: retry ile kurtarılan
                            // ilan da "Bana göre" uygunluk kuralını geçerse bildirim atar.
                            val notified = notificationGate.tryNotify {
                                MatchNotificationHelper.maybeNotifyMatch(
                                    context = applicationContext,
                                    database = database,
                                    load = entity.copy(id = insertedId)
                                )
                            }
                            if (notified) {
                                ContentlessTelemetry.recordOnce(
                                    applicationContext,
                                    TelemetryMilestone.FIRST_MATCH,
                                    TelemetryEvents.funnel("first_match")
                                )
                            }
                        }
                    }
                    recordParseDebugLog(
                        parseDebugLogDao = parseDebugLogDao,
                        failedMessage = failedMessage,
                        apiResponseJson = result.rawJson,
                        status = statusWithParserSource("retry_success", result.parserSource),
                        savedOfferCount = savedOfferCount,
                        skippedOfferCount = skippedOfferCount,
                        error = null
                    )
                    if (savedOfferCount > 0) {
                        EntitlementRuntime.recordSuccessfulParse()
                        ContentlessTelemetry.recordOnce(
                            applicationContext,
                            TelemetryMilestone.FIRST_PARSE_SUCCESS,
                            TelemetryEvents.funnel("first_parse_success")
                        )
                    }
                    failedMessageDao.delete(failedMessage)
                    Timber.tag(TAG).d("Retry basarili: %s", failedMessage.id)
                }
                is ParseResult.NoLogisticContent -> {
                    recordParseDebugLog(
                        parseDebugLogDao = parseDebugLogDao,
                        failedMessage = failedMessage,
                        apiResponseJson = result.rawJson,
                        status = statusWithParserSource("retry_no_parse", result.parserSource),
                        savedOfferCount = 0,
                        skippedOfferCount = 0,
                        error = "Parser lojistik ilan cikarmadi"
                    )
                    markRetryFailed(failedMessageDao, parseDebugLogDao, failedMessage)
                }
                is ParseResult.Retryable -> {
                    Timber.tag(TAG).e("Retryable retry hatasi")
                    recordParseDebugLog(
                        parseDebugLogDao = parseDebugLogDao,
                        failedMessage = failedMessage,
                        apiResponseJson = null,
                        status = "retry_retryable",
                        savedOfferCount = 0,
                        skippedOfferCount = 0,
                        error = result.cause.message
                    )
                    markRetryFailed(failedMessageDao, parseDebugLogDao, failedMessage)
                }
                is ParseResult.BadResponse -> {
                    Timber.tag(TAG).e("Bad response retry hatasi")
                    recordParseDebugLog(
                        parseDebugLogDao = parseDebugLogDao,
                        failedMessage = failedMessage,
                        apiResponseJson = null,
                        status = "retry_bad_response",
                        savedOfferCount = 0,
                        skippedOfferCount = 0,
                        error = result.cause.message
                    )
                    markRetryFailed(failedMessageDao, parseDebugLogDao, failedMessage)
                }
            }
        }

        return Result.success()
    }

    private suspend fun markRetryFailed(
        failedMessageDao: com.example.test.data.local.FailedMessageDao,
        parseDebugLogDao: ParseDebugLogDao,
        failedMessage: FailedMessage
    ) {
        val nextRetryCount = failedMessage.retryCount + 1
        if (nextRetryCount >= MAX_RETRY_COUNT) {
            recordDeadLetterLog(parseDebugLogDao, failedMessage)
            failedMessageDao.delete(failedMessage)
        } else {
            failedMessageDao.insert(
                failedMessage.copy(
                    retryCount = nextRetryCount,
                    lastRetryTime = System.currentTimeMillis()
                )
            )
        }
    }

    private fun backoffMinutesFor(retryCount: Int): Long {
        return when (retryCount) {
            0 -> 0
            1 -> 15
            2 -> 30
            3 -> 60
            4 -> 120
            else -> 120
        }
    }

    private fun recordParseTelemetry(result: ParseResult, startedAtNanos: Long) {
        val latencyMillis = ((System.nanoTime() - startedAtNanos) / 1_000_000L)
            .coerceAtLeast(0L)
        val outputCount = if (result is ParseResult.Success) result.ilanlar.size else 0
        val parserSource = when (result) {
            is ParseResult.Success -> result.parserSource
            is ParseResult.NoLogisticContent -> result.parserSource
            else -> null
        }
        val resultCode = when (result) {
            is ParseResult.Success -> ParseTelemetryResult.SUCCESS
            is ParseResult.NoLogisticContent -> ParseTelemetryResult.EMPTY
            is ParseResult.Retryable -> ParseTelemetryResult.RETRYABLE_ERROR
            is ParseResult.BadResponse -> ParseTelemetryResult.INVALID_RESPONSE
        }
        ContentlessTelemetry.record(
            applicationContext,
            TelemetryEvents.parseAttempt(
                processor = TelemetryEvents.processorFromParserSource(parserSource),
                result = resultCode,
                latencyMillis = latencyMillis,
                outputCount = outputCount
            )
        )
        ContentlessTelemetry.record(
            applicationContext,
            TelemetryEvents.candidateBatch(outputCount)
        )
    }

    private suspend fun recordDeadLetterLog(
        parseDebugLogDao: ParseDebugLogDao,
        failedMessage: FailedMessage
    ) {
        try {
            val log = ParseDebugLogEntity(
                contentHash = failedMessage.contentHash,
                sourceGroup = failedMessage.groupName,
                rawMessage = failedMessage.messageText,
                sanitizedMessage = failedMessage.messageText,
                apiResponseJson = null,
                status = "dead_letter",
                savedOfferCount = 0,
                skippedOfferCount = 0,
                error = "MAX_RETRY_REACHED",
                receivedAt = failedMessage.timestamp,
                createdAt = System.currentTimeMillis()
            )
            parseDebugLogDao.insert(log)
            if (BuildConfig.DEBUG) {
                ParseDebugPanelReporter.report(log)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("Dead letter parse debug log kaydedilemedi")
        }
    }

    private suspend fun recordParseDebugLog(
        parseDebugLogDao: ParseDebugLogDao,
        failedMessage: FailedMessage,
        apiResponseJson: String?,
        status: String,
        savedOfferCount: Int,
        skippedOfferCount: Int,
        error: String?
    ) {
        try {
            val log = ParseDebugLogEntity(
                contentHash = failedMessage.contentHash,
                sourceGroup = failedMessage.groupName,
                rawMessage = failedMessage.messageText,
                sanitizedMessage = failedMessage.messageText,
                apiResponseJson = apiResponseJson,
                status = status,
                savedOfferCount = savedOfferCount,
                skippedOfferCount = skippedOfferCount,
                error = error,
                receivedAt = failedMessage.timestamp
            )
            parseDebugLogDao.insert(log)
            if (BuildConfig.DEBUG) {
                ParseDebugPanelReporter.report(log)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("Retry parse debug log kaydedilemedi")
        }
    }

    private fun isMissingOrMaskedPhone(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        return text.isBlank() ||
            text.contains("TELEFON", ignoreCase = true) ||
            text.none { it.isDigit() }
    }

    private fun statusWithParserSource(status: String, parserSource: String?): String {
        val source = parserSource?.takeIf { it.isNotBlank() } ?: return status
        return "${status}_$source"
    }

    private fun restoreMaskedPii(
        ilan: Ilan,
        devicePhone: String?,
        devicePhones: Map<String, String>,
        deviceNames: Map<String, String>
    ): Ilan {
        val restoredYetkili = PiiSanitizer.restoreNames(ilan.yetkili, deviceNames)
        val restoredFirma = PiiSanitizer.restoreNames(ilan.firma, deviceNames)
        val fallbackName = deviceNames.values.singleOrNull()
        return ilan.copy(
            telefon = PiiSanitizer.restorePhone(ilan.telefon, devicePhones, devicePhone),
            yetkili = restoredYetkili.takeUnless { isMissingOrMaskedName(it) } ?: fallbackName,
            firma = restoredFirma.takeUnless { isMissingOrMaskedName(it) }
        )
    }

    private fun isMissingOrMaskedName(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        return text.isBlank() ||
            text.contains("ISIM", ignoreCase = true) ||
            text.contains("İSİM", ignoreCase = true)
    }

    private suspend fun isAllowedWhatsappSource(database: AppDatabase, groupName: String): Boolean {
        val cleanName = WhatsappNotificationPrivacy.cleanSourceName(groupName)
        val normalizedName = LogisticSourceFilter.normalize(cleanName)
        val savedSource = database.whatsappSourceDao().getByNormalizedName(normalizedName)
        // Listener nötr bir kaynak adını güçlü mesaj-gövdesi sinyaliyle kabul
        // etmiş olabilir. recordSeen(ALLOWED) bu kararı içeriksiz taşır; retry
        // aynı mesajı sırf grup adında anahtar kelime yok diye silmemeli.
        return resolveWhatsappSourceAllowed(
            manualMode = savedSource?.mode,
            sourceLooksLogistic = LogisticSourceFilter.isAllowed(groupName),
            messageLooksLogistic = savedSource?.lastDecision == WhatsappSourceDecision.ALLOWED
        )
    }

    private companion object {
        const val TAG = "MessageRetryWorker"
        const val MAX_RETRY_COUNT = 5
    }
}

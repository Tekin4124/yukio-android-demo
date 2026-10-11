package com.example.test.notification

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.test.BuildConfig
import com.example.test.billing.EntitlementRuntime
import com.example.test.data.ConsentStore
import com.example.test.data.FirstRunTestOfferStore
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.FailedMessage
import com.example.test.data.local.FailedMessageDao
import com.example.test.data.local.ParseDebugLogDao
import com.example.test.data.local.ParseDebugLogEntity
import com.example.test.data.local.WhatsappSourceDecision
import com.example.test.data.local.WhatsappSourceMode
import com.example.test.data.remote.Ilan
import com.example.test.data.remote.LoadParserApi
import com.example.test.data.remote.ParseResult
import com.example.test.telemetry.ContentlessTelemetry
import com.example.test.telemetry.ListenerHealthState
import com.example.test.telemetry.NotificationCaptureLayer
import com.example.test.telemetry.NotificationCaptureState
import com.example.test.telemetry.ParseTelemetryResult
import com.example.test.telemetry.TelemetryEvents
import com.example.test.telemetry.TelemetryMilestone
import com.example.test.util.LogisticSourceFilter
import com.example.test.util.ParserSampleSanitizer
import com.example.test.util.PiiSanitizer
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

class WhatsappNotificationListener : NotificationListenerService() {

    private val supportedPackages = setOf("com.whatsapp", "com.whatsapp.w4b")

    // Coroutine icindeki beklenmedik hata (ornegin disk dolunca SQLiteFullException)
    // TUM UYGULAMAYI COKERTMESIN. Beta4 sahada tam boyle dustu: 2026-06-30'da
    // telefon deposu dolunca her bildirimde crash loop'a girdi ve gunlerce ilan
    // kaybetti. Hata loglanir, mesaj kalici retry kuyrugunda zaten durur.
    private val crashGuard = CoroutineExceptionHandler { _, error ->
        Timber.tag(TAG).e("Listener is parcasi hata verdi, yutuldu (cokme onlendi)")
        ContentlessTelemetry.record(
            applicationContext,
            TelemetryEvents.crash("notification_listener")
        )
    }
    private var serviceScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)
    private val processedMessages = ConcurrentHashMap<String, Long>()

    /**
     * Debug-only test giriş noktası. ADB broadcast ile uçtan uca pipeline ölçümü için.
     * processMessage'ı doğrudan çağırır: dedup + PII mask + parser + DB + push tetiklenir.
     * Truncation ve SBN yakalama bu yoldan ölçülmez (üst katman atlanır).
     */
    internal fun testIngest(sender: String, text: String, senderPhoneCandidate: String? = null) {
        if (!BuildConfig.DEBUG) return
        serviceScope.launch {
            try {
                val hash = generateMessageHash(text)
                processMessage(
                    sender = sender,
                    message = text,
                    senderPhoneCandidate = senderPhoneCandidate,
                    contentHash = hash,
                    receivedAt = System.currentTimeMillis(),
                    bypassRecentDuplicate = true
                )
            } catch (t: Throwable) {
                Timber.tag(TAG).e("testIngest processMessage hata; len=%d", text.length)
            }
        }
    }
    @Volatile private var consentAccepted = false
    @Volatile private var consentObserverStarted = false

    private val maxCacheSize = 2000
    private val cacheTtlMs = 2 * 60 * 60_000L
    private val duplicateWindowMs = 2 * 60 * 60_000L

    override fun onListenerConnected() {
        super.onListenerConnected()
        val wasRebound = !serviceScope.isActive
        if (wasRebound) {
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + crashGuard)
            consentObserverStarted = false
        }
        INSTANCE = this
        observeConsentAccepted()
        ContentlessTelemetry.record(
            applicationContext,
            TelemetryEvents.listenerHealth(
                if (wasRebound) ListenerHealthState.REBOUND else ListenerHealthState.CONNECTED
            )
        )
        Timber.tag(TAG).d("Listener basladi")
        activeNotifications?.forEach { notification ->
            serviceScope.launch { handleStatusBarNotification(notification) }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        ContentlessTelemetry.record(
            applicationContext,
            TelemetryEvents.listenerHealth(ListenerHealthState.DISCONNECTED)
        )
        INSTANCE = null
        serviceScope.cancel()
        consentObserverStarted = false
        Timber.tag(TAG).d("Listener kapandi")
    }

    override fun onDestroy() {
        INSTANCE = null
        serviceScope.cancel()
        consentObserverStarted = false
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        serviceScope.launch { handleStatusBarNotification(sbn) }
    }

    private fun observeConsentAccepted() {
        if (consentObserverStarted) return
        consentObserverStarted = true
        serviceScope.launch {
            val store = ConsentStore(applicationContext)
            consentAccepted = store.isAccepted.first()
            store.isAccepted.collect { accepted ->
                consentAccepted = accepted
            }
        }
    }

    private suspend fun ensureConsentAccepted(): Boolean {
        if (consentAccepted) return true
        consentAccepted = ConsentStore(applicationContext).isAccepted.first()
        return consentAccepted
    }

    private suspend fun handleStatusBarNotification(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName !in supportedPackages) return
        if (!ensureConsentAccepted()) return

        val notification = sbn.notification ?: return
        val extras = notification.extras
        val conversationTitle = extras
            ?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()
            .orEmpty()
        val notificationTitle = extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()
        val subText = extras
            ?.getCharSequence(Notification.EXTRA_SUB_TEXT)
            ?.toString()
            .orEmpty()
        val sourceCandidates = WhatsappNotificationPrivacy.sourceCandidates(
            conversationTitle = conversationTitle,
            notificationTitle = notificationTitle,
            subText = subText
        )
        val manualSource = findManualSourceCandidate(sourceCandidates)
        val title = manualSource
            ?: sourceCandidates.firstOrNull { isAllowedLogisticSource(it) }
            ?: sourceCandidates.first()
        val sourceText = listOf(title, conversationTitle, notificationTitle, subText).joinToString(" ")
        val receivedAt = sbn.postTime
        // Kaynak filtresi henuz calismadan dusen bildirimler kisisel sohbet
        // olabilir. Release'te kisi adini DB'ye yazma (KVKK ayak izi birakma);
        // debug'da teshis icin gercek ad kalir.
        val preFilterSender = if (BuildConfig.DEBUG) title else WhatsappNotificationPrivacy.HIDDEN_SENDER
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            Timber.tag(TAG).d("WhatsApp grup ozeti atlandi")
            logDroppedMessage(
                sender = preFilterSender,
                message = sourceText,
                contentHash = generateMessageHash(sourceText),
                receivedAt = receivedAt,
                status = "DROPPED_GROUP_SUMMARY",
                reason = "WhatsApp group summary notification"
            )
            return
        }
        val cleanedNotificationTitle = WhatsappNotificationPrivacy.cleanSourceName(notificationTitle)
        val senderCandidates = WhatsappNotificationPrivacy.senderNameCandidates(
            conversationTitle,
            notificationTitle,
            subText
        ) + listOfNotNull(
            cleanedNotificationTitle.takeIf {
                it.isNotBlank() &&
                    it != "WhatsApp" &&
                    !it.equals(title, ignoreCase = true) &&
                    (conversationTitle.isNotBlank() || subText.isNotBlank())
            }
        )
        val messages = buildMessagesFromNotification(notification, extras, senderCandidates)

        if (messages.isEmpty()) {
            logDroppedMessage(
                sender = preFilterSender,
                message = sourceText,
                contentHash = generateMessageHash(sourceText),
                receivedAt = receivedAt,
                status = "DROPPED_EMPTY_MESSAGES",
                reason = "Notification did not contain readable message text"
            )
            return
        }
        Timber.tag(TAG).d("WhatsApp aday bildirim; messages=%d", messages.size)
        // Kaynak adında lojistik kelime yoksa mesaj gövdesi de kaynak kararını
        // kurtarabilir; ancak kişisel sohbetleri yanlışlıkla açmamak için gövde
        // sinyali normal ön filtreden daha sıkıdır (rota + güçlü yük bağlamı).
        val hasLogisticMessageSignal = messages.any { hasStrongLogisticMessageSignal(it.text) }
        if (!isAllowedWhatsappSource(title, sourceText, hasLogisticMessageSignal)) {
            Timber.tag(TAG).d("Lojistik kaynak degil, atlandi")
            logDroppedMessage(
                sender = preFilterSender,
                message = sourceText,
                contentHash = generateMessageHash(sourceText),
                receivedAt = receivedAt,
                status = "DROPPED_SOURCE_NOT_ALLOWED",
                reason = "Source is not allowed by logistic source filter"
            )
            return
        }

        val now = System.currentTimeMillis()
        cleanupCache(now)

        messages.forEachIndexed { index, message ->
            val messageHash = generateMessageHash(message.text)
            val lastSeen = processedMessages[messageHash]
            if (lastSeen != null && now - lastSeen < duplicateWindowMs) {
                Timber.tag(TAG).d("Duplicate mesaj atlandi")
                logDroppedMessage(
                    sender = title,
                    message = message.text,
                    contentHash = messageHash,
                    receivedAt = receivedAt + index,
                    status = "DROPPED_DUPLICATE",
                    reason = "Message was seen in duplicate window"
                )
                return@forEachIndexed
            }

            processedMessages[messageHash] = now
            ContentlessTelemetry.record(
                applicationContext,
                TelemetryEvents.notificationCapture(
                    layer = message.layer,
                    state = if (NotificationPayloadQuality.isProbablyTruncated(message.text)) {
                        NotificationCaptureState.PROBABLY_TRUNCATED
                    } else {
                        NotificationCaptureState.COMPLETE
                    }
                )
            )
            Timber.tag(TAG).d("WhatsApp lojistik mesaji isleniyor; chars=%d", message.text.length)

            processMessage(
                sender = title,
                message = message.text,
                senderPhoneCandidate = message.senderHint,
                contentHash = messageHash,
                receivedAt = receivedAt + index
            )
        }
    }

    private fun processMessage(
        sender: String,
        message: String,
        senderPhoneCandidate: String?,
        contentHash: String,
        receivedAt: Long,
        bypassRecentDuplicate: Boolean = false
    ) {
        if (message.length < 8) {
            Timber.tag(TAG).d("Mesaj cok kisa, atlandi")
            logDroppedMessage(
                sender = sender,
                message = message,
                contentHash = contentHash,
                receivedAt = receivedAt,
                status = "DROPPED_TOO_SHORT",
                reason = "Message is shorter than 8 characters"
            )
            return
        }

        if (!looksLogistic(message)) {
            Timber.tag(TAG).d("Lojistik anahtar kelime yok, atlandi")
            logDroppedMessage(
                sender = sender,
                message = message,
                contentHash = contentHash,
                receivedAt = receivedAt,
                status = "DROPPED_NOT_LOGISTIC",
                reason = "Message does not look like a logistic load post"
            )
            return
        }

        // Listener keeps observing at the Android boundary, but an expired
        // trial must not enqueue, send or persist newly received content.
        if (!EntitlementRuntime.hasAccessOrDefault()) {
            Timber.tag(TAG).d("Entitlement yok, lojistik bildirim islenmedi")
            return
        }

        // KVKK: Telefonu cihaz tarafinda mask'le, AI'a maskeli metin gitsin.
        val sanitized = PiiSanitizer.sanitize(
            text = message,
            fallbackPhoneCandidate = senderPhoneCandidate
        )
        val safeMessage = sanitized.cleanText
        val devicePhone = sanitized.phones.firstOrNull()
        val devicePhones = sanitized.phoneMap
        val deviceNames = sanitized.names

        serviceScope.launch {
            val database = AppDatabase.getInstance(applicationContext)
            val loadDao = database.loadOfferDao()
            // Fresh-install test cards must never mix with actual WhatsApp
            // traffic. A valid logistics notification permanently removes them,
            // even if the parser later cannot produce a complete offer.
            FirstRunTestOfferStore(applicationContext).removeAfterFirstRealOffer(
                loadOfferDao = loadDao,
                receivedAtMillis = receivedAt
            )
            val failedMessageDao = database.failedMessageDao()
            val parseDebugLogDao = database.parseDebugLogDao()
            val duplicateSince = System.currentTimeMillis() - duplicateWindowMs
            if (!bypassRecentDuplicate &&
                parseDebugLogDao.countRecentByContentHash(contentHash, duplicateSince) > 0
            ) {
                Timber.tag(TAG).d("Son 2 saatte islenmis mesaj atlandi")
                return@launch
            }
            // Count only after the durable reconnect/dedup guard. The counter
            // itself stores/sends no title, sender, body, route, timestamp or hash.
            EntitlementRuntime.recordNotificationReceived()

            // Bildirim listener'i arka planda dondurulursa mesaj kaybolmasin:
            // once kalici retry kuyruguna yaz, basarili parse olursa asagida sil.
            // NOT: Burada acil retry worker'i KURULMAZ — parse hala devam ederken
            // 10 sn sonra ayni mesaji ikinci kez parse etmesin (cifte API cagrisi
            // + bildirim yutma yarisi). Worker yalnizca asagidaki basarisizlik
            // dallarinda kurulur; listener olurse 15 dk'lik periyodik worker toplar.
            queueFailedMessage(
                failedMessageDao = failedMessageDao,
                sender = sender,
                message = safeMessage,
                contentHash = contentHash,
                receivedAt = receivedAt,
                devicePhone = devicePhone,
                devicePhones = PiiSanitizer.encodePhones(devicePhones),
                deviceNames = PiiSanitizer.encodeNames(deviceNames)
            )

            // Kimliksiz parser örneği: yalnız ayrı opt-in açıkken ve
            // ParserSampleSanitizer fail-closed kontrolünden geçerse istekle
            // birlikte gider. Parser isteği bu akışta bir kere yapılır; retry
            // worker aynı mesajı ikinci kez sample olarak YÜKLEMEZ.
            val sampleText = if (
                ConsentStore(applicationContext).isParserSamplesEnabled.first()
            ) {
                ParserSampleSanitizer.sanitize(safeMessage).takeIf { it.ok }?.text
            } else {
                null
            }

            val parseStartedAtNanos = System.nanoTime()
            val parseResult = LoadParserApi.parseIlan(safeMessage, sampleText)
            recordParseTelemetry(parseResult, parseStartedAtNanos)
            when (val result = parseResult) {
                is ParseResult.Success -> {
                    var savedOfferCount = 0
                    var skippedOfferCount = 0
                    val notificationGate = MessageNotificationGate()
                    result.ilanlar.forEachIndexed { offerIndex, ilan ->
                        val ilanWithPii = restoreMaskedPii(
                            ilan = ilan,
                            devicePhone = devicePhone,
                            devicePhones = devicePhones,
                            deviceNames = deviceNames
                        )
                        val entity = LoadOfferMapper.fromIlanOrNull(
                            ilan = ilanWithPii,
                            rawMessage = safeMessage,  // DB'ye de maskeli kaydet, ham PII tutma
                            sourceGroup = sender,
                            fallbackSender = WhatsappNotificationPrivacy.HIDDEN_SENDER,
                            createdAt = receivedAt,
                            parserSource = result.parserSource,
                            sampleId = result.sampleId,
                            offerIndex = offerIndex
                        ) ?: run {
                            skippedOfferCount++
                            Timber.tag(TAG).d("Eksik sehir bilgisi nedeniyle ilan atlandi")
                            return@forEachIndexed
                        }
                        // Once ayni mesajin daha once telefonsuz kaydedilmis satirlarini
                        // bu telefonla guncelle (display icin). Bu, asagidaki insertDistinct'in
                        // ayni ilani duplicate olarak yakalamasini saglar; farkli yukler
                        // (notes/sehir farkli) ise yine ayri satir olarak eklenir.
                        if (entity.phone.isNotBlank()) {
                            loadDao.updateMissingPhoneForRawMessage(entity.rawMessage, entity.phone)
                        }

                        val insertedId = loadDao.insertDistinct(entity)
                        if (insertedId == -1L) {
                            skippedOfferCount++
                            Timber.tag(TAG).d("Duplicate ilan atlandi")
                        } else {
                            savedOfferCount++
                            Timber.tag(TAG).d("Ilan kaydedildi")
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
                        sender = sender,
                        message = message,
                        safeMessage = safeMessage,
                        contentHash = contentHash,
                        receivedAt = receivedAt,
                        apiResponseJson = result.rawJson,
                        status = statusWithParserSource("success", result.parserSource),
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
                    failedMessageDao.deleteByContentHash(contentHash)
                }
                is ParseResult.NoLogisticContent -> {
                    if (looksLogistic(safeMessage)) {
                        Timber.tag(TAG).w("Lojistik mesaj parse edilemedi, retry queue'ya alindi")
                        queueFailedMessage(
                            failedMessageDao = failedMessageDao,
                            sender = sender,
                            message = safeMessage,
                            contentHash = contentHash,
                            receivedAt = receivedAt,
                            devicePhone = devicePhone,
                            devicePhones = PiiSanitizer.encodePhones(devicePhones),
                            deviceNames = PiiSanitizer.encodeNames(deviceNames)
                        )
                        scheduleImmediateRetryWorker()
                        recordParseDebugLog(
                            parseDebugLogDao = parseDebugLogDao,
                            sender = sender,
                            message = message,
                            safeMessage = safeMessage,
                            contentHash = contentHash,
                            receivedAt = receivedAt,
                            apiResponseJson = result.rawJson,
                            status = statusWithParserSource("queued_no_parse", result.parserSource),
                            savedOfferCount = 0,
                            skippedOfferCount = 0,
                            error = "Parser lojistik ilan cikarmadi"
                        )
                    } else {
                        Timber.tag(TAG).d("Lojistik mesaj degil, atlandi")
                        recordParseDebugLog(
                            parseDebugLogDao = parseDebugLogDao,
                            sender = sender,
                            message = message,
                            safeMessage = safeMessage,
                            contentHash = contentHash,
                            receivedAt = receivedAt,
                            apiResponseJson = result.rawJson,
                            status = statusWithParserSource("ignored_no_logistic", result.parserSource),
                            savedOfferCount = 0,
                            skippedOfferCount = 0,
                            error = "Lojistik mesaj degil"
                        )
                        failedMessageDao.deleteByContentHash(contentHash)
                    }
                }
                is ParseResult.Retryable -> {
                    queueFailedMessage(
                        failedMessageDao = failedMessageDao,
                        sender = sender,
                        message = safeMessage,
                        contentHash = contentHash,
                        receivedAt = receivedAt,
                        devicePhone = devicePhone,
                        devicePhones = PiiSanitizer.encodePhones(devicePhones),
                        deviceNames = PiiSanitizer.encodeNames(deviceNames)
                    )
                    scheduleImmediateRetryWorker()
                    Timber.tag(TAG).e("Retryable parser hatasi, mesaj retry kuyruguna alindi")
                    recordParseDebugLog(
                        parseDebugLogDao = parseDebugLogDao,
                        sender = sender,
                        message = message,
                        safeMessage = safeMessage,
                        contentHash = contentHash,
                        receivedAt = receivedAt,
                        apiResponseJson = null,
                        status = "queued_retryable",
                        savedOfferCount = 0,
                        skippedOfferCount = 0,
                        error = result.cause.message
                    )
                }
                is ParseResult.BadResponse -> {
                    queueFailedMessage(
                        failedMessageDao = failedMessageDao,
                        sender = sender,
                        message = safeMessage,
                        contentHash = contentHash,
                        receivedAt = receivedAt,
                        devicePhone = devicePhone,
                        devicePhones = PiiSanitizer.encodePhones(devicePhones),
                        deviceNames = PiiSanitizer.encodeNames(deviceNames)
                    )
                    scheduleImmediateRetryWorker()
                    Timber.tag(TAG).e("Bad parser response, mesaj retry kuyruguna alindi")
                    recordParseDebugLog(
                        parseDebugLogDao = parseDebugLogDao,
                        sender = sender,
                        message = message,
                        safeMessage = safeMessage,
                        contentHash = contentHash,
                        receivedAt = receivedAt,
                        apiResponseJson = null,
                        status = "queued_bad_response",
                        savedOfferCount = 0,
                        skippedOfferCount = 0,
                        error = result.cause.message
                    )
                }
            }
        }
    }

    private fun buildMessagesFromNotification(
        notification: Notification,
        extras: Bundle?,
        titleSenderCandidates: List<String>
    ): List<IncomingWhatsAppMessage> {
        if (extras == null) return emptyList()

        val currentMessages = parseMessageBundles(
            bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES),
            titleSenderCandidates = titleSenderCandidates,
            layer = NotificationCaptureLayer.CURRENT_MESSAGES
        )
        val historicMessages = parseMessageBundles(
            bundles = extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES),
            titleSenderCandidates = titleSenderCandidates,
            layer = NotificationCaptureLayer.HISTORIC_MESSAGES
        )
        if (currentMessages.isNotEmpty() || historicMessages.isNotEmpty()) {
            // Historic comes first for chronology, but if WhatsApp exposes the
            // same entry in both layers the current layer wins.
            val currentHashes = currentMessages
                .mapTo(mutableSetOf()) { normalizeForHash(it.text) }
            return (
                historicMessages.filterNot { normalizeForHash(it.text) in currentHashes } +
                    currentMessages
                ).distinctBy { normalizeForHash(it.text) }
        }

        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val fallbackText = bigText.ifBlank { text }
        val fallbackLayer = if (bigText.isNotBlank()) {
            NotificationCaptureLayer.BIG_TEXT
        } else {
            NotificationCaptureLayer.SHORT_TEXT
        }
        return listOfNotNull(
            WhatsappNotificationPrivacy
                .stripKnownSenderPrefix(fallbackText, titleSenderCandidates)
                .takeIf { it.isNotBlank() }
                ?.let { cleanText ->
                    IncomingWhatsAppMessage(
                        text = cleanText,
                        senderHint = titleSenderCandidates.firstOrNull { it.isNotBlank() },
                        layer = fallbackLayer
                    )
                }
        )
    }

    private fun parseMessageBundles(
        bundles: Array<out android.os.Parcelable>?,
        titleSenderCandidates: List<String>,
        layer: NotificationCaptureLayer
    ): List<IncomingWhatsAppMessage> {
        if (bundles.isNullOrEmpty()) return emptyList()
        return bundles
            .mapNotNull { it as? Bundle }
            .mapNotNull { bundle ->
                val sender = bundle.getCharSequence("sender")?.toString().orEmpty()
                val text = bundle.getCharSequence("text")?.toString().orEmpty()
                val senderCandidates = titleSenderCandidates + sender
                WhatsappNotificationPrivacy
                    .stripKnownSenderPrefix(text, senderCandidates)
                    .takeIf { it.isNotBlank() }
                    ?.let { cleanText ->
                        IncomingWhatsAppMessage(
                            text = cleanText,
                            senderHint = sender.takeIf { it.isNotBlank() },
                            layer = layer
                        )
                    }
            }
            .distinctBy { normalizeForHash(it.text) }
    }

    private fun recordParseTelemetry(result: ParseResult, startedAtNanos: Long) {
        val latencyMillis = ((System.nanoTime() - startedAtNanos) / 1_000_000L).coerceAtLeast(0L)
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

    private fun logDroppedMessage(
        sender: String,
        message: String,
        contentHash: String,
        receivedAt: Long,
        status: String,
        reason: String
    ) {
        serviceScope.launch {
            try {
                val debugText = if (BuildConfig.DEBUG) message else ""
                val log = ParseDebugLogEntity(
                    contentHash = contentHash,
                    sourceGroup = sender,
                    rawMessage = debugText,
                    sanitizedMessage = debugText,
                    apiResponseJson = null,
                    status = status,
                    savedOfferCount = 0,
                    skippedOfferCount = 0,
                    error = reason,
                    receivedAt = receivedAt
                )
                AppDatabase.getInstance(applicationContext).parseDebugLogDao().insert(log)
                if (BuildConfig.DEBUG) {
                    ParseDebugPanelReporter.report(log)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e("Dropped debug log kaydedilemedi")
            }
        }
    }

    private suspend fun queueFailedMessage(
        failedMessageDao: FailedMessageDao,
        sender: String,
        message: String,
        contentHash: String,
        receivedAt: Long,
        devicePhone: String?,
        devicePhones: String?,
        deviceNames: String?
    ) {
        failedMessageDao.insert(
            FailedMessage(
                groupName = sender,
                sender = WhatsappNotificationPrivacy.HIDDEN_SENDER,
                messageText = message,
                timestamp = receivedAt,
                contentHash = contentHash,
                devicePhone = devicePhone,
                devicePhones = devicePhones,
                deviceNames = deviceNames,
                retryCount = 0,
                lastRetryTime = 0L
            )
        )
    }

    private fun scheduleImmediateRetryWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<MessageRetryWorker>()
            .setConstraints(constraints)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "failed_message_retry_once",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private suspend fun recordParseDebugLog(
        parseDebugLogDao: ParseDebugLogDao,
        sender: String,
        message: String,
        safeMessage: String,
        contentHash: String,
        receivedAt: Long,
        apiResponseJson: String?,
        status: String,
        savedOfferCount: Int,
        skippedOfferCount: Int,
        error: String?
    ) {
        try {
            val log = ParseDebugLogEntity(
                contentHash = contentHash,
                sourceGroup = sender,
                rawMessage = if (BuildConfig.DEBUG) message else safeMessage,
                sanitizedMessage = safeMessage,
                apiResponseJson = apiResponseJson,
                status = status,
                savedOfferCount = savedOfferCount,
                skippedOfferCount = skippedOfferCount,
                error = error,
                receivedAt = receivedAt
            )
            parseDebugLogDao.insert(log)
            if (BuildConfig.DEBUG) {
                ParseDebugPanelReporter.report(log)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e("Parse debug log kaydedilemedi")
        }
    }

    internal fun looksLogistic(message: String): Boolean {
        val text = normalizeForKeywordSearch(message)
        if (extractCityCandidates(message).size >= 2) return true
        val hasRouteMarker = message.contains(">>") ||
            message.contains("->") ||
            message.contains("\u2192") ||
            message.contains("\u27a1") ||
            message.contains("\u25b6")
        val hasStandaloneTir = Regex("(^|[^a-z0-9])tir([^a-z0-9]|$)").containsMatchIn(text)
        val hasStandaloneTl = Regex("(^|[^a-z0-9])tl([^a-z0-9]|$)").containsMatchIn(text) ||
            message.contains("\u20ba")
        val hasTonnage = Regex("(^|[^a-z0-9])\\d+\\s*(ton|t)([^a-z0-9]|$)").containsMatchIn(text)
        val hasWeightKg = Regex("\\d+\\s*kg").containsMatchIn(text)
        val hasPalStandalone = Regex("(^|[^a-z])pal([^a-z]|$)").containsMatchIn(text)
        val hasDorseSize = Regex("(^|[^a-z0-9])13\\s*[./-]?\\s*60([^a-z0-9]|$)").containsMatchIn(text) ||
            Regex("(^|[^a-z0-9])1360([^a-z0-9]|$)").containsMatchIn(text)
        val hasPhoneLikeNumber = Regex("(?<!\\d)(?:\\+?90\\s*)?0?5\\d{2}[\\s.()\\-]*\\d{3}[\\s.()\\-]*\\d{2}[\\s.()\\-]*\\d{2}(?!\\d)")
            .containsMatchIn(message)
        val cityCandidateCount = extractCityCandidates(message).size
        val hasVehicleOrLoadWord = listOf(
            // araç tipleri
            "kamyon", "kamyonet", "tir", "cekici", "tirci", "treyler",
            "yunus", "kirkayak", "kirk ayak", "tirton", "panelvan",
            // dorse / kasa tipleri
            "tente", "tenteli", "frigo", "frigorifik", "dorse", "damper",
            "kayarperde", "kayarperdeli", "kapali kasa", "acik kasa", "kasali",
            "konteyner", "container", "silobas", "silo bas", "lowbed",
            "yarimomork", "romork", "tanker",
            // yük tipleri
            "palet", "paket", "koli", "balya", "cuval",
            "hayvan", "canli hayvan", "buyukbas", "kucukbas",
            "parca", "parça", "boru", "demir", "celik", "mermer",
            "adet", "metre", "mtr", "tkr",
            // sektör terimleri
            "nakliye", "nakliyat", "lojistik", "tasima", "tasimacilik",
            "yuk", "yukleme", "yukler", "bosaltma", "bosaltir", "indirme",
            "iner", "teslim", "teker", "arac", "vasita",
            // dorse ölçü
            "13.60", "1360"
        ).any { text.contains(it) } || hasDorseSize

        // Net araç/dorse/yük terimi varsa ek context aramaya gerek yok —
        // backend zaten ikinci filtre, false-positive geçerse "boş cevap" döner.
        // Eski mantık (hasVehicleOrLoadWord && hasLogisticContext) gold-safe 100
        // mesajda 17 gerçek ilan yiyordu (örn. "AMBARLI/PENDIK/KAYARPERDELI/10 TEKER").
        val hasSingleCityPhoneLoadSignal = cityCandidateCount >= 1 &&
            hasPhoneLikeNumber &&
            (hasVehicleOrLoadWord || hasTonnage || hasDorseSize)
        val hasStructuredDashRoute = STRUCTURED_DASH_ROUTE.containsMatchIn(message)
        val hasStructuredRouteLoadContext = hasTonnage ||
            hasWeightKg ||
            hasPalStandalone ||
            hasDorseSize ||
            STRONG_ROUTE_LOAD_CONTEXT.any { text.contains(it) }

        return hasRouteMarker ||
            hasStandaloneTir ||
            hasTonnage ||
            hasDorseSize ||
            hasVehicleOrLoadWord ||
            hasWeightKg ||
            hasPalStandalone ||
            (hasStructuredDashRoute && hasStandaloneTl && hasStructuredRouteLoadContext) ||
            hasSingleCityPhoneLoadSignal
    }

    internal fun hasStrongLogisticMessageSignal(message: String): Boolean {
        if (!looksLogistic(message)) return false
        val text = normalizeForKeywordSearch(message)
        val hasRouteShape = extractCityCandidates(message).size >= 2 ||
            message.contains(">>") ||
            message.contains("->") ||
            message.contains("\u2192") ||
            message.contains("\u27a1") ||
            message.contains("\u25b6") ||
            STRUCTURED_DASH_ROUTE.containsMatchIn(message)
        val hasStrongLoadContext =
            Regex("(^|[^a-z0-9])\\d+\\s*(ton|t)([^a-z0-9]|$)").containsMatchIn(text) ||
            Regex("\\d+\\s*kg").containsMatchIn(text) ||
            Regex("(^|[^a-z])pal([^a-z]|$)").containsMatchIn(text) ||
            STRONG_ROUTE_LOAD_CONTEXT.any { text.contains(it) }
        return hasRouteShape && hasStrongLoadContext
    }

    private fun extractCityCandidates(message: String): Set<String> {
        val tokens = normalizeForKeywordSearch(message)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }
            .toSet()
        return tokens
            .mapNotNull { token -> CITY_ALIASES[token] ?: token.takeIf { it in TURKEY_CITIES } }
            .toSet()
    }

    private fun statusWithParserSource(status: String, parserSource: String?): String {
        val source = parserSource?.takeIf { it.isNotBlank() } ?: return status
        return "${status}_$source"
    }

    private fun normalizeForKeywordSearch(value: String): String {
        return LogisticSourceFilter.normalize(value)
    }

    private fun isAllowedLogisticSource(source: String): Boolean {
        return LogisticSourceFilter.isAllowed(source)
    }

    private suspend fun findManualSourceCandidate(sourceCandidates: List<String>): String? {
        val sourceDao = AppDatabase.getInstance(applicationContext).whatsappSourceDao()
        return sourceCandidates.firstOrNull { candidate ->
            val normalized = LogisticSourceFilter.normalize(candidate)
            val mode = sourceDao.getByNormalizedName(normalized)?.mode
            mode == WhatsappSourceMode.ALLOW || mode == WhatsappSourceMode.BLOCK
        }
    }

    private suspend fun isAllowedWhatsappSource(
        sourceName: String,
        sourceText: String,
        hasLogisticMessageSignal: Boolean
    ): Boolean {
        val sourceDao = AppDatabase.getInstance(applicationContext).whatsappSourceDao()
        val cleanName = WhatsappNotificationPrivacy.cleanSourceName(sourceName)
        val normalizedName = LogisticSourceFilter.normalize(cleanName)
        val savedSource = sourceDao.getByNormalizedName(normalizedName)
        val allowed = resolveWhatsappSourceAllowed(
            manualMode = savedSource?.mode,
            sourceLooksLogistic = isAllowedLogisticSource(sourceText),
            messageLooksLogistic = hasLogisticMessageSignal
        )

        val shouldTrackSource = allowed ||
            savedSource?.mode == WhatsappSourceMode.ALLOW ||
            savedSource?.mode == WhatsappSourceMode.BLOCK

        if (shouldTrackSource) {
            sourceDao.recordSeen(
                groupName = cleanName,
                normalizedName = normalizedName,
                decision = if (allowed) {
                    WhatsappSourceDecision.ALLOWED
                } else {
                    WhatsappSourceDecision.BLOCKED
                }
            )
        }

        return allowed
    }

    private fun generateMessageHash(message: String): String {
        val input = normalizeForHash(message)
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun normalizeForHash(message: String): String {
        return message.trim()
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    private fun isMissingOrMaskedPhone(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        return text.isBlank() ||
            text.contains("TELEFON", ignoreCase = true) ||
            text.none { it.isDigit() }
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

    private fun cleanupCache(now: Long) {
        val expiredKeys = processedMessages.entries
            .filter { now - it.value > cacheTtlMs }
            .map { it.key }

        expiredKeys.forEach { processedMessages.remove(it) }

        if (processedMessages.size > maxCacheSize) {
            val sortedEntries = processedMessages.entries.sortedBy { it.value }
            val toRemove = sortedEntries.take(processedMessages.size - maxCacheSize / 2)
            toRemove.forEach { processedMessages.remove(it.key) }
        }
    }

    companion object {
        @Volatile internal var INSTANCE: WhatsappNotificationListener? = null
        const val TAG = "WhatsAppListener"
        // Normalized 81 il seti. Test örnekleri:
        // - "Ankara İstanbul palet" -> iki şehir adayıyla lojistik kabul edilir.
        // - "İST -> ANK 35 ton" -> alias + rota/tonaj sinyaliyle lojistik kabul edilir.
        val TURKEY_CITIES = setOf(
            "adana", "adiyaman", "afyonkarahisar", "agri", "aksaray", "amasya", "ankara", "antalya",
            "ardahan", "artvin", "aydin", "balikesir", "bartin", "batman", "bayburt", "bilecik",
            "bingol", "bitlis", "bolu", "burdur", "bursa", "canakkale", "cankiri", "corum",
            "denizli", "diyarbakir", "duzce", "edirne", "elazig", "erzincan", "erzurum",
            "eskisehir", "gaziantep", "giresun", "gumushane", "hakkari", "hatay", "igdir",
            "isparta", "istanbul", "izmir", "kahramanmaras", "karabuk", "karaman", "kars",
            "kastamonu", "kayseri", "kilis", "kirikkale", "kirklareli", "kirsehir", "kocaeli",
            "konya", "kutahya", "malatya", "manisa", "mardin", "mersin", "mugla", "mus",
            "nevsehir", "nigde", "ordu", "osmaniye", "rize", "sakarya", "samsun", "sanliurfa",
            "siirt", "sinop", "sirnak", "sivas", "tekirdag", "tokat", "trabzon", "tunceli",
            "usak", "van", "yalova", "yozgat", "zonguldak"
        )
        val CITY_ALIASES = mapOf(
            "ist" to "istanbul",
            "ank" to "ankara",
            "antep" to "gaziantep",
            "urfa" to "sanliurfa",
            "afyon" to "afyonkarahisar",
            "maras" to "kahramanmaras"
        )
        private val STRUCTURED_DASH_ROUTE = Regex(
            pattern = """(?m)^\s*[\p{L}]{2,}(?:(?:\s*/\s*|\s+)[\p{L}]{2,}){0,2}\s+[-–—]\s+""" +
                """[\p{L}]{2,}(?:(?:\s*/\s*|\s+)[\p{L}]{2,}){0,2}(?=\s+\d)"""
        )
        private val STRONG_ROUTE_LOAD_CONTEXT = listOf(
            "kamyon", "kamyonet", "tir", "cekici", "panelvan",
            "tente", "tenteli", "frigo", "dorse", "konteyner",
            "palet", "paket", "koli", "balya", "cuval",
            "scooter", "motosiklet", "motor", "nakliye", "lojistik", "yuk"
        )
    }

    private data class IncomingWhatsAppMessage(
        val text: String,
        val senderHint: String?,
        val layer: NotificationCaptureLayer
    )
}

internal fun resolveWhatsappSourceAllowed(
    manualMode: String?,
    sourceLooksLogistic: Boolean,
    messageLooksLogistic: Boolean
): Boolean {
    return when (manualMode) {
        WhatsappSourceMode.BLOCK -> false
        WhatsappSourceMode.ALLOW -> true
        else -> sourceLooksLogistic || messageLooksLogistic
    }
}

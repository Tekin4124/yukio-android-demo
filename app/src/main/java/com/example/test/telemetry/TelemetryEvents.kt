package com.example.test.telemetry

import com.example.test.BuildConfig

internal data class TelemetryEvent(
    val name: String,
    val dimensions: Map<String, String>,
    val count: Int = 1
)

enum class TelemetryMilestone(val storageKey: String) {
    ONBOARDING_COMPLETED("onboarding_completed"),
    FIRST_PARSE_SUCCESS("first_parse_success"),
    FIRST_MATCH("first_match"),
    PURCHASE_SUCCEEDED("purchase_succeeded")
}

enum class TelemetryPermission(val wireValue: String) {
    NOTIFICATION_LISTENER("notification_listener"),
    POST_NOTIFICATIONS("post_notifications"),
    LOCATION("location")
}

enum class TelemetryPermissionState(val wireValue: String) {
    GRANTED("granted"),
    DENIED("denied"),
    NOT_REQUESTED("not_requested")
}

enum class ListenerHealthState(val wireValue: String) {
    CONNECTED("connected"),
    DISCONNECTED("disconnected"),
    REBOUND("rebound")
}

enum class NotificationCaptureLayer(val wireValue: String) {
    CURRENT_MESSAGES("current_stack"),
    HISTORIC_MESSAGES("historic_stack"),
    BIG_TEXT("big_text"),
    SHORT_TEXT("short_text")
}

enum class NotificationCaptureState(val wireValue: String) {
    COMPLETE("complete"),
    PROBABLY_TRUNCATED("probably_truncated")
}

enum class ParseProcessor(val wireValue: String) {
    DEVICE_LOCAL("device_local"),
    SERVER_DETERMINISTIC("server_deterministic"),
    SERVER_AI("server_ai")
}

enum class ParseTelemetryResult(val wireValue: String) {
    SUCCESS("success"),
    EMPTY("empty"),
    RETRYABLE_ERROR("retryable_error"),
    INVALID_RESPONSE("invalid_response")
}

internal object TelemetryEvents {
    private const val PARSER_VERSION = "v5.4.19"

    fun appOpen(androidApi: Int): TelemetryEvent = TelemetryEvent(
        name = "app_open",
        dimensions = mapOf(
            "app_version" to BuildConfig.VERSION_NAME,
            "android_api" to androidApi.toString()
        )
    )

    fun permissionState(
        permission: TelemetryPermission,
        state: TelemetryPermissionState
    ): TelemetryEvent = TelemetryEvent(
        name = "permission_state",
        dimensions = mapOf(
            "permission" to permission.wireValue,
            "state" to state.wireValue
        )
    )

    fun listenerHealth(state: ListenerHealthState): TelemetryEvent = TelemetryEvent(
        name = "listener_health",
        dimensions = mapOf(
            "state" to state.wireValue,
            "app_version" to BuildConfig.VERSION_NAME
        )
    )

    fun notificationCapture(
        layer: NotificationCaptureLayer,
        state: NotificationCaptureState
    ): TelemetryEvent = TelemetryEvent(
        name = "notification_capture",
        dimensions = mapOf(
            "layer" to layer.wireValue,
            "state" to state.wireValue,
            "app_version" to BuildConfig.VERSION_NAME
        )
    )

    fun parseAttempt(
        processor: ParseProcessor,
        result: ParseTelemetryResult,
        latencyMillis: Long,
        outputCount: Int
    ): TelemetryEvent = TelemetryEvent(
        name = "parse_attempt",
        dimensions = mapOf(
            "parser_version" to PARSER_VERSION,
            "processor" to processor.wireValue,
            "result" to result.wireValue,
            "latency_bucket" to latencyBucket(latencyMillis),
            "output_bucket" to outputBucket(outputCount)
        )
    )

    fun candidateBatch(candidateCount: Int): TelemetryEvent = TelemetryEvent(
        name = "candidate_batch",
        dimensions = mapOf("candidate_bucket" to candidateBucket(candidateCount))
    )

    fun crash(area: String): TelemetryEvent {
        require(area in CRASH_AREAS) { "Unsupported crash area" }
        return TelemetryEvent(
            name = "crash",
            dimensions = mapOf(
                "area" to area,
                "app_version" to BuildConfig.VERSION_NAME
            )
        )
    }

    fun funnel(name: String): TelemetryEvent {
        require(name in FUNNEL_EVENT_NAMES) { "Unsupported funnel event" }
        return TelemetryEvent(
            name = name,
            dimensions = mapOf("app_version" to BuildConfig.VERSION_NAME)
        )
    }

    fun processorFromParserSource(parserSource: String?): ParseProcessor = when {
        parserSource?.contains("AI", ignoreCase = true) == true -> ParseProcessor.SERVER_AI
        parserSource.isNullOrBlank() -> ParseProcessor.SERVER_DETERMINISTIC
        else -> ParseProcessor.SERVER_DETERMINISTIC
    }

    internal fun latencyBucket(milliseconds: Long): String = when {
        milliseconds < 100 -> "lt_100"
        milliseconds < 500 -> "100_499"
        milliseconds < 1_500 -> "500_1499"
        milliseconds < 5_000 -> "1500_4999"
        else -> "gte_5000"
    }

    internal fun outputBucket(count: Int): String = when {
        count <= 0 -> "0"
        count == 1 -> "1"
        count <= 5 -> "2_5"
        count <= 10 -> "6_10"
        else -> "gte_11"
    }

    internal fun candidateBucket(count: Int): String = when {
        count <= 0 -> "0"
        count <= 5 -> "1_5"
        count <= 20 -> "6_20"
        count <= 50 -> "21_50"
        else -> "gte_51"
    }

    private val FUNNEL_EVENT_NAMES = setOf(
        "onboarding_completed",
        "notification_access_opened",
        "notification_access_enabled",
        "first_parse_success",
        "first_match",
        "match_viewed",
        "call_tapped",
        "paywall_viewed",
        "purchase_started",
        "purchase_succeeded",
        "purchase_failed",
        "subscription_active",
        "subscription_grace",
        "subscription_hold",
        "subscription_expired"
    )

    private val CRASH_AREAS = setOf(
        "startup",
        "database",
        "network",
        "parser",
        "notification_listener",
        "unknown"
    )
}

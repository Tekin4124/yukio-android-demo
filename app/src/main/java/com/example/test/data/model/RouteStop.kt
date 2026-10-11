package com.example.test.data.model

import com.google.gson.annotations.SerializedName
import java.util.Locale

/**
 * One ordered pickup/delivery point returned by the parser.
 *
 * Strings are kept at the network/storage boundary so an unknown backend role
 * cannot make the complete parser response fail to deserialize.
 */
data class RouteStop(
    @SerializedName(value = "rol", alternate = ["role"])
    val role: String? = null,
    @SerializedName(value = "sira", alternate = ["sequence"])
    val sequence: Int? = null,
    @SerializedName(value = "il", alternate = ["city"])
    val city: String? = null,
    @SerializedName(value = "ilce", alternate = ["district"])
    val district: String? = null
)

enum class RouteStopRole {
    PICKUP,
    DELIVERY
}

fun RouteStop.normalizedRole(): RouteStopRole? {
    return when (
        role
            ?.uppercase(Locale.ROOT)
            ?.replace('\u0130', 'I')
            ?.replace('\u015E', 'S')
            ?.trim()
    ) {
        "ALIS", "PICKUP" -> RouteStopRole.PICKUP
        "TESLIM", "DELIVERY" -> RouteStopRole.DELIVERY
        else -> null
    }
}

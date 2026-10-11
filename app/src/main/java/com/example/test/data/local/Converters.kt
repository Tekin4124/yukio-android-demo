package com.example.test.data.local

import androidx.room.TypeConverter
import com.example.test.data.model.RouteStop
import com.google.gson.Gson

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>?): String = gson.toJson(value ?: emptyList<String>())

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return gson.fromJson(value, Array<String>::class.java)?.toList() ?: emptyList()
    }

    @TypeConverter
    fun fromRouteList(value: List<RouteItem>?): String = gson.toJson(value ?: emptyList<RouteItem>())

    @TypeConverter
    fun toRouteList(value: String?): List<RouteItem> {
        if (value.isNullOrEmpty()) return emptyList()
        return gson.fromJson(value, Array<RouteItem>::class.java)?.toList() ?: emptyList()
    }

    @TypeConverter
    fun fromRouteStopList(value: List<RouteStop>?): String? =
        value?.let(gson::toJson)

    @TypeConverter
    fun toRouteStopList(value: String?): List<RouteStop>? {
        if (value == null) return null
        if (value.isEmpty()) return emptyList()
        return runCatching {
            gson.fromJson(value, Array<RouteStop>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }
}

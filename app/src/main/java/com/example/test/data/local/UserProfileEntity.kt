package com.example.test.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    val id: Int = 1,
    val vehicleType: String,
    val trailerType: String?,
    val maxTonnage: Int,
    val height: Float?,
    val width: Float?,
    val length: Float?,
    val fuelConsumption: Float?,
    val costPerKm: Float?,
    val priceMin: Int?,
    val priceMax: Int?,
    val preferredLoadTypes: List<String>,
    val routes: List<RouteItem>,
    val isVehicleEmpty: Boolean,
    // "Şu an buradayım" — sürücünün elle seçtiği iller. Filtre ve bildirim
    // eşleşmesi yalnız bu listeyi kullanır; beta9 konum izni istemez.
    val currentCities: List<String> = emptyList(),
    // Saatlik GPS task'inden gelen son tespit edilen il. Null ise henüz yok/izin yok.
    val gpsCity: String? = null,
    val gpsCityAt: Long? = null,
    // Geriye dönük Room şeması için tutulur; beta9 migration'ı bu alanları
    // temizler ve ürün akışı bunları kullanmaz.
    val gpsAutoEnabled: Boolean = false,
    // Araca uygun yük geldiğinde push bildirim atılsın mı?
    val notifyOnVehicleMatch: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class RouteItem(
    val fromCity: String,
    val toCity: String
)

package com.example.test.notification

import com.example.test.data.remote.Ilan
import com.example.test.ui.format.inferredVehicleDisplayText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Araç tahmini alanlarının cihaz tarafı sözleşmesi:
 * - Tahmin adayları yalnız açık araç bilgisi YOKKEN saklanır.
 * - Adaylar vehicleCanonical'a asla yazılmaz ("Bana göre" tahmini gerçek
 *   araç gibi kullanmasın).
 * - Gösterim "Tahmini araç: ..." kalıbıyla, kesinlik iddia etmeden yapılır.
 */
class LoadOfferMapperVehicleInferenceTest {

    private fun ilan(
        aracTipi: String? = null,
        aracTipiKanonik: String? = null,
        vehicleCandidates: List<String>? = null,
        vehicleInferred: Boolean? = null
    ) = Ilan(
        gondericiIl = "İZMİR",
        gondericiIlce = null,
        aliciIl = "ANKARA",
        aliciIlce = null,
        aracTipi = aracTipi,
        dorseTipi = null,
        aracTipiKanonik = aracTipiKanonik,
        dorseTipiKanonik = null,
        tonaj = null,
        miktar = "4",
        miktarBirimi = "PALET",
        agirlik = null,
        yukCinsi = null,
        yukDetay = null,
        fiyat = null,
        fiyatNotu = null,
        yuklemeTarihi = null,
        firma = null,
        yetkili = null,
        telefon = null,
        vehicleCandidates = vehicleCandidates,
        vehicleInferred = vehicleInferred
    )

    private fun map(ilan: Ilan) = LoadOfferMapper.fromIlanOrNull(
        ilan = ilan,
        rawMessage = "İZMİR - ANKARA 4 PALET",
        sourceGroup = "grup",
        fallbackSender = "Gizli"
    )!!

    @Test
    fun `tahmin adaylari ayri kolonda saklanir`() {
        val entity = map(
            ilan(vehicleCandidates = listOf("kamyonet", "kamyon"), vehicleInferred = true)
        )
        assertEquals("kamyonet,kamyon", entity.vehicleInferredCandidates)
        assertNull("tahmin kanonik alana sizmamali", entity.vehicleCanonical)
        assertNull(entity.vehicleType)
    }

    @Test
    fun `acik arac varsa tahmin adaylari yok sayilir`() {
        val entity = map(
            ilan(
                aracTipi = "TIR",
                vehicleCandidates = listOf("kamyonet"),
                vehicleInferred = true
            )
        )
        assertNull(entity.vehicleInferredCandidates)
        assertEquals("TIR", entity.vehicleType)
    }

    @Test
    fun `kanonik arac varsa da tahmin saklanmaz`() {
        val entity = map(
            ilan(
                aracTipiKanonik = "kamyon",
                vehicleCandidates = listOf("kamyonet"),
                vehicleInferred = true
            )
        )
        assertNull(entity.vehicleInferredCandidates)
        assertEquals("kamyon", entity.vehicleCanonical)
    }

    @Test
    fun `vehicleInferred isareti olmadan aday listesi kabul edilmez`() {
        val entity = map(ilan(vehicleCandidates = listOf("kamyonet", "kamyon")))
        assertNull(entity.vehicleInferredCandidates)
    }

    @Test
    fun `tahmin gosterimi kesinlik iddia etmez`() {
        assertEquals(
            "Tahmini araç: Kamyonet / Kamyon",
            inferredVehicleDisplayText("kamyonet,kamyon")
        )
        assertEquals("Tahmini araç: Tır", inferredVehicleDisplayText("tır"))
        assertNull(inferredVehicleDisplayText(null))
        assertNull(inferredVehicleDisplayText("  "))
    }
}

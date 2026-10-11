package com.example.test.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.test.MainActivity
import com.example.test.R
import com.example.test.ai.ProfileLoadEligibility
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.LoadOfferEntity
import timber.log.Timber

/**
 * Araca uygun yük geldiğinde push bildirim gönderir.
 * Channel oluşturma idempotent — her çağrıda güvenle çalışır.
 */
object MatchNotificationHelper {
    private const val TAG = "MatchNotification"
    // NOT: Kanal ayarları (ses/titreşim) ilk oluşturulduktan sonra kilitlenir.
    // Özel sesi mevcut kurulumlara da uygulamak için kanal ID'si sürümlendi.
    const val CHANNEL_ID = "load_match_v2"
    private const val CHANNEL_NAME = "Araca Uygun Yük"
    private const val CHANNEL_DESC = "Profilindeki araca uygun yeni ilan geldiğinde bildirim atar."
    /**
     * Yeni eklenen ilanı kullanıcı profili ile eşleştirir ve bildirim atılıp
     * atılmayacağına karar verir. Profil yoksa veya bildirim kapalıysa sessizce çıkar.
     * Hem canlı dinleyici hem retry worker başarı yolunda bunu çağırır; ilan
     * hangi yoldan kaydedilirse kaydedilsin bildirim davranışı aynı kalır.
     */
    suspend fun maybeNotifyMatch(context: Context, database: AppDatabase, load: LoadOfferEntity): Boolean {
        val profile = database.userProfileDao().getProfileOnce() ?: return false
        if (!profile.notifyOnVehicleMatch) return false
        // Push ile liste aynı kararı versin: bildirim yalnız ilan "Bana göre"
        // açıldığında listede yer alacaksa gönderilir (araç + rota + mevcut şehir).
        if (!ProfileLoadEligibility.matchesBanaGore(load, profile)) {
            Timber.tag(TAG).d("Bildirim atlandı: ilan Bana göre filtresine uymuyor")
            return false
        }
        return notifyMatch(context = context, loadId = load.id)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        // Eski varsayılan-sesli kanalı temizle (varsa).
        manager.deleteNotificationChannel("load_match")
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.yeni_ilan}")
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
            setSound(soundUri, audioAttributes)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 120, 250)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * @param loadId DB içindeki ilan id'si — bildirime tıklayınca açılacak ilan
     */
    fun notifyMatch(context: Context, loadId: Long): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Timber.tag(TAG).d("POST_NOTIFICATIONS yok, bildirim atlandı")
                return false
            }
        }

        ensureChannel(context)

        val title = "Aracınıza uygun yeni bir ilan var"

        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("openLoadId", loadId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            loadId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = (loadId and 0x7FFFFFFFL).toInt()
        return runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        }.onFailure { error ->
            Timber.tag(TAG).w("Bildirim post edilemedi: %s", error::class.java.simpleName)
        }.getOrDefault(false)
    }
}

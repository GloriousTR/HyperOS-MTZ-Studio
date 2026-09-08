package dev.glorioustr.mtzstudio.core

import java.util.Locale

/** Exact translations for short lock-screen phrases that language identification often misreads. */
object ConversationalThemeGlossary {
    data class Match(val sourceLanguage: String, val translation: String)
    private val turkish = mapOf(
        "en" to mapOf("tap to unlock" to "Kilidi açmak için dokunun", "swipe up to unlock" to "Kilidi açmak için yukarı kaydırın", "double tap to wake" to "Ekranı uyandırmak için çift dokunun", "no notifications" to "Bildirim yok", "charging" to "Şarj ediliyor", "mi charge" to "Mi Şarj", "fully charged" to "Şarj tamamlandı", "battery" to "Pil", "settings" to "Ayarlar", "customize" to "Özelleştir", "wallpaper" to "Duvar kâğıdı", "weather" to "Hava durumu", "music" to "Müzik", "today" to "Bugün", "tomorrow" to "Yarın"),
        "de" to mapOf("zum entsperren nach oben wischen" to "Kilidi açmak için yukarı kaydırın", "keine benachrichtigungen" to "Bildirim yok", "einstellungen" to "Ayarlar", "wetter" to "Hava durumu"),
        "es" to mapOf("desliza hacia arriba para desbloquear" to "Kilidi açmak için yukarı kaydırın", "sin notificaciones" to "Bildirim yok", "ajustes" to "Ayarlar", "tiempo" to "Hava durumu"),
        "fr" to mapOf("balayez vers le haut pour déverrouiller" to "Kilidi açmak için yukarı kaydırın", "aucune notification" to "Bildirim yok", "paramètres" to "Ayarlar", "météo" to "Hava durumu"),
        "it" to mapOf("scorri verso l'alto per sbloccare" to "Kilidi açmak için yukarı kaydırın", "nessuna notifica" to "Bildirim yok", "impostazioni" to "Ayarlar", "meteo" to "Hava durumu"),
    )
    fun resolve(text: String, targetLanguage: String): Match? {
        if (targetLanguage.substringBefore('-') != "tr") return null
        val normalized = text.trim().lowercase(Locale.ROOT).trimEnd('.', '!', '?', '…')
        turkish.forEach { (source, phrases) -> phrases[normalized]?.let { return Match(source, it) } }
        return null
    }
}

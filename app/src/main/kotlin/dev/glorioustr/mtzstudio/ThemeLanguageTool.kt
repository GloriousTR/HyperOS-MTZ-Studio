package dev.glorioustr.mtzstudio

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.glorioustr.mtzstudio.core.ThemeTextLocalizer
import dev.glorioustr.mtzstudio.library.LibraryTheme
import dev.glorioustr.mtzstudio.library.ThemeLibrary
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Device-local translation; the original library source is backed up before replacement. */
internal class ThemeLanguageTool(context: Context, private val library: ThemeLibrary) {
    private val diagnostics = LiveDiagnosticsRecorder.get(context.applicationContext)

    fun translateChineseTextToSystemLanguage(theme: LibraryTheme): LibraryTheme {
        val target = TranslateLanguage.fromLanguageTag(Locale.getDefault().language) ?: TranslateLanguage.ENGLISH
        require(target != TranslateLanguage.CHINESE) { "Cihaz dili zaten Çince" }
        val output = library.newExportPath("${theme.displayName}-translated")
        val translator = com.google.mlkit.nl.translate.Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.CHINESE).setTargetLanguage(target).build(),
        )
        try {
            var modelReady = false
            var uniqueTexts = 0
            diagnostics.record("theme_language_tool_started", "İç bileşenler dahil tema metinleri taranıyor",
                mapOf("sourceTheme" to theme.id.value, "targetLanguage" to target))
            val result = ThemeTextLocalizer().rewrite(theme.archive.source, output) { text ->
                if (!modelReady) {
                    diagnostics.record("theme_language_model_loading", "Yerel çeviri dil modeli hazırlanıyor")
                    Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()), 5, TimeUnit.MINUTES)
                    modelReady = true
                    diagnostics.record("theme_language_model_ready", "Dil modeli hazır; tema çevirisi başlıyor")
                }
                val translated = Tasks.await(translator.translate(text), 30, TimeUnit.SECONDS).trim()
                uniqueTexts++
                if (uniqueTexts % 25 == 0) diagnostics.record("theme_language_progress", "Tema metinleri çevriliyor",
                    mapOf("uniqueTexts" to uniqueTexts))
                translated
            }
            require(result.translatedNodes > 0) { "Desteklenen metinlerde çeviri yapılamadı; tema değiştirilmedi (${result.skippedFiles.size} bölüm atlandı)." }
            val localized = Files.newInputStream(output).use { library.replaceTheme(theme, it) }
            diagnostics.record("theme_language_tool_completed", "Mevcut MTZ iç bileşenleriyle birlikte çevrildi",
                mapOf("sourceTheme" to theme.id.value, "targetLanguage" to target,
                    "changedFiles" to result.changedFiles.joinToString(), "translatedNodes" to result.translatedNodes,
                    "skippedFiles" to result.skippedFiles.distinct().joinToString()))
            return localized
        } catch (error: Throwable) {
            diagnostics.record("theme_language_tool_failed", "Çeviri tamamlanamadı; mevcut tema değiştirilmedi", error = error)
            throw error
        } finally {
            translator.close()
            Files.deleteIfExists(output)
        }
    }
}

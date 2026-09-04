package dev.glorioustr.mtzstudio

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import dev.glorioustr.mtzstudio.core.ThemeGlossary
import dev.glorioustr.mtzstudio.core.ThemeTextLocalizer
import dev.glorioustr.mtzstudio.library.LibraryTheme
import dev.glorioustr.mtzstudio.library.ThemeLibrary
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Device-local multilingual translation; the original library source is retained before replacement. */
internal class ThemeLanguageTool(context: Context, private val library: ThemeLibrary) {
    private val appContext = context.applicationContext
    private val diagnostics = LiveDiagnosticsRecorder.get(appContext)

    private data class TranslatorSession(val translator: Translator, var modelReady: Boolean = false)

    fun translateTextToSystemLanguage(theme: LibraryTheme): LibraryTheme {
        val locale = appContext.resources.configuration.locales[0] ?: Locale.getDefault()
        val target = translateLanguage(locale.toLanguageTag())
            ?: translateLanguage(locale.language)
            ?: TranslateLanguage.ENGLISH
        val output = library.newExportPath("${theme.displayName}-translated")
        val identifier = LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.30f).build(),
        )
        val translators = linkedMapOf<String, TranslatorSession>()
        val detectedLanguageCounts = linkedMapOf<String, Int>()
        val undetermined = linkedSetOf<String>()
        var uniqueTexts = 0

        fun identifySource(text: String): String? {
            val scriptHint = when {
                JAPANESE_SCRIPT.containsMatchIn(text) -> TranslateLanguage.JAPANESE
                KOREAN_SCRIPT.containsMatchIn(text) -> TranslateLanguage.KOREAN
                ThemeGlossary.containsChinese(text) -> TranslateLanguage.CHINESE
                else -> null
            }
            val identified = scriptHint ?: runCatching {
                Tasks.await(identifier.identifyLanguage(text.take(200)), 30, TimeUnit.SECONDS)
            }.getOrNull()
            val source = identified?.takeUnless { it == UNDETERMINED }?.let(::translateLanguage)
            if (source == null) undetermined += text
            return source
        }

        fun translate(text: String): String {
            val source = identifySource(text) ?: return text
            detectedLanguageCounts[source] = (detectedLanguageCounts[source] ?: 0) + 1
            if (source == target) return text

            // Preserve the carefully curated Chinese theme vocabulary before neural translation.
            if (source == TranslateLanguage.CHINESE) {
                ThemeGlossary.resolve(text, target)?.let { return it }
            }

            val session = translators.getOrPut(source) {
                TranslatorSession(
                    Translation.getClient(
                        TranslatorOptions.Builder()
                            .setSourceLanguage(source)
                            .setTargetLanguage(target)
                            .build(),
                    ),
                )
            }
            if (!session.modelReady) {
                diagnostics.record(
                    "theme_language_model_loading",
                    "Yerel çeviri dil modeli hazırlanıyor",
                    mapOf("sourceLanguage" to source, "targetLanguage" to target),
                )
                Tasks.await(
                    session.translator.downloadModelIfNeeded(DownloadConditions.Builder().build()),
                    5,
                    TimeUnit.MINUTES,
                )
                session.modelReady = true
                diagnostics.record(
                    "theme_language_model_ready",
                    "Dil modeli hazır",
                    mapOf("sourceLanguage" to source, "targetLanguage" to target),
                )
            }
            val translated = Tasks.await(session.translator.translate(text), 30, TimeUnit.SECONDS).trim()
            uniqueTexts++
            if (uniqueTexts % 25 == 0) {
                diagnostics.record(
                    "theme_language_progress",
                    "Tema metinleri çevriliyor",
                    mapOf("uniqueTexts" to uniqueTexts, "detectedLanguages" to detectedLanguageCounts.keys.joinToString()),
                )
            }
            val polished = ThemeGlossary.postProcessTranslation(translated, target)
            return text.takeWhile(Char::isWhitespace) + polished + text.takeLastWhile(Char::isWhitespace)
        }

        try {
            diagnostics.record(
                "theme_language_tool_started",
                "İç bileşenler dahil çok dilli tema metinleri taranıyor",
                mapOf("sourceTheme" to theme.id.value, "targetLanguage" to target),
            )
            val original = library.translationSource(theme)
            val result = ThemeTextLocalizer(
                targetLanguage = target,
                translateAllDisplayText = true,
            ).rewrite(original, output, ::translate)
            require(result.translatedNodes > 0) {
                "Hedef dilden farklı, desteklenen bir tema metni bulunamadı; tema değiştirilmedi (${result.skippedFiles.size} bölüm atlandı)."
            }
            val localized = Files.newInputStream(output).use { library.replaceTheme(theme, it) }
            library.recordTranslation(localized)
            diagnostics.record(
                "theme_language_tool_completed",
                "Mevcut MTZ çok dilli içerikleriyle birlikte çevrildi",
                mapOf(
                    "sourceTheme" to theme.id.value,
                    "targetLanguage" to target,
                    "detectedLanguages" to detectedLanguageCounts.entries.joinToString { "${it.key}:${it.value}" },
                    "undeterminedTextCount" to undetermined.size,
                    "changedFiles" to result.changedFiles.joinToString(),
                    "translatedNodes" to result.translatedNodes,
                    "unresolvedTextCount" to result.unresolvedTexts.size,
                    "unresolvedTexts" to result.unresolvedTexts.take(40).joinToString(" | "),
                    "skippedFiles" to result.skippedFiles.distinct().joinToString(),
                ),
            )
            return localized
        } catch (error: Throwable) {
            diagnostics.record("theme_language_tool_failed", "Çeviri tamamlanamadı; mevcut tema değiştirilmedi", error = error)
            throw error
        } finally {
            identifier.close()
            translators.values.forEach { it.translator.close() }
            Files.deleteIfExists(output)
        }
    }

    private fun translateLanguage(tag: String): String? {
        val normalized = when (tag.substringBefore('-').lowercase(Locale.ROOT)) {
            "in" -> "id"
            "iw" -> "he"
            else -> tag
        }
        return TranslateLanguage.fromLanguageTag(normalized)
    }

    companion object {
        private const val UNDETERMINED = "und"
        private val JAPANESE_SCRIPT = Regex("[\\p{IsHiragana}\\p{IsKatakana}]")
        private val KOREAN_SCRIPT = Regex("[\\p{IsHangul}]")
    }
}

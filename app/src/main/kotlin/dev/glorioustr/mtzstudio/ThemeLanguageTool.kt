package dev.glorioustr.mtzstudio

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dev.glorioustr.mtzstudio.library.LibraryTheme
import dev.glorioustr.mtzstudio.library.ThemeLibrary
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Updates the selected library MTZ with Chinese XML labels localized to the device language.
 *
 * Theme images and script identifiers are intentionally untouched: changing either would make
 * a visual/text-only request unsafe and can break an advanced lock screen. ML Kit downloads the
 * selected language model locally; Studio does not send MTZ contents to a translation service.
 */
internal class ThemeLanguageTool(
    context: Context,
    private val library: ThemeLibrary,
) {
    private val appContext = context.applicationContext
    private val diagnostics = LiveDiagnosticsRecorder.get(appContext)

    fun translateChineseTextToSystemLanguage(theme: LibraryTheme): LibraryTheme {
        val target = deviceLanguage()
        require(target != TranslateLanguage.CHINESE) { "Cihaz dili zaten Çince" }
        val source = theme.archive.source
        val output = library.newExportPath("${theme.displayName}-${target.uppercase(Locale.ROOT)}")
        val translator = translator(target)
        try {
            Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            var translatedNodes = 0
            var changedFiles = 0
            ZipFile(source.toFile()).use { input ->
                ZipOutputStream(Files.newOutputStream(output)).use { zip ->
                    input.entries().asSequence().forEach { entry ->
                        val bytes = if (entry.isDirectory) ByteArray(0) else input.getInputStream(entry).use { it.readBytes() }
                        val replacement = if (entry.isDirectory || !isTextResource(entry.name, bytes)) bytes else {
                            val localized = localizeXml(String(bytes, StandardCharsets.UTF_8), translator)
                            translatedNodes += localized.changed
                            if (localized.changed > 0) changedFiles++
                            localized.value.toByteArray(StandardCharsets.UTF_8)
                        }
                        val outEntry = ZipEntry(entry.name).apply { time = entry.time }
                        zip.putNextEntry(outEntry)
                        if (!entry.isDirectory) zip.write(replacement)
                        zip.closeEntry()
                    }
                }
            }
            require(translatedNodes > 0) { "Bu temada çevrilebilir Çince XML metni bulunamadı" }
            val localized = Files.newInputStream(output).use { input ->
                library.replaceTheme(theme, input)
            }
            diagnostics.record(
                "theme_language_tool_completed",
                "Tema metinleri yerel dile çevrilerek mevcut MTZ güncellendi",
                mapOf("sourceTheme" to theme.id.value, "targetLanguage" to target, "changedFiles" to changedFiles, "translatedNodes" to translatedNodes),
            )
            return localized
        } catch (error: Throwable) {
            diagnostics.record("theme_language_tool_failed", "Tema Dil Aracı tamamlanamadı", error = error)
            throw error
        } finally {
            translator.close()
            Files.deleteIfExists(output)
        }
    }

    private fun translator(target: String): Translator = com.google.mlkit.nl.translate.Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CHINESE)
            .setTargetLanguage(target)
            .build(),
    )

    private fun deviceLanguage(): String {
        val candidate = Locale.getDefault().language.lowercase(Locale.ROOT)
        return TranslateLanguage.fromLanguageTag(candidate)
            ?: TranslateLanguage.ENGLISH
    }

    private fun isTextResource(name: String, data: ByteArray): Boolean =
        name.endsWith(".xml", ignoreCase = true) && data.size <= MAX_XML_BYTES && data.containsChinese()

    private fun localizeXml(xml: String, translator: Translator): LocalizedXml {
        var changes = 0
        // Advanced lockscreen Config files store visible UI exclusively in these attributes. Keep
        // IDs, expressions, action names and every non-Chinese value unchanged.
        val attribute = Regex("""\\b(text|summary|title|description|hint)\\s*=\\s*([\"'])(.*?)\\2""", RegexOption.DOT_MATCHES_ALL)
        var result = attribute.replace(xml) { match ->
            val original = match.groupValues[3]
            if (!original.containsChinese()) match.value else {
                changes++
                "${match.groupValues[1]}=${match.groupValues[2]}${translate(original, translator)}${match.groupValues[2]}"
            }
        }
        // Standard Android-like string resources contain the label as an element body.
        val stringElement = Regex("""(<string\\b[^>]*>)(.*?)(</string>)""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        result = stringElement.replace(result) { match ->
            val original = match.groupValues[2]
            if (!original.containsChinese()) match.value else {
                changes++
                "${match.groupValues[1]}${translate(original, translator)}${match.groupValues[3]}"
            }
        }
        return LocalizedXml(result, changes)
    }

    private fun translate(value: String, translator: Translator): String =
        Tasks.await(translator.translate(value)).trim().ifBlank { value }

    private fun ByteArray.containsChinese(): Boolean = toString(StandardCharsets.UTF_8).containsChinese()
    private fun String.containsChinese(): Boolean = any { it in '\u4e00'..'\u9fff' }

    private data class LocalizedXml(val value: String, val changed: Int)

    private companion object {
        const val MAX_XML_BYTES = 1_048_576
    }
}

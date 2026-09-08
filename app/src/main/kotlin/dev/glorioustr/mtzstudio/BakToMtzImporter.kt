package dev.glorioustr.mtzstudio

import android.content.Context
import dev.glorioustr.mtzstudio.core.MtzParser
import dev.glorioustr.mtzstudio.core.ThemeVisualPolicy
import dev.glorioustr.mtzstudio.library.LibraryTheme
import dev.glorioustr.mtzstudio.library.ThemeLibrary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Reconstructs portable MTZ packages directly from a Xiaomi Themes MIUI Backup v2 archive. */
internal class BakToMtzImporter(context: Context) {
    private val appContext = context.applicationContext
    private val diagnostics = LiveDiagnosticsRecorder.get(appContext)
    private data class TarItem(val path: String, val offset: Long, val size: Long)
    private data class Resource(val id: String, val code: String)
    private data class Record(
        val id: String, val platform: Int, val version: String, val title: String,
        val author: String?, val designer: String?, val description: String?, val resources: List<Resource>,
    )

    fun importThemes(archive: ThemeManagerBakArchive, library: ThemeLibrary): List<LibraryTheme> {
        val items = index(archive)
        val portable = items.filter { it.path.endsWith(".mtz", true) && it.size > 4L }
        val imported = mutableListOf<LibraryTheme>()
        if (portable.isNotEmpty()) {
            portable.forEach { item ->
                val output = File.createTempFile("bak-portable-", ".mtz", appContext.cacheDir)
                try {
                    copyItem(archive.source, item, output)
                    output.inputStream().use { imported += library.importTheme(it, item.path.substringAfterLast('/')) }
                } finally { output.delete() }
            }
        } else {
            items.filter { ROOT_METADATA.matches(it.path) }.forEach { item ->
                val record = parseRecord(JSONObject(readItem(archive.source, item, MAX_METADATA_BYTES).toString(Charsets.UTF_8)))
                val output = File.createTempFile("bak-reconstructed-", ".mtz", appContext.cacheDir)
                try {
                    buildMtz(archive.source, items, record, output)
                    val verified = MtzParser().parse(output.toPath())
                    output.inputStream().use {
                        imported += library.importTheme(
                            it,
                            record.title,
                            includeInThemeGallery = !ThemeVisualPolicy.isFontOnly(verified),
                        )
                    }
                } finally { output.delete() }
            }
        }
        require(imported.isNotEmpty()) { "BAK içinde MTZ'ye dönüştürülebilecek tema bulunamadı" }
        diagnostics.record("bak_to_mtz_completed", "BAK içindeki temalar doğrudan MTZ kitaplığına dönüştürüldü", mapOf("count" to imported.size))
        return imported
    }

    private fun index(archive: ThemeManagerBakArchive): List<TarItem> {
        val result = mutableListOf<TarItem>()
        RandomAccessFile(archive.source, "r").use { input ->
            input.seek(archive.tarOffset)
            val header = ByteArray(BLOCK)
            while (input.read(header) == BLOCK) {
                val path = header.tarPath()
                if (path.isBlank()) break
                val size = header.octal(124, 12)
                require(size in 0..MAX_ENTRY_BYTES) { "BAK girdisi çok büyük: $path" }
                result += TarItem(path, input.filePointer, size)
                require(result.size < MAX_ENTRIES) { "BAK çok fazla girdi içeriyor" }
                input.seek(input.filePointer + padded(size))
            }
        }
        return result
    }

    private fun parseRecord(json: JSONObject): Record {
        val id = json.getString("localId").safeId()
        val resources = buildList {
            val array = json.optJSONArray("subResources") ?: JSONArray()
            repeat(array.length()) { index ->
                val value = array.getJSONObject(index)
                add(Resource(value.getString("localId").safeId(), value.getString("resourceCode").safeCode()))
            }
        }
        require(resources.isNotEmpty()) { "BAK tema kaydı bileşen içermiyor" }
        return Record(
            id = id,
            platform = json.optInt("platform", 17).coerceIn(1, 99),
            version = json.optString("version", "1.0").take(80),
            title = localized(json.optJSONObject("titles"))?.take(180) ?: "Theme ${id.take(8)}",
            author = localized(json.optJSONObject("authors"))?.take(180),
            designer = localized(json.optJSONObject("designers"))?.take(180),
            description = localized(json.optJSONObject("descriptions"))?.take(16_384),
            resources = resources,
        )
    }

    private fun buildMtz(source: File, items: List<TarItem>, record: Record, output: File) {
        val byPath = items.associateBy(TarItem::path)
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            zip.setLevel(0)
            zip.putNextEntry(ZipEntry("description.xml"))
            zip.write(description(record).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val written = hashSetOf("description.xml")
            record.resources.forEach { resource ->
                val item = byPath["$DATA_CONTENT/${resource.code}/${resource.id}.mrc"]
                    ?: error("BAK bileşeni eksik: ${resource.code}")
                if (item.size <= 0L) return@forEach
                val path = archivePath(resource.code)
                require(written.add(path.lowercase(Locale.ROOT))) { "Tekrarlanan MTZ bileşeni: $path" }
                zip.putNextEntry(ZipEntry(path))
                copyItem(source, item, zip)
                zip.closeEntry()
            }
            val previewPrefix = "$DATA_PREVIEW/${record.id}/"
            items.asSequence().filter { it.path.startsWith(previewPrefix) && it.size in 1..MAX_PREVIEW_BYTES }
                .take(MAX_PREVIEWS).forEach { item ->
                    val name = item.path.removePrefix(previewPrefix).substringAfterLast('/').replace(UNSAFE_FILE, "_")
                    val path = "preview/$name"
                    if (name.isBlank() || !written.add(path.lowercase(Locale.ROOT))) return@forEach
                    zip.putNextEntry(ZipEntry(path)); copyItem(source, item, zip); zip.closeEntry()
                }
        }
    }

    private fun description(record: Record) = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<theme>\n")
        append("<version>${xml(record.version)}</version>\n<uiVersion>${record.platform}</uiVersion>\n")
        record.author?.let { append("<author>${xml(it)}</author>\n") }
        record.designer?.let { append("<designer>${xml(it)}</designer>\n") }
        append("<title>${xml(record.title)}</title>\n")
        record.description?.let { append("<description>${xml(it)}</description>\n") }
        append("</theme>\n")
    }

    private fun archivePath(code: String) = when (code) {
        "contact" -> "com.android.contacts"
        "mms" -> "com.android.mms"
        "launcher" -> "com.miui.home"
        "statusbar" -> "com.android.systemui"
        "lockstyle" -> "lockscreen"
        "wallpaper" -> "wallpaper/default_wallpaper.jpg"
        "lockscreen" -> "wallpaper/default_lock_wallpaper.jpg"
        "fonts" -> "fonts/Roboto-Regular.ttf"
        "framework" -> "framework-res"
        "bootanimation" -> "boots/bootanimation.zip"
        else -> code
    }

    private fun localized(values: JSONObject?): String? {
        if (values == null) return null
        val locale = appContext.resources.configuration.locales[0] ?: Locale.getDefault()
        listOf("${locale.language}_${locale.country}", locale.toLanguageTag().replace('-', '_'), locale.language, "en_US", "fallback", "zh_CN")
            .forEach { key -> values.optString(key).takeIf(String::isNotBlank)?.let { return it } }
        val keys = values.keys(); while (keys.hasNext()) values.optString(keys.next()).takeIf(String::isNotBlank)?.let { return it }
        return null
    }

    private fun copyItem(source: File, item: TarItem, output: File) =
        output.outputStream().buffered().use { copyItem(source, item, it) }

    private fun copyItem(source: File, item: TarItem, output: java.io.OutputStream) {
        RandomAccessFile(source, "r").use { input ->
            input.seek(item.offset); var remaining = item.size; val buffer = ByteArray(64 * 1024)
            while (remaining > 0) { val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt()); check(count > 0) { "BAK verisi eksik" }; output.write(buffer, 0, count); remaining -= count }
        }
    }

    private fun readItem(source: File, item: TarItem, limit: Int): ByteArray {
        require(item.size in 0..limit.toLong()) { "BAK meta verisi çok büyük" }
        return ByteArray(item.size.toInt()).also { data -> RandomAccessFile(source, "r").use { it.seek(item.offset); it.readFully(data) } }
    }

    private fun ByteArray.tarPath(): String { val name = text(0, 100); val prefix = text(345, 155); return if (prefix.isBlank()) name else "$prefix/$name" }
    private fun ByteArray.text(offset: Int, length: Int) = String(this, offset, length, Charsets.UTF_8).trimEnd('\u0000')
    private fun ByteArray.octal(offset: Int, length: Int): Long = String(this, offset, length, Charsets.US_ASCII).trimEnd('\u0000', ' ').let { if (it.isBlank()) 0 else it.toLong(8) }
    private fun padded(size: Long) = ((size + BLOCK - 1) / BLOCK) * BLOCK
    private fun String.safeId() = also { require(SAFE_ID.matches(it)) { "Geçersiz BAK kimliği" } }
    private fun String.safeCode() = also { require(SAFE_CODE.matches(it)) { "Geçersiz BAK bileşeni" } }
    private fun xml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

    private companion object {
        const val BLOCK = 512
        const val MAX_ENTRIES = 30_000
        const val MAX_ENTRY_BYTES = 512L * 1024 * 1024
        const val MAX_METADATA_BYTES = 2 * 1024 * 1024
        const val MAX_PREVIEW_BYTES = 20L * 1024 * 1024
        const val MAX_PREVIEWS = 40
        const val DATA_ROOT = "apps/com.android.thememanager/ef/MIUI/theme/.data"
        const val DATA_CONTENT = "$DATA_ROOT/content"
        const val DATA_PREVIEW = "$DATA_ROOT/preview/theme"
        val ROOT_METADATA = Regex("$DATA_ROOT/meta/theme/[A-Za-z0-9._-]+\\.mrm")
        val SAFE_ID = Regex("[A-Za-z0-9._-]{1,180}")
        val SAFE_CODE = Regex("[A-Za-z0-9._-]{1,180}")
        val UNSAFE_FILE = Regex("[^A-Za-z0-9._-]")
    }
}

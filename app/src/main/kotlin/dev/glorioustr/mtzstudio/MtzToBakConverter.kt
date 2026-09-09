package dev.glorioustr.mtzstudio

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.Normalizer
import java.util.UUID
import java.util.zip.ZipFile

/** Produces a Xiaomi Themes MIUI Backup v2 archive from a portable MTZ. */
object MtzToBakConverter {
    private const val PACKAGE = "com.android.thememanager"
    private const val BLOCK = 512
    private const val MAX_ENTRY = 512L * 1024 * 1024

    data class DeviceInfo(val versionCode: Long, val sdk: Int, val installer: String, val signatures: List<String>)
    data class Metadata(val title: String, val author: String, val version: String, val description: String, val id: String = UUID.randomUUID().toString())
    private data class Component(val entry: String, val code: String, val id: String, val size: Long, val sha1: String)

    fun deviceInfo(context: Context): DeviceInfo {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        else @Suppress("DEPRECATION") pm.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }
        else @Suppress("DEPRECATION") info.signatures.orEmpty().map { it.toCharsString() }
        val installer = if (Build.VERSION.SDK_INT >= 30) runCatching { pm.getInstallSourceInfo(PACKAGE).installingPackageName.orEmpty() }.getOrDefault("")
        else @Suppress("DEPRECATION") pm.getInstallerPackageName(PACKAGE).orEmpty()
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        return DeviceInfo(versionCode, Build.VERSION.SDK_INT, installer, signatures)
    }

    fun convert(source: File, output: File, device: DeviceInfo): File {
        require(source.isFile && source.length() > 0) { "MTZ dosyası okunamıyor" }
        require(device.signatures.isNotEmpty()) { "Xiaomi Temalar imzası okunamadı" }
        val meta = inspect(source)
        val assembly = UUID.nameUUIDFromBytes("assembly:${meta.id}".toByteArray()).toString()
        output.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(output), 128 * 1024).use { out ->
            out.write("MIUI BACKUP\n2\n$PACKAGE Themes\n-1\n0\nANDROID BACKUP\n5\n0\nnone\n".toByteArray())
            val tar = Tar(out)
            val manifest = buildString {
                append("1\n$PACKAGE\n${device.versionCode}\n${device.sdk}\n${device.installer}\n0\n${device.signatures.size}\n")
                device.signatures.forEach { append(it).append('\n') }
            }.toByteArray()
            tar.bytes("apps/$PACKAGE/_manifest", manifest)
            ZipFile(source).use { zip ->
                val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
                val used = mutableSetOf<String>()
                val components = entries.mapNotNull { entry ->
                    val name = safe(entry.name)
                    val rawCode = when {
                        name.substringBeforeLast('.', name) == "wallpaper/default_wallpaper" -> "wallpaper"
                        name.substringBeforeLast('.', name) == "wallpaper/default_lock_wallpaper" -> "lockscreen"
                        name == "boots/bootanimation.zip" && zipMagic(zip, name) -> "bootanimation"
                        '/' !in name && name != "description.xml" && zipMagic(zip, name) -> normalizeCode(name)
                        else -> null
                    } ?: return@mapNotNull null
                    require(entry.size in 0..MAX_ENTRY) { "MTZ bileşeni çok büyük: $name" }
                    val code = uniqueCode(rawCode, used)
                    val componentHash = zip.getInputStream(entry).use(::sha1)
                    val componentId = UUID.nameUUIDFromBytes("${meta.id}:$code:$componentHash".toByteArray()).toString()
                    Component(name, code, componentId, entry.size, componentHash)
                }
                val root = metadata(meta, meta.id, assembly, "theme", sha1(source.inputStream()), source.length(), null, components)
                tar.bytes("apps/$PACKAGE/ef/MIUI/theme/.data/meta/theme/${meta.id}.mrm", root)
                tar.header("apps/$PACKAGE/ef/MIUI/theme/.data/content/theme/${meta.id}.mrc", 0)
                components.forEach { component ->
                    tar.bytes("apps/$PACKAGE/ef/MIUI/theme/.data/meta/${component.code}/${component.id}.mrm", metadata(meta, component.id, assembly, component.code, component.sha1, component.size, meta.id, emptyList()))
                    tar.header("apps/$PACKAGE/ef/MIUI/theme/.data/content/${component.code}/${component.id}.mrc", component.size)
                    zip.getInputStream(zip.getEntry(component.entry)).use { input -> input.copyTo(out) }
                    tar.pad(component.size)
                }
                entries.filter { it.name.startsWith("preview/") }.forEach { preview ->
                    val name = safe(preview.name).removePrefix("preview/")
                    if (name.isNotBlank()) {
                        tar.header("apps/$PACKAGE/ef/MIUI/theme/.data/preview/theme/${meta.id}/$name", preview.size)
                        zip.getInputStream(preview).use { it.copyTo(out) }
                        tar.pad(preview.size)
                    }
                }
            }
            tar.header("apps/$PACKAGE/ef/MIUI/theme/${fileName(meta.title)}", source.length())
            FileInputStream(source).use { it.copyTo(out) }
            tar.pad(source.length())
            out.write(ByteArray(BLOCK * 2))
        }
        return output
    }

    /** Path owned by Xiaomi Themes after the external-files backup domain is restored. */
    fun restoredThemePath(source: File): String {
        val title = inspect(source).title
        return "/storage/emulated/0/Android/data/$PACKAGE/files/MIUI/theme/${fileName(title)}"
    }

    private fun inspect(file: File): Metadata {
        var title = file.nameWithoutExtension; var author = "Unknown"; var version = "1.0"; var description = ""
        ZipFile(file).use { zip -> zip.getEntry("description.xml")?.let { entry ->
            val xml = zip.getInputStream(entry).bufferedReader().readText()
            title = tag(xml, "title") ?: title; author = tag(xml, "author") ?: author
            version = tag(xml, "version") ?: version; description = tag(xml, "description") ?: description
        } }
        val id = UUID.nameUUIDFromBytes("theme:${sha1(file.inputStream())}".toByteArray()).toString()
        return Metadata(title, author, version, description, id)
    }

    private fun metadata(m: Metadata, id: String, assembly: String, code: String, hash: String, size: Long, parent: String?, children: List<Component>) = buildString {
        append("{\"localId\":${json(id)},\"onlineId\":null,\"assemblyId\":${json(assembly)},\"productId\":null,")
        append("\"hash\":${json(hash)},\"platform\":17,\"size\":$size,\"updatedTime\":0,\"version\":${json(m.version)},")
        append("\"authors\":{\"fallback\":${json(m.author)}},\"designers\":{\"fallback\":${json(m.author)}},")
        append("\"titles\":{\"fallback\":${json(m.title)}},\"descriptions\":{\"fallback\":${json(m.description)}},")
        append("\"builtInThumbnails\":{\"fallback\":[]},\"builtInPreviews\":{\"fallback\":[]},\"thumbnails\":[],\"previews\":[],")
        append("\"parentResources\":[")
        if (parent != null) append("{\"localId\":${json(parent)},\"resourceCode\":\"theme\",\"extraMeta\":{}}")
        append("],\"subResources\":[")
        children.forEachIndexed { index, child -> if (index > 0) append(','); append("{\"localId\":${json(child.id)},\"resourceCode\":${json(child.code)},\"extraMeta\":{}}") }
        append("],\"extraMeta\":{},\"resourceCode\":${json(code)},\"price\":0,\"isBackUpVersion\":true,\"themeType\":0,\"miuiAdapterVersion\":\"3.3\"}")
    }.toByteArray()

    private fun normalizeCode(name: String) = when (name) { "lockscreen" -> "lockstyle"; "com.android.systemui" -> "statusbar"; "com.miui.home" -> "launcher"; "com.android.contacts" -> "contact"; "com.android.mms" -> "mms"; else -> name }
    private fun portable(code: String): String {
        val ascii = buildString { Normalizer.normalize(code, Normalizer.Form.NFKD).forEach { c -> if (Character.getType(c) != Character.NON_SPACING_MARK.toInt()) append(if (c.code in 33..126 && (c.isLetterOrDigit() || c in "._-")) c else '_') } }.replace(Regex("_+"), "_").trim('_', '.')
        return ascii.ifBlank { "custom_${sha1(code.byteInputStream()).take(12)}" }.take(64)
    }
    private fun uniqueCode(code: String, used: MutableSet<String>): String { val base = portable(code); if (used.add(base)) return base; val alt = "${base.take(48)}_${sha1(code.byteInputStream()).take(12)}"; used += alt; return alt }
    private fun zipMagic(zip: ZipFile, name: String) = zip.getInputStream(zip.getEntry(name)).use { input -> val b = ByteArray(4); input.read(b) == 4 && b.contentEquals(byteArrayOf(80, 75, 3, 4)) }
    private fun safe(name: String): String { val n = name.replace('\\', '/'); require(n.isNotBlank() && !n.startsWith('/') && n.split('/').none { it == ".." || it.isBlank() }); return n }
    private fun fileName(title: String) = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifBlank { "theme" }.take(60) + ".mtz"
    private fun tag(xml: String, name: String): String? = Regex("<$name>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    private fun json(value: String) = buildString { append('"'); value.forEach { c -> when (c) { '"', '\\' -> { append('\\'); append(c) }; '\n' -> append("\\n"); '\r' -> append("\\r"); else -> append(c) } }; append('"') }
    private fun sha1(input: java.io.InputStream): String { val d = MessageDigest.getInstance("SHA-1"); input.use { s -> val b = ByteArray(32 * 1024); while (true) { val n = s.read(b); if (n < 0) break; d.update(b, 0, n) } }; return d.digest().joinToString("") { "%02x".format(it) } }

    private class Tar(private val out: OutputStream) {
        fun bytes(name: String, data: ByteArray) { header(name, data.size.toLong()); out.write(data); pad(data.size.toLong()) }
        fun header(path: String, size: Long) {
            val h = ByteArray(BLOCK); writePath(h, path); octal(h, 100, 8, 420); octal(h, 108, 8, 1000); octal(h, 116, 8, 1000); octal(h, 124, 12, size); octal(h, 136, 12, System.currentTimeMillis() / 1000); h[156] = '0'.code.toByte(); "ustar\u0000".toByteArray().copyInto(h, 257); "00".toByteArray().copyInto(h, 263); for (i in 148 until 156) h[i] = 32; val sum = h.sumOf { it.toInt() and 255 }.toLong(); octal(h, 148, 7, sum); h[155] = 32; out.write(h)
        }
        fun pad(size: Long) { val r = (size % BLOCK).toInt(); if (r > 0) out.write(ByteArray(BLOCK - r)) }
        private fun octal(h: ByteArray, at: Int, length: Int, value: Long) { val s = value.toString(8).padStart(length - 1, '0').takeLast(length - 1); s.toByteArray().copyInto(h, at); h[at + length - 1] = 0 }
        private fun writePath(h: ByteArray, path: String) { val bytes = path.toByteArray(); if (bytes.size <= 100) { bytes.copyInto(h); return }; val split = path.indices.reversed().first { path[it] == '/' && path.substring(0, it).toByteArray().size <= 155 && path.substring(it + 1).toByteArray().size <= 100 }; path.substring(split + 1).toByteArray().copyInto(h); path.substring(0, split).toByteArray().copyInto(h, 345) }
    }
}

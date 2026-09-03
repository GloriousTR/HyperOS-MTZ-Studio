package dev.glorioustr.mtzstudio

import android.content.Context
import dev.glorioustr.mtzstudio.tester.PrivilegedCommandRunner
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.UUID

data class ThemeManagerBakArchive(
    val source: File,
    val displayName: String,
    val backupVersionCode: Long,
    val tarOffset: Long,
    val entryCount: Int,
) {
    fun discardStagedCopy() {
        source.delete()
        source.parentFile?.takeIf { it.listFiles().isNullOrEmpty() }?.delete()
    }
}

/**
 * Imports a standard MIUI Backup v2 archive created for com.android.thememanager.
 * The operation is root-only because it restores the Theme Manager's own app data.
 * Every restore starts with a root-side snapshot and validates the archive structure first.
 */
class ThemeManagerBakImporter(
    private val context: Context,
    private val commandRunner: PrivilegedCommandRunner,
) {
    private val diagnostics get() = LiveDiagnosticsRecorder.get(context)

    fun stageAndInspect(input: InputStream, displayName: String): ThemeManagerBakArchive {
        require(displayName.endsWith(".bak", ignoreCase = true)) { "Seçilen dosya .bak uzantılı değil" }
        val directory = File(context.cacheDir, "theme-manager-bak").apply { mkdirs() }
        val target = File(directory, "${UUID.randomUUID()}.bak")
        input.use { source -> target.outputStream().use(source::copyTo) }
        try {
            require(target.length() in 76..MAX_BAK_BYTES) { "BAK dosyası geçersiz boyutta" }
            val archive = inspect(target, displayName)
            diagnostics.record(
                "bak_inspected",
                "Tema Yöneticisi BAK arşivi doğrulandı",
                mapOf("name" to displayName, "versionCode" to archive.backupVersionCode, "entries" to archive.entryCount),
            )
            return archive
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    fun restore(archive: ThemeManagerBakArchive, installedVersionCode: Long, allowVersionMismatch: Boolean) {
        require(archive.backupVersionCode == installedVersionCode || allowVersionMismatch) {
            "BAK sürümü cihazdaki Temalar sürümüyle eşleşmiyor"
        }
        val work = "/data/local/tmp/mtzstudio-bak-${UUID.randomUUID()}"
        val source = shellQuote(archive.source.absolutePath)
        val command = """
            set -eu
            stage=preflight
            trap 'rc=${'$'}?; if [ "${'$'}rc" -ne 0 ]; then echo "MTZ_BAK_FAILED stage=${'$'}stage exit=${'$'}rc" >&2; fi' EXIT
            test "${'$'}(id -u)" = 0
            mkdir -p $work/payload $work/rollback
            pm path $PACKAGE_NAME >/dev/null
            if [ ! -d /data/user/0/$PACKAGE_NAME ]; then echo "Theme Manager data is not visible in this root mount namespace" >&2; exit 41; fi
            stage=snapshot
            am force-stop $PACKAGE_NAME || true
            if [ -d /data/user/0/$PACKAGE_NAME ]; then tar -cpf $work/rollback/private.tar -C /data/user/0 $PACKAGE_NAME; touch $work/rollback/private.existed; else touch $work/rollback/private.missing; fi
            if [ -d /data/user_de/0/$PACKAGE_NAME ]; then tar -cpf $work/rollback/device.tar -C /data/user_de/0 $PACKAGE_NAME; touch $work/rollback/device.existed; else touch $work/rollback/device.missing; fi
            if [ -d /storage/emulated/0/Android/data/$PACKAGE_NAME ]; then tar -cpf $work/rollback/external.tar -C /storage/emulated/0/Android/data $PACKAGE_NAME; touch $work/rollback/external.existed; else touch $work/rollback/external.missing; fi
            touch $work/rollback/ready
            stage=extract
            dd if=$source ibs=${archive.tarOffset} skip=1 | tar -xpf - -C $work/payload
            test -f $work/payload/apps/$PACKAGE_NAME/_manifest
            stage=identity
            identity_path=/data/user/0/$PACKAGE_NAME
            [ -d ${'$'}identity_path ] || identity_path=/data/user_de/0/$PACKAGE_NAME
            test -d ${'$'}identity_path
            uid=${'$'}(stat -c %u ${'$'}identity_path)
            gid=${'$'}(stat -c %g ${'$'}identity_path)
            stage=restore
            touch $work/rollback/mutation-started
            rm -rf /data/user/0/$PACKAGE_NAME/files /data/user/0/$PACKAGE_NAME/databases /data/user/0/$PACKAGE_NAME/shared_prefs
            rm -rf /data/user_de/0/$PACKAGE_NAME/shared_prefs
            rm -rf /storage/emulated/0/Android/data/$PACKAGE_NAME/files
            mkdir -p /data/user/0/$PACKAGE_NAME/files /data/user/0/$PACKAGE_NAME/databases /data/user/0/$PACKAGE_NAME/shared_prefs
            mkdir -p /data/user_de/0/$PACKAGE_NAME/shared_prefs /storage/emulated/0/Android/data/$PACKAGE_NAME/files
            [ ! -d $work/payload/apps/$PACKAGE_NAME/f ] || cp -a $work/payload/apps/$PACKAGE_NAME/f/. /data/user/0/$PACKAGE_NAME/files/
            [ ! -d $work/payload/apps/$PACKAGE_NAME/db ] || cp -a $work/payload/apps/$PACKAGE_NAME/db/. /data/user/0/$PACKAGE_NAME/databases/
            [ ! -d $work/payload/apps/$PACKAGE_NAME/sp ] || cp -a $work/payload/apps/$PACKAGE_NAME/sp/. /data/user/0/$PACKAGE_NAME/shared_prefs/
            [ ! -d $work/payload/apps/$PACKAGE_NAME/d_sp ] || cp -a $work/payload/apps/$PACKAGE_NAME/d_sp/. /data/user_de/0/$PACKAGE_NAME/shared_prefs/
            [ ! -d $work/payload/apps/$PACKAGE_NAME/ef ] || cp -a $work/payload/apps/$PACKAGE_NAME/ef/. /storage/emulated/0/Android/data/$PACKAGE_NAME/files/
            chown -R ${'$'}uid:${'$'}gid /data/user/0/$PACKAGE_NAME /data/user_de/0/$PACKAGE_NAME
            restorecon -RF /data/user/0/$PACKAGE_NAME /data/user_de/0/$PACKAGE_NAME 2>/dev/null || true
            am force-stop $PACKAGE_NAME || true
            rm -rf $work
        """.trimIndent().replace("\n", " ; ")
        diagnostics.record(
            "bak_restore_started",
            "Tema Yöneticisi BAK geri yüklemesi başladı; geri dönüş yedeği oluşturuluyor",
            mapOf("name" to archive.displayName, "backupVersionCode" to archive.backupVersionCode, "installedVersionCode" to installedVersionCode),
        )
        val result = commandRunner.run(command, 300)
        if (result.exitCode != 0) {
            // A rollback may delete live data only after every pre-restore snapshot completed.
            // This prevents a missing optional directory from turning a failed backup attempt
            // into destructive data loss.
            val rollback = runCatching { commandRunner.run(rollbackCommand(work), 120) }
            diagnostics.record("bak_restore_failed", "Tema Yöneticisi BAK geri yüklemesi başarısız", mapOf(
                "exitCode" to result.exitCode,
                "channel" to result.authorizationSource,
                "output" to result.output.takeLast(2_000),
                "rollbackExitCode" to rollback.getOrNull()?.exitCode,
                "rollbackOutput" to (rollback.getOrNull()?.output ?: rollback.exceptionOrNull()?.message),
            ))
            error("BAK geri yüklenemedi: ${result.output.takeLast(500)}")
        }
        diagnostics.record("bak_restore_completed", "Tema Yöneticisi BAK geri yüklemesi tamamlandı", mapOf("name" to archive.displayName))
    }

    private fun inspect(file: File, displayName: String): ThemeManagerBakArchive {
        RandomAccessFile(file, "r").use { stream ->
            val header = ByteArray(HEADER_SEARCH_BYTES)
            val read = stream.read(header)
            require(read > 0) { "BAK başlığı okunamadı" }
            val prefix = String(header, 0, read, Charsets.UTF_8)
            require(prefix.startsWith("MIUI BACKUP\n2\n$PACKAGE_NAME ")) { "Bu, Xiaomi Temalar için MIUI Backup v2 arşivi değil" }
            val marker = "ANDROID BACKUP\n5\n0\nnone\n"
            val markerIndex = prefix.indexOf(marker)
            require(markerIndex >= 0) { "BAK Android arşiv başlığı bulunamadı" }
            val offset = (markerIndex + marker.length).toLong()
            require(offset % 1L == 0L && offset < HEADER_SEARCH_BYTES) { "BAK veri başlangıcı geçersiz" }
            stream.seek(offset)
            val firstHeader = ByteArray(TAR_BLOCK)
            require(stream.read(firstHeader) == TAR_BLOCK) { "BAK tar başlığı okunamadı" }
            val firstName = firstHeader.readTarPath()
            require(firstName == "apps/$PACKAGE_NAME/_manifest") { "BAK Temalar manifesti içermiyor" }
            val firstSize = firstHeader.readOctal(124, 12)
            require(firstSize in 1..16_384) { "BAK manifest boyutu geçersiz" }
            val manifest = ByteArray(firstSize.toInt())
            require(stream.read(manifest) == manifest.size) { "BAK manifesti eksik" }
            val manifestLines = String(manifest, Charsets.UTF_8).lineSequence().toList()
            require(manifestLines.getOrNull(1) == PACKAGE_NAME) { "BAK paket kimliği uyuşmuyor" }
            val versionCode = manifestLines.getOrNull(2)?.trim()?.toLongOrNull()
                ?: error("BAK Temalar sürüm kodu okunamadı")
            require(versionCode > 0) { "BAK Temalar sürüm kodu geçersiz" }
            val entries = countSafeEntries(stream, offset)
            require(entries > 1) { "BAK içerik taşımıyor" }
            return ThemeManagerBakArchive(file, displayName, versionCode, offset, entries)
        }
    }

    private fun countSafeEntries(stream: RandomAccessFile, offset: Long): Int {
        stream.seek(offset)
        var count = 0
        val block = ByteArray(TAR_BLOCK)
        while (count < MAX_ENTRIES && stream.read(block) == TAR_BLOCK) {
            // MIUI's backup writer uses the USTAR prefix field for long paths. Reading only the
            // 100-byte name field turns a valid `apps/<package>/ef/MIUI/...` entry into `MIUI/...`.
            val name = block.readTarPath()
            if (name.isBlank()) break
            require(name.startsWith("apps/$PACKAGE_NAME/") && !name.contains("..")) { "BAK güvenli olmayan yol içeriyor" }
            val size = block.readOctal(124, 12)
            require(size in 0..MAX_ENTRY_BYTES) { "BAK içerik boyutu sınırı aşıldı" }
            val blocks = (size + TAR_BLOCK - 1) / TAR_BLOCK
            stream.seek(stream.filePointer + blocks * TAR_BLOCK)
            count++
        }
        require(count < MAX_ENTRIES) { "BAK çok fazla dosya içeriyor" }
        return count
    }

    private fun ByteArray.readAscii(offset: Int, length: Int): String =
        String(this, offset, length, Charsets.US_ASCII).trimEnd('\u0000')

    private fun ByteArray.readTarPath(): String {
        val name = readAscii(0, 100)
        val prefix = readAscii(345, 155)
        return if (prefix.isBlank()) name else "$prefix/$name"
    }

    private fun ByteArray.readOctal(offset: Int, length: Int): Long {
        val value = readAscii(offset, length).trim()
        return if (value.isBlank()) 0 else value.toLong(8)
    }

    private fun shellQuote(value: String) = "'${value.replace("'", "'\\\"'\\\"'")}'"

    private fun rollbackCommand(work: String) = """
        set -e
        am force-stop $PACKAGE_NAME || true
        if [ -f $work/rollback/ready ] && [ -f $work/rollback/mutation-started ]; then if [ -f $work/rollback/private.existed ]; then rm -rf /data/user/0/$PACKAGE_NAME; tar -xpf $work/rollback/private.tar -C /data/user/0; fi; if [ -f $work/rollback/private.missing ]; then rm -rf /data/user/0/$PACKAGE_NAME; fi; if [ -f $work/rollback/device.existed ]; then rm -rf /data/user_de/0/$PACKAGE_NAME; tar -xpf $work/rollback/device.tar -C /data/user_de/0; fi; if [ -f $work/rollback/device.missing ]; then rm -rf /data/user_de/0/$PACKAGE_NAME; fi; if [ -f $work/rollback/external.existed ]; then rm -rf /storage/emulated/0/Android/data/$PACKAGE_NAME; tar -xpf $work/rollback/external.tar -C /storage/emulated/0/Android/data; fi; if [ -f $work/rollback/external.missing ]; then rm -rf /storage/emulated/0/Android/data/$PACKAGE_NAME; fi; fi
        rm -rf $work
    """.trimIndent().replace("\n", " ; ")

    private companion object {
        const val PACKAGE_NAME = "com.android.thememanager"
        const val HEADER_SEARCH_BYTES = 4_096
        const val TAR_BLOCK = 512
        const val MAX_BAK_BYTES = 1_024L * 1_024L * 1_024L
        const val MAX_ENTRY_BYTES = 512L * 1024L * 1024L
        const val MAX_ENTRIES = 20_000
    }
}

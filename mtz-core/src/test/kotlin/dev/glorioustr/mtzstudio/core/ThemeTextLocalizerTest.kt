package dev.glorioustr.mtzstudio.core

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.*

class ThemeTextLocalizerTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { out -> entries.forEach { (name, data) ->
            out.putNextEntry(ZipEntry(name)); out.write(data); out.closeEntry()
        } }
    }.toByteArray()
    private fun archive(bytes: ByteArray): Pair<Path, Path> {
        val dir = Files.createTempDirectory("translation-test")
        return Files.write(dir.resolve("source.mtz"), bytes) to dir.resolve("output.mtz")
    }
    private fun nested(path: Path, name: String): ByteArray = ZipFile(path.toFile()).use { z -> z.getInputStream(z.getEntry(name)).use { it.readBytes() } }
    private fun entry(bytes: ByteArray, name: String): ByteArray = ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val item = zip.nextEntry ?: error("Missing $name")
            if (item.name == name) return zip.readBytes()
        }
        error("Unreachable")
    }

    @Test fun `translates nested extensionless packages and preserves scripts and images`() {
        val xml = """<Root><Text name="中文标识" text="壁纸设置" textExp="'壁纸'+#number"/><Var name="id" expression="'中文代码'"/><Image src="中文.png"/><Group text="壁纸设置"/></Root>"""
        val image = byteArrayOf(1, 4, 7, 9)
        val (source, output) = archive(zip("lockscreen" to zip("advance/manifest.xml" to xml.toByteArray(), "中文.png" to image)))
        var calls = 0
        val result = ThemeTextLocalizer().rewrite(source, output) { calls++; "Translated & \"quoted\"" }
        assertEquals(3, result.translatedNodes)
        assertEquals(2, calls)
        assertEquals(listOf("lockscreen!/advance/manifest.xml"), result.changedFiles)
        val lock = nested(output, "lockscreen")
        assertContentEquals(image, entry(lock, "中文.png"))
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(entry(lock, "advance/manifest.xml").inputStream())
        val text = doc.getElementsByTagName("Text").item(0) as org.w3c.dom.Element
        assertEquals("中文标识", text.getAttribute("name"))
        assertEquals("Translated & \"quoted\"", text.getAttribute("text"))
        assertTrue(text.getAttribute("textExp").endsWith("+#number"))
        assertEquals("'中文代码'", (doc.getElementsByTagName("Var").item(0) as org.w3c.dom.Element).getAttribute("expression"))
    }

    @Test fun `unchanged nested packages remain byte identical`() {
        val original = zip("manifest.xml" to "<Text text='Hello'/>".toByteArray())
        val (source, output) = archive(zip("lockscreen" to original))
        val result = ThemeTextLocalizer().rewrite(source, output) { error("No download or translation needed") }
        assertEquals(0, result.translatedNodes)
        assertContentEquals(original, nested(output, "lockscreen"))
    }

    @Test fun `handles encoded XML text and skips comments and unsafe display expressions`() {
        val xml = """<Root><!-- 中文注释 --><string name="id">&#x58C1;&#x7EB8;</string><Text textExp="formatDate('M月d日',#time)"/><Text textExp="ifelse(eqs(@a,'中文'),'是','否')"/></Root>"""
        val (source, output) = archive(zip("config.xml" to xml.toByteArray()))
        val result = ThemeTextLocalizer().rewrite(source, output) { "Duvar kağıdı" }
        assertEquals(1, result.translatedNodes)
        assertEquals(2, result.skippedFiles.size)
        val rewritten = nested(output, "config.xml").toString(Charsets.UTF_8)
        assertTrue(rewritten.contains("中文注释"))
        assertTrue(rewritten.contains("M月d日"))
    }

    @Test fun `rejects external entities without changing source`() {
        val original = zip("manifest.xml" to """<!DOCTYPE r [<!ENTITY x SYSTEM "file:///private">]><r text="中文"/>""".toByteArray())
        val (source, output) = archive(original)
        assertFails { ThemeTextLocalizer().rewrite(source, output) { "translated" } }
        assertContentEquals(original, Files.readAllBytes(source))
        assertFalse(Files.exists(output))
    }

    @Test fun `limits expansion and nesting`() {
        val (source, output) = archive(zip("lockscreen" to zip("manifest.xml" to "<Text text='中文'/>".toByteArray())))
        assertFails { ThemeTextLocalizer(maxDepth = 0).rewrite(source, output) { "x" } }
        assertFails { ThemeTextLocalizer(maxExpandedBytes = 10).rewrite(source, output) { "x" } }
        assertFalse(Files.exists(output))
        Files.list(output.parent).use { paths -> assertEquals(1L, paths.count()) }
    }

    @Test fun `failure does not modify original archive`() {
        val original = zip("manifest.xml" to "<Text text='中文'/>".toByteArray())
        val (source, output) = archive(original)
        assertFails { ThemeTextLocalizer().rewrite(source, output) { error("Model download failed") } }
        assertContentEquals(original, Files.readAllBytes(source))
        assertFalse(Files.exists(output))
    }

    @Test fun `optional real device fixture includes wallpaper settings in nested lockscreen`() {
        val fixture = System.getenv("MTZ_TRANSLATION_FIXTURE") ?: return
        val output = Files.createTempDirectory("real-theme-translation").resolve("output.mtz")
        val result = ThemeTextLocalizer().rewrite(Path.of(fixture), output) { "Translated" }
        assertTrue(result.translatedNodes > 100, result.toString())
        assertTrue(result.changedFiles.contains("lockscreen!/advance/manifest.xml"), result.toString())
        val lock = nested(output, "lockscreen")
        val xml = entry(lock, "advance/manifest.xml").toString(Charsets.UTF_8)
        assertFalse(xml.contains("text=\"壁纸设置\""))
        println("Real theme: ${result.translatedNodes} translated nodes, ${result.changedFiles.size} changed XML files")
    }
}

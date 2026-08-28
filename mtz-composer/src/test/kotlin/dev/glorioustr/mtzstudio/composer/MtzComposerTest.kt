package dev.glorioustr.mtzstudio.composer

import dev.glorioustr.mtzstudio.core.ComponentCategory
import dev.glorioustr.mtzstudio.core.MtzParser
import dev.glorioustr.mtzstudio.core.ThemeId
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MtzComposerTest {
    @Test
    fun `packages an active font as a verified font MTZ`() {
        val directory = Files.createTempDirectory("mtz-font-export-test")
        val font = directory.resolve("Current Font.ttf")
        Files.write(font, byteArrayOf(0, 1, 0, 0, 7, 8, 9))
        val preview = directory.resolve("font-preview.png")
        Files.write(preview, byteArrayOf(1, 2, 3))

        val result = MtzComposer().composeFont(
            FontExportRequest(
                metadata = CompositionMetadata(name = "Active device font"),
                fontFile = font,
                archiveFileName = font.fileName.toString(),
                previewFile = preview,
            ),
            directory.resolve("active-font.mtz"),
        )

        assertEquals(setOf(ComponentCategory.FONT), result.verifiedArchive.components.map { it.category }.toSet())
        ZipFile(result.output.toFile()).use { zip ->
            assertTrue(zip.getEntry("fonts/Current-Font.ttf") != null)
            assertTrue(zip.getEntry("preview/preview_fonts_0.png") != null)
            val xml = zip.getInputStream(zip.getEntry("description.xml")).bufferedReader().readText()
            assertTrue("<fontWeight>" in xml)
        }
    }

    @Test
    fun `composes one selected component for a single-source custom theme`() {
        val parser = MtzParser()
        val source = parser.parse(zip("icons" to byteArrayOf(1, 2, 3)))
        val request = CompositionRequest(
            metadata = CompositionMetadata(name = "Tek Kaynak"),
            selections = listOf(selection("one", source, ComponentCategory.ICONS)),
        )

        val result = MtzComposer(parser).compose(
            request,
            Files.createTempDirectory("mtz-single-compose-test").resolve("single.mtz"),
        )

        assertEquals(setOf(ComponentCategory.ICONS), result.verifiedArchive.components.map { it.category }.toSet())
    }

    @Test
    fun `stores generated gallery preview inside composed MTZ`() {
        val parser = MtzParser()
        val source = parser.parse(zip("icons" to byteArrayOf(1, 2, 3)))
        val preview = byteArrayOf(9, 8, 7, 6)
        val request = CompositionRequest(
            metadata = CompositionMetadata(name = "Preview theme"),
            selections = listOf(selection("one", source, ComponentCategory.ICONS)),
            generatedPreviewBytes = preview,
        )

        val result = MtzComposer(parser).compose(
            request,
            Files.createTempDirectory("mtz-preview-compose-test").resolve("preview.mtz"),
        )

        ZipFile(result.output.toFile()).use { zip ->
            val entry = zip.getEntry("preview/preview_launcher_0.jpg")
            assertTrue(entry != null)
            assertTrue(zip.getInputStream(entry).readBytes().contentEquals(preview))
            val markerEntry = zip.getEntry("preview/mtz_studio_generated.jpg")
            assertTrue(markerEntry != null)
            assertTrue(zip.getInputStream(markerEntry).readBytes().contentEquals(preview))
        }
    }

    @Test
    fun `base theme keeps every unchanged root while selected categories override it`() {
        val parser = MtzParser()
        val base = parser.parse(
            zip(
                "description.xml" to """
                    <theme>
                      <title>Circle</title>
                      <uiVersion>17</uiVersion>
                      <author>VedaT</author>
                      <designer>Circle Designer</designer>
                      <miuiAdapterVersion>4.2</miuiAdapterVersion>
                    </theme>
                """.trimIndent().encodeToByteArray(),
                "icons" to byteArrayOf(1),
                "com.android.systemui" to byteArrayOf(2),
                "miui.systemui.plugin" to byteArrayOf(3),
                "framework-miui-res" to byteArrayOf(4),
                "com.android.settings" to byteArrayOf(5),
                "rights/rights.xml" to byteArrayOf(9),
            ),
        )
        val icons = parser.parse(zip("icons" to byteArrayOf(7)))
        val font = parser.parse(zip("fonts/Roboto-Regular.ttf" to byteArrayOf(8)))
        val request = CompositionRequest(
            metadata = CompositionMetadata(name = "Inherited mix"),
            baseSource = CompositionSource(ThemeId("base"), "Circle", base),
            selections = listOf(
                selection("icons", icons, ComponentCategory.ICONS),
                selection("font", font, ComponentCategory.FONT),
            ),
        )

        val result = MtzComposer(parser).compose(
            request,
            Files.createTempDirectory("mtz-base-compose-test").resolve("inherited.mtz"),
        )

        ZipFile(result.output.toFile()).use { output ->
            assertTrue(output.getInputStream(output.getEntry("icons")).readBytes().contentEquals(byteArrayOf(7)))
            assertTrue(output.getInputStream(output.getEntry("fonts/Roboto-Regular.ttf")).readBytes().contentEquals(byteArrayOf(8)))
            assertTrue(output.getInputStream(output.getEntry("com.android.systemui")).readBytes().contentEquals(byteArrayOf(2)))
            assertTrue(output.getInputStream(output.getEntry("miui.systemui.plugin")).readBytes().contentEquals(byteArrayOf(3)))
            assertTrue(output.getInputStream(output.getEntry("framework-miui-res")).readBytes().contentEquals(byteArrayOf(4)))
            assertTrue(output.getInputStream(output.getEntry("com.android.settings")).readBytes().contentEquals(byteArrayOf(5)))
            assertTrue(output.getEntry("rights/rights.xml") == null)
            val description = output.getInputStream(output.getEntry("description.xml")).bufferedReader().readText()
            assertTrue("<uiVersion>17</uiVersion>" in description)
            assertTrue("<miuiAdapterVersion>4.2</miuiAdapterVersion>" in description)
            assertTrue("<author>VedaT</author>" in description)
            assertTrue("<designer>Circle Designer</designer>" in description)
        }
    }

    @Test
    fun `custom maker replaces inherited author and designer metadata`() {
        val parser = MtzParser()
        val base = parser.parse(
            zip(
                "description.xml" to """
                    <theme>
                      <title>Base</title>
                      <author>Original Author</author>
                      <designer>Original Designer</designer>
                    </theme>
                """.trimIndent().encodeToByteArray(),
                "icons" to byteArrayOf(1),
            ),
        )
        val request = CompositionRequest(
            metadata = CompositionMetadata(
                name = "Renamed",
                author = "New Maker",
                designer = "New Maker",
            ),
            baseSource = CompositionSource(ThemeId("base"), "Base", base),
            selections = emptyList(),
        )

        val result = MtzComposer(parser).compose(
            request,
            Files.createTempDirectory("mtz-maker-compose-test").resolve("maker.mtz"),
        )

        ZipFile(result.output.toFile()).use { output ->
            val description = output.getInputStream(output.getEntry("description.xml")).bufferedReader().readText()
            assertTrue("<author>New Maker</author>" in description)
            assertTrue("<designer>New Maker</designer>" in description)
            assertFalse("Original Author" in description)
            assertFalse("Original Designer" in description)
        }
    }

    @Test
    fun `composes two themes deterministically excludes rights and reopens output`() {
        val parser = MtzParser()
        val first = parser.parse(zip("icons" to byteArrayOf(1, 2), "rights/rights.xml" to byteArrayOf(9)))
        val second = parser.parse(zip("wallpaper/home.jpg" to byteArrayOf(3, 4)))
        val request = CompositionRequest(
            metadata = CompositionMetadata(name = "My <Theme>"),
            selections = listOf(
                selection("one", first, ComponentCategory.ICONS),
                selection("two", second, ComponentCategory.WALLPAPER),
            ),
        )
        val directory = Files.createTempDirectory("mtz-compose-test")

        val one = MtzComposer(parser).compose(request, directory.resolve("one.mtz"))
        val two = MtzComposer(parser).compose(request, directory.resolve("two.mtz"))

        assertEquals(one.outputSha256, two.outputSha256)
        assertEquals(setOf(ComponentCategory.ICONS, ComponentCategory.WALLPAPER), one.verifiedArchive.components.map { it.category }.toSet())
        assertTrue(one.verifiedArchive.rightsEntries.isEmpty())
        ZipFile(one.output.toFile()).use { zip ->
            assertFalse(zip.entries().asSequence().any { it.name.contains("rights", ignoreCase = true) })
            val xml = zip.getInputStream(zip.getEntry("description.xml")).bufferedReader().readText()
            assertTrue("My &lt;Theme&gt;" in xml)
        }
    }

    private fun selection(id: String, archive: dev.glorioustr.mtzstudio.core.MtzArchive, category: ComponentCategory): ComponentSelection {
        val component = archive.components.single { it.category == category }
        return ComponentSelection(
            source = CompositionSource(ThemeId(id), id, archive),
            category = category,
            rootPath = component.rootPath,
        )
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): Path {
        val path = Files.createTempFile("mtz-compose-source", ".mtz")
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return path
    }
}

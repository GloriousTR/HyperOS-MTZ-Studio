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

package dev.glorioustr.mtzstudio.core

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Text-only rewrite. Nested components are identified by ZIP magic, not file extensions. */
class ThemeTextLocalizer(
    private val maxExpandedBytes: Long = 512L * 1024 * 1024,
    private val maxEntryBytes: Long = 128L * 1024 * 1024,
    private val maxDepth: Int = 4,
) {
    data class Result(val changedFiles: List<String>, val translatedNodes: Int, val skippedFiles: List<String>)

    fun rewrite(source: Path, output: Path, translate: (String) -> String): Result {
        require(source.toAbsolutePath().normalize() != output.toAbsolutePath().normalize())
        val state = State(translate)
        try {
            rewriteZip(source, output, "", 0, state)
            return Result(state.changed.toList(), state.nodes, state.skipped.toList())
        } catch (error: Throwable) {
            Files.deleteIfExists(output)
            throw error
        }
    }

    private inner class State(val translate: (String) -> String) {
        var expanded = 0L
        var entries = 0
        var nodes = 0
        val changed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val cache = mutableMapOf<String, String>()
        fun text(value: String): String {
            if (!CHINESE.containsMatchIn(value)) return value
            check(!Thread.currentThread().isInterrupted) { "Translation interrupted" }
            return cache.getOrPut(value) {
                translate(value).also { check(it.isNotBlank()) { "Empty translation result" } }
            }
        }
    }

    private fun rewriteZip(source: Path, output: Path, prefix: String, depth: Int, state: State) {
        check(depth <= maxDepth) { "Theme archive nesting limit exceeded" }
        ZipFile(source.toFile()).use { zip ->
            ZipOutputStream(Files.newOutputStream(output)).use { out ->
                val names = hashSetOf<String>()
                zip.entries().asSequence().forEach { entry ->
                    check(++state.entries <= 20_000) { "Theme archive entry limit exceeded" }
                    val name = SafeArchivePath.normalize(entry.name, entry.isDirectory)
                    check(names.add(name)) { "Duplicate archive entry: $name" }
                    check(entry.size <= maxEntryBytes) { "Theme entry too large: $name" }
                    val path = "$prefix$name"
                    out.putNextEntry(ZipEntry(entry.name).apply { time = entry.time })
                    if (!entry.isDirectory) zip.getInputStream(entry).buffered().use { input ->
                        input.mark(4)
                        val magic = ByteArray(4)
                        val count = input.read(magic)
                        input.reset()
                        when {
                            count == 4 && magic.contentEquals(byteArrayOf(80, 75, 3, 4)) -> {
                                val nested = Files.createTempFile(output.parent, ".translate-input-", ".zip")
                                val rewritten = Files.createTempFile(output.parent, ".translate-output-", ".zip")
                                try {
                                    Files.newOutputStream(nested).use { copyBounded(input, it, state) }
                                    val before = state.nodes
                                    rewriteZip(nested, rewritten, "$path!/", depth + 1, state)
                                    // Preserve the exact original component bytes if no text changed.
                                    Files.newInputStream(if (before == state.nodes) nested else rewritten).use { it.copyTo(out) }
                                } finally {
                                    Files.deleteIfExists(nested)
                                    Files.deleteIfExists(rewritten)
                                }
                            }
                            name.endsWith(".xml", true) && !name.endsWith("description.xml", true) &&
                                !name.contains("rights", true) && entry.size in 0..MAX_XML_BYTES -> {
                                val bytes = ByteArrayOutputStream()
                                copyBounded(input, bytes, state, MAX_XML_BYTES)
                                val original = bytes.toByteArray()
                                val replacement = localizeXml(original, path, state)
                                out.write(replacement)
                            }
                            else -> {
                                if (name.endsWith(".xml", true) && entry.size > MAX_XML_BYTES) state.skipped += path
                                copyBounded(input, out, state)
                            }
                        }
                    }
                    out.closeEntry()
                }
            }
        }
    }

    private fun copyBounded(input: InputStream, output: OutputStream, state: State, limit: Long = maxEntryBytes) {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            check(!Thread.currentThread().isInterrupted) { "Translation interrupted" }
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            state.expanded += count
            check(total <= limit && state.expanded <= maxExpandedBytes) { "Theme expansion limit exceeded" }
            output.write(buffer, 0, count)
        }
    }

    private fun localizeXml(bytes: ByteArray, path: String, state: State): ByteArray {
        // Reject declarations even on Android parsers that do not implement every hardening flag.
        val declarationProbe = bytes.toString(Charsets.ISO_8859_1).replace("\u0000", "")
        check(!Regex("<!\\s*(DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE).containsMatchIn(declarationProbe)) {
            "External XML declarations are forbidden: $path"
        }
        val document = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
                runCatching { isXIncludeAware = false }
                runCatching { isExpandEntityReferences = false }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> throw SAXException("External XML entities are disabled") }
            }.parse(bytes.inputStream())
        } catch (error: Exception) {
            // Non-XML resources with an .xml suffix must remain untouched.
            state.skipped += path
            return bytes
        }
        val before = state.nodes
        fun visit(node: Node) {
            if (node is Element) {
                for (name in DISPLAY_ATTRIBUTES) {
                    if (!node.hasAttribute(name)) continue
                    val original = node.getAttribute(name)
                    val replacement = state.text(original)
                    if (replacement != original) {
                        node.setAttribute(name, replacement)
                        state.nodes++
                    }
                }
                // Only display expressions, never Var/VariableCommand expressions or identifiers.
                if (node.tagName == "Text" && node.hasAttribute("textExp")) {
                    val original = node.getAttribute("textExp")
                    if (UNSAFE_DISPLAY_EXPRESSION.containsMatchIn(original)) {
                        if (CHINESE.containsMatchIn(original)) state.skipped += "$path#textExp"
                    } else {
                        val replacement = STRING_LITERAL.replace(original) { literal ->
                            CHINESE.replace(literal.value) { fragment ->
                                state.text(fragment.value).replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"")
                            }
                        }
                        if (replacement != original) {
                            node.setAttribute("textExp", replacement)
                            state.nodes++
                        }
                    }
                }
                if (node.tagName == "string" && (0 until node.childNodes.length).none { node.childNodes.item(it) is Element }) {
                    val original = node.textContent
                    val replacement = state.text(original)
                    if (replacement != original) {
                        node.textContent = replacement
                        state.nodes++
                    }
                }
            }
            val children = node.childNodes
            for (i in 0 until children.length) visit(children.item(i))
        }
        visit(document.documentElement)
        if (state.nodes == before) return bytes
        val result = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(result))
        // Parse the serialized XML again before it may replace a library item.
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(result.toString("UTF-8"))))
        state.changed += path
        return result.toByteArray()
    }

    companion object {
        private const val MAX_XML_BYTES = 2L * 1024 * 1024
        private val CHINESE = Regex("[\\p{IsHan}]+")
        private val DISPLAY_ATTRIBUTES = listOf("text", "summary", "title", "description", "hint")
        private val STRING_LITERAL = Regex("""'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"""")
        private val UNSAFE_DISPLAY_EXPRESSION = Regex("""(?i)formatDate|formatTime|eqs\s*\(|str\w*\s*\(|[=!]=\s*['"]""")
    }
}

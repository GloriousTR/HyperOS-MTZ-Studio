package dev.glorioustr.mtzstudio.core

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.SAXException

internal object DescriptionXmlParser {
    fun parse(bytes: ByteArray): MtzMetadata {
        try {
            rejectDeclarations(bytes)
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
                optionalSetting { isXIncludeAware = false }
                optionalSetting { setExpandEntityReferences(false) }
                optionalSetting { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
                optionalSetting { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                optionalSetting { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                optionalSetting { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                optionalSetting { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
                optionalSetting { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> throw SAXException("External XML entities are disabled") }
            }
            val document = builder.parse(ByteArrayInputStream(bytes))
            val fields = linkedMapOf<String, String>()
            collect(document.documentElement, fields)
            return MtzMetadata(
                name = first(fields, "title", "name"),
                author = first(fields, "author"),
                designer = first(fields, "designer"),
                version = first(fields, "version"),
                description = first(fields, "description", "desc"),
                fields = fields.toMap(),
            )
        } catch (error: UnsafeMtzException) {
            throw error
        } catch (error: Exception) {
            throw UnsafeMtzException(
                UnsafeMtzException.Reason.UNSAFE_XML,
                "description.xml is not safe, well-formed XML",
                error,
            )
        }
    }

    private fun collect(element: Element, target: MutableMap<String, String>) {
        val children = element.childNodes
        var hasElementChild = false
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child is Element) {
                hasElementChild = true
                collect(child, target)
            }
        }
        if (!hasElementChild) {
            val key = (element.localName ?: element.tagName).substringAfter(':').lowercase()
            val value = element.textContent.orEmpty().trim().take(4_096)
            if (value.isNotEmpty()) target.putIfAbsent(key, value)
        }
    }

    private fun first(fields: Map<String, String>, vararg names: String): String? =
        names.firstNotNullOfOrNull(fields::get)

    private fun rejectDeclarations(bytes: ByteArray) {
        val text = String(bytes, Charsets.ISO_8859_1)
        if (FORBIDDEN_DECLARATION.containsMatchIn(text)) {
            throw UnsafeMtzException(
                UnsafeMtzException.Reason.UNSAFE_XML,
                "description.xml contains a forbidden DOCTYPE or ENTITY declaration",
            )
        }
    }

    private inline fun DocumentBuilderFactory.optionalSetting(block: DocumentBuilderFactory.() -> Unit) {
        runCatching { block() }
    }

    private val FORBIDDEN_DECLARATION = Regex("<!\\s*(DOCTYPE|ENTITY)\\b", RegexOption.IGNORE_CASE)
}

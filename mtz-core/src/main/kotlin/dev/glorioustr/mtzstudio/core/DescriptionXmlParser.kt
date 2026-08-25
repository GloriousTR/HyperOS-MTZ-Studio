package dev.glorioustr.mtzstudio.core

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

internal object DescriptionXmlParser {
    fun parse(bytes: ByteArray): MtzMetadata {
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                setExpandEntityReferences(false)
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
                setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
            }
            val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
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
}


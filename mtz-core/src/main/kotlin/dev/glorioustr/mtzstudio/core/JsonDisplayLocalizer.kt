package dev.glorioustr.mtzstudio.core

/** A small JSON tokenizer: keys, identifiers, unknown values and whitespace are retained verbatim. */
internal class JsonDisplayLocalizer(private val source: String, private val translate: (String) -> String) {
    private var index = 0
    private val edits = mutableListOf<Triple<Int, Int, String>>()
    fun rewrite(): String {
        value(false, 0)
        space()
        require(index == source.length)
        val output = StringBuilder(source)
        edits.asReversed().forEach { (start, end, text) -> output.replace(start, end, encode(text)) }
        return output.toString()
    }
    private fun space() { while (index < source.length && source[index] in " \t\r\n") index++ }
    private fun expect(c: Char) { space(); require(index < source.length && source[index++] == c) }
    private fun value(display: Boolean, depth: Int) {
        require(depth <= 64)
        space()
        require(index < source.length)
        when (source[index]) {
            '{' -> {
                index++; space()
                if (source.getOrNull(index) == '}') { index++; return }
                while (true) {
                    space(); val key = string(); expect(':')
                    value(key in setOf("title", "summary", "description", "label", "hint", "text", "subtitle", "message"), depth + 1)
                    space()
                    if (source.getOrNull(index) == '}') { index++; break }
                    expect(',')
                }
            }
            '[' -> {
                index++; space()
                if (source.getOrNull(index) == ']') { index++; return }
                while (true) {
                    value(display, depth + 1); space()
                    if (source.getOrNull(index) == ']') { index++; break }
                    expect(',')
                }
            }
            '"' -> {
                val start = index; val original = string()
                if (display) {
                    val translated = translate(original)
                    if (original != translated) edits += Triple(start, index, translated)
                }
            }
            else -> {
                val start = index
                while (index < source.length && source[index] !in ",]} \t\r\n") index++
                require(Regex("true|false|null|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matches(source.substring(start, index)))
            }
        }
    }
    private fun string(): String {
        expect('"'); val out = StringBuilder()
        while (index < source.length) {
            val c = source[index++]
            if (c == '"') return out.toString()
            require(c >= ' ')
            if (c != '\\') { out.append(c); continue }
            require(index < source.length)
            when (val escaped = source[index++]) {
                '"', '\\', '/' -> out.append(escaped)
                'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                'b' -> out.append('\b'); 'f' -> out.append('\u000C')
                'u' -> { require(index + 4 <= source.length); out.append(source.substring(index, index + 4).toInt(16).toChar()); index += 4 }
                else -> error("Invalid JSON escape")
            }
        }
        error("Unclosed JSON string")
    }
    private fun encode(value: String): String = buildString {
        append('"')
        for (c in value) when (c) {
            '"', '\\' -> { append('\\'); append(c) }
            in '\u0000'..'\u001F' -> append("\\u" + c.code.toString(16).padStart(4, '0'))
            else -> append(c)
        }
        append('"')
    }
}

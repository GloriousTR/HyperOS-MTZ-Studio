package dev.glorioustr.mtzstudio.core

/** Splits Chinese interface prose at semantic punctuation so local models translate complete clauses. */
object ChineseTranslationSegmenter {
    fun translate(text: String, targetLanguage: String, translateClause: (String) -> String): String {
        ThemeGlossary.resolve(text, targetLanguage)?.let { return it }
        if (!ThemeGlossary.containsChinese(text)) return text
        val output = StringBuilder(text.length)
        val clause = StringBuilder()
        fun flush() {
            if (clause.isEmpty()) return
            val value = clause.toString()
            clause.setLength(0)
            val leading = value.takeWhile(Char::isWhitespace)
            val trailing = value.takeLastWhile(Char::isWhitespace)
            val core = value.substring(leading.length, value.length - trailing.length)
            output.append(leading)
            output.append(if (ThemeGlossary.containsChinese(core)) ThemeGlossary.resolve(core, targetLanguage) ?: translateClause(core) else core)
            output.append(trailing)
        }
        text.forEach { char ->
            val separator = when (char) {
                '\r' -> ""
                '\n' -> "\n"
                '。' -> ". "
                '！' -> "! "
                '？' -> "? "
                '；' -> "; "
                '，' -> ", "
                '：' -> ": "
                '丨' -> " | "
                '【' -> "["
                '】' -> "]"
                '（' -> " ("
                '）' -> ")"
                else -> null
            }
            if (separator == null) clause.append(char) else { flush(); output.append(separator) }
        }
        flush()
        return output.toString().trim()
    }
}

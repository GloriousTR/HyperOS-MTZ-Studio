package dev.glorioustr.mtzstudio.core

/** Rejects identifiers, paths and expressions while retaining visible natural-language text. */
object TranslationTextFilter {
    private val url = Regex("(?i)^(?:https?|content|file|market)://")
    private val packageName = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*){2,}$")
    private val numberOrPunctuation = Regex("^[\\p{N}\\p{P}\\p{S}\\s]+$")
    private val hexColor = Regex("^#[0-9A-Fa-f]{3,8}$")
    private val shortDisplayTerms = setOf("music", "play", "pause", "next", "back", "stop", "edit", "save", "done", "more", "open", "auto", "on", "off", "yes", "no")

    fun isCandidate(value: String): Boolean {
        if (ThemeGlossary.containsChinese(value)) return true
        val text = value.trim()
        if (text.length !in 2..500) return false
        if (url.containsMatchIn(text) || packageName.matches(text) || hexColor.matches(text)) return false
        if (text.startsWith("@") || text.startsWith("#") || text.startsWith("${'$'}{") || text.startsWith("%{")) return false
        if ((text.contains('/') || text.contains('\\')) && !text.contains(' ')) return false
        if (text.contains('_') && !text.contains(' ')) return false
        if (numberOrPunctuation.matches(text) || text.count(Char::isLetter) < 2) return false
        if (!text.contains(' ') && text.length <= 5 && text.all { !it.isLetter() || it.isUpperCase() } && text.lowercase() !in shortDisplayTerms) return false
        return true
    }
}

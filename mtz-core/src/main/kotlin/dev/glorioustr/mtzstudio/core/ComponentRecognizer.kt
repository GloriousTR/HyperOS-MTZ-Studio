package dev.glorioustr.mtzstudio.core

internal object ComponentRecognizer {
    private val ignoredRoots = setOf("description.xml", "preview", "previews")

    fun recognize(entries: List<MtzEntry>): List<ThemeComponent> {
        val grouped = linkedMapOf<Pair<ComponentCategory, String>, MutableList<MtzEntry>>()
        entries.asSequence()
            .filterNot { it.directory || it.rightsRelated }
            .forEach { entry ->
                val match = classify(entry.path) ?: return@forEach
                grouped.getOrPut(match) { mutableListOf() }.add(entry)
            }
        return grouped.map { (key, members) ->
            ThemeComponent(
                category = key.first,
                rootPath = key.second,
                entryPaths = members.map(MtzEntry::path).sorted(),
                expandedBytes = members.sumOf(MtzEntry::expandedBytes),
            )
        }.sortedWith(compareBy({ it.category.ordinal }, { it.rootPath.lowercase() }))
    }

    private fun classify(path: String): Pair<ComponentCategory, String>? {
        val lower = path.lowercase()
        val segments = lower.split('/')
        val root = segments.first()
        if (root in ignoredRoots || root.startsWith("meta-inf")) return null

        val category = when {
            root == "icons" -> ComponentCategory.ICONS
            root == "lockscreen" || root == "lock_style" -> ComponentCategory.LOCKSCREEN
            root == "wallpaper" || root == "wallpapers" -> ComponentCategory.WALLPAPER
            root == "framework-miui-res" -> ComponentCategory.FRAMEWORK
            root == "com.android.systemui" -> ComponentCategory.SYSTEM_UI
            root == "miui.systemui.plugin" -> ComponentCategory.SYSTEM_UI_PLUGIN
            root == "com.miui.home" || root == "launcher" -> ComponentCategory.LAUNCHER
            root == "aod" || root == "com.miui.aod" -> ComponentCategory.AOD
            root == "ringtone" || root == "ringtones" || lower.startsWith("audio/ringtone") -> ComponentCategory.RINGTONE
            root == "font" || root == "fonts" -> ComponentCategory.FONT
            root.startsWith("com.") || root.startsWith("miui.") || root.startsWith("framework-") -> ComponentCategory.OTHER
            else -> return null
        }
        return category to root
    }
}


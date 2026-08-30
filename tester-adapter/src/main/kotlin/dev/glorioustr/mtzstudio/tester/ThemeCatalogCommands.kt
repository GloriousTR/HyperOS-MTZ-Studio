package dev.glorioustr.mtzstudio.tester

object ThemeCatalogCommands {
    fun copyMetadata(source: String, target: String, uid: Int): String {
        require(uid >= 0)
        return """
            source_dir=${quote(source)}
            if [ ! -d "${'$'}source_dir" ]; then echo 'CATALOG_LOCATION_UNAVAILABLE'; exit 4; fi
            if [ ! -r "${'$'}source_dir" ] || [ ! -x "${'$'}source_dir" ]; then echo 'CATALOG_UNREADABLE'; exit 5; fi
            count=0
            for entry in "${'$'}source_dir"/*.mrm; do
              [ -e "${'$'}entry" ] || continue
              [ -f "${'$'}entry" ] && [ -r "${'$'}entry" ] || exit 5
              cp "${'$'}entry" ${quote(target)}/ || exit 5
              count=${'$'}((count + 1))
            done
            chown -R $uid:$uid ${quote(target)} || exit 6
            chmod -R u+rwX,go-rwx ${quote(target)} || exit 7
            echo "MTZ_METADATA_COUNT=${'$'}count"
        """.trimIndent()
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
}

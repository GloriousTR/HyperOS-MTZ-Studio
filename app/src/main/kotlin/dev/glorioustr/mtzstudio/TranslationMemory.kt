package dev.glorioustr.mtzstudio

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Persistent translation memory; stores translated text only and never stores API credentials. */
class TranslationMemory(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE translations (
                source TEXT NOT NULL,
                target_language TEXT NOT NULL,
                engine TEXT NOT NULL,
                translation TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (source, target_language, engine)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun get(texts: Collection<String>, targetLanguage: String, engine: String): Map<String, String> {
        if (texts.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, String>()
        readableDatabase.useQueryLoop(texts, targetLanguage, engine) { source, translation ->
            result[source] = translation
        }
        return result
    }

    fun put(translations: Map<String, String>, targetLanguage: String, engine: String) {
        if (translations.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            translations.forEach { (source, translation) ->
                writableDatabase.insertWithOnConflict(
                    "translations",
                    null,
                    ContentValues().apply {
                        put("source", source)
                        put("target_language", targetLanguage)
                        put("engine", engine)
                        put("translation", translation)
                        put("updated_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun clear() {
        writableDatabase.delete("translations", null, null)
    }

    private fun SQLiteDatabase.useQueryLoop(
        texts: Collection<String>,
        targetLanguage: String,
        engine: String,
        consume: (String, String) -> Unit,
    ) {
        texts.distinct().chunked(MAX_QUERY_ITEMS).forEach { batch ->
            val placeholders = batch.joinToString(",") { "?" }
            query(
                "translations",
                arrayOf("source", "translation"),
                "source IN ($placeholders) AND target_language = ? AND engine = ?",
                (batch + targetLanguage + engine).toTypedArray(),
                null,
                null,
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) consume(cursor.getString(0), cursor.getString(1))
            }
        }
    }

    companion object {
        private const val DATABASE_NAME = "translation_memory.db"
        private const val DATABASE_VERSION = 1
        private const val MAX_QUERY_ITEMS = 400
    }
}

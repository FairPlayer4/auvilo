package com.avlreminder.data

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File

/** Databases of an older version, built from the exported Room schema, for migration tests. */
object SchemaDatabases {
    /** Creates [file] in schema [version] and fills it with [data]; Room migrates it on open. */
    fun create(file: File, version: Int, data: (SQLiteDatabase) -> Unit) {
        file.parentFile?.mkdirs()
        file.delete()
        // Unit tests run in the module directory.
        val schema = JSONObject(File("schemas/com.avlreminder.data.AvlDatabase/$version.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            data(db)
        }
    }
}

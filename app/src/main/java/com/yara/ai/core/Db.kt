package com.yara.ai.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Msg(val role: String, val text: String, val ts: Long = System.currentTimeMillis())
data class Fact(val id: Long, val text: String)

/** Local brain: every chat message and every remembered fact lives on the phone (SQLite). */
class Db private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "yara.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, text TEXT NOT NULL, ts INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE facts(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL UNIQUE, ts INTEGER NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun addMessage(role: String, text: String) {
        val cv = ContentValues()
        cv.put("role", role); cv.put("text", text); cv.put("ts", System.currentTimeMillis())
        writableDatabase.insert("messages", null, cv)
    }

    /** Newest [limit] messages, oldest first. */
    fun recent(limit: Int): List<Msg> {
        val out = ArrayList<Msg>()
        readableDatabase.rawQuery(
            "SELECT role, text, ts FROM messages ORDER BY id DESC LIMIT ?", arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(Msg(c.getString(0), c.getString(1), c.getLong(2)))
        }
        out.reverse()
        return out
    }

    fun clearMessages() { writableDatabase.delete("messages", null, null) }

    fun addFact(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        val cv = ContentValues()
        cv.put("text", t); cv.put("ts", System.currentTimeMillis())
        return writableDatabase.insertWithOnConflict("facts", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun facts(): List<Fact> {
        val out = ArrayList<Fact>()
        readableDatabase.rawQuery("SELECT id, text FROM facts ORDER BY id ASC", null).use { c ->
            while (c.moveToNext()) out.add(Fact(c.getLong(0), c.getString(1)))
        }
        return out
    }

    fun deleteFact(id: Long) { writableDatabase.delete("facts", "id=?", arrayOf(id.toString())) }

    companion object {
        @Volatile private var inst: Db? = null
        fun get(ctx: Context): Db = inst ?: synchronized(this) {
            inst ?: Db(ctx.applicationContext).also { inst = it }
        }
    }
}

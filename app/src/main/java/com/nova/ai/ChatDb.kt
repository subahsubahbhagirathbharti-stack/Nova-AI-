package com.nova.ai

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Msg(val role: String, val text: String) // role: "user" | "model"

class ChatDb(ctx: Context) : SQLiteOpenHelper(ctx, "nova.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, text TEXT, ts INTEGER)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun add(m: Msg) {
        writableDatabase.insert("messages", null, ContentValues().apply {
            put("role", m.role); put("text", m.text); put("ts", System.currentTimeMillis())
        })
    }
    fun all(): MutableList<Msg> {
        val out = mutableListOf<Msg>()
        readableDatabase.rawQuery("SELECT role,text FROM messages ORDER BY id", null).use {
            while (it.moveToNext()) out.add(Msg(it.getString(0), it.getString(1)))
        }
        return out
    }
    fun clear() { writableDatabase.delete("messages", null, null) }
}

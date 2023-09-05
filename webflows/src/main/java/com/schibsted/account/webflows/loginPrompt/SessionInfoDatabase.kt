package com.schibsted.account.webflows.loginPrompt

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper


internal class SessionInfoDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    companion object {
        const val DATABASE_NAME = "SessionInfoDB"
        const val TABLE_NAME = "Sessions"
        const val DATABASE_VERSION = 1
        const val CREATE_DB_TABLE = """
      CREATE TABLE $TABLE_NAME (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        packageName STRING NOT NULL,
        timestamp INTEGER NOT NULL
    );"""
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_DB_TABLE)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun saveSessionTimestamp(packageName: String): Long {
        val values = ContentValues().apply {
            put("packageName", packageName)
            put("timestamp", java.util.Date().time)
        }
        return this.readableDatabase.insert(TABLE_NAME, null, values)
    }

    fun getSessions(): Cursor? {
        val projection = arrayOf("id", "packageName", "timestamp")
        val selection = "timestamp > ?"
        //get sessions the last year period
        val oneYearPeriodInMinutes = 60 * 24 * 365
        val arguments = arrayOf("${java.util.Date().time - (oneYearPeriodInMinutes)}")
        val sortOrder = "timestamp DESC"
        return this.readableDatabase.query(
            TABLE_NAME,
            projection,
            selection,
            arguments,
            null,
            null,
            sortOrder
        )
    }

    fun clearSessionsForPackage(packageName: String): Int {
        val selection = "packageName LIKE ?"
        val arguments = arrayOf(packageName)
        return this.readableDatabase.delete(TABLE_NAME, selection, arguments)
    }
}

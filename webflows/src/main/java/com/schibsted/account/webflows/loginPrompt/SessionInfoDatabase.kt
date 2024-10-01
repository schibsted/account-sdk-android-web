package com.schibsted.account.webflows.loginPrompt

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Date

internal class SessionInfoDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    companion object {
        const val DATABASE_NAME = "SessionInfoDB"
        const val TABLE_NAME = "Sessions"
        const val DATABASE_VERSION = 1
        const val CREATE_DB_TABLE = """
      CREATE TABLE  IF NOT EXISTS $TABLE_NAME (
        packageName STRING PRIMARY KEY,
        timestamp INTEGER NOT NULL,
        serverUrl STRING NOT NULL
    )  WITHOUT ROWID;"""
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_DB_TABLE)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun saveSessionTimestamp(
        packageName: String,
        serverUrl: String,
    ): Long {
        val values =
            ContentValues().apply {
                put("packageName", packageName)
                put("timestamp", Date().time)
                put("serverUrl", serverUrl)
            }
        return this.writableDatabase.insertWithOnConflict(
            TABLE_NAME,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun getSessions(serverUrl: String): Cursor? {
        val projection = arrayOf("packageName", "timestamp", "serverUrl")
        val selection = "timestamp > ? AND serverUrl = ?"
        // get sessions the last year period
        val oneYearInMilliseconds = 365 * 24 * 60 * 60 * 1000
        val arguments = arrayOf("${Date().time - (oneYearInMilliseconds)}", "$serverUrl")
        val sortOrder = "timestamp DESC"
        return this.readableDatabase.query(
            TABLE_NAME,
            projection,
            selection,
            arguments,
            null,
            null,
            sortOrder,
        )
    }

    fun clearSessionsForPackage(packageName: String): Int {
        val selection = "packageName LIKE ?"
        val arguments = arrayOf(packageName)
        return this.readableDatabase.delete(TABLE_NAME, selection, arguments)
    }
}

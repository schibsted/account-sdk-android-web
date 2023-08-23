package com.schibsted.account.webflows.loginPrompt

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.schibsted.account.webflows.tracking.SchibstedAccountTracker
import com.schibsted.account.webflows.tracking.SchibstedAccountTrackingEvent

internal class LoginPromptContentProvider : ContentProvider() {

    lateinit var uriMatcher: UriMatcher
    lateinit var db: SessionInfoDatabase
    lateinit var contentURI: Uri
    private val uriCode = 1

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            uriCode -> "vnd.android.cursor.dir/sessions"
            else -> throw IllegalArgumentException("Unsupported URI: $uri")
        }
    }

    override fun onCreate(): Boolean {
        context?.let { db = SessionInfoDatabase(it) } ?: return false

        val providerName = "${context?.packageName}.contentprovider"
        val providerUrl = "content://$providerName/sessions"

        contentURI = Uri.parse(providerUrl)
        uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
        uriMatcher.addURI(
            providerName, "sessions", uriCode
        )

        return db != null
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        if (uriMatcher.match(uri) != uriCode) {
            throw IllegalArgumentException("Unknown URI $uri")
        }
        return db?.getSessions()
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val rowId = db?.saveSessionTimestamp(values?.get("packageName") as String)
        if (rowId != null) {
            val uri: Uri = ContentUris.withAppendedId(contentURI, rowId)
            context!!.contentResolver.notifyChange(uri, null)
            SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.LoginPromptContentProviderInsert)
            return uri
        }
        throw SQLiteException("Failed to add a record into $uri")
    }

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
    ): Int {
        return 0
    }

    override fun delete(
        uri: Uri, selection: String?, selectionArgs: Array<String>?
    ): Int {
        val rowsAffected = db?.clearSessionsForPackage(selectionArgs?.first() as String);
        SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.LoginPromptContentProviderDelete)
        return rowsAffected ?: 0
    }
}

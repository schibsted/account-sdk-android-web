package com.schibsted.account.webflows.loginPrompt
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.net.Uri

class LoginPromptContentProvider : ContentProvider() {
  companion object {
    const val PROVIDER_NAME = "com.schibsted.account"
    const val PROVIDER_URL = "content://$PROVIDER_NAME/sessions"
    const val uriCode = 1
    val CONTENT_URI = Uri.parse(PROVIDER_URL)
    var uriMatcher: UriMatcher? = null
    var db: SessionInfoDatabase? = null
  }

  init {
    uriMatcher = UriMatcher(UriMatcher.NO_MATCH)
    uriMatcher!!.addURI(
      PROVIDER_NAME,
      "sessions",
      uriCode
    )
  }

  override fun getType(uri: Uri): String? {
    return when (uriMatcher!!.match(uri)) {
      uriCode -> "vnd.android.cursor.dir/sessions"
      else -> throw IllegalArgumentException("Unsupported URI: $uri")
    }
  }

  override fun onCreate(): Boolean {
    if(context == null) return false
    db = SessionInfoDatabase(context!!)
    return db != null
  }

  override fun query(
    uri: Uri, projection: Array<String>?, selection: String?,
    selectionArgs: Array<String>?, sortOrder: String?
  ): Cursor? {
    if(uriMatcher!!.match(uri) != uriCode) {
      throw IllegalArgumentException("Unknown URI $uri")
    }
    return db?.getSessions()
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? {
    val rowId = db?.saveSessionTimestamp(values?.get("packageName") as String)
    if(rowId != null) {
      val uri: Uri = ContentUris.withAppendedId(CONTENT_URI, rowId)
      context!!.contentResolver.notifyChange(uri, null)
      return uri
    }
    throw SQLiteException("Failed to add a record into $uri")
  }

  override fun update(
    uri: Uri, values: ContentValues?, selection: String?,
    selectionArgs: Array<String>?
  ): Int {
    return 0
  }

  override fun delete(
    uri: Uri,
    selection: String?,
    selectionArgs: Array<String>?
  ): Int {
    val rowsAffected = db?.clearSessionsForPackage(selectionArgs?.first() as String);
    return rowsAffected ?: 0
  }
}

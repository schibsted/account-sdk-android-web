package com.schibsted.account.webflows.loginPrompt

import android.content.ContentValues
import android.content.Context
import android.net.Uri

class SessionInfoManager(context: Context) {
  private val contentResolver = context.contentResolver;
  private val packageName = context.packageName

  fun save() {
    contentResolver.insert(Uri.parse("content://${packageName}.contentprovider/sessions"), ContentValues().apply {
      put("packageName", packageName)
    })
  }

  fun clear() {
    contentResolver.delete(Uri.parse("content://${packageName}.contentprovider/sessions"), null, arrayOf(packageName))
  }

  fun isSessionPresent():Boolean {
    val cursor = contentResolver.query(Uri.parse("content://${packageName}.contentprovider/sessions"), null, null, null, null)
    return cursor?.count != null && cursor.count > 0
  }
}

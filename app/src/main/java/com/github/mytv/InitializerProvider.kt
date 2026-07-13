package com.github.mytv

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri

internal class InitializerProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        SP.init(context!!)
        return true
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?) = unsupported()
    override fun getType(uri: Uri) = unsupported()
    override fun insert(uri: Uri, values: ContentValues?) = unsupported()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = unsupported()
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?) = unsupported()
    private fun unsupported(msg: String? = null): Nothing = throw UnsupportedOperationException(msg)
}

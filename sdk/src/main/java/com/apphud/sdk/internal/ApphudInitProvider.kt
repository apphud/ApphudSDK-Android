package com.apphud.sdk.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.apphud.sdk.ApphudLog
import com.apphud.sdk.internal.data.ClientSessionLifecycle

internal class ApphudInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return false
        ServiceLocator.initAppScope(ctx)
        // At process start, before the SDK's first request, so registration carries the session.
        runCatching { ClientSessionLifecycle.attach(ServiceLocator.instance.clientSessionRepository) }
            .onFailure { ApphudLog.logE("Client session lifecycle not attached: ${it.message}") }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

package com.aliothmoon.maameow.root

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import timber.log.Timber

class RootServiceBootstrapProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != RootServiceBootstrapRegistry.METHOD_ATTACH_REMOTE_SERVICE || extras == null) {
            return super.call(method, arg, extras)
        }
        val callingUid = Binder.getCallingUid()
        if (callingUid != Process.SHELL_UID && callingUid != 0) {
            Timber.w("Rejecting root bootstrap caller uid=%s", callingUid)
            return null
        }

        val token = extras.getString(RootServiceBootstrapRegistry.KEY_TOKEN)
            ?: return null
        val binder = extras.getBinder(RootServiceBootstrapRegistry.KEY_SERVICE_BINDER)
            ?: return null

        val appBinder = RootServiceBootstrapRegistry.attach(token, binder) ?: run {
            Timber.w("Root bootstrap token not found: %s", token)
            return null
        }

        return Bundle().apply {
            putBinder(RootServiceBootstrapRegistry.KEY_APP_BINDER, appBinder)
            putInt(RootServiceBootstrapRegistry.KEY_APP_PID, Process.myPid())
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}

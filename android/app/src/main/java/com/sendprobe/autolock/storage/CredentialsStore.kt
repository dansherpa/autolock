package com.sendprobe.autolock.storage

import android.content.Context
import com.sendprobe.autolock.model.BluelinkCredentials

/**
 * Loads and saves the single Bluelink account's credentials. One phone,
 * one Bluelink account -- no account switching, so a fixed key.
 */
object CredentialsStore {
    private const val ACCOUNT = "bluelink-primary"

    fun save(context: Context, credentials: BluelinkCredentials) {
        SecureStore.save(context, credentials, ACCOUNT)
    }

    fun load(context: Context): BluelinkCredentials? =
        SecureStore.load(context, ACCOUNT)

    fun clear(context: Context) {
        SecureStore.delete(context, ACCOUNT)
    }
}

package com.sendprobe.autolock.storage

import android.content.Context
import com.sendprobe.autolock.bluelink.BluelinkSession

/**
 * Persists the Bluelink session (tokens + vehicle info) separately from
 * CredentialsStore's account/password/PIN, so clearing a stale session
 * never requires re-entering credentials.
 */
object BluelinkSessionStore {
    private const val ACCOUNT = "bluelink-session"

    fun save(context: Context, session: BluelinkSession) {
        SecureStore.save(context, session, ACCOUNT)
    }

    fun load(context: Context): BluelinkSession? =
        SecureStore.load(context, ACCOUNT)

    fun clear(context: Context) {
        SecureStore.delete(context, ACCOUNT)
    }
}

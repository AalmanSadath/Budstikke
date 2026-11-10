package dev.aalman.budstikke.data

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Emits once at once, then again every time anything in the SMS store changes. */
internal fun ContentResolver.smsChanges(): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
    trySend(Unit)
    awaitClose { unregisterContentObserver(observer) }
}

package dev.aalman.budstikke.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Declared because Android grants the SMS role only to an app with a WAP_PUSH_DELIVER
 * receiver. MMS is not supported in this version, so the push is ignored.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        // MMS is not supported yet: the push is dropped.
    }
}

package dev.aalman.budstikke.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Declared because the SMS role requires a RESPOND_VIA_MESSAGE service. Sending is not
 * built yet, so a reply requested from an incoming call is not sent.
 */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

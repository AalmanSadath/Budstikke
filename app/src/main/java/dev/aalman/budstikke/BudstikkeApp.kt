package dev.aalman.budstikke

import android.app.Application
import dev.aalman.budstikke.data.Trash
import dev.aalman.budstikke.lock.LockSession
import dev.aalman.budstikke.sms.OutgoingQueue
import dev.aalman.budstikke.trash.schedulePurge
import kotlin.concurrent.thread

class BudstikkeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LockSession.install(this)
        // Empty expired trash whenever the app's process starts, and make sure the daily
        // alarm that does it otherwise is set.
        schedulePurge(this, onlyIfMissing = true)
        // Likewise send scheduled messages that fell due while alarms were cleared, as
        // force-stopping the app does, and set the alarm for the next one.
        thread(name = "app-start") {
            runCatching { Trash.get(this).purgeExpired() }
            runCatching { OutgoingQueue.sendDue(this) }
        }
    }
}

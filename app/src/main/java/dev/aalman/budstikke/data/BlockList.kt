package dev.aalman.budstikke.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.telephony.PhoneNumberUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Blocked numbers and sender names. Kept by the app rather than in Android's own block
 * list: Android drops SMS from numbers on its list before the app sees them, and these are
 * kept, read and silent, in Spam & blocked instead.
 */
class BlockList private constructor(context: Context) {

    private val db = AppDatabase.get(context)
    private val version = MutableStateFlow(0)
    @Volatile
    private var cached: List<String>? = null

    /** Emits at once, then after every change. */
    fun changes(): Flow<Unit> = version.map { }

    /** Blocked addresses, newest first. */
    fun all(): List<String> = cached ?: db.readableDatabase.query(
        TABLE, arrayOf(ADDRESS), null, null, null, null, "$BLOCKED_AT DESC",
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }.also { cached = it }

    fun isBlocked(address: String): Boolean = all().any { sameSender(it, address) }

    fun block(address: String) {
        if (isBlocked(address)) return
        db.writableDatabase.insertWithOnConflict(
            TABLE, null,
            ContentValues().apply {
                put(ADDRESS, address)
                put(BLOCKED_AT, System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        changed()
    }

    /** Unblocks every stored form of [address], so "+61 4..." and "04..." both go. */
    fun unblock(address: String) {
        val sql = db.writableDatabase
        all().filter { sameSender(it, address) }.forEach { sql.delete(TABLE, "$ADDRESS = ?", arrayOf(it)) }
        changed()
    }

    private fun changed() {
        cached = null
        version.update { it + 1 }
    }

    companion object {
        private const val TABLE = "blocked_numbers"
        private const val ADDRESS = "address"
        private const val BLOCKED_AT = "blocked_at"

        @Volatile
        private var instance: BlockList? = null

        fun get(context: Context): BlockList =
            instance ?: synchronized(this) {
                instance ?: BlockList(context).also { instance = it }
            }
    }
}

/**
 * Whether two addresses are the same sender. Phone numbers match loosely, so a number
 * written with or without its country code is one sender; sender names such as a bank's
 * match by text, ignoring case.
 */
internal fun sameSender(a: String, b: String): Boolean {
    val named = a.any { it.isLetter() } || b.any { it.isLetter() }
    return if (named) a.trim().equals(b.trim(), ignoreCase = true) else PhoneNumberUtils.compare(a, b)
}

package dev.aalman.budstikke.data

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The app's own data: what the system message store has no place for, such as pins and the
 * archive. Messages themselves stay in the system store.
 */
class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversation_flags (
                thread_id INTEGER PRIMARY KEY,
                pinned_at INTEGER,
                archived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        createTrash(db)
        createBlocks(db)
        createDrafts(db)
        createOutgoing(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createTrash(db)
        if (oldVersion < 3) createBlocks(db)
        if (oldVersion < 4) createDrafts(db)
        if (oldVersion < 5) createOutgoing(db)
    }

    /**
     * Messages waiting to be sent at a set time: scheduled ones, and ones held back by the
     * send delay. They reach the system store only when they are sent.
     */
    private fun createOutgoing(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE scheduled_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                thread_id INTEGER NOT NULL,
                address TEXT NOT NULL,
                body TEXT NOT NULL,
                sub_id INTEGER NOT NULL,
                send_at INTEGER NOT NULL,
                delayed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
    }

    /** Text typed in a conversation and not sent yet, one per conversation. */
    private fun createDrafts(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE drafts (thread_id INTEGER PRIMARY KEY, body TEXT NOT NULL, updated_at INTEGER NOT NULL)",
        )
    }

    /** Numbers and sender names whose messages are kept out of sight and never notify. */
    private fun createBlocks(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE blocked_numbers (address TEXT PRIMARY KEY, blocked_at INTEGER NOT NULL)")
    }

    /**
     * Deleted conversations and messages. One item is one deletion; its messages are copies
     * of the rows taken out of the system store, enough to write them back as they were.
     */
    private fun createTrash(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE trash_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                whole_conversation INTEGER NOT NULL,
                address TEXT NOT NULL,
                deleted_at INTEGER NOT NULL,
                pinned_at INTEGER,
                archived INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE trash_messages (
                item_id INTEGER NOT NULL REFERENCES trash_items(id) ON DELETE CASCADE,
                address TEXT,
                body TEXT,
                date INTEGER NOT NULL,
                date_sent INTEGER NOT NULL,
                type INTEGER NOT NULL,
                read INTEGER NOT NULL,
                seen INTEGER NOT NULL,
                status INTEGER NOT NULL,
                sub_id INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX trash_messages_item ON trash_messages(item_id)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        private const val NAME = "budstikke.db"
        private const val VERSION = 5

        // Holds only the application context, which lives as long as the process anyway.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: AppDatabase(context).also { instance = it }
            }
    }
}

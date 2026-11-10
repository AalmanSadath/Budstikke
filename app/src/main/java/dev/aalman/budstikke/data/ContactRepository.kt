package dev.aalman.budstikke.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.LruCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * A saved contact, as matched from a phone number. [version] counts changes to the contacts
 * store: a new photo keeps the same [photoUri], so the version is what tells a screen to
 * load it again.
 */
data class Contact(val name: String, val photoUri: Uri?, val version: Int = 0)

/**
 * Looks up saved contacts by phone number, through Android's own matching so that
 * "+61 412 345 678" and "0412345678" find the same person. Results are cached until the
 * contacts store changes.
 *
 * Lookups read the contacts store, so call them off the main thread.
 */
class ContactRepository private constructor(context: Context) {

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver
    private val cache = ConcurrentHashMap<String, Lookup>()
    private val photos = LruCache<Uri, Bitmap>(PHOTO_CACHE_SIZE)
    private val version = MutableStateFlow(0)

    init {
        // Watched for the life of the process rather than only while a screen collects:
        // contacts are usually edited while this app is in the background, and the cache
        // has to be dropped then too. Registering needs no permission.
        resolver.registerContentObserver(
            ContactsContract.AUTHORITY_URI,
            true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = invalidate()
            },
        )
    }

    private sealed interface Lookup {
        data class Found(val contact: Contact) : Lookup
        data object None : Lookup
    }

    val canRead: Boolean
        get() = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** The contact saved for [address], or null for an unsaved number, a sender name, or no access. */
    fun lookup(address: String): Contact? {
        if (address.isBlank() || !canRead) return null
        val cached = cache[address]
        if (cached != null) return (cached as? Lookup.Found)?.contact
        val contact = query(address)
        cache[address] = if (contact != null) Lookup.Found(contact) else Lookup.None
        return contact
    }

    /** The contact's photo thumbnail, or null when there is none or it cannot be read. */
    fun photo(uri: Uri): Bitmap? {
        photos.get(uri)?.let { return it }
        val bitmap = runCatching {
            resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return null
        photos.put(uri, bitmap)
        return bitmap
    }

    /**
     * Emits now, and again whenever saved contacts may have changed: an edit in the contacts
     * store, or access to contacts being granted.
     */
    fun changes(): Flow<Unit> = version.map { }

    /** Forgets cached lookups and photos, and tells every screen to look again. */
    fun invalidate() {
        cache.clear()
        photos.evictAll()
        version.update { it + 1 }
    }

    private fun query(address: String): Contact? {
        // A sender name such as "BANK" is not a number and cannot match a contact.
        if (address.none(Char::isDigit)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        return runCatching {
            resolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                null,
                null,
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
                Contact(name, c.getString(1)?.let(Uri::parse), version.value)
            }
        }.getOrNull()
    }

    companion object {
        private const val PHOTO_CACHE_SIZE = 64

        // Holds only the application context, which lives as long as the process anyway.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ContactRepository? = null

        fun get(context: Context): ContactRepository =
            instance ?: synchronized(this) {
                instance ?: ContactRepository(context).also { instance = it }
            }
    }
}

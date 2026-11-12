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
data class Contact(
    val name: String,
    val photoUri: Uri?,
    val version: Int = 0,
    /** Saved in the work profile rather than the personal one. */
    val work: Boolean = false,
    /** Opens this contact in the contacts app, where it can be seen and edited. */
    val lookupUri: Uri? = null,
)

/** One saved number of a contact, as found by [ContactRepository.search]. */
data class ContactMatch(
    val name: String,
    val number: String,
    /** The number's type as the contacts app names it: "Mobile", "Work", a custom label. */
    val label: String,
    val contact: Contact,
)

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

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = invalidate()
    }

    @Volatile
    private var observing = false

    init {
        watch()
    }

    /**
     * Watches the contacts store for the life of the process rather than only while a
     * screen collects: contacts are usually edited while this app is in the background, and
     * the cache has to be dropped then too.
     *
     * Registering needs access to contacts, and throws without it, so this waits until
     * access is granted; [invalidate], which runs on the grant, tries again. Taking access
     * away later stops the process, so a registration never outlives it.
     */
    private fun watch() {
        if (observing || !canRead) return
        synchronized(this) {
            if (observing) return
            observing = runCatching {
                resolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, observer)
            }.isSuccess
        }
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

    /**
     * Saved phone numbers whose contact name or number matches [text], as Android's own
     * contact search finds them, at most [limit]. Empty without access to contacts.
     */
    fun search(text: String, limit: Int = SEARCH_LIMIT): List<ContactMatch> {
        if (text.isBlank() || !canRead) return emptyList()
        val personal = searchDirectory(text.trim(), ContactsContract.Directory.DEFAULT, limit)
        // The work profile's contacts, when there is one and its admin allows looking them
        // up from the personal side. Without one this finds nothing.
        val work = searchDirectory(text.trim(), ContactsContract.Directory.ENTERPRISE_DEFAULT, limit - personal.size)
        return personal + work
    }

    private fun searchDirectory(text: String, directory: Long, limit: Int): List<ContactMatch> {
        if (limit <= 0) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(text))
            .buildUpon()
            .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, directory.toString())
            .build()
        return runCatching {
            resolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ),
                null,
                null,
                null,
            )?.use { c ->
                val seen = HashSet<Pair<Long, String>>()
                buildList {
                    while (c.moveToNext() && size < limit) {
                        val number = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                        val id = c.getLong(0)
                        // A number saved twice on one contact, often once per account.
                        if (!seen.add(id to number.filter { it.isDigit() || it == '+' })) continue
                        val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: number
                        val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources, c.getInt(3), c.getString(4),
                        ).toString()
                        val work = directory == ContactsContract.Directory.ENTERPRISE_DEFAULT ||
                            ContactsContract.Contacts.isEnterpriseContactId(id)
                        add(ContactMatch(name, number, label, Contact(name, c.getString(5)?.let(Uri::parse), version.value, work)))
                    }
                }
            }
        }.getOrNull().orEmpty()
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
        watch()
        cache.clear()
        photos.evictAll()
        version.update { it + 1 }
    }

    private fun query(address: String): Contact? {
        // A sender name such as "BANK" is not a number and cannot match a contact.
        if (address.none(Char::isDigit)) return null
        // The enterprise lookup finds personal contacts first, then work ones when the phone
        // has a work profile that allows it; without one it is the same as the plain lookup.
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.ENTERPRISE_CONTENT_FILTER_URI, Uri.encode(address))
        return runCatching {
            resolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.LOOKUP_KEY,
                ),
                null,
                null,
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
                val id = c.getLong(2)
                Contact(
                    name,
                    c.getString(1)?.let(Uri::parse),
                    version.value,
                    work = ContactsContract.Contacts.isEnterpriseContactId(id),
                    lookupUri = c.getString(3)?.let { ContactsContract.Contacts.getLookupUri(id, it) },
                )
            }
        }.getOrNull()
    }

    companion object {
        private const val PHOTO_CACHE_SIZE = 64
        private const val SEARCH_LIMIT = 50

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

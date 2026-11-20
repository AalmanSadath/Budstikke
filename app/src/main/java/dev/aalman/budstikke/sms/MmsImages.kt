package dev.aalman.budstikke.sms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.telephony.SmsManager
import java.io.ByteArrayOutputStream

/**
 * What the carrier accepts in one MMS, and [textThreshold]: how many SMS parts a text may
 * take before the carrier would rather have it as an MMS, or 0 when it names none.
 */
data class MmsLimits(val maxMessageBytes: Int, val maxWidth: Int, val maxHeight: Int, val textThreshold: Int = 0) {

    /** How many SMS parts a text may take before it goes as an MMS, when that is on. */
    val longTextParts: Int get() = if (textThreshold > 0) textThreshold else DEFAULT_LONG_TEXT_PARTS

    /**
     * Whether a text of [parts] SMS goes as one MMS. [setting] is the person's choice, or
     * null to follow the carrier, which turns it on by naming a threshold.
     */
    fun sendsAsMms(parts: Int, setting: Boolean?): Boolean = (setting ?: (textThreshold > 0)) && parts > longTextParts

    companion object {
        /** Used when the carrier names no threshold: past three SMS, parts often arrive out of order. */
        const val DEFAULT_LONG_TEXT_PARTS = 3

        /** Used when the carrier names no limit: what most carriers accept. */
        val DEFAULT = MmsLimits(maxMessageBytes = 300 * 1024, maxWidth = 1600, maxHeight = 1600)

        /** The limits of the carrier behind [subscriptionId], from Android's MMS configuration. */
        fun of(context: Context, subscriptionId: Int): MmsLimits {
            val config = runCatching { smsManagerFor(context, subscriptionId).carrierConfigValues }.getOrNull()
                ?: return DEFAULT
            fun positive(key: String, fallback: Int) = config.getInt(key, fallback).takeIf { it > 0 } ?: fallback
            return MmsLimits(
                maxMessageBytes = positive(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, DEFAULT.maxMessageBytes),
                maxWidth = positive(SmsManager.MMS_CONFIG_MAX_IMAGE_WIDTH, DEFAULT.maxWidth),
                maxHeight = positive(SmsManager.MMS_CONFIG_MAX_IMAGE_HEIGHT, DEFAULT.maxHeight),
                textThreshold = config.getInt(SmsManager.MMS_CONFIG_SMS_TO_MMS_TEXT_THRESHOLD, 0).coerceAtLeast(0),
            )
        }
    }
}

/** One attempt at fitting a picture: its longest side in pixels and its JPEG quality. */
data class ShrinkStep(val longestSide: Int, val quality: Int)

/**
 * Shrinks pictures to send. Each is decoded straight to a bounded size, re-encoded as JPEG,
 * and made smaller step by step until it fits its share of the carrier's limit. Re-encoding
 * also drops the photo's metadata, location included.
 */
object MmsImages {

    /** How many pictures one message carries at most. */
    const val MAX_PHOTOS = 5

    /** Room left in a message for its headers, text and layout. */
    const val OVERHEAD_BYTES = 8 * 1024

    /** The smallest a picture is made before giving up on it. */
    const val SMALLEST_SIDE = 320

    private val QUALITIES = listOf(85, 70, 55, 40)

    /**
     * The attempts, largest first: every quality at one size, then the same at three
     * quarters of that size, down to [SMALLEST_SIDE].
     */
    fun steps(startSide: Int): List<ShrinkStep> {
        val out = ArrayList<ShrinkStep>()
        var side = startSide.coerceAtLeast(SMALLEST_SIDE)
        while (true) {
            QUALITIES.forEach { out += ShrinkStep(side, it) }
            if (side <= SMALLEST_SIDE) break
            side = (side * 3 / 4).coerceAtLeast(SMALLEST_SIDE)
        }
        return out
    }

    /** Each picture's share of the message, after its text and [OVERHEAD_BYTES]. */
    fun budgetPerImage(limits: MmsLimits, textBytes: Int, images: Int): Int =
        if (images <= 0) 0 else ((limits.maxMessageBytes - OVERHEAD_BYTES - textBytes) / images).coerceAtLeast(0)

    /**
     * The picture at [uri] as a JPEG of at most [budget] bytes, or null when it cannot be
     * read or does not fit even at its smallest. Call off the main thread.
     */
    fun shrink(context: Context, uri: Uri, limits: MmsLimits, budget: Int): ByteArray? {
        if (budget <= 0) return null
        val start = minOf(limits.maxWidth, limits.maxHeight)
        var lastSide = -1
        var bitmap: Bitmap? = null
        try {
            for (step in steps(start)) {
                if (step.longestSide != lastSide) {
                    bitmap?.recycle()
                    bitmap = decode(context, uri, step.longestSide) ?: return null
                    lastSide = step.longestSide
                }
                val out = ByteArrayOutputStream()
                bitmap!!.compress(Bitmap.CompressFormat.JPEG, step.quality, out)
                if (out.size() <= budget) return out.toByteArray()
            }
            return null
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * Every picture at [uris] shrunk to share one message with [text], or null when any
     * cannot be read or made to fit. Call off the main thread.
     */
    fun prepare(context: Context, uris: List<Uri>, text: String, subscriptionId: Int): List<ByteArray>? {
        val limits = MmsLimits.of(context, subscriptionId)
        val budget = budgetPerImage(limits, text.toByteArray().size, uris.size)
        return uris.map { shrink(context, it, limits, budget) ?: return null }
    }

    /** Decodes at most [longestSide] on the longer edge, set before any pixels are read. */
    private fun decode(context: Context, uri: Uri, longestSide: Int): Bitmap? = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val longest = maxOf(w, h)
            if (longest > longestSide) {
                val scale = longestSide.toFloat() / longest
                decoder.setTargetSize(maxOf(1, (w * scale).toInt()), maxOf(1, (h * scale).toInt()))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }.getOrNull()
}

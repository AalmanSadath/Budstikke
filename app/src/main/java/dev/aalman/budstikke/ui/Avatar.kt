package dev.aalman.budstikke.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aalman.budstikke.R
import dev.aalman.budstikke.data.Contact
import dev.aalman.budstikke.data.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Avatar colours. Picked from the number, so a conversation keeps its colour across launches. */
private val avatarColours = listOf(
    Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF59E0B),
    Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFFEF4444), Color(0xFF6366F1),
)

fun avatarColour(address: String): Color = avatarColours[Math.floorMod(address.hashCode(), avatarColours.size)]

/**
 * A conversation's avatar: the contact's photo when there is one, the contact's initials
 * on a coloured circle when there is not, and a person icon for an unsaved number or a
 * sender name. Decorative: the name next to it already says who it is.
 */
@Composable
fun Avatar(address: String, contact: Contact?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val context = LocalContext.current
    val photoUri = contact?.photoUri
    // Keyed on the version too: a changed photo keeps its URI.
    val photo by produceState<ImageBitmap?>(null, photoUri, contact?.version) {
        value = photoUri?.let { uri ->
            withContext(Dispatchers.IO) { ContactRepository.get(context).photo(uri)?.asImageBitmap() }
        }
    }
    Box(
        modifier.size(size).clip(CircleShape).background(avatarColour(address)),
        contentAlignment = Alignment.Center,
    ) {
        val image = photo
        val initials = contact?.name?.let(::initialsOf)
        when {
            image != null -> Image(image, contentDescription = null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            initials != null -> Text(
                initials,
                color = Color.White,
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            else -> Icon(
                painterResource(R.drawable.ic_person),
                contentDescription = null,
                modifier = Modifier.size(size * 0.6f),
                tint = Color.White,
            )
        }
    }
}

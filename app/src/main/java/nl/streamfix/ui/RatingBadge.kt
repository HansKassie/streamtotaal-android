package nl.streamfix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.Locale
import nl.streamfix.R

/** Goud, niet het oranje merkaccent: dat vecht met de focusrand. */
private val Ster = Color(0xFFFFC107)

/**
 * Het cijfer rechtsboven op een poster.
 *
 * Eigen donkere achtergrond en niet alleen tekst, want posters zijn daar
 * vaak zelf al donker of juist fel, en een los cijfer verdwijnt dan.
 *
 * Bewust geen IMDb-logo of -tekst erbij. Providers leveren alleen een veld
 * `rating` zonder te zeggen waar het vandaan komt; er IMDb bij zetten zou
 * een bewering zijn die we niet kunnen onderbouwen.
 *
 * Toon dit alleen met een waarde uit [nl.streamfix.domain.util.MediaRating];
 * die bepaalt of er überhaupt iets te tonen valt.
 */
@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    val tekst = String.format(Locale.getDefault(), "%.1f", rating)
    Row(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(
                R.string.rating_badge_description,
                tekst,
            ),
            tint = Ster,
            modifier = Modifier.size(11.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = tekst,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

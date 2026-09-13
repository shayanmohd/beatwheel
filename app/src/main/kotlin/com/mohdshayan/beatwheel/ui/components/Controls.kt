package com.mohdshayan.beatwheel.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohdshayan.beatwheel.core.music.Accidental
import com.mohdshayan.beatwheel.core.music.SpelledNote
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.theme.ButtonShape
import com.mohdshayan.beatwheel.ui.theme.RadiusSm

@Composable
fun PrimaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A plain label above a group of settings or rows. Sentence case, Graphite. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, start: Dp = 16.dp) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = start, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** A skeleton bar in the Case colour, in the shape of the text or row it stands in for. No shimmer. */
@Composable
fun SkeletonBar(width: Dp?, height: Dp, modifier: Modifier = Modifier) {
    Box(
        (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(RadiusSm))
            .background(Beatwheel.colors.case),
    )
}

/** Note letter, drawn accidental and octave. TalkBack reads "B flat 4". */
@Composable
fun NoteName(
    note: SpelledNote,
    letterStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    showOctave: Boolean = true,
) {
    val size = with(androidx.compose.ui.platform.LocalDensity.current) { letterStyle.fontSize.toDp().value }
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = note.spoken },
        verticalAlignment = Alignment.Top,
    ) {
        Text(note.letter.toString(), style = letterStyle, color = color)
        if (note.accidental != Accidental.NATURAL) {
            val glyph = if (note.accidental == Accidental.SHARP) Glyphs.Sharp else Glyphs.Flat
            val h = (size * 0.46f).dp
            Icon(
                glyph,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .padding(top = (size * 0.2f).dp, start = (size * 0.03f).dp)
                    .size(width = h * (if (glyph == Glyphs.Sharp) 0.67f else 0.53f), height = h),
            )
        }
        if (showOctave) {
            Text(
                note.octave.toString(),
                // A third of the letter, but never under 12sp so the octave of a small note name stays legible.
                style = letterStyle.copy(
                    fontSize = maxOf(letterStyle.fontSize.value * 0.34f, 12f).sp,
                    lineHeight = maxOf(letterStyle.fontSize.value * 0.34f, 12f).sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = (size * 0.62f).dp, start = (size * 0.02f).dp),
            )
        }
    }
}

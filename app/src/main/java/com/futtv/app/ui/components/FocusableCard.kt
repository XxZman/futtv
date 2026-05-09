package com.futtv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.futtv.app.ui.theme.CardBackground
import com.futtv.app.ui.theme.CardBackgroundFocused
import com.futtv.app.ui.theme.FocusBorder

@Composable
fun FocusableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    content: @Composable BoxScope.(isFocused: Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // Spring animation para escala — se siente más natural que tween en TV
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "scale"
    )
    val elevation by animateFloatAsState(
        targetValue = if (isFocused) 18f else 2f,
        animationSpec = tween(140),
        label = "elevation"
    )

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = elevation.dp,
                shape = shape,
                spotColor = if (isFocused) FocusBorder else Color.Black.copy(alpha = 0.4f),
                ambientColor = if (isFocused) FocusBorder.copy(alpha = 0.25f) else Color.Transparent
            )
            .clip(shape)
            .background(if (isFocused) CardBackgroundFocused else CardBackground)
            .border(
                BorderStroke(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) FocusBorder else Color.White.copy(alpha = 0.06f)
                ),
                shape
            )
            // ORDEN CORRECTO para TV: focusProperties → onFocusChanged → focusable → clickable
            .focusProperties { canFocus = true }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        content(isFocused)
    }
}

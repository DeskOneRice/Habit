package com.habit.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ArrowDirection { PREVIOUS, NEXT }

@Composable
fun LightweightArrowButton(
    onClick: () -> Unit,
    direction: ArrowDirection,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .semantics { this.contentDescription = contentDescription },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = if (direction == ArrowDirection.PREVIOUS) "‹" else "›",
            fontSize = 34.sp,
            lineHeight = 34.sp,
        )
    }
}

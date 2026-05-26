package com.rooftop.healthlog.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rooftop.healthlog.ui.theme.PrimaryBlue
import com.rooftop.healthlog.ui.theme.BgWhite

/** 大号主按钮（默认 48dp 高，文字 20sp） */
@Composable
fun PrimaryBigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = PrimaryBlue,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = BgWhite)
    ) {
        Text(text, style = textStyle, maxLines = 2, textAlign = TextAlign.Center)
    }
}

/** 次要按钮（描边） */
@Composable
fun SecondaryBigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

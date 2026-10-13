package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// สีถอดจาก :root ของ developer.html
object DevC {
    val P = Color(0xFF0C4AA6)
    val PD = Color(0xFF083570)
    val PL = Color(0xFFE8F0FE)
    val G = Color(0xFFF5F6F8)
    val T = Color(0xFF0F172A)
    val S = Color(0xFF64748B)
    val Gn = Color(0xFF16A34A)
    val GnL = Color(0xFFDCFCE7)
    val R = Color(0xFFDC2626)
    val RL = Color(0xFFFEE2E2)
    val O = Color(0xFFEA580C)
    val OL = Color(0xFFFFF7ED)
}

@Composable
fun DevCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(14.dp)
    ) { content() }
}

@Composable
fun DevSTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        color = DevC.P,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

package com.nimit.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val ArrowBackIcon: ImageVector by lazy {
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).addPath(
        pathData = PathParser().parsePathString("M19 12H5M12 5l-7 7 7 7").toNodes(),
        stroke = SolidColor(Color(0xFF0F172A)), strokeLineWidth = 2.5f
    ).build()
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.Gray).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) { Icon(ArrowBackIcon, null, tint = Color.Unspecified, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = C.Text)
    }
    HorizontalDivider(color = Color(0xFFF0F0F0))
}

@Composable
fun GradientButton(text: String, enabled: Boolean, height: Dp = 54.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.fillMaxWidth().height(height)
            .then(if (enabled) Modifier.shadow(10.dp, shape, ambientColor = C.Primary.copy(alpha = 0.3f), spotColor = C.Primary.copy(alpha = 0.3f)) else Modifier)
            .clip(shape)
            .background(if (enabled) Brush.linearGradient(listOf(C.Primary, C.PrimaryDark)) else SolidColor(Color(0xFFC5CFE8)))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
}

@Composable
fun NimitInput(
    value: String, onChange: (String) -> Unit, placeholder: String,
    height: Dp = 52.dp, singleLine: Boolean = true, fontSize: Int = 15,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        textStyle = TextStyle(fontSize = fontSize.sp, color = C.Text, lineHeight = (fontSize * 1.5).sp),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        modifier = modifier.fillMaxWidth().height(height).clip(shape).background(Color.White)
            .border(2.dp, if (focused) C.Primary else Color(0xFFE8E8E8), shape)
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = if (singleLine) 0.dp else 12.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (value.isEmpty()) Text(placeholder, fontSize = fontSize.sp, color = Color(0xFF9CA3AF))
                inner()
            }
        }
    )
}

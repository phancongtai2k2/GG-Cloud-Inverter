package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import vn.ggpower.inverter.domain.account.initialsOf
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Ảnh đại diện tròn (Me.dc.html, Account.dc.html): nền `primarySoft`, chữ viết tắt của [name] màu `primary`; có
 * [imageUri] thì hiện ảnh cắt vừa khung (đặc tả §3.15). Chỉ để trang trí, TalkBack không đọc.
 */
@Composable
fun Avatar(name: String, imageUri: String?, size: Dp, textStyle: TextStyle, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    Box(
        modifier.size(size).clip(CircleShape).background(colors.primarySoft).clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        val image = imageUri?.let { rememberImageThumbnail(it) }
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(initialsOf(name), style = textStyle, color = colors.primary)
        }
    }
}

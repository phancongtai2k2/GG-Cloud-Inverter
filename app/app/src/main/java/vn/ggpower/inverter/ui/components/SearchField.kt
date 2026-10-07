package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Ô tìm kiếm (Projects.dc.html; dùng lại ở Khám phá 7): cao 44 dp, bo 10 dp, nền `surfaceRaised`, viền 1 dp `lineStrong`,
 * đệm ngang 14 dp, icon kính lúp 20 dp `inkMuted` cách chữ 8 dp, chữ 14. [label] chỉ dành cho TalkBack (prototype ẩn nhãn).
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val shape = RoundedCornerShape(GgRadius.radiusMd)
    val textStyle = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, color = colors.ink)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.semantics { contentDescription = label },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(shape)
                    .background(colors.surfaceRaised)
                    .border(1.dp, colors.lineStrong, shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2),
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = colors.inkMuted), maxLines = 1)
                    inner()
                }
            }
        },
    )
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun SearchFieldPreview() {
    GgPowerTheme(darkTheme = false) {
        SearchField("", {}, placeholder = "Tìm theo tên hoặc địa chỉ", label = "Tìm dự án", modifier = Modifier.padding(20.dp))
    }
}

package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Bottom sheet (sheet thêm / sửa khung, TouStrategy.dc.html): nền `surfaceRaised`, bo trên 24 dp,
 * thanh kéo 40 × 4 dp màu `line`, đệm 12 / 20 / 24 dp, các khối cách 14 dp.
 * Tiêu đề 18/26 700, có vạch màu 4 × 22 dp bên trái khi truyền [accent] (ví dụ màu khung sạc khi sửa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GgBottomSheet(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GgTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = colors.surfaceRaised,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .size(40.dp, 4.dp)
                    .clip(CircleShape)
                    .background(colors.line),
            )
        },
    ) {
        GgSheetContent(title = title, accent = accent, content = content)
    }
}

/** Phần thân của sheet, tách riêng để xem trong Preview (ModalBottomSheet không vẽ trong Preview tĩnh). */
@Composable
fun GgSheetContent(
    title: String,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (accent != null) Box(Modifier.width(4.dp).height(22.dp).clip(CircleShape).background(accent))
            Text(
                text = title,
                style = GgTheme.typography.body.copy(fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
                color = GgTheme.colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun GgSheetContentPreview() {
    GgPowerTheme(darkTheme = false) {
        GgSheetContent(title = "Sửa khung Sạc 1", accent = GgTheme.colors.touCharge) {
            Text("Nguồn sạc: lưới điện", style = GgTheme.typography.caption, color = GgTheme.colors.inkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Huỷ", onClick = {}, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
                PrimaryButton("Lưu", onClick = {}, size = ButtonSize.Medium, modifier = Modifier.weight(2f))
            }
            DangerTextButton("Xoá khung này", onClick = {})
        }
    }
}

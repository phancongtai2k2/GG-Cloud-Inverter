package vn.ggpower.inverter.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.DangerTextButton
import vn.ggpower.inverter.ui.components.DashedButton
import vn.ggpower.inverter.ui.components.DayChips
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.GgBottomSheet
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgFilterChip
import vn.ggpower.inverter.ui.components.GgToggle
import vn.ggpower.inverter.ui.components.LabeledSlider
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.SectionHeader
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.SegmentedSize
import vn.ggpower.inverter.ui.components.SliderTickLabel
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.components.Timeline24h
import vn.ggpower.inverter.ui.components.TimelineHourTicks
import vn.ggpower.inverter.ui.components.TimelineLegend
import vn.ggpower.inverter.ui.components.TimelineSize
import vn.ggpower.inverter.ui.components.TimelineSlot
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgTheme
import java.time.DayOfWeek
import java.time.LocalTime

/** Chỉ bản debug: các component dùng chung với dữ liệu mẫu của prototype, để chụp và so với ảnh thiết kế. */
class ComponentGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val dark = intent.getBooleanExtra("dark", false)
        setContent { GgPowerTheme(darkTheme = dark) { Gallery() } }
    }
}

private val sampleSlots = listOf(
    TimelineSlot(LocalTime.of(22, 0), LocalTime.of(6, 0), SlotKind.Charge),
    TimelineSlot(LocalTime.of(13, 0), LocalTime.of(16, 0), SlotKind.Charge),
    TimelineSlot(LocalTime.of(9, 0), LocalTime.of(12, 0), SlotKind.Discharge),
    TimelineSlot(LocalTime.of(18, 0), LocalTime.of(21, 0), SlotKind.Discharge),
)

@Composable
private fun Gallery() {
    var seg by remember { mutableIntStateOf(0) }
    var kind by remember { mutableIntStateOf(0) }
    var filter by remember { mutableIntStateOf(0) }
    var soc by remember { mutableIntStateOf(20) }
    var t1 by remember { mutableStateOf(true) }
    var t2 by remember { mutableStateOf(false) }
    var days by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    var sheet by remember { mutableStateOf(false) }
    val colors = GgTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader("Khung giờ sạc/xả")
        GgCard(onClick = {}) {
            Text("Hôm nay", style = GgTheme.typography.caption, color = colors.inkMuted)
            Text("Ngày thường", style = GgTheme.typography.body, color = colors.ink)
            Timeline24h(sampleSlots, size = TimelineSize.Medium, modifier = Modifier.padding(top = 14.dp))
            TimelineHourTicks(Modifier.padding(top = 4.dp))
        }
        Timeline24h(sampleSlots.mapIndexed { i, s -> s.copy(dimmed = i != 0) }, size = TimelineSize.Large)
        TimelineHourTicks()
        TimelineLegend()
        Timeline24h(sampleSlots, size = TimelineSize.Mini)

        SectionHeader("Pin và ngưỡng SOC")
        GgCard {
            LabeledSlider(
                label = "SOC dự phòng (cắt xả)", valueText = "$soc %", value = soc, onValueChange = { soc = it },
                valueRange = 5..100, step = 5, scale = 0..100, marks = listOf(50),
                tickLabels = listOf(SliderTickLabel(0, "0"), SliderTickLabel(20, "20"), SliderTickLabel(50, "50"), SliderTickLabel(100, "100 %")),
            )
        }
        GgCard(verticalSpacing = 4.dp) {
            Text("Áp dụng các ngày", style = GgTheme.typography.label, color = colors.ink)
            DayChips(selected = days, onToggle = { d -> days = if (d in days) days - d else days + d })
        }

        SegmentedControl(listOf("Ngày", "Tháng", "Năm"), seg, { seg = it }, size = SegmentedSize.Compact)
        SegmentedControl(listOf("Khung sạc", "Khung xả"), kind, { kind = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tất cả (4)", "Chưa xử lý (2)", "Đã xử lý (2)").forEachIndexed { i, s ->
                GgFilterChip(s, selected = filter == i, onClick = { filter = i })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip("Online", StatusTone.Success)
            StatusChip("Cảnh báo", StatusTone.Warning)
            StatusChip("Lỗi", StatusTone.Danger)
            StatusChip("Hôm nay", StatusTone.Success, showDot = false, small = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            GgToggle(t1, { t1 = it })
            GgToggle(t2, { t2 = it })
            GgToggle(true, {}, enabled = false)
        }
        PrimaryButton("Đăng nhập", onClick = { sheet = true }, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Thêm thiết bị bằng mã QR", onClick = {}, modifier = Modifier.fillMaxWidth())
        DashedButton("Thêm khung sạc hoặc xả", onClick = { sheet = true })
        DangerTextButton("Xoá chiến lược này", onClick = {})
    }

    if (sheet) {
        GgBottomSheet(onDismiss = { sheet = false }, title = "Sửa khung Sạc 1", accent = colors.touCharge) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GgTextField("Giờ bắt đầu", "22:00", {}, Modifier.weight(1f))
                GgTextField("Giờ kết thúc", "06:00", {}, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GgTextField("SOC dừng (%)", "90", {}, Modifier.weight(1f))
                GgTextField("Công suất tối đa (W)", "3.000", {}, Modifier.weight(1f))
            }
            Text("Nguồn sạc: lưới điện", style = GgTheme.typography.caption, color = colors.inkMuted)
            ErrorBox("Trùng với Xả 1 (09:00 – 12:00). Các khung không được chồng lấn.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Huỷ", onClick = { sheet = false }, size = ButtonSize.Medium, modifier = Modifier.weight(1f))
                PrimaryButton("Lưu", onClick = { sheet = false }, size = ButtonSize.Medium, modifier = Modifier.weight(2f))
            }
            DangerTextButton("Xoá khung này", onClick = { sheet = false })
        }
    }
}

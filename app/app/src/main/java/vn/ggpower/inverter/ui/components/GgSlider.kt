package vn.ggpower.inverter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgTheme
import kotlin.math.roundToInt

/** Nhãn dưới thanh trượt tại [at] (cùng thang với [GgSlider] `scale`). */
data class SliderTickLabel(val at: Int, val text: String)

/**
 * Thanh trượt kiểu SOC dự phòng (Config.dc.html): vùng chạm cao 44 dp (Thay đổi 0.6 §1), ray 6 dp `surfaceSunken`,
 * phần đã chọn `energyBattery`, núm 22 dp trắng viền 2 dp, vạch mốc 2 × 14 dp `sliderTick`, nhãn 11/14 `inkMuted`.
 *
 * Vị trí vẽ theo [scale] (prototype vẽ trên thang 0–100 % dù giá trị nhỏ nhất là 5), giá trị bị kẹp trong
 * [valueRange] và làm tròn theo [step]. Kéo hoặc chạm đều đổi giá trị; hỗ trợ TalkBack qua `setProgress`.
 * [onValueChangeFinished]: gọi khi thả tay, sau một lần chạm hoặc sau `setProgress` (để mở hộp xác nhận).
 * Khoá ([enabled] = false): phần đã chọn và viền núm đổi sang `sliderDisabled`; [dimmed] làm mờ ray 50 % (Config.dc.html
 * chỉ mờ khi chưa có truyền thông BMS, còn khoá vì đang ghi / mất kết nối thì không).
 */
@Composable
fun GgSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    step: Int,
    modifier: Modifier = Modifier,
    scale: IntRange = valueRange,
    marks: List<Int> = emptyList(),
    tickLabels: List<SliderTickLabel> = emptyList(),
    valueText: String = value.toString(),
    enabled: Boolean = true,
    dimmed: Boolean = false,
    onValueChangeFinished: () -> Unit = {},
) {
    val colors = GgTheme.colors
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val span = (scale.last - scale.first).toFloat()
    fun fractionOf(v: Int) = ((v - scale.first) / span).coerceIn(0f, 1f)
    fun valueAt(fraction: Float): Int {
        val raw = scale.first + fraction.coerceIn(0f, 1f) * span
        val stepped = (raw / step).roundToInt() * step
        return stepped.coerceIn(valueRange.first, valueRange.last)
    }

    val fill = if (enabled) colors.energyBattery else colors.sliderDisabled
    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .alpha(if (dimmed) 0.5f else 1f)
                .semantics {
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value.toFloat(),
                        range = valueRange.first.toFloat()..valueRange.last.toFloat(),
                        steps = (valueRange.last - valueRange.first) / step - 1,
                    )
                    if (enabled) {
                        setProgress { target ->
                            val v = ((target / step).roundToInt() * step).coerceIn(valueRange.first, valueRange.last)
                            if (v != value) currentOnChange(v)
                            currentOnFinished()
                            true
                        }
                    } else {
                        disabled()
                    }
                }
                .then(
                    if (enabled) Modifier
                        .pointerInput(scale, valueRange, step) {
                            detectTapGestures { offset ->
                                currentOnChange(valueAt(offset.x / size.width))
                                currentOnFinished()
                            }
                        }
                        .pointerInput(scale, valueRange, step) {
                            detectHorizontalDragGestures(
                                onDragEnd = { currentOnFinished() },
                                onDragCancel = { currentOnFinished() },
                            ) { change, _ ->
                                change.consume()
                                currentOnChange(valueAt(change.position.x / size.width))
                            }
                        }
                    else Modifier,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            val width = maxWidth
            // Ray
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(colors.surfaceSunken))
            // Phần đã chọn
            Box(Modifier.width(width * fractionOf(value)).height(6.dp).clip(CircleShape).background(fill))
            // Vạch mốc
            marks.forEach { m ->
                Box(Modifier.offset(x = width * fractionOf(m) - 1.dp).size(2.dp, 14.dp).background(colors.sliderTick))
            }
            // Núm
            Box(
                Modifier
                    .offset(x = width * fractionOf(value) - 11.dp)
                    .size(22.dp)
                    .shadow(2.dp, CircleShape, ambientColor = colors.shadow, spotColor = colors.shadow)
                    .clip(CircleShape)
                    .background(colors.surfaceRaised)
                    .border(2.dp, fill, CircleShape),
            )
        }
        if (tickLabels.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
                val width = maxWidth
                tickLabels.forEach { label ->
                    val f = fractionOf(label.at)
                    Text(
                        text = label.text,
                        style = GgTheme.typography.caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                        color = colors.inkMuted,
                        modifier = Modifier.layout { measurable, constraints ->
                            val p = measurable.measure(constraints.copy(minWidth = 0))
                            val center = (width * f).roundToPx()
                            // Nhãn ở hai đầu canh theo mép, nhãn giữa canh giữa (như prototype).
                            val x = when {
                                f <= 0f -> 0
                                f >= 1f -> width.roundToPx() - p.width
                                else -> center - p.width / 2
                            }
                            layout(p.width, p.height) { p.place(x, 0) }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Thanh trượt có tiêu đề và giá trị lớn (Config.dc.html "SOC dự phòng (cắt xả)"): nhãn 14/20 500 bên trái,
 * giá trị 22/28 700 số đều bên phải. Prototype đặt ray trong hộp cao 24 dp cách hàng nhãn 8 dp; vùng chạm của
 * [GgSlider] cao 44 dp nên được đặt lùi lên 10 dp (đè lên đáy hàng nhãn) để tâm ray đúng vị trí thiết kế.
 */
@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    step: Int,
    modifier: Modifier = Modifier,
    scale: IntRange = valueRange,
    marks: List<Int> = emptyList(),
    tickLabels: List<SliderTickLabel> = emptyList(),
    enabled: Boolean = true,
    dimmed: Boolean = false,
    onValueChangeFinished: () -> Unit = {},
) {
    Layout(
        modifier = modifier,
        content = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                color = GgTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = GgTheme.typography.screenTitle.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                color = GgTheme.colors.ink,
            )
        }
        GgSlider(
            value = value, onValueChange = onValueChange, valueRange = valueRange, step = step,
            scale = scale, marks = marks, tickLabels = tickLabels, valueText = valueText, enabled = enabled, dimmed = dimmed,
            onValueChangeFinished = onValueChangeFinished,
        )
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val head = measurables[0].measure(loose)
        val slider = measurables[1].measure(loose)
        val sliderY = (head.height + 8.dp.roundToPx() - 10.dp.roundToPx()).coerceAtLeast(0)
        val width = constraints.constrainWidth(maxOf(head.width, slider.width))
        val height = constraints.constrainHeight(maxOf(head.height, sliderY + slider.height))
        layout(width, height) {
            head.place(0, 0)
            slider.place(0, sliderY)
        }
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF9FAF7)
@Composable
private fun LabeledSliderPreview() {
    GgPowerTheme(darkTheme = false) {
        LabeledSlider(
            label = "SOC dự phòng (cắt xả)",
            valueText = "20 %",
            value = 20,
            onValueChange = {},
            valueRange = 5..100,
            step = 5,
            scale = 0..100,
            marks = listOf(50),
            tickLabels = listOf(
                SliderTickLabel(0, "0"), SliderTickLabel(20, "20"),
                SliderTickLabel(50, "50"), SliderTickLabel(100, "100 %"),
            ),
            modifier = Modifier.padding(horizontal = 36.dp, vertical = 16.dp),
        )
    }
}

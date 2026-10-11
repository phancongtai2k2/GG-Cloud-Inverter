package vn.ggpower.inverter.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.project.ProjectStatus
import vn.ggpower.inverter.domain.settings.TemperatureUnit
import vn.ggpower.inverter.domain.telemetry.DataAge
import vn.ggpower.inverter.domain.telemetry.dataAge
import vn.ggpower.inverter.ui.components.ButtonSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.format.temperatureText
import vn.ggpower.inverter.ui.projects.ProjectStatusChip
import vn.ggpower.inverter.ui.theme.GgRadius
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

// Phần dùng chung của màn 17 · Theo dõi dữ liệu (inverter và pin), theo DataInverter / DataBattery.dc.html.

/** Một dòng giá trị: tên trường, thanh ghi (monospace) và giá trị đã định dạng ("—" khi chưa có). */
data class DataRow(val label: String, val register: String, val value: String)

/** Nút làm mới 48 dp ở đầu trang (§17.1). */
@Composable
fun RefreshButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            painterResource(R.drawable.ic_refresh),
            contentDescription = stringResource(R.string.cd_refresh),
            tint = GgTheme.colors.ink,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Dòng thiết bị dưới đầu trang (§17.1): tên 16/22 600; "SN … · cập nhật 3 giây trước" monospace 12/16 `inkMuted`;
 * chip trạng thái bên phải. Đệm 0 20 10, cách 10.
 */
@Composable
fun DeviceLine(name: String, serial: String, status: ProjectStatus, updatedAt: Instant, now: Instant) {
    val colors = GgTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(start = GgSpacing.space5, end = GgSpacing.space5, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = GgTheme.typography.body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            Text(
                stringResource(R.string.data_serial_updated, serial, ageText(dataAge(updatedAt, now))),
                style = GgTheme.typography.code.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = colors.inkMuted,
            )
        }
        ProjectStatusChip(status)
    }
}

@Composable
private fun ageText(age: DataAge): String = when (age) {
    is DataAge.Seconds -> pluralStringResource(R.plurals.data_updated_seconds, age.value.toInt(), age.value)
    is DataAge.Minutes -> pluralStringResource(R.plurals.data_updated_minutes, age.value.toInt(), age.value)
    is DataAge.At -> stringResource(
        R.string.data_updated_at,
        GgFormat.dateTimeShort(LocalDateTime.ofInstant(age.instant, ZoneId.systemDefault())),
    )
}

/**
 * Thẻ danh sách hai cột (§17.5): đệm 4 16; mỗi dòng cao tối thiểu 48, đệm 8, không kẻ phân cách; tên 13/18;
 * giá trị 14/20 600 số đều canh phải. Mã thanh ghi không hiển thị cho người dùng (quyết 06/10/2026).
 * Giá trị dài (ví dụ danh sách mã lỗi) xuống dòng riêng dưới tên: nếu để cùng hàng, nó chiếm hết bề ngang và tên bị
 * ép xuống dòng từng chữ cái.
 */
@Composable
fun DataRowsCard(rows: List<DataRow>) {
    val colors = GgTheme.colors
    GgCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        rows.forEach { row ->
            val labelStyle = GgTheme.typography.bodySm.copy(lineHeight = 18.sp)
            val valueStyle = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
            val rowModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = GgSpacing.space2)
                .clearAndSetSemantics { contentDescription = "${row.label}: ${row.value}" }
            if (row.value.length > LONG_VALUE_CHARS) {
                Column(rowModifier, verticalArrangement = Arrangement.spacedBy(GgSpacing.space1, Alignment.CenterVertically)) {
                    Text(row.label, style = labelStyle, color = colors.ink)
                    Text(row.value, style = valueStyle, color = colors.ink)
                }
            } else {
                Row(
                    rowModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
                ) {
                    Text(row.label, style = labelStyle, color = colors.ink, modifier = Modifier.weight(1f))
                    Text(row.value, style = valueStyle, color = colors.ink, textAlign = TextAlign.End, maxLines = 1)
                }
            }
        }
    }
}

/** Giá trị dài hơn mức này không còn vừa một hàng cạnh tên ở khổ 390 dp. */
private const val LONG_VALUE_CHARS = 22

/** Định dạng giá trị có đơn vị, null → "—" (§17.5). */
@Composable
fun valueOrDash(value: Double?, decimals: Int, unit: String): String =
    value?.let { GgFormat.withUnit(it, decimals, unit) } ?: stringResource(R.string.value_missing)

/** Nhiệt độ đo theo cài đặt °C/°F, 0 số lẻ; null → "—". */
@Composable
fun temperatureOrDash(celsius: Double?, unit: TemperatureUnit): String =
    celsius?.let { temperatureText(it, unit) } ?: stringResource(R.string.value_missing)

@Composable
fun DataLoadingBlocks() {
    val sunken = GgTheme.colors.surfaceSunken
    Column(Modifier.padding(horizontal = GgSpacing.space5), verticalArrangement = Arrangement.spacedBy(GgSpacing.space3)) {
        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(sunken))
        GgCard { Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(GgRadius.radiusMd)).background(sunken)) }
    }
}

@Composable
fun ColumnScope.DataErrorBlock(noConnection: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = GgSpacing.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GgSpacing.space4, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(if (noConnection) R.string.error_no_internet else R.string.error_server),
            style = GgTheme.typography.body,
            color = GgTheme.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        SecondaryButton(stringResource(R.string.action_retry), onClick = onRetry, size = ButtonSize.Medium)
    }
}

/**
 * Một nhóm dữ liệu xếp dọc (gg-inverter-design DataInverter / DataBattery: không còn tab chọn nhóm): tiêu đề in hoa
 * 12/16 600, giãn chữ 0,06 em, `inkMuted`, đệm ngang 4; nhóm sau cách thêm 8 ở trên. [content] mặc định là thẻ các dòng.
 */
@Composable
fun DataSection(title: String, first: Boolean, content: @Composable () -> Unit) {
    Text(
        title.uppercase(),
        style = GgTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em),
        color = GgTheme.colors.inkMuted,
        modifier = Modifier.padding(start = GgSpacing.space1, end = GgSpacing.space1, top = if (first) 0.dp else GgSpacing.space2)
            .semantics { heading() },
    )
    content()
}

package vn.ggpower.inverter.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.ggpower.inverter.R
import vn.ggpower.inverter.data.project.FakeProjectRepository
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.project.ProjectStatus
import vn.ggpower.inverter.domain.project.ProjectSummary
import vn.ggpower.inverter.domain.project.SiteType
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.StatusChip
import vn.ggpower.inverter.ui.components.StatusTone
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

/**
 * Thẻ dự án (Projects.dc.html): đệm 16 dp, các khối cách 12 dp. Hàng trên: ô icon 44 dp bo 12 dp, tên 15/22 600,
 * địa chỉ có ghim 14 dp và cấu hình 12/16 `inkMuted`, chip trạng thái bên phải. Hàng dưới (vạch trên 1 dp,
 * đệm trên 12 dp): 3 chỉ số, nhãn 12/16 500 `inkMuted`, giá trị 15/20 600 số đều.
 */
@Composable
fun ProjectCard(project: ProjectSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GgTheme.colors
    val type = GgTheme.typography
    GgCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(GgSpacing.space4),
        verticalSpacing = GgSpacing.space3,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3), verticalAlignment = Alignment.Top) {
            val (tileBg, tileFg, icon) = when (project.siteType) {
                SiteType.Home -> Triple(colors.primarySoft, colors.primary, R.drawable.ic_site_home)
                SiteType.Factory -> Triple(colors.accentSoft, colors.accentText, R.drawable.ic_site_factory)
            }
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = tileFg, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    project.name,
                    style = type.body.copy(lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GgSpacing.space1)) {
                    Icon(painterResource(R.drawable.ic_pin), contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(14.dp))
                    Text(
                        project.address,
                        style = type.caption.copy(fontWeight = FontWeight.Normal),
                        color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    project.configuration,
                    style = type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            ProjectStatusChip(project.status)
        }
        // Hàng chỉ số nhanh
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GgSpacing.space2)) {
            val missing = stringResource(R.string.value_missing)
            Metric(
                stringResource(R.string.projects_metric_pv),
                project.pvPowerKw?.let { GgFormat.withUnit(it, 1, stringResource(R.string.unit_kw)) } ?: missing,
                Modifier.weight(1f),
            )
            Metric(
                stringResource(R.string.projects_metric_today),
                project.todayEnergyKwh?.let { GgFormat.withUnit(it, 1, stringResource(R.string.unit_kwh)) } ?: missing,
                Modifier.weight(1f),
            )
            Metric(
                stringResource(R.string.projects_metric_soc),
                project.batterySocPercent?.let { GgFormat.withUnit(it.toLong(), stringResource(R.string.unit_percent)) } ?: missing,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun ProjectStatusChip(
    status: ProjectStatus,
    modifier: Modifier = Modifier,
    /** Kiểu nhỏ không chấm (chữ 11) của danh sách dự án màn 8 (Support.dc.html). */
    compact: Boolean = false,
) {
    val (text, tone) = when (status) {
        ProjectStatus.Online -> R.string.status_online to StatusTone.Success
        ProjectStatus.Warning -> R.string.status_warning to StatusTone.Warning
        ProjectStatus.Offline -> R.string.status_offline to StatusTone.Neutral
        ProjectStatus.Error -> R.string.status_error to StatusTone.Danger
        ProjectStatus.WaitingConnection -> R.string.status_waiting_connection to StatusTone.Info
    }
    StatusChip(stringResource(text), tone, modifier, showDot = !compact, small = compact)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label: $value" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = GgTheme.typography.caption, color = GgTheme.colors.inkMuted, maxLines = 1)
        Text(
            value,
            style = GgTheme.typography.label.copy(fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"),
            color = GgTheme.colors.ink,
            maxLines = 1,
        )
    }
}

@Preview(widthDp = 390, showBackground = true, backgroundColor = 0xFFF5F7F3)
@Composable
private fun ProjectCardPreview() {
    GgPowerTheme(darkTheme = false) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProjectCard(FakeProjectRepository.SAMPLE[0], onClick = {})
            ProjectCard(
                FakeProjectRepository.SAMPLE[2].copy(status = ProjectStatus.WaitingConnection, pvPowerKw = null, todayEnergyKwh = null, batterySocPercent = null),
                onClick = {},
            )
        }
    }
}

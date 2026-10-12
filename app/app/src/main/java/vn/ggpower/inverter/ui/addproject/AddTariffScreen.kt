package vn.ggpower.inverter.ui.addproject

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.ggpower.inverter.R
import vn.ggpower.inverter.domain.format.GgFormat
import vn.ggpower.inverter.domain.project.EVN_RESIDENTIAL_TIERS
import vn.ggpower.inverter.domain.project.EvnTier
import vn.ggpower.inverter.domain.project.TariffError
import vn.ggpower.inverter.domain.project.TariffInput
import vn.ggpower.inverter.domain.project.TariffKind
import vn.ggpower.inverter.ui.components.ErrorBox
import vn.ggpower.inverter.ui.components.FieldSize
import vn.ggpower.inverter.ui.components.GgCard
import vn.ggpower.inverter.ui.components.GgTextField
import vn.ggpower.inverter.ui.components.PrimaryButton
import vn.ggpower.inverter.ui.components.ScreenHeader
import vn.ggpower.inverter.ui.components.SecondaryButton
import vn.ggpower.inverter.ui.components.SegmentedControl
import vn.ggpower.inverter.ui.components.StepProgress
import vn.ggpower.inverter.ui.components.boldArgument
import vn.ggpower.inverter.ui.theme.GgPowerTheme
import vn.ggpower.inverter.ui.theme.GgSpacing
import vn.ggpower.inverter.ui.theme.GgTheme

@Composable
fun AddTariffRoute(viewModel: AddProjectViewModel, onBack: () -> Unit, onCreated: (projectId: String) -> Unit) {
    val state by viewModel.tariff.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.created.collect { onCreated(it) } }
    AddTariffScreen(state, onBack, viewModel::onTariffChange, viewModel::onSkip, viewModel::onCreate)
}

/**
 * Màn 14c · Thêm dự án – Giá điện (AddProjectTariff.png; đặc tả §3.14 bước 3). "Bỏ qua" và "Tạo dự án" đều tạo dự án
 * (Bỏ qua: không lưu giá điện, cài sau) rồi mở Màn hình chính của dự án mới.
 */
@Composable
fun AddTariffScreen(
    state: AddTariffUiState,
    onBack: () -> Unit,
    onChange: ((TariffInput) -> TariffInput) -> Unit,
    onSkip: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GgTheme.colors
    val focus = LocalFocusManager.current
    val input = state.input
    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding().imePadding()) {
        ScreenHeader(title = stringResource(R.string.add_project_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = GgSpacing.space5, end = GgSpacing.space5, top = GgSpacing.space2, bottom = GgSpacing.space6),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StepProgress(addProjectSteps(), current = 3)
            Text(
                boldArgument(stringResource(R.string.add_tariff_intro), stringResource(R.string.add_tariff_savings), colors.ink),
                style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 22.sp),
                color = colors.inkMuted,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.add_tariff_buy),
                    style = GgTheme.typography.caption.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                SegmentedControl(
                    options = listOf(stringResource(R.string.add_tariff_kind_evn), stringResource(R.string.add_tariff_kind_fixed)),
                    selectedIndex = if (input.kind == TariffKind.EvnTiered) 0 else 1,
                    onSelect = { i -> onChange { it.copy(kind = if (i == 0) TariffKind.EvnTiered else TariffKind.Fixed) } },
                    groupLabel = stringResource(R.string.add_tariff_buy),
                )
            }
            if (input.kind == TariffKind.EvnTiered) {
                EvnTierCard(EVN_RESIDENTIAL_TIERS)
            } else {
                GgTextField(
                    label = stringResource(R.string.add_tariff_fixed),
                    value = input.fixedPrice,
                    onValueChange = { v -> onChange { it.copy(fixedPrice = v) } },
                    size = FieldSize.Form,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    errorText = when (state.fixedError) {
                        TariffError.FixedEmpty -> stringResource(R.string.add_tariff_error_fixed_empty)
                        TariffError.FixedZero -> stringResource(R.string.add_tariff_error_fixed_zero)
                        null -> null
                    },
                )
            }
            GgTextField(
                label = stringResource(R.string.add_tariff_sell),
                value = input.sellPrice,
                onValueChange = { v -> onChange { it.copy(sellPrice = v) } },
                size = FieldSize.Form,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                helperText = stringResource(R.string.add_tariff_sell_hint),
            )
            state.formError?.let { error ->
                ErrorBox(
                    stringResource(
                        when (error) {
                            CreateError.LimitReached -> R.string.add_tariff_error_limit
                            CreateError.NoConnection -> R.string.error_no_internet
                            CreateError.Server -> R.string.error_server
                        },
                    ),
                )
            }
            Row(Modifier.padding(top = GgSpacing.space1), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    stringResource(R.string.add_tariff_skip),
                    onClick = { focus.clearFocus(); onSkip() },
                    enabled = !state.creating,
                    loading = state.skipping,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    stringResource(R.string.add_tariff_create),
                    onClick = { focus.clearFocus(); onCreate() },
                    enabled = !state.skipping,
                    loading = state.creating,
                    modifier = Modifier.weight(2f),
                )
            }
        }
    }
}

/**
 * Thẻ "Bậc thang EVN sinh hoạt" (AddProjectTariff.dc.html): đệm 8 16; tiêu đề 13/20 600 đệm 6; hai bậc đầu rồi dòng
 * tóm tắt "… · 6 bậc"; dòng tối thiểu 44 + đệm 8 hai đầu, nhãn 13/18 `inkMuted`, giá trị 14/20 600 số đều. Giá chưa
 * có thì hiện "[GIÁ] đ/kWh" như prototype.
 */
@Composable
private fun EvnTierCard(tiers: List<EvnTier>) {
    val colors = GgTheme.colors
    val placeholder = stringResource(R.string.add_tariff_price_placeholder)
    val unit = stringResource(R.string.unit_vnd_per_kwh)
    val rows = tiers.take(2).map { t ->
        val range = t.toKwh?.let { stringResource(R.string.add_tariff_tier, t.index, t.fromKwh, it) }
            ?: stringResource(R.string.add_tariff_tier_open, t.index, t.fromKwh)
        range to "${t.pricePerKwh?.let { GgFormat.number(it) } ?: placeholder} $unit"
    } + ("…" to pluralStringResource(R.plurals.add_tariff_tier_count, tiers.size, tiers.size))
    GgCard(contentPadding = PaddingValues(horizontal = GgSpacing.space4, vertical = GgSpacing.space2)) {
        Text(
            stringResource(R.string.add_tariff_evn_title),
            style = GgTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        rows.forEach { (label, value) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = GgSpacing.space2).heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GgSpacing.space3),
            ) {
                Text(label, style = GgTheme.typography.bodySm.copy(lineHeight = 18.sp), color = colors.inkMuted, modifier = Modifier.weight(1f))
                Text(
                    value,
                    style = GgTheme.typography.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                    color = colors.ink,
                )
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AddTariffPreview() {
    GgPowerTheme(darkTheme = false) { AddTariffScreen(AddTariffUiState(), {}, {}, {}, {}) }
}

@Preview(widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun AddTariffFixedPreview() {
    GgPowerTheme(darkTheme = false) { AddTariffScreen(AddTariffUiState(TariffInput(TariffKind.Fixed, fixedPrice = "2.500")), {}, {}, {}, {}) }
}

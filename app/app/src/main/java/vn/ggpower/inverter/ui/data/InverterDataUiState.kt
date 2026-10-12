package vn.ggpower.inverter.ui.data

import vn.ggpower.inverter.domain.telemetry.InverterTelemetry

/** Bốn nhóm dữ liệu inverter (đặc tả §17.2), xếp dọc theo thứ tự: Tổng quan · PV · Lưới & tải · Nhiệt độ. */
enum class InverterGroup { Overview, Pv, GridLoad, Temperature }

typealias InverterDataUiState = LiveUiState<InverterTelemetry>

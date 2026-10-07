package vn.ggpower.inverter.ui.home

import vn.ggpower.inverter.data.home.HomeOverview

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val overview: HomeOverview) : HomeUiState
}

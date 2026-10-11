package vn.ggpower.inverter.domain.home

/** Kiểu sơ đồ dòng năng lượng ở Màn hình chính (Main.dc.html `vw`). */
enum class FlowView { House, Nodes }

/**
 * Thẻ tuỳ chỉnh được của Màn hình chính (Main.dc.html `WDEF`, mã lưu [key]). Thẻ "Chế độ & pin" cố định ngay dưới sơ đồ,
 * không nằm trong danh sách này.
 */
enum class HomeWidget(val key: String, val isChart: Boolean) {
    Summary("sum", isChart = false),
    Power("pw", isChart = true),
    Yield("yl", isChart = true),
    Battery("bat", isChart = true),
}

/**
 * Bố cục Màn hình chính: kiểu sơ đồ và các thẻ đang hiện theo thứ tự (CLAUDE.md thiết kế §6). Lưu chung cho mọi dự án
 * (quyết định 11/10/2026). Thẻ không có trong [widgets] là đang ẩn.
 */
data class HomeLayout(
    val view: FlowView = FlowView.House,
    val widgets: List<HomeWidget> = DefaultWidgets,
) {
    val hidden: List<HomeWidget> get() = HomeWidget.entries.filter { it !in widgets }

    /**
     * Vị trí (trong [widgets]) của biểu đồ đầu tiên — dải chọn kỳ Ngày / Tháng / Năm và hàng ‹ kỳ › nằm ngay trên nó;
     * null khi không có biểu đồ nào (ẩn luôn dải chọn kỳ).
     */
    val firstChartIndex: Int? get() = widgets.indexOfFirst { it.isChart }.takeIf { it >= 0 }

    fun hide(widget: HomeWidget) = copy(widgets = widgets - widget)

    /** Thêm vào cuối danh sách đang hiện (dấu + ở mục "Thẻ có thể thêm"). */
    fun show(widget: HomeWidget) = if (widget in widgets) this else copy(widgets = widgets + widget)

    /** Kéo thẻ ở vị trí [from] tới [to] (kẹp trong danh sách). */
    fun move(from: Int, to: Int): HomeLayout {
        if (from !in widgets.indices) return this
        val target = to.coerceIn(0, widgets.lastIndex)
        if (target == from) return this
        val list = widgets.toMutableList()
        list.add(target, list.removeAt(from))
        return copy(widgets = list)
    }

    /** Chuỗi lưu DataStore: mã thẻ cách nhau dấu phẩy (rỗng = ẩn hết). */
    fun encodeWidgets(): String = widgets.joinToString(",") { it.key }

    companion object {
        val DefaultWidgets = listOf(HomeWidget.Summary, HomeWidget.Power, HomeWidget.Yield, HomeWidget.Battery)

        /** Đọc lại từ DataStore; [view] / [widgets] null (chưa lưu) thì dùng mặc định, mã lạ hoặc trùng thì bỏ qua. */
        fun decode(view: String?, widgets: String?): HomeLayout = HomeLayout(
            view = FlowView.entries.firstOrNull { it.name == view } ?: FlowView.House,
            widgets = widgets?.split(",")?.mapNotNull { k -> HomeWidget.entries.firstOrNull { it.key == k } }?.distinct()
                ?: DefaultWidgets,
        )
    }
}

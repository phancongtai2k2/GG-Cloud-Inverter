package vn.ggpower.inverter.domain.explore

import vn.ggpower.inverter.domain.project.foldVietnamese

/** Ba thẻ của màn 7 · Khám phá (đặc tả §7). */
enum class ExploreTab { Products, News, Videos }

/**
 * Hình minh hoạ của một mục; mỗi hình đi kèm một cặp màu cố định trong prototype (Explore.dc.html):
 * pin xanh lá, SmartBox xanh chủ đạo, inverter xanh dương, tấm pin cam, phụ kiện xám, bài viết xanh chủ đạo.
 */
enum class ExploreIcon { Battery, SmartBox, Inverter, SolarPanel, Accessory, Article }

/** Thẻ Sản phẩm: tên và mô tả ngắn (thông số còn để "[…]" chờ số liệu thật — đặc tả mục 7 điểm 3). */
data class ExploreProduct(
    val id: String,
    val name: String,
    val description: String,
    val icon: ExploreIcon,
)

/** Thẻ Tin tức: bài viết; [featured] là tin NỔI BẬT có ảnh lớn và ngày đăng. */
data class ExploreArticle(
    val id: String,
    val title: String,
    /** Chuyên mục, ví dụ "Hướng dẫn", "Mẹo sử dụng". */
    val category: String,
    val readMinutes: Int,
    val icon: ExploreIcon,
    val featured: Boolean = false,
    /** Ngày đăng hiện dưới tin nổi bật; prototype còn để "[26/09/2026]". */
    val dateLabel: String = "",
    /** Mô tả ảnh bìa cho TalkBack. */
    val imageDescription: String = "",
)

/** Thẻ Hướng dẫn: video có thời lượng và sản phẩm liên quan. */
data class ExploreVideo(
    val id: String,
    val title: String,
    val relatedProduct: String,
    val durationSeconds: Int,
    val icon: ExploreIcon,
)

data class ExploreContent(
    val products: List<ExploreProduct>,
    val articles: List<ExploreArticle>,
    val videos: List<ExploreVideo>,
) {
    fun isEmpty(tab: ExploreTab): Boolean = when (tab) {
        ExploreTab.Products -> products.isEmpty()
        ExploreTab.News -> articles.isEmpty()
        ExploreTab.Videos -> videos.isEmpty()
    }
}

/**
 * Lọc theo ô "Tìm sản phẩm, tin tức, video" (chốt 03/10/2026): giống tìm dự án — không phân biệt hoa thường và dấu,
 * mọi từ phải khớp. Sản phẩm khớp theo tên + mô tả, bài viết theo tiêu đề + chuyên mục, video theo tiêu đề + sản phẩm
 * liên quan. Câu tìm rỗng → giữ nguyên.
 */
fun ExploreContent.search(query: String): ExploreContent {
    val words = foldVietnamese(query).split(' ').filter { it.isNotEmpty() }
    if (words.isEmpty()) return this
    fun matches(vararg texts: String): Boolean {
        val haystack = foldVietnamese(texts.joinToString(" "))
        return words.all { it in haystack }
    }
    return ExploreContent(
        products = products.filter { matches(it.name, it.description) },
        articles = articles.filter { matches(it.title, it.category) },
        videos = videos.filter { matches(it.title, it.relatedProduct) },
    )
}

/** Nhãn thời lượng video: 225 → "3:45", 3725 → "1:02:05". */
fun formatVideoDuration(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

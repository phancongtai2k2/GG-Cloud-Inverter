package vn.ggpower.inverter.data.explore

import vn.ggpower.inverter.domain.explore.ExploreArticle
import vn.ggpower.inverter.domain.explore.ExploreContent
import vn.ggpower.inverter.domain.explore.ExploreIcon
import vn.ggpower.inverter.domain.explore.ExploreProduct
import vn.ggpower.inverter.domain.explore.ExploreVideo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nội dung màn 7 · Khám phá. Sau này lấy từ CMS (chưa có hợp đồng — docs/OPEN_QUESTIONS.md); giai đoạn này là dữ liệu
 * tĩnh nên đọc đồng bộ, không có trạng thái tải / lỗi (chốt 03/10/2026).
 */
interface ExploreRepository {
    fun getContent(): ExploreContent
}

/**
 * Dữ liệu mẫu của Explore.dc.html. Thông số sản phẩm (dung lượng S16 Pre, dải công suất inverter, công suất tấm)
 * **chưa xác nhận** (đặc tả mục 7 điểm 3) nên giữ nguyên chữ "[…]" như prototype.
 */
@Singleton
class FakeExploreRepository @Inject constructor() : ExploreRepository {
    override fun getContent(): ExploreContent = SAMPLE

    companion object {
        val SAMPLE = ExploreContent(
            products = listOf(
                ExploreProduct("s16-pre", "Pin lưu trữ S16 Pre", "Pin lưu trữ dân dụng · [DUNG LƯỢNG]", ExploreIcon.Battery),
                ExploreProduct("smartbox", "GG SmartBox", "Bộ điều khiển năng lượng cho hệ PV + pin", ExploreIcon.SmartBox),
                ExploreProduct("inverter-hybrid", "Inverter hybrid", "[DẢI CÔNG SUẤT]", ExploreIcon.Inverter),
                ExploreProduct("solar-panel", "Tấm pin mặt trời", "[CÔNG SUẤT TẤM]", ExploreIcon.SolarPanel),
                ExploreProduct("accessories", "Phụ kiện lắp đặt", "Cáp, tủ điện, giá đỡ", ExploreIcon.Accessory),
            ),
            articles = listOf(
                ExploreArticle(
                    "gg-power-quoc-te", "GG Power vươn tầm quốc tế", "Tin tức", 0, ExploreIcon.Article,
                    featured = true, dateLabel = "[26/09/2026]", imageDescription = "Trụ sở và nhà máy GG Power",
                ),
                ExploreArticle("doc-so-do-nang-luong", "Cách đọc sơ đồ dòng năng lượng trong app", "Hướng dẫn", 4, ExploreIcon.Article),
                ExploreArticle("ve-sinh-tam-pin", "Vệ sinh tấm pin mặt trời đúng cách", "Mẹo sử dụng", 3, ExploreIcon.SolarPanel),
                ExploreArticle("chon-che-do-pin", "Chọn chế độ vận hành pin phù hợp", "Hướng dẫn", 5, ExploreIcon.Battery),
            ),
            videos = listOf(
                ExploreVideo("video-smartbox-wifi", "Kết nối GG SmartBox với Wi-Fi", "GG SmartBox", 225, ExploreIcon.SmartBox),
                ExploreVideo("video-so-do", "Đọc sơ đồ dòng năng lượng trên app", "Ứng dụng GG Power", 130, ExploreIcon.SolarPanel),
                ExploreVideo("video-che-do-pin", "Cài đặt chế độ vận hành pin", "Pin lưu trữ S16 Pre", 245, ExploreIcon.Battery),
            ),
        )
    }
}

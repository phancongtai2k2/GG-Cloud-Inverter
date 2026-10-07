package vn.ggpower.inverter.domain.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreContentTest {

    private val content = ExploreContent(
        products = listOf(
            ExploreProduct("battery", "Pin lưu trữ S16 Pre", "Pin lưu trữ dân dụng · [DUNG LƯỢNG]", ExploreIcon.Battery),
            ExploreProduct("smartbox", "GG SmartBox", "Bộ điều khiển năng lượng cho hệ PV + pin", ExploreIcon.SmartBox),
            ExploreProduct("panel", "Tấm pin mặt trời", "[CÔNG SUẤT TẤM]", ExploreIcon.SolarPanel),
        ),
        articles = listOf(
            ExploreArticle("a1", "Vệ sinh tấm pin mặt trời đúng cách", "Mẹo sử dụng", 3, ExploreIcon.SolarPanel),
            ExploreArticle("a2", "Chọn chế độ vận hành pin phù hợp", "Hướng dẫn", 5, ExploreIcon.Battery),
        ),
        videos = listOf(
            ExploreVideo("v1", "Kết nối GG SmartBox với Wi-Fi", "GG SmartBox", 225, ExploreIcon.SmartBox),
            ExploreVideo("v2", "Cài đặt chế độ vận hành pin", "Pin lưu trữ S16 Pre", 245, ExploreIcon.Battery),
        ),
    )

    @Test
    fun `cau tim rong giu nguyen`() {
        assertSame(content, content.search("   "))
    }

    @Test
    fun `khong phan biet dau va hoa thuong`() {
        val result = content.search("PIN mat troi")
        assertEquals(listOf("panel"), result.products.map { it.id })
        assertEquals(listOf("a1"), result.articles.map { it.id })
        assertTrue(result.videos.isEmpty())
    }

    @Test
    fun `khop theo mo ta, chuyen muc va san pham lien quan`() {
        assertEquals(listOf("smartbox"), content.search("dieu khien").products.map { it.id })
        assertEquals(listOf("a1"), content.search("meo").articles.map { it.id })
        assertEquals(listOf("v2"), content.search("s16").videos.map { it.id })
    }

    @Test
    fun `moi tu phai khop`() {
        val result = content.search("smartbox wi-fi")
        assertEquals(listOf("v1"), result.videos.map { it.id })
        assertTrue(result.products.isEmpty())
    }

    @Test
    fun `khong con muc nao thi moi the deu rong`() {
        val result = content.search("xyz")
        ExploreTab.entries.forEach { assertTrue(result.isEmpty(it)) }
    }

    @Test
    fun `dinh dang thoi luong video`() {
        assertEquals("3:45", formatVideoDuration(225))
        assertEquals("0:05", formatVideoDuration(5))
        assertEquals("10:00", formatVideoDuration(600))
        assertEquals("1:02:05", formatVideoDuration(3725))
        assertEquals("0:00", formatVideoDuration(-3))
    }
}

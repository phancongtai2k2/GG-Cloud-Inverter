package vn.ggpower.inverter.domain.format

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ggpower.inverter.domain.project.normalizeKwpInput
import vn.ggpower.inverter.domain.project.normalizeMoneyInput
import vn.ggpower.inverter.domain.project.parseDecimal
import vn.ggpower.inverter.domain.project.parseMoney
import vn.ggpower.inverter.domain.settings.AppLanguage
import vn.ggpower.inverter.domain.tou.SlotKind
import vn.ggpower.inverter.domain.tou.TouSlot
import vn.ggpower.inverter.domain.tou.normalizePowerInput
import vn.ggpower.inverter.domain.tou.toInput
import java.time.LocalTime

/** Dấu số theo ngôn ngữ app (đặc tả mục 5: tiếng Việt "4,2 kW; 48.000 đ", tiếng Anh "4.2 kW; 48,000 VND"). */
class NumberStyleByLanguageTest {

    @After fun reset() {
        GgFormat.defaultStyle = NumberStyle.VI
    }

    @Test fun `kieu so theo ngon ngu`() {
        assertEquals(NumberStyle.VI, NumberStyle.of(AppLanguage.Vietnamese))
        assertEquals(NumberStyle.EN, NumberStyle.of(AppLanguage.English))
    }

    @Test fun `mac dinh la tieng Viet`() {
        assertEquals("4,2 kW", GgFormat.withUnit(4.2, 1, "kW"))
        assertEquals("48.000 đ", GgFormat.money(48_000, "đ"))
    }

    @Test fun `tieng Anh doi dau thap phan va dau nghin`() {
        GgFormat.defaultStyle = NumberStyle.EN
        assertEquals("4.2 kW", GgFormat.withUnit(4.2, 1, "kW"))
        assertEquals("48,000 VND", GgFormat.money(48_000, "VND"))
        assertEquals("3,000 W", GgFormat.withUnit(3000L, "W"))
        assertEquals("+1.1 kW", GgFormat.signedWithUnit(1.1, 1, "kW"))
        assertEquals("5.50 MWh", GgFormat.energy(5_500.0, "kWh", "MWh"))
        assertEquals("3.24 t", GgFormat.mass(3_240.0, "kg", "t"))
        assertEquals("2.5", GgFormat.trimmed(2.5))
        assertEquals("1,000", GgFormat.trimmed(1000.0))
        assertEquals("90.5 °F", GgFormat.temperature(32.5, vn.ggpower.inverter.domain.settings.TemperatureUnit.Fahrenheit, 1, "°F"))
    }

    @Test fun `o nhap so tieng Anh`() {
        GgFormat.defaultStyle = NumberStyle.EN
        assertEquals("3,500", normalizePowerInput("3500"))
        assertEquals("3,000", TouSlot(SlotKind.Charge, LocalTime.of(22, 0), LocalTime.of(6, 0), 90, 3000).toInput().maxPower)
        assertEquals("2,500", normalizeMoneyInput("2500"))
        assertEquals(2500L, parseMoney("2,500"))
        assertEquals("6.5", normalizeKwpInput("6,5"))
        assertEquals("12.34", normalizeKwpInput("12a.345"))
        assertEquals(6.5, parseDecimal("6.5")!!, 0.0)
    }
}

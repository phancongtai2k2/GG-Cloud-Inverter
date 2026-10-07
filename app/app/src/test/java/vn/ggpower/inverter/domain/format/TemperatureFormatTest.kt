package vn.ggpower.inverter.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ggpower.inverter.domain.settings.TemperatureUnit.Celsius
import vn.ggpower.inverter.domain.settings.TemperatureUnit.Fahrenheit

class TemperatureFormatTest {

    private fun c(v: Double, decimals: Int = 0) = GgFormat.temperature(v, Celsius, decimals, "°C")
    private fun f(v: Double, decimals: Int = 0) = GgFormat.temperature(v, Fahrenheit, decimals, "°F")

    @Test fun `do C giu nguyen gia tri`() {
        assertEquals("42 °C", c(42.0))
        assertEquals("32,5 °C", c(32.5, 1))
    }

    @Test fun `doi sang do F`() {
        assertEquals("113 °F", f(45.0))
        assertEquals("90,5 °F", f(32.5, 1))
        assertEquals("32 °F", f(0.0))
        assertEquals("212 °F", f(100.0))
    }

    @Test fun `cung so le nhu do C, lam tron nua len`() {
        assertEquals("108 °F", f(42.0))          // 107,6
        assertEquals("107,6 °F", f(42.0, 1))
        assertEquals("65,3 °F", f(18.5, 1))       // 65,3
        assertEquals("88 °F", f(31.0))            // 87,8
    }

    @Test fun `so am`() {
        assertEquals("-4 °F", f(-20.0))
        assertEquals("-40 °F", f(-40.0))
        assertEquals("-5,5 °C", c(-5.5, 1))
        assertEquals("0 °F", f(-17.8))            // -0,04 → không in "-0"
    }

    @Test fun `kieu so tieng Anh`() {
        assertEquals("90.5 °F", GgFormat.temperature(32.5, Fahrenheit, 1, "°F", NumberStyle.EN))
    }
}

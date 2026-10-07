package vn.ggpower.inverter.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import vn.ggpower.inverter.domain.account.initialsOf

class AppSettingsTest {
    @Test fun `ngon ngu theo he dieu hanh, ngoai vi va en thi dung tieng Viet`() {
        assertEquals(AppLanguage.Vietnamese, AppLanguage.fromTag("vi-VN"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("EN_gb"))
        assertEquals(AppLanguage.Vietnamese, AppLanguage.fromTag("fr-FR"))
        assertEquals(AppLanguage.Vietnamese, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.Vietnamese, AppLanguage.fromTag(null))
    }

    @Test fun `doi do C sang do F`() {
        assertEquals(42.0, TemperatureUnit.Celsius.fromCelsius(42.0), 1e-9)
        assertEquals(32.0, TemperatureUnit.Fahrenheit.fromCelsius(0.0), 1e-9)
        assertEquals(113.0, TemperatureUnit.Fahrenheit.fromCelsius(45.0), 1e-9)
        assertEquals(-4.0, TemperatureUnit.Fahrenheit.fromCelsius(-20.0), 1e-9)
    }

    @Test fun `chu viet tat tren avatar`() {
        assertEquals("MD", initialsOf("Michael Demo"))
        assertEquals("NA", initialsOf("Nguyễn Văn An"))
        assertEquals("ĐH", initialsOf("  đức   hải "))
        assertEquals("L", initialsOf("Lan"))
        assertEquals("", initialsOf("   "))
    }
}

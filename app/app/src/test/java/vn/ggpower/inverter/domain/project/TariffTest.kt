package vn.ggpower.inverter.domain.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TariffTest {

    @Test fun `bac thang EVN khong can o gia, gia ban trong coi nhu 0`() {
        val c = checkTariff(TariffInput(sellPrice = ""))
        assertNull(c.fixedError)
        assertEquals(Tariff(TariffKind.EvnTiered, null, 0), c.tariff)
    }

    @Test fun `gia co dinh bat buoc va lon hon 0`() {
        assertEquals(TariffError.FixedEmpty, checkTariff(TariffInput(TariffKind.Fixed)).fixedError)
        assertEquals(TariffError.FixedZero, checkTariff(TariffInput(TariffKind.Fixed, fixedPrice = "0")).fixedError)
        assertEquals(Tariff(TariffKind.Fixed, 2500, 0), checkTariff(TariffInput(TariffKind.Fixed, fixedPrice = "2.500")).tariff)
    }

    @Test fun `o tien khi go chen dau cham ngan nghin`() {
        assertEquals("2.500", normalizeMoneyInput("2500"))
        assertEquals("1.234.567", normalizeMoneyInput("12345678"))
        assertEquals("0", normalizeMoneyInput("000"))
        assertEquals("", normalizeMoneyInput("abc"))
        assertEquals(2500L, parseMoney("2.500"))
        assertNull(parseMoney(""))
    }

    @Test fun `sau bac EVN sinh hoat`() {
        assertEquals(6, EVN_RESIDENTIAL_TIERS.size)
        assertEquals(EvnTier(2, 51, 100), EVN_RESIDENTIAL_TIERS[1])
        assertNull(EVN_RESIDENTIAL_TIERS.last().toKwh)
    }
}

package vn.ggpower.inverter.domain.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyTest {
    @Test
    fun autoIsDayFromSixToEighteen() {
        assertTrue(isNightSky(SkyMode.Auto, 5))
        assertFalse(isNightSky(SkyMode.Auto, 6))
        assertFalse(isNightSky(SkyMode.Auto, 17))
        assertTrue(isNightSky(SkyMode.Auto, 18))
        assertTrue(isNightSky(SkyMode.Auto, 0))
    }

    @Test
    fun fixedModesIgnoreHour() {
        assertTrue(isNightSky(SkyMode.Night, 12))
        assertFalse(isNightSky(SkyMode.Day, 23))
    }

    @Test
    fun starsAreDeterministicAndInRange() {
        val stars = skyStars()
        assertEquals(46, stars.size)
        assertEquals(stars, skyStars())
        stars.forEach {
            assertTrue(it.xFraction in 0f..1f)
            assertTrue(it.topDp in 8f..128f)
            assertTrue(it.sizeDp in listOf(1f, 1.5f, 2.5f))
            assertTrue(it.periodMs in 2400..4800)
        }
    }
}

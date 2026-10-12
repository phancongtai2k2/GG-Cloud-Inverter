package vn.ggpower.inverter.domain.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalLevelTest {
    @Test
    fun rssiThresholds() {
        assertNull(SignalLevel.fromRssi(null))
        assertEquals(SignalLevel.Strong, SignalLevel.fromRssi(-45))
        assertEquals(SignalLevel.Strong, SignalLevel.fromRssi(-60))
        assertEquals(SignalLevel.Medium, SignalLevel.fromRssi(-61))
        assertEquals(SignalLevel.Medium, SignalLevel.fromRssi(-70))
        assertEquals(SignalLevel.Weak, SignalLevel.fromRssi(-71))
    }
}

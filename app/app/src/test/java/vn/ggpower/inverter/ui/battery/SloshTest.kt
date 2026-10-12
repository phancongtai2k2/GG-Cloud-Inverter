package vn.ggpower.inverter.ui.battery

import org.junit.Assert.assertEquals
import org.junit.Test

class SloshTest {
    @Test fun `song sanh theo moc prototype va ket thuc phang`() {
        assertEquals(0f, sloshOffset(0f), 1e-6f)
        assertEquals(10f, sloshOffset(0.1f), 1e-4f)
        assertEquals(-8f, sloshOffset(0.25f), 1e-4f)
        assertEquals(-1.2f, sloshOffset(0.85f), 1e-4f)
        assertEquals(0f, sloshOffset(1f), 1e-6f)
        // Giữa hai mốc, spline đối xứng cho đúng trung điểm.
        assertEquals(1f, sloshOffset(0.175f), 1e-3f)
    }
}

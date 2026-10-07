package vn.ggpower.inverter.domain.project

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdentityTest {
    @Test fun `serial bo khoang trang va viet hoa`() {
        assertEquals("SN2609A00137", normalizeSerial(" sn2609a 00137 "))
        assertEquals("", normalizeSerial("   "))
    }
}

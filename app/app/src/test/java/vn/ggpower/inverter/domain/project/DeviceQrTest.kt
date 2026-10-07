package vn.ggpower.inverter.domain.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceQrTest {

    @Test fun `checksum XOR cua phan truoc dau hai cham cuoi`() {
        assertEquals("19", qrChecksum("GGP:HYB5K:SN2609A00137"))
        assertEquals("00", qrChecksum(""))
    }

    @Test fun `ma hop le`() {
        assertEquals(DeviceQr("HYB5K", "SN2609A00137"), parseDeviceQr("GGP:HYB5K:SN2609A00137:19"))
        assertEquals(DeviceQr("HYB5K", "SN2609A00137"), parseDeviceQr("  GGP:HYB5K:SN2609A00137:19\n"))
    }

    @Test fun `sai checksum, sai tien to, chu thuong, dau cach, thieu phan deu khong hop le`() {
        assertNull(parseDeviceQr("GGP:HYB5K:SN2609A00137:7F"))
        assertNull(parseDeviceQr("ABC:HYB5K:SN2609A00137:19"))
        assertNull(parseDeviceQr("ggp:hyb5k:sn2609a00137:19"))
        assertNull(parseDeviceQr("GGP:HYB5K:SN2609 A00137:19"))
        assertNull(parseDeviceQr("GGP:HYB5K:19"))
        assertNull(parseDeviceQr("https://example.com"))
    }
}

package vn.ggpower.inverter.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordResetTest {

    @Test fun `so dien thoai giu 4 so dau va 3 so cuoi`() {
        assertEquals("0912 ••• 678", maskedDestination(AccountInput.parse("0912 345 678")))
        assertEquals("0912 ••• 678", maskedDestination(AccountInput.parse("+84912345678")))
    }

    @Test fun `email giu ky tu dau va ten mien`() {
        assertEquals("d•••@ggp.vn", maskedDestination(AccountInput.parse("Demo@GGP.vn")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tai khoan khong hop le thi bao loi`() {
        maskedDestination(AccountInput.Invalid)
    }
}

package vn.ggpower.inverter.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountInputTest {

    @Test fun `rong hoac chi khoang trang`() {
        assertEquals(AccountInput.Empty, AccountInput.parse(""))
        assertEquals(AccountInput.Empty, AccountInput.parse("   "))
    }

    @Test fun `so dien thoai mau trong thiet ke`() {
        assertEquals(AccountInput.Phone("0912345678"), AccountInput.parse("0912 345 678"))
    }

    @Test fun `so dien thoai co dau cham gach va dau cong 84`() {
        assertEquals(AccountInput.Phone("0912345678"), AccountInput.parse("0912.345.678"))
        assertEquals(AccountInput.Phone("0912345678"), AccountInput.parse("0912-345-678"))
        assertEquals(AccountInput.Phone("0912345678"), AccountInput.parse("+84 912 345 678"))
        assertEquals(AccountInput.Phone("0912345678"), AccountInput.parse("84912345678"))
    }

    @Test fun `cac dau so di dong`() {
        listOf("0312345678", "0512345678", "0712345678", "0812345678", "0912345678").forEach {
            assertEquals(AccountInput.Phone(it), AccountInput.parse(it))
        }
    }

    @Test fun `so dien thoai sai`() {
        assertEquals(AccountInput.Invalid, AccountInput.parse("091234567"))      // 9 số
        assertEquals(AccountInput.Invalid, AccountInput.parse("09123456789"))    // 11 số
        assertEquals(AccountInput.Invalid, AccountInput.parse("0212345678"))     // đầu số cố định
        assertEquals(AccountInput.Invalid, AccountInput.parse("09123abc78"))
    }

    @Test fun `email hop le viet thuong`() {
        assertEquals(AccountInput.Email("an.nguyen@ggpower.vn"), AccountInput.parse("  An.Nguyen@GGPower.vn "))
        assertEquals(AccountInput.Email("a+b@mail.co.uk"), AccountInput.parse("a+b@mail.co.uk"))
    }

    @Test fun `email sai`() {
        assertEquals(AccountInput.Invalid, AccountInput.parse("an@"))
        assertEquals(AccountInput.Invalid, AccountInput.parse("an@ggpower"))
        assertEquals(AccountInput.Invalid, AccountInput.parse("@ggpower.vn"))
        assertEquals(AccountInput.Invalid, AccountInput.parse("an nguyen@ggpower.vn"))
    }
}

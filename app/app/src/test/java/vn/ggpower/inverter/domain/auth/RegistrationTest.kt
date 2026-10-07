package vn.ggpower.inverter.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationTest {
    private val ok = RegistrationInput("Nguyễn Văn An", "an.nguyen@gmail.com", "0912 345 678", "GGpower2026", "GGpower2026")

    @Test fun `du lieu mau cua prototype hop le va duoc chuan hoa`() {
        val c = checkRegistration(ok)
        assertTrue(c.valid)
        assertEquals(AccountInput.Email("an.nguyen@gmail.com"), c.email)
        assertEquals(AccountInput.Phone("0912345678"), c.phone)
    }

    @Test fun `o trong`() {
        val c = checkRegistration(RegistrationInput())
        assertFalse(c.valid)
        assertEquals(RegisterFieldError.NameEmpty, c.nameError)
        assertEquals(RegisterFieldError.EmailEmpty, c.emailError)
        assertEquals(RegisterFieldError.PhoneEmpty, c.phoneError)
        assertEquals(PasswordError.Empty, c.passwordError)
        assertEquals(PasswordError.ConfirmEmpty, c.confirmError)
    }

    @Test fun `email nhap so dien thoai va so dien thoai nhap email deu sai dinh dang`() {
        val c = checkRegistration(ok.copy(email = "0912345678", phone = "an@gmail.com"))
        assertEquals(RegisterFieldError.EmailInvalid, c.emailError)
        assertEquals(RegisterFieldError.PhoneInvalid, c.phoneError)
        assertNull(c.email)
        assertNull(c.phone)
    }

    @Test fun `ho ten chi co khoang trang la trong, mat khau yeu va khong khop`() {
        val c = checkRegistration(ok.copy(name = "   ", password = "abc", confirm = "abd"))
        assertEquals(RegisterFieldError.NameEmpty, c.nameError)
        assertEquals(PasswordError.TooWeak, c.passwordError)
        assertEquals(PasswordError.Mismatch, c.confirmError)
    }
}

package vn.ggpower.inverter.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRulesTest {

    @Test fun `toi thieu 8 ky tu, co ca chu va so`() {
        assertTrue(isValidPassword("matkhau1"))
        assertFalse(isValidPassword("matkh1"))
        assertFalse(isValidPassword("matkhauabc"))
        assertFalse(isValidPassword("12345678"))
    }

    @Test fun `loi o mat khau moi va o nhap lai`() {
        assertEquals(PasswordError.Empty, newPasswordError(""))
        assertEquals(PasswordError.TooWeak, newPasswordError("abc12"))
        assertNull(newPasswordError("GGpower2026"))
        assertEquals(PasswordError.ConfirmEmpty, confirmPasswordError("GGpower2026", ""))
        assertEquals(PasswordError.Mismatch, confirmPasswordError("GGpower2026", "GGpower2025"))
        assertNull(confirmPasswordError("GGpower2026", "GGpower2026"))
    }

    @Test fun `do manh theo prototype va cac muc khac`() {
        assertNull(passwordStrength(""))
        assertEquals(PasswordStrength.Weak, passwordStrength("abc"))
        assertEquals(PasswordStrength.Fair, passwordStrength("matkhau123"))
        assertEquals(PasswordStrength.Good, passwordStrength("GGpower2026"))
        assertEquals(PasswordStrength.Strong, passwordStrength("GGpower2026!"))
        assertEquals(PasswordStrength.Strong, passwordStrength("ggpower2026!x"))
    }
}

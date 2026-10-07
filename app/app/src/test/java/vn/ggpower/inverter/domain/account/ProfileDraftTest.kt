package vn.ggpower.inverter.domain.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDraftTest {

    private val saved = UserProfile(name = "Michael Demo", email = "demo@ggp.vn", phone = "0912 345 678")

    @Test fun `nhap moi mo tu ho so thi chua co thay doi`() {
        assertFalse(ProfileDraft.of(saved).hasChangesFrom(saved))
    }

    @Test fun `doi ten hoac anh thi co thay doi`() {
        assertTrue(ProfileDraft("Nguyễn Văn An", null).hasChangesFrom(saved))
        assertTrue(ProfileDraft("Michael Demo", "content://media/1").hasChangesFrom(saved))
    }

    @Test fun `khoang trang dau cuoi ten khong tinh la thay doi`() {
        assertFalse(ProfileDraft("  Michael Demo ", null).hasChangesFrom(saved))
    }

    @Test fun `ten trong hoac chi co khoang trang thi bao loi`() {
        assertEquals(ProfileNameError.Empty, ProfileDraft("", null).nameError())
        assertEquals(ProfileNameError.Empty, ProfileDraft("   ", null).nameError())
        assertNull(ProfileDraft("An", null).nameError())
    }

    @Test fun `luu thi ten bo khoang trang va giu email so dien thoai`() {
        val after = ProfileDraft(" Nguyễn Văn An ", "content://media/1").applyTo(saved)
        assertEquals("Nguyễn Văn An", after.name)
        assertEquals("content://media/1", after.avatarUri)
        assertEquals(saved.email, after.email)
        assertEquals(saved.phone, after.phone)
    }

    @Test fun `chu viet tat dung cho avatar`() {
        assertEquals("MD", initialsOf("Michael Demo"))
        assertEquals("NA", initialsOf("Nguyễn Văn An"))
    }
}

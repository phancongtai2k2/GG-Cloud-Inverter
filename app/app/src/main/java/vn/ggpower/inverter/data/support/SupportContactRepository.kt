package vn.ggpower.inverter.data.support

import vn.ggpower.inverter.domain.support.SupportContact
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hotline và email hỗ trợ (màn 6). Là cấu hình tĩnh của app, chưa có API; số chính thức còn chờ xác nhận
 * (đặc tả mục 7, điểm 2).
 */
interface SupportContactRepository {
    fun getContact(): SupportContact
}

/** Giá trị mẫu của Service.dc.html — **cần thay bằng thông tin thật** (đặc tả §6). */
@Singleton
class FakeSupportContactRepository @Inject constructor() : SupportContactRepository {
    override fun getContact(): SupportContact = SAMPLE

    companion object {
        val SAMPLE = SupportContact(hotline = "1900 6868", email = "support@ggpower.vn")
    }
}

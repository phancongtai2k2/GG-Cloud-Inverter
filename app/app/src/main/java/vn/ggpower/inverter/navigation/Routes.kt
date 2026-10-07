package vn.ggpower.inverter.navigation

import kotlinx.serialization.Serializable

// Route theo docs/design/screens.md. Số màn ghi ở chú thích.

// Ngoài phiên đăng nhập
@Serializable data object Login                                 // 1

@Serializable data class ForgotPasswordFlow(val account: String = "") // luồng 12 (nested graph); account: chữ đã gõ ở Đăng nhập
@Serializable data object ForgotPasswordFind                    // 12a
@Serializable data object ForgotPasswordOtp                     // 12b
@Serializable data object ForgotPasswordNew                     // 12c
@Serializable data object ForgotPasswordDone                    // 12d

@Serializable data object RegisterFlow                          // luồng 13 (nested graph)
@Serializable data object RegisterInfo                          // 13a
@Serializable data object RegisterOtp                           // 13b
@Serializable data object TermsOfUse                            // trang Điều khoản sử dụng (chưa có địa chỉ, OPEN_QUESTIONS)
@Serializable data object PrivacyPolicy                         // trang Chính sách bảo mật (chưa có địa chỉ, OPEN_QUESTIONS)

@Serializable data object QrScan                                // 18

// Cấp tài khoản (thanh thẻ: Dự án · Tôi)
@Serializable data object Projects                              // 2 (cả trạng thái rỗng)
@Serializable data object Me                                    // 3
@Serializable data object Account                               // 15
@Serializable data class ChangeContact(val phone: Boolean)       // 25 (chưa thiết kế: placeholder)
@Serializable data object ChangePassword                        // 26 (chưa thiết kế: placeholder)

@Serializable data object AddProjectFlow                        // luồng 14 (nested graph)
@Serializable data object AddProjectDevice                      // 14a
@Serializable data object AddProjectInfo                        // 14b
@Serializable data object AddProjectTariff                      // 14c

// Cấp dự án (thanh thẻ: Màn hình chính · Thiết bị · Dịch vụ)
@Serializable data class ProjectHome(val projectId: String)      // 4
@Serializable data class Devices(val projectId: String)          // 5
@Serializable data class Service(val projectId: String)          // 6

@Serializable data class Config(val projectId: String)           // 16
@Serializable data class TouSchedule(val projectId: String)      // 16a
/** 16b. [strategyId] = [NEW_STRATEGY_ID] khi mở từ "Thêm chiến lược": chiến lược trống chỉ có trong bản nháp (ADR-002 mục 9). */
@Serializable data class TouStrategy(val projectId: String, val strategyId: String)
const val NEW_STRATEGY_ID = "new"
@Serializable data class Alerts(val projectId: String)           // 10
@Serializable data class AlertSettings(val projectId: String)    // 28 · chưa có thiết kế (đặc tả §3.28)
@Serializable data class AddDevice(val projectId: String)        // 27 · chưa có thiết kế (đặc tả §3.27; ADR-004 mục 3)
@Serializable data class AlertDetail(val projectId: String, val alertId: String)    // 10a
@Serializable data class BatteryEnergy(val projectId: String)    // 11
@Serializable data class DataInverter(val projectId: String)     // 17 · Inverter
@Serializable data class DataBattery(val projectId: String)      // 17 · Pin S16 Pre
@Serializable data class Explore(val projectId: String)          // 7
@Serializable data class ExploreDetail(val projectId: String, val itemId: String)  // 29 · chưa có thiết kế (đặc tả §3.29)
/** 8. Mở từ 10a thì có [alertId] để điền sẵn form, quay lại về 10a (AGENTS.md §6). */
@Serializable data class Support(val projectId: String, val alertId: String? = null)
/** 24 · Gửi yêu cầu thành công (đặc tả §3.24; chưa có ảnh — chốt 03/10/2026). */
@Serializable data class SupportSent(val projectId: String, val ticketCode: String, val technical: Boolean)
@Serializable data class TicketDetail(val projectId: String, val ticketId: String)  // 8 · chi tiết
@Serializable data class Warranty(val projectId: String)         // 9

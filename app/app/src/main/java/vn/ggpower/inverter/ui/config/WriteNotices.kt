package vn.ggpower.inverter.ui.config

import vn.ggpower.inverter.domain.config.WriteErrorMessage
import vn.ggpower.inverter.domain.config.WriteOutcome

/** Snackbar cho kết quả lệnh (BO_SUNG_v3.md §2); [WriteOutcome.Unknown] dùng dialog nên không có snackbar. */
fun WriteOutcome.noticeKind(): NoticeKind? = when (this) {
    WriteOutcome.Applied -> NoticeKind.Applied
    WriteOutcome.AppliedWithoutDischarge -> NoticeKind.AppliedWithoutDischarge
    WriteOutcome.SavedNotWritten -> NoticeKind.SavedNotWritten
    WriteOutcome.Reverted -> NoticeKind.Reverted
    WriteOutcome.Failed -> NoticeKind.Failed
    WriteOutcome.DeviceRejected -> NoticeKind.DeviceRejected
    WriteOutcome.Unknown -> null
}

/** Snackbar cho lỗi gửi lệnh chỉ cần báo (BO_SUNG_v3.md §3). */
fun WriteErrorMessage.noticeKind(): NoticeKind = when (this) {
    WriteErrorMessage.NotPermitted -> NoticeKind.NotPermitted
    WriteErrorMessage.CommandInProgress -> NoticeKind.CommandInProgress
    WriteErrorMessage.RateLimited -> NoticeKind.RateLimited
    WriteErrorMessage.InvalidData -> NoticeKind.InvalidData
    WriteErrorMessage.Generic -> NoticeKind.Generic
}

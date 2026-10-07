package vn.ggpower.inverter.domain.tou

import java.time.DayOfWeek

/**
 * Một dòng của hộp xác nhận "cũ → mới" khi lưu lịch TOU (ADR-002 mục 2, BO_SUNG_v3.md §1). Chữ hiển thị dựng ở
 * tầng UI. [strategyName] là tên trong bản nháp (hoặc tên cũ nếu chiến lược bị xoá).
 */
sealed interface TouChange {
    val strategyName: String

    data class StrategyAdded(override val strategyName: String) : TouChange
    data class StrategyRemoved(override val strategyName: String, val days: Set<DayOfWeek>) : TouChange
    data class Renamed(override val strategyName: String, val oldName: String) : TouChange
    data class DaysChanged(override val strategyName: String, val old: Set<DayOfWeek>, val new: Set<DayOfWeek>) : TouChange
    data class SlotAdded(override val strategyName: String, val kind: SlotKind, val number: Int, val slot: TouSlot) : TouChange
    data class SlotRemoved(override val strategyName: String, val kind: SlotKind, val number: Int, val slot: TouSlot) : TouChange
    data class SlotChanged(override val strategyName: String, val kind: SlotKind, val number: Int, val old: TouSlot, val new: TouSlot) : TouChange
}

/**
 * Danh sách thay đổi từ [saved] sang [draft], theo thứ tự chiến lược trong bản nháp rồi chiến lược bị xoá. Khung so
 * theo "loại + số thứ tự" (Sạc 1 với Sạc 1…); thứ chuyển từ chiến lược khác hiện thành thay đổi ngày của cả hai.
 */
fun diffSchedules(saved: List<TouStrategy>, draft: List<TouStrategy>): List<TouChange> {
    val out = mutableListOf<TouChange>()
    val savedById = saved.associateBy { it.id }
    draft.forEach { d ->
        val s = savedById[d.id]
        if (s == null) {
            out += TouChange.StrategyAdded(d.name)
            if (d.weekdays.isNotEmpty()) out += TouChange.DaysChanged(d.name, emptySet(), d.weekdays)
            SlotKind.entries.forEach { kind ->
                d.slotsOf(kind).forEachIndexed { i, slot -> out += TouChange.SlotAdded(d.name, kind, i + 1, slot) }
            }
            return@forEach
        }
        if (s.name != d.name) out += TouChange.Renamed(d.name, s.name)
        if (s.weekdays != d.weekdays) out += TouChange.DaysChanged(d.name, s.weekdays, d.weekdays)
        SlotKind.entries.forEach { kind ->
            val old = s.slotsOf(kind)
            val new = d.slotsOf(kind)
            for (i in 0 until maxOf(old.size, new.size)) {
                val o = old.getOrNull(i)
                val n = new.getOrNull(i)
                when {
                    o == null && n != null -> out += TouChange.SlotAdded(d.name, kind, i + 1, n)
                    o != null && n == null -> out += TouChange.SlotRemoved(d.name, kind, i + 1, o)
                    o != null && n != null && o != n -> out += TouChange.SlotChanged(d.name, kind, i + 1, o, n)
                }
            }
        }
    }
    val draftIds = draft.map { it.id }.toSet()
    saved.filter { it.id !in draftIds }.forEach { out += TouChange.StrategyRemoved(it.name, it.weekdays) }
    return out
}

/** Chiến lược trong bản nháp không còn ngày nào → chặn Lưu (ADR-002 mục 3). */
fun saveBlockers(draft: List<TouStrategy>): List<TouStrategy> = draft.filter { it.weekdays.isEmpty() }

/** Lưu được khi có thay đổi và không có chiến lược nào thiếu ngày. */
fun canSave(saved: List<TouStrategy>, draft: List<TouStrategy>): Boolean =
    saveBlockers(draft).isEmpty() && diffSchedules(saved, draft).isNotEmpty()

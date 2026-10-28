package dev.wiptv.player

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Whether a programme can be replayed now: it has started, and the provider kept a recording. */
fun Programme.canCatchUp(channel: Channel, now: Long): Boolean = channel.catchUp && archived && start < now

/** "Today", "Yesterday", "Tomorrow", or the date further out, for [time] seen from [now]. */
internal fun dayLabel(time: Long, now: Long): String {
    fun dayOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
    return when (dayOf(time) - dayOf(now)) {
        0 -> "Today"
        -1 -> "Yesterday"
        1 -> "Tomorrow"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.UK).format(Date(time))
    }
}

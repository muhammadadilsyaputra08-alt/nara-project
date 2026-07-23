package com.axel.nara.utils

import java.util.Calendar

/**
 * Utilitas waktu — dipakai untuk menentukan sapaan persona ("Selamat pagi/siang/sore/malam, tuan...")
 * sesuai jam saat ini di device.
 */
object TimeUtils {

    enum class DayPeriod { PAGI, SIANG, SORE, MALAM }

    fun currentDayPeriod(calendar: Calendar = Calendar.getInstance()): DayPeriod {
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..10 -> DayPeriod.PAGI
            in 11..14 -> DayPeriod.SIANG
            in 15..18 -> DayPeriod.SORE
            else -> DayPeriod.MALAM
        }
    }

    fun currentTimeIso(): String {
        val cal = Calendar.getInstance()
        return "%04d-%02d-%02dT%02d:%02d:%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }
}

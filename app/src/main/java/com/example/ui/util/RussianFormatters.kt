package com.example.ui.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Утилиты форматирования дат, времени и чисел в русской локали (24-часовой формат).
 */
object RussianFormatters {

    private val ruLocale = Locale("ru", "RU")

    fun formatDateTime(timestampMillis: Long): String {
        val formatter = SimpleDateFormat("d MMM yyyy, HH:mm", ruLocale)
        return formatter.format(Date(timestampMillis))
    }

    fun formatShortTime(timestampMillis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("HH:mm", ruLocale)
        return formatter.format(Date(timestampMillis))
    }

    fun formatFullDateWithWeekday(timestampMillis: Long = System.currentTimeMillis()): String {
        val formatter = SimpleDateFormat("EEEE, d MMMM", ruLocale)
        val raw = formatter.format(Date(timestampMillis))
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(ruLocale) else it.toString() }
    }

    fun formatNumber(value: Int): String {
        return NumberFormat.getIntegerInstance(ruLocale).format(value)
    }
}

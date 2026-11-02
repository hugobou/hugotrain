package com.hugobou.hugotrain.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Utilidades para interpretar celdas leídas de Sheets (UNFORMATTED_VALUE + SERIAL_NUMBER). */
object Cells {
    private val EPOCH = LocalDate.of(1899, 12, 30)
    private val FORMATS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("d/M/yy"),
    )

    fun str(v: Any?): String = when (v) {
        null -> ""
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        else -> v.toString().trim()
    }

    fun num(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }

    fun int(v: Any?): Int? = num(v)?.toInt()

    fun date(v: Any?): LocalDate? = when (v) {
        is Number -> EPOCH.plusDays(v.toLong())
        is String -> {
            val s = v.trim().take(10)
            FORMATS.firstNotNullOfOrNull { f -> runCatching { LocalDate.parse(s, f) }.getOrNull() }
        }
        else -> null
    }

    fun get(row: List<Any?>, i: Int): Any? = row.getOrNull(i)
}

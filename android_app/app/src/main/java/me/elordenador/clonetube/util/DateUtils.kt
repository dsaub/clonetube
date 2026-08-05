package me.elordenador.clonetube.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SPANISH = Locale.forLanguageTag("es-ES")

/**
 * Formats an ISO-8601 timestamp from the backend (e.g. `2026-07-12T10:00:00+00:00`) into a
 * short Spanish date such as `12 jul 2026`. Tolerates the `Z` suffix and missing offsets.
 */
fun formatSpanishDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val parsers = arrayOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
    )
    for (parser in parsers) {
        parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
        runCatching { parser.parse(iso) }.getOrNull()?.let { date: Date ->
            val out = SimpleDateFormat("d MMM yyyy", SPANISH)
            out.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return out.format(date)
        }
    }
    return iso.take(10)
}

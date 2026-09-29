package com.quickqr.app.util

import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The different kinds of content a decoded QR/barcode value can represent. */
sealed class ParsedContent(val rawValue: String) {
    data class Url(val url: String) : ParsedContent(url)
    data class Wifi(val ssid: String, val password: String, val security: String) : ParsedContent(ssid)
    data class Email(val address: String, val subject: String?, val body: String?) : ParsedContent(address)
    data class Phone(val number: String) : ParsedContent(number)
    data class Sms(val number: String, val message: String?) : ParsedContent(number)
    data class Contact(val name: String?, val phone: String?, val email: String?, val organization: String? = null) :
        ParsedContent(name ?: "")
    data class Location(val latitude: String, val longitude: String, val label: String?) :
        ParsedContent("$latitude,$longitude")
    data class Event(
        val title: String?,
        val location: String?,
        val start: String?,
        val end: String?,
        val description: String?
    ) : ParsedContent(title ?: "Event")
    data class PlainText(val text: String) : ParsedContent(text)
}

/**
 * Best-effort classifier for raw barcode/QR payloads into the common QR sub-formats
 * (WIFI:, MATMSG:/mailto:, tel:, smsto:/sms:, vCard, geo:, VEVENT). Anything that doesn't
 * match a known pattern safely falls through to PlainText.
 */
object ContentParser {

    fun parse(raw: String): ParsedContent {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) ->
                ParsedContent.Url(trimmed)
            trimmed.startsWith("WIFI:", true) -> parseWifi(trimmed)
            trimmed.startsWith("MATMSG:", true) || trimmed.startsWith("mailto:", true) -> parseEmail(trimmed)
            trimmed.startsWith("tel:", true) -> ParsedContent.Phone(trimmed.removePrefix("tel:"))
            trimmed.startsWith("smsto:", true) || trimmed.startsWith("sms:", true) -> parseSms(trimmed)
            trimmed.startsWith("BEGIN:VCARD", true) -> parseVCard(trimmed)
            trimmed.contains("BEGIN:VEVENT", true) -> parseEvent(trimmed)
            trimmed.startsWith("geo:", true) -> parseGeo(trimmed)
            else -> ParsedContent.PlainText(trimmed)
        }
    }

    private fun namedField(raw: String, key: String): String {
        // Terminator is a semicolon OR end-of-string: not every QR generator in the
        // wild appends the trailing ";;" the spec calls for, and without this the
        // last field (often the Wi-Fi password) would silently come back empty.
        val match = Regex("$key:((?:\\\\.|[^;])*)(?:;|\$)").find(raw)
        return match?.groupValues?.get(1)
            ?.replace("\\;", ";")
            ?.replace("\\,", ",")
            ?.replace("\\:", ":")
            ?.replace("\\\\", "\\")
            ?: ""
    }

    private fun parseWifi(raw: String): ParsedContent.Wifi {
        val ssid = namedField(raw, "S")
        val password = namedField(raw, "P")
        val rawType = namedField(raw, "T").ifEmpty { "WPA" }
        val type = when (rawType.uppercase()) {
            "NOPASS" -> "None"
            "WEP" -> "WEP"
            else -> "WPA/WPA2"
        }
        return ParsedContent.Wifi(ssid, password, type)
    }

    private fun parseEmail(raw: String): ParsedContent.Email {
        if (raw.startsWith("mailto:", true)) {
            val rest = raw.removePrefix("mailto:")
            val address = rest.substringBefore("?")
            val query = rest.substringAfter("?", "")
            val params = query.split("&").mapNotNull { part ->
                val kv = part.split("=", limit = 2)
                if (kv.size == 2) kv[0].lowercase() to urlDecode(kv[1]) else null
            }.toMap()
            return ParsedContent.Email(address, params["subject"], params["body"])
        }
        val to = namedField(raw, "TO")
        val subject = namedField(raw, "SUB").ifEmpty { null }
        val body = namedField(raw, "BODY").ifEmpty { null }
        return ParsedContent.Email(to, subject, body)
    }

    private fun parseSms(raw: String): ParsedContent.Sms {
        val withoutScheme = raw.substringAfter(":")
        val parts = withoutScheme.split(":", limit = 2)
        val number = parts.getOrNull(0).orEmpty()
        val message = parts.getOrNull(1)
        return ParsedContent.Sms(number, message)
    }

    private fun urlDecode(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (e: Exception) {
        value
    }

    private fun parseVCard(raw: String): ParsedContent.Contact {
        fun field(key: String): String? {
            val match = Regex("(?m)^$key[^:]*:(.*)$").find(raw)
            return match?.groupValues?.get(1)?.trim()?.ifEmpty { null }
        }
        val name = field("FN") ?: field("N")
        val phone = field("TEL")
        val email = field("EMAIL")
        val org = field("ORG")
        return ParsedContent.Contact(name, phone, email, org)
    }

    private fun parseGeo(raw: String): ParsedContent.Location {
        // geo:lat,lon or geo:lat,lon,altitude or geo:0,0?q=lat,lon(Label)
        val body = raw.removePrefix("geo:")
        val beforeQuery = body.substringBefore("?")
        val query = body.substringAfter("?", "")
        var lat = beforeQuery.split(",").getOrNull(0)?.trim().orEmpty()
        var lon = beforeQuery.split(",").getOrNull(1)?.trim().orEmpty()
        var label: String? = null

        if (query.startsWith("q=", true)) {
            val qValue = query.removePrefix("q=")
            val labelMatch = Regex("\\(([^)]*)\\)").find(qValue)
            label = labelMatch?.groupValues?.get(1)?.ifEmpty { null }
            val coordsPart = qValue.substringBefore("(")
            val qCoords = coordsPart.split(",")
            if ((lat.isEmpty() || lat == "0") && qCoords.size >= 2) {
                lat = qCoords.getOrNull(0)?.trim() ?: lat
                lon = qCoords.getOrNull(1)?.trim() ?: lon
            } else if (label == null && !coordsPart.contains(Regex("^[0-9.\\-]+$"))) {
                // "q=Some Place Name" with no coordinates - use it as the label instead.
                label = qValue.ifEmpty { null }
            }
        }
        return ParsedContent.Location(lat, lon, label)
    }

    private fun parseEvent(raw: String): ParsedContent.Event {
        fun field(key: String): String? {
            val match = Regex("(?m)^$key[^:\\r\\n]*:(.*)$").find(raw)
            return match?.groupValues?.get(1)?.trim()?.ifEmpty { null }?.replace("\\n", "\n")
        }
        return ParsedContent.Event(
            title = field("SUMMARY"),
            location = field("LOCATION"),
            start = field("DTSTART"),
            end = field("DTEND"),
            description = field("DESCRIPTION")
        )
    }

    /** Parses an iCalendar DTSTART/DTEND value (basic or extended form) into epoch millis. */
    fun parseIcalMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val patterns = listOf("yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmmss", "yyyyMMdd")
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                if (pattern.endsWith("'Z'")) sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(value)?.time
            } catch (e: Exception) {
                // try the next pattern
            }
        }
        return null
    }

    /** Human-readable rendering of an iCalendar date value, falling back to the raw text. */
    fun formatIcalDate(value: String?): String {
        val millis = parseIcalMillis(value) ?: return value.orEmpty()
        val hasTime = (value?.length ?: 0) > 8
        val pattern = if (hasTime) "MMM d, yyyy \u00b7 h:mm a" else "MMM d, yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(java.util.Date(millis))
    }
}

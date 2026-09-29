package com.quickqr.app.util

/** Small emoji + human label pair describing a [ParsedContent] kind, shared by history + result UI. */
object ContentTypeLabel {

    fun of(parsed: ParsedContent): Pair<String, String> = when (parsed) {
        is ParsedContent.Url -> "\uD83D\uDD17" to "Website Link"
        is ParsedContent.Wifi -> "\uD83D\uDCF6" to "Wi-Fi Network"
        is ParsedContent.Email -> "\u2709\uFE0F" to "Email Address"
        is ParsedContent.Phone -> "\uD83D\uDCDE" to "Phone Number"
        is ParsedContent.Sms -> "\uD83D\uDCAC" to "Text Message"
        is ParsedContent.Contact -> "\uD83D\uDC64" to "Contact Card"
        is ParsedContent.Location -> "\uD83D\uDCCD" to "Location"
        is ParsedContent.Event -> "\uD83D\uDCC5" to "Event"
        is ParsedContent.PlainText -> "\uD83D\uDCDD" to "Text"
    }

    /** A short single-line preview of the parsed content, used in the history list. */
    fun previewOf(parsed: ParsedContent): String = when (parsed) {
        is ParsedContent.Url -> parsed.url
        is ParsedContent.Wifi -> "Wi-Fi: ${parsed.ssid}"
        is ParsedContent.Email -> parsed.address
        is ParsedContent.Phone -> parsed.number
        is ParsedContent.Sms -> parsed.number
        is ParsedContent.Contact -> parsed.name ?: "Contact"
        is ParsedContent.Location -> parsed.label ?: "${parsed.latitude}, ${parsed.longitude}"
        is ParsedContent.Event -> parsed.title ?: "Event"
        is ParsedContent.PlainText -> parsed.text
    }
}

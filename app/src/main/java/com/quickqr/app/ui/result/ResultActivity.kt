package com.quickqr.app.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.common.Barcode
import com.quickqr.app.R
import com.quickqr.app.data.AppDatabase
import com.quickqr.app.data.ScanEntity
import com.quickqr.app.databinding.ActivityResultBinding
import com.quickqr.app.util.ContentParser
import com.quickqr.app.util.ContentTypeLabel
import com.quickqr.app.util.ParsedContent
import kotlinx.coroutines.launch

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    companion object {
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_SKIP_SAVE = "extra_skip_save"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val content = intent.getStringExtra(EXTRA_CONTENT).orEmpty()
        val format = intent.getIntExtra(EXTRA_FORMAT, Barcode.FORMAT_UNKNOWN)
        val skipSave = intent.getBooleanExtra(EXTRA_SKIP_SAVE, false)

        renderContent(ContentParser.parse(content), formatName(format))

        if (!skipSave && content.isNotBlank()) {
            saveToHistory(content, format)
        }
    }

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR Code"
        Barcode.FORMAT_AZTEC -> "Aztec"
        Barcode.FORMAT_DATA_MATRIX -> "Data Matrix"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_EAN_13 -> "EAN-13"
        Barcode.FORMAT_EAN_8 -> "EAN-8"
        Barcode.FORMAT_UPC_A -> "UPC-A"
        Barcode.FORMAT_UPC_E -> "UPC-E"
        Barcode.FORMAT_CODE_128 -> "Code 128"
        Barcode.FORMAT_CODE_39 -> "Code 39"
        Barcode.FORMAT_CODE_93 -> "Code 93"
        Barcode.FORMAT_CODABAR -> "Codabar"
        Barcode.FORMAT_ITF -> "ITF"
        else -> "Code"
    }

    private fun renderContent(parsed: ParsedContent, formatLabel: String) {
        binding.buttonRow.removeAllViews()

        val (emoji, typeLabel) = ContentTypeLabel.of(parsed)
        binding.textFormat.text = "$emoji $typeLabel  \u00b7  $formatLabel"

        when (parsed) {
            is ParsedContent.Url -> {
                binding.textContent.text = parsed.url
                addAction("Open in Browser") { openUrl(parsed.url) }
                addAction("Copy") { copyToClipboard(parsed.url) }
                addAction("Share") { shareText(parsed.url) }
            }
            is ParsedContent.Wifi -> {
                binding.textContent.text = getString(R.string.wifi_details, parsed.ssid, parsed.password, parsed.security)
                addAction("Copy Password") { copyToClipboard(parsed.password) }
                addAction("Copy Network Name") { copyToClipboard(parsed.ssid) }
            }
            is ParsedContent.Email -> {
                binding.textContent.text = parsed.address
                addAction("Send Email") { sendEmail(parsed.address, parsed.subject, parsed.body) }
                addAction("Copy") { copyToClipboard(parsed.address) }
            }
            is ParsedContent.Phone -> {
                binding.textContent.text = parsed.number
                addAction("Call") { dialNumber(parsed.number) }
                addAction("Copy") { copyToClipboard(parsed.number) }
            }
            is ParsedContent.Sms -> {
                binding.textContent.text = parsed.message?.let { "${parsed.number}\n$it" } ?: parsed.number
                addAction("Send Message") { sendSms(parsed.number, parsed.message) }
                addAction("Copy Number") { copyToClipboard(parsed.number) }
            }
            is ParsedContent.Contact -> {
                binding.textContent.text = listOfNotNull(
                    parsed.name?.let { "Name: $it" },
                    parsed.organization?.let { "Organization: $it" },
                    parsed.phone?.let { "Phone: $it" },
                    parsed.email?.let { "Email: $it" }
                ).joinToString("\n")
                parsed.phone?.let { phone -> addAction("Call") { dialNumber(phone) } }
                parsed.email?.let { email -> addAction("Send Email") { sendEmail(email, null, null) } }
                addAction("Copy") { copyToClipboard(binding.textContent.text.toString()) }
            }
            is ParsedContent.Location -> {
                binding.textContent.text = listOfNotNull(
                    parsed.label,
                    "Latitude: ${parsed.latitude}",
                    "Longitude: ${parsed.longitude}"
                ).joinToString("\n")
                addAction("Open in Maps") { openLocation(parsed) }
                addAction("Copy Coordinates") { copyToClipboard("${parsed.latitude}, ${parsed.longitude}") }
            }
            is ParsedContent.Event -> {
                binding.textContent.text = listOfNotNull(
                    parsed.title?.let { "Title: $it" },
                    parsed.location?.let { "Location: $it" },
                    parsed.start?.let { "Starts: ${ContentParser.formatIcalDate(it)}" },
                    parsed.end?.let { "Ends: ${ContentParser.formatIcalDate(it)}" },
                    parsed.description?.let { "Notes: $it" }
                ).joinToString("\n")
                addAction("Add to Calendar") { addToCalendar(parsed) }
                addAction("Copy") { copyToClipboard(binding.textContent.text.toString()) }
            }
            is ParsedContent.PlainText -> {
                binding.textContent.text = parsed.text
                addAction("Copy") { copyToClipboard(parsed.text) }
                addAction("Share") { shareText(parsed.text) }
                addAction("Search Web") { searchWeb(parsed.text) }
            }
        }
    }

    private fun addAction(label: String, onClick: () -> Unit) {
        val button = MaterialButton(this).apply {
            text = label
            setOnClickListener { onClick() }
        }
        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
        button.layoutParams = params
        binding.buttonRow.addView(button)
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open link", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QuickQR", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, null))
    }

    private fun dialNumber(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open dialer", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSms(number: String, message: String?) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number"))
            message?.let { intent.putExtra("sms_body", it) }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open messages", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendEmail(address: String, subject: String?, body: String?) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply {
                subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                body?.let { putExtra(Intent.EXTRA_TEXT, it) }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open email app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun searchWeb(query: String) {
        openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
    }

    private fun openLocation(location: ParsedContent.Location) {
        val uriString = if (!location.label.isNullOrBlank()) {
            "geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(${Uri.encode(location.label)})"
        } else {
            "geo:${location.latitude},${location.longitude}"
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uriString)))
        } catch (e: Exception) {
            // No maps app registered for geo: URIs - fall back to a browser map view.
            openUrl("https://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}")
        }
    }

    private fun addToCalendar(event: ParsedContent.Event) {
        val startMillis = ContentParser.parseIcalMillis(event.start)
        if (startMillis == null) {
            Toast.makeText(this, "Couldn't read the event's date", Toast.LENGTH_SHORT).show()
            return
        }
        val endMillis = ContentParser.parseIcalMillis(event.end) ?: (startMillis + 60 * 60 * 1000)
        try {
            val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
                putExtra(CalendarContract.Events.TITLE, event.title.orEmpty())
                putExtra(CalendarContract.Events.EVENT_LOCATION, event.location.orEmpty())
                putExtra(CalendarContract.Events.DESCRIPTION, event.description.orEmpty())
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open a calendar app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToHistory(content: String, format: Int) {
        lifecycleScope.launch {
            AppDatabase.getInstance(applicationContext).scanDao().insert(
                ScanEntity(content = content, format = format, timestamp = System.currentTimeMillis())
            )
        }
    }
}

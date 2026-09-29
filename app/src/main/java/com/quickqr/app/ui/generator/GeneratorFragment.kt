package com.quickqr.app.ui.generator

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.quickqr.app.BuildConfig
import com.quickqr.app.databinding.FragmentGeneratorBinding
import com.quickqr.app.util.QRCodeGenerator
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** The 9 kinds of QR content this screen can build. */
private enum class QrType { URL, WIFI, PHONE, EMAIL, CONTACT, TEXT, LOCATION, EVENT, SOCIAL }

private data class SocialPlatform(val label: String, val urlBuilder: (String) -> String)

class GeneratorFragment : Fragment() {

    private var _binding: FragmentGeneratorBinding? = null
    private val binding get() = _binding!!
    private var currentBitmap: Bitmap? = null
    private var currentType = QrType.URL

    private var eventStartMillis: Long? = null
    private var eventEndMillis: Long? = null
    private val eventDisplayFormat = SimpleDateFormat("MMM d, yyyy \u00b7 h:mm a", Locale.getDefault())

    private val wifiSecurityOptions = listOf("WPA/WPA2", "WEP", "None")

    private val socialPlatforms = listOf(
        SocialPlatform("Instagram") { h -> "https://instagram.com/$h" },
        SocialPlatform("X (Twitter)") { h -> "https://twitter.com/$h" },
        SocialPlatform("Facebook") { h -> "https://facebook.com/$h" },
        SocialPlatform("LinkedIn") { h -> "https://linkedin.com/in/$h" },
        SocialPlatform("TikTok") { h -> "https://tiktok.com/@$h" },
        SocialPlatform("YouTube") { h -> "https://youtube.com/@$h" },
        SocialPlatform("Snapchat") { h -> "https://snapchat.com/add/$h" },
        SocialPlatform("Threads") { h -> "https://threads.net/@$h" },
        SocialPlatform("WhatsApp") { h -> "https://wa.me/${h.filter { it.isDigit() }}" }
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGeneratorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setUpDropdowns()
        setUpChips()
        showFormFor(QrType.URL)

        binding.btnPickEventStart.setOnClickListener { pickEventDateTime(isStart = true) }
        binding.btnPickEventEnd.setOnClickListener { pickEventDateTime(isStart = false) }

        binding.btnGenerate.setOnClickListener { generateCode() }
        binding.btnSave.setOnClickListener { saveToGallery() }
        binding.btnShare.setOnClickListener { shareCode() }
    }

    private fun setUpDropdowns() {
        val securityAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, wifiSecurityOptions)
        binding.inputWifiSecurity.setAdapter(securityAdapter)
        binding.inputWifiSecurity.setText(wifiSecurityOptions[0], false)

        val platformAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_list_item_1, socialPlatforms.map { it.label }
        )
        binding.inputSocialPlatform.setAdapter(platformAdapter)
        binding.inputSocialPlatform.setText(socialPlatforms[0].label, false)
    }

    private fun setUpChips() {
        binding.chipUrl.setOnClickListener { showFormFor(QrType.URL) }
        binding.chipWifi.setOnClickListener { showFormFor(QrType.WIFI) }
        binding.chipPhone.setOnClickListener { showFormFor(QrType.PHONE) }
        binding.chipEmail.setOnClickListener { showFormFor(QrType.EMAIL) }
        binding.chipContact.setOnClickListener { showFormFor(QrType.CONTACT) }
        binding.chipText.setOnClickListener { showFormFor(QrType.TEXT) }
        binding.chipLocation.setOnClickListener { showFormFor(QrType.LOCATION) }
        binding.chipEvent.setOnClickListener { showFormFor(QrType.EVENT) }
        binding.chipSocial.setOnClickListener { showFormFor(QrType.SOCIAL) }
    }

    private fun showFormFor(type: QrType) {
        currentType = type
        binding.resultGroup.visibility = View.GONE
        binding.formUrl.visibility = if (type == QrType.URL) View.VISIBLE else View.GONE
        binding.formWifi.visibility = if (type == QrType.WIFI) View.VISIBLE else View.GONE
        binding.formPhone.visibility = if (type == QrType.PHONE) View.VISIBLE else View.GONE
        binding.formEmail.visibility = if (type == QrType.EMAIL) View.VISIBLE else View.GONE
        binding.formContact.visibility = if (type == QrType.CONTACT) View.VISIBLE else View.GONE
        binding.formText.visibility = if (type == QrType.TEXT) View.VISIBLE else View.GONE
        binding.formLocation.visibility = if (type == QrType.LOCATION) View.VISIBLE else View.GONE
        binding.formEvent.visibility = if (type == QrType.EVENT) View.VISIBLE else View.GONE
        binding.formSocial.visibility = if (type == QrType.SOCIAL) View.VISIBLE else View.GONE
    }

    private fun pickEventDateTime(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        val label = eventDisplayFormat.format(cal.time)
                        if (isStart) {
                            eventStartMillis = cal.timeInMillis
                            binding.textEventStart.text = getString(com.quickqr.app.R.string.event_start_set, label)
                        } else {
                            eventEndMillis = cal.timeInMillis
                            binding.textEventEnd.text = getString(com.quickqr.app.R.string.event_end_set, label)
                        }
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    false
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun generateCode() {
        val payload = buildPayload()
        if (payload == null) {
            Toast.makeText(requireContext(), "Please fill in the required fields", Toast.LENGTH_SHORT).show()
            return
        }
        val bitmap = QRCodeGenerator.generate(payload)
        if (bitmap == null) {
            Toast.makeText(requireContext(), "Couldn't generate a code for that content", Toast.LENGTH_SHORT).show()
            return
        }
        currentBitmap = bitmap
        binding.imageQr.setImageBitmap(bitmap)
        binding.resultGroup.visibility = View.VISIBLE
    }

    /** Builds the raw QR payload string for [currentType], or null if a required field is missing. */
    private fun buildPayload(): String? {
        fun text(view: com.google.android.material.textfield.TextInputEditText) =
            view.text?.toString()?.trim().orEmpty()

        return when (currentType) {
            QrType.URL -> {
                val url = text(binding.inputUrl)
                if (url.isBlank() || url == "https://") null else url
            }
            QrType.WIFI -> {
                val ssid = text(binding.inputWifiSsid)
                if (ssid.isBlank()) return null
                val password = text(binding.inputWifiPassword)
                val securityLabel = binding.inputWifiSecurity.text?.toString().orEmpty()
                val securityCode = when (securityLabel) {
                    "WEP" -> "WEP"
                    "None" -> "nopass"
                    else -> "WPA"
                }
                val hidden = if (binding.checkWifiHidden.isChecked) "true" else "false"
                fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
                "WIFI:T:$securityCode;S:${esc(ssid)};P:${esc(password)};H:$hidden;;"
            }
            QrType.PHONE -> {
                val phone = text(binding.inputPhone)
                if (phone.isBlank()) null else "tel:$phone"
            }
            QrType.EMAIL -> {
                val to = text(binding.inputEmailTo)
                if (to.isBlank()) return null
                val subject = text(binding.inputEmailSubject)
                val body = text(binding.inputEmailBody)
                val params = mutableListOf<String>()
                if (subject.isNotBlank()) params.add("subject=" + uriEncode(subject))
                if (body.isNotBlank()) params.add("body=" + uriEncode(body))
                "mailto:$to" + if (params.isNotEmpty()) "?" + params.joinToString("&") else ""
            }
            QrType.CONTACT -> {
                val name = text(binding.inputContactName)
                if (name.isBlank()) return null
                val phone = text(binding.inputContactPhone)
                val email = text(binding.inputContactEmail)
                val org = text(binding.inputContactOrg)
                buildString {
                    append("BEGIN:VCARD\n")
                    append("VERSION:3.0\n")
                    append("N:$name\n")
                    append("FN:$name\n")
                    if (org.isNotBlank()) append("ORG:$org\n")
                    if (phone.isNotBlank()) append("TEL:$phone\n")
                    if (email.isNotBlank()) append("EMAIL:$email\n")
                    append("END:VCARD")
                }
            }
            QrType.TEXT -> {
                val t = text(binding.inputText)
                if (t.isBlank()) null else t
            }
            QrType.LOCATION -> {
                val lat = text(binding.inputLatitude)
                val lon = text(binding.inputLongitude)
                if (lat.isBlank() || lon.isBlank()) return null
                val label = text(binding.inputLocationLabel)
                if (label.isNotBlank()) "geo:$lat,$lon?q=$lat,$lon($label)" else "geo:$lat,$lon"
            }
            QrType.EVENT -> {
                val title = text(binding.inputEventTitle)
                val start = eventStartMillis
                if (title.isBlank() || start == null) return null
                val location = text(binding.inputEventLocation)
                val description = text(binding.inputEventDescription)
                val end = eventEndMillis ?: (start + 60 * 60 * 1000)
                val icalFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
                buildString {
                    append("BEGIN:VEVENT\n")
                    append("SUMMARY:$title\n")
                    if (location.isNotBlank()) append("LOCATION:$location\n")
                    append("DTSTART:${icalFormat.format(java.util.Date(start))}\n")
                    append("DTEND:${icalFormat.format(java.util.Date(end))}\n")
                    if (description.isNotBlank()) append("DESCRIPTION:$description\n")
                    append("END:VEVENT")
                }
            }
            QrType.SOCIAL -> {
                val handleRaw = text(binding.inputSocialHandle)
                if (handleRaw.isBlank()) return null
                val handle = handleRaw.removePrefix("@")
                val platformLabel = binding.inputSocialPlatform.text?.toString().orEmpty()
                val platform = socialPlatforms.firstOrNull { it.label == platformLabel } ?: socialPlatforms[0]
                platform.urlBuilder(handle)
            }
        }
    }

    private fun uriEncode(value: String): String = android.net.Uri.encode(value)

    private fun saveToGallery() {
        val bitmap = currentBitmap ?: return
        try {
            val filename = "QuickQR_${System.currentTimeMillis()}.png"
            val resolver = requireContext().contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QuickQR")
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                Toast.makeText(requireContext(), "Saved to Pictures/QuickQR", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Couldn't save image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Couldn't save image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareCode() {
        val bitmap = currentBitmap ?: return
        try {
            val cacheDir = File(requireContext().cacheDir, "images").apply { mkdirs() }
            val file = File(cacheDir, "shared_qr.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(requireContext(), "${BuildConfig.APPLICATION_ID}.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share QR code"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Couldn't share image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

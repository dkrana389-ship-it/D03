package com.deep.focus

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import kotlin.concurrent.thread

object P {
    const val FILE = "focus"
    const val DREAM = "dream"
    const val LIMIT = "limit"
    const val COUNT = "count"
    const val EVENTS = "events"
    const val LOOSE = "loose"
    const val PHOTO = "dream.jpg"
    const val PHOTO_SRC = "photo_src" // "" = none, "auto" = from internet, "manual" = from gallery
    const val PHOTO_Q = "photo_q"     // keyword the automatic photo was found for
}

class MainActivity : Activity() {

    // ---- Palette ----
    private val bg = "#0E1020"
    private val surface = "#171B30"
    private val stroke = "#262B47"
    private val white = "#FFFFFF"
    private val muted = "#9AA3C0"
    private val accent = "#FFB84D"
    private val accentText = "#1A1530"
    private val good = "#4ADE80"
    private val bad = "#F87171"

    private val pickCode = 101
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var dreamEt: EditText
    private lateinit var limitEt: EditText
    private lateinit var looseSw: Switch
    private lateinit var preview: ImageView
    private lateinit var caption: TextView
    private lateinit var statusDot: View
    private lateinit var statusTitle: TextView
    private lateinit var statusDetail: TextView
    private lateinit var statusEvents: TextView
    private lateinit var progress: ProgressBar

    // Automatic photo search state
    private var candidates: List<String> = emptyList()
    private var candidateQuery = ""
    private var candidateIndex = 0
    private var requestId = 0
    private var debounce: Runnable? = null

    private fun c(s: String) = Color.parseColor(s)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun prefs() = getSharedPreferences(P.FILE, Context.MODE_PRIVATE)
    private fun dreamFile() = File(filesDir, P.PHOTO)

    // ---- UI helpers ----

    private fun rounded(color: String, radius: Int, strokeColor: String? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(c(color))
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null) setStroke(dp(1), c(strokeColor))
        }
    }

    private fun ripple(d: GradientDrawable) =
        RippleDrawable(ColorStateList.valueOf(c("#33FFFFFF")), d, null)

    private fun text(
        t: String, size: Float, color: String, bold: Boolean = false, spacing: Float = 0f
    ): TextView {
        return TextView(this).apply {
            text = t
            textSize = size
            letterSpacing = spacing
            setTextColor(c(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(surface, 18, stroke)
            setPadding(dp(18), dp(16), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        }
    }

    private fun sectionTitle(t: String) = text(t, 11f, accent, true, 0.15f).apply {
        setPadding(0, 0, 0, dp(10))
    }

    private fun primaryBtn(t: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = t
            isAllCaps = false
            textSize = 15f
            stateListAnimator = null
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(c(accentText))
            background = ripple(rounded(accent, 14))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(16) }
        }
    }

    private fun ghostBtn(t: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = t
            isAllCaps = false
            textSize = 14f
            stateListAnimator = null
            setTextColor(c(white))
            background = ripple(rounded("#1F2440", 12, stroke))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
            ).apply { topMargin = dp(8) }
        }
    }

    private fun smallBtn(t: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = t
            isAllCaps = false
            textSize = 12.5f
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(4), 0, dp(4), 0)
            setTextColor(c(white))
            background = ripple(rounded("#1F2440", 12, stroke))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                marginEnd = dp(6)
            }
        }
    }

    private fun field(hint: String): EditText {
        return EditText(this).apply {
            this.hint = hint
            setHintTextColor(c("#5E6685"))
            setTextColor(c(white))
            textSize = 16f
            background = rounded("#0E1124", 12, stroke)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    // ---- Screen ----

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = c(bg)
        window.navigationBarColor = c(bg)
        val p = prefs()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(44), dp(18), dp(32))
        }
        val sv = ScrollView(this).apply {
            setBackgroundColor(c(bg))
            isFillViewport = true
            addView(root)
        }

        // Header
        root.addView(text("FOCUS", 12f, accent, true, 0.25f))
        root.addView(text("Reclaim your attention", 28f, white, true).apply {
            setPadding(0, dp(4), 0, dp(6))
        })
        root.addView(
            text(
                "After your reel limit, Focus reminds you of the dream you are working toward.",
                14f, muted
            )
        )

        // Status card
        val statusCard = card()
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c(bad))
            }
            layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(10) }
        }
        statusTitle = text("Protection inactive", 17f, white, true)
        statusRow.addView(statusDot)
        statusRow.addView(statusTitle)
        statusCard.addView(statusRow)

        statusDetail = text("", 14f, muted).apply { setPadding(0, dp(8), 0, dp(8)) }
        statusCard.addView(statusDetail)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = ColorStateList.valueOf(c(accent))
            progressBackgroundTintList = ColorStateList.valueOf(c("#2A2F4D"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6)
            )
        }
        statusCard.addView(progress)

        statusEvents = text("", 12f, "#6F7898").apply { setPadding(0, dp(10), 0, 0) }
        statusCard.addView(statusEvents)
        root.addView(statusCard)

        // Dream card
        val dreamCard = card()
        dreamCard.addView(sectionTitle("YOUR DREAM"))
        dreamEt = field("e.g. Mahindra Scorpio, Bali villa, MBA").apply {
            setText(p.getString(P.DREAM, ""))
        }
        dreamCard.addView(dreamEt)
        dreamCard.addView(
            text(
                "Type your dream and a matching photo loads automatically. " +
                    "Be specific for better results, for example \"Mahindra Scorpio\".",
                12f, muted
            ).apply { setPadding(0, dp(8), 0, dp(10)) }
        )

        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded("#0E1124", 14)
            clipToOutline = true
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200)
            )
        }
        dreamCard.addView(preview)

        caption = text("", 12f, muted).apply { setPadding(0, dp(8), 0, dp(10)) }
        dreamCard.addView(caption)

        val photoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        photoRow.addView(smallBtn("Next photo") { autoPhoto(true) })
        photoRow.addView(smallBtn("Gallery") {
            val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(i, "Select your dream photo"), pickCode)
        })
        photoRow.addView(smallBtn("Remove") {
            requestId++
            dreamFile().delete()
            prefs().edit().putString(P.PHOTO_SRC, "").putString(P.PHOTO_Q, "").apply()
            loadPreview()
            Toast.makeText(this, "Dream photo removed", Toast.LENGTH_SHORT).show()
        })
        dreamCard.addView(photoRow)
        root.addView(dreamCard)

        // Limit card
        val limitCard = card()
        limitCard.addView(sectionTitle("REEL LIMIT"))
        limitEt = field("30").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(p.getInt(P.LIMIT, 30).toString())
        }
        limitCard.addView(limitEt)
        limitCard.addView(
            text("Number of reels you can watch before the reminder appears.", 12f, muted)
                .apply { setPadding(0, dp(8), 0, dp(6)) }
        )
        looseSw = Switch(this).apply {
            text = "Relaxed detection"
            textSize = 14f
            setTextColor(c(white))
            isChecked = p.getBoolean(P.LOOSE, false)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(c(accent), c("#7A819E")))
            trackTintList = ColorStateList(states, intArrayOf(c("#80FFB84D"), c("#33FFFFFF")))
            setPadding(0, dp(6), 0, dp(2))
        }
        limitCard.addView(looseSw)
        limitCard.addView(
            text("Turn this on if your reels are not being counted.", 12f, muted)
        )
        root.addView(limitCard)

        root.addView(primaryBtn("Save settings") { save() })

        // Setup card
        val setupCard = card()
        setupCard.addView(sectionTitle("SETUP"))
        setupCard.addView(
            text(
                "1. Open Accessibility settings and turn on Focus.\n" +
                    "2. On Android 13+, first allow restricted settings from the app info page " +
                    "(three-dot menu).",
                13f, muted
            )
        )
        setupCard.addView(ghostBtn("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        setupCard.addView(ghostBtn("Allow restricted settings (App info)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        })
        setupCard.addView(ghostBtn("Preview reminder popup") {
            val s = ReelService.instance
            if (s == null) {
                Toast.makeText(this, "Please enable the Accessibility Service first", Toast.LENGTH_LONG).show()
            } else {
                save()
                s.showPopup()
            }
        })
        root.addView(setupCard)

        root.addView(
            text(
                "Focus collects no data. Reel counting happens only on your device.",
                11f, "#6F7898"
            ).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(18), 0, 0)
            }
        )

        setContentView(sv)
        loadPreview()

        // Auto-load a photo shortly after the user stops typing.
        dreamEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                debounce?.let { ui.removeCallbacks(it) }
                val r = Runnable { autoPhoto(false) }
                debounce = r
                ui.postDelayed(r, 1200)
            }
        })
        if (dreamEt.text.toString().trim().length >= 2 && !dreamFile().exists()) {
            autoPhoto(false)
        }
    }

    // ---- Photo handling ----

    private fun loadPreview() {
        val f = dreamFile()
        val bmp = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        if (bmp != null) {
            preview.setImageBitmap(bmp)
            preview.visibility = View.VISIBLE
            val p = prefs()
            val q = p.getString(P.PHOTO_Q, "") ?: ""
            caption.text = if (p.getString(P.PHOTO_SRC, "") == "manual") {
                "Your own photo from the gallery."
            } else if (candidateQuery == q && candidates.isNotEmpty()) {
                "Photo ${candidateIndex + 1} of ${candidates.size} for \u201C$q\u201D. Tap Next photo for another."
            } else {
                "Automatic photo for \u201C$q\u201D. Tap Next photo for another."
            }
        } else {
            preview.setImageDrawable(null)
            preview.visibility = View.GONE
            caption.text = "No photo yet. It will load automatically when you type your dream."
        }
    }

    /**
     * Finds and loads a photo for the dream text.
     * next = false: automatic (after typing / on save), next = true: user asked for another photo.
     */
    private fun autoPhoto(next: Boolean) {
        val q = dreamEt.text.toString().trim()
        if (q.length < 2) {
            if (next) Toast.makeText(this, "Write your dream first", Toast.LENGTH_SHORT).show()
            return
        }
        val p = prefs()
        val src = p.getString(P.PHOTO_SRC, "") ?: ""
        if (!next) {
            if (src == "manual") return
            if (src == "auto" && p.getString(P.PHOTO_Q, "") == q && dreamFile().exists()) return
        }
        if (next && candidateQuery == q && candidates.isNotEmpty()) {
            loadFrom(q, (candidateIndex + 1) % candidates.size)
            return
        }

        val id = ++requestId
        caption.text = "Searching a photo for \u201C$q\u201D..."
        thread {
            val list = PhotoFinder.search(q)
            runOnUiThread {
                if (id != requestId || isFinishing) return@runOnUiThread
                if (list.isEmpty()) {
                    caption.text = "No photo found for \u201C$q\u201D. Check your internet, try another name, or choose from the gallery."
                    return@runOnUiThread
                }
                candidates = list
                candidateQuery = q
                candidateIndex = 0
                loadFrom(q, 0)
            }
        }
    }

    private fun loadFrom(q: String, start: Int) {
        val id = ++requestId
        val list = candidates
        caption.text = "Loading photo..."
        thread {
            var found: Bitmap? = null
            var at = start
            var tried = 0
            while (tried < list.size && tried < 6 && found == null) {
                val i = (start + tried) % list.size
                val b = PhotoFinder.download(list[i])
                if (b != null) {
                    found = b
                    at = i
                }
                tried++
            }
            val bmp = found
            if (bmp != null && id == requestId) {
                try {
                    openFileOutput(P.PHOTO, Context.MODE_PRIVATE).use {
                        bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
                    }
                } catch (e: Exception) {
                }
            }
            runOnUiThread {
                if (id != requestId || isFinishing) return@runOnUiThread
                if (bmp == null) {
                    caption.text = "Could not load a photo. Check your internet and tap Next photo."
                    return@runOnUiThread
                }
                candidateIndex = at
                prefs().edit().putString(P.PHOTO_SRC, "auto").putString(P.PHOTO_Q, q).apply()
                loadPreview()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickCode && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                requestId++
                saveDreamPhoto(uri)
                prefs().edit().putString(P.PHOTO_SRC, "manual").putString(P.PHOTO_Q, "").apply()
                loadPreview()
                Toast.makeText(this, "Dream photo saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not load this photo. Please try another one.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveDreamPhoto(uri: Uri) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp: Bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalStateException("decode failed")

        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            contentResolver.openInputStream(uri)?.use {
                orientation = ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (e: Exception) {
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val m = Matrix().apply { postRotate(degrees) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }

        openFileOutput(P.PHOTO, Context.MODE_PRIVATE).use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
    }

    // ---- Settings & status ----

    private fun save() {
        val limit = limitEt.text.toString().trim().toIntOrNull()?.coerceAtLeast(1) ?: 30
        prefs().edit()
            .putString(P.DREAM, dreamEt.text.toString().trim())
            .putInt(P.LIMIT, limit)
            .putBoolean(P.LOOSE, looseSw.isChecked)
            .apply()
        limitEt.setText(limit.toString())
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        refresh()
        autoPhoto(false)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val p = prefs()
        val enabled = (Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: "").contains(packageName)

        (statusDot.background as GradientDrawable).setColor(c(if (enabled) good else bad))
        statusTitle.text = if (enabled) "Protection active" else "Protection inactive"

        val limit = p.getInt(P.LIMIT, 30)
        val count = p.getInt(P.COUNT, 0)
        statusDetail.text = if (enabled) {
            "Reels counted: $count / $limit"
        } else {
            "Enable Focus in Accessibility settings to start. Reels counted: $count / $limit"
        }
        progress.max = limit
        progress.progress = count.coerceAtMost(limit)
        statusEvents.text = "Instagram scroll events detected: ${p.getInt(P.EVENTS, 0)}" +
            if (p.getInt(P.EVENTS, 0) == 0) "  \u2022  stays 0 if the service is off" else ""
    }
}

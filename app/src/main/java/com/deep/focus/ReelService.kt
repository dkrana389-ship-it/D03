package com.deep.focus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class ReelService : AccessibilityService() {

    companion object {
        var instance: ReelService? = null
    }

    private val ui = Handler(Looper.getMainLooper())
    private var lastTime = 0L
    private var overlay: View? = null
    private var fetching = false
    private var qNext: String? = null
    private var bNext: Bitmap? = null

    private val fallback = listOf(
        "“Small disciplines repeated every day create extraordinary results.”",
        "“The time you spend scrolling is time taken from your goals.”",
        "“Discipline is choosing what you want most over what you want right now.”",
        "“Your future is built by what you do today, not tomorrow.”",
        "“Dreams do not work unless you do.”"
    )

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        removeOverlay()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    private fun prefs() = getSharedPreferences(P.FILE, MODE_PRIVATE)
    private fun dreamFile() = File(filesDir, P.PHOTO)

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        if (e.packageName?.toString() != "com.instagram.android") return

        val p = prefs()
        p.edit().putInt(P.EVENTS, p.getInt(P.EVENTS, 0) + 1).apply()

        if (overlay != null) return
        val now = System.currentTimeMillis()
        val gap = now - lastTime
        if (gap < 700) return

        val loose = p.getBoolean(P.LOOSE, false)
        if (!loose && !onReels()) return

        var count = if (gap > 10 * 60 * 1000) 0 else p.getInt(P.COUNT, 0)
        lastTime = now
        count++

        val limit = p.getInt(P.LIMIT, 30)
        if (count >= limit - 3) prefetch()

        if (count >= limit) {
            p.edit().putInt(P.COUNT, 0).apply()
            showPopup()
        } else {
            p.edit().putInt(P.COUNT, count).apply()
        }
    }

    private fun onReels(): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < 500) {
            val n = queue.removeFirst()
            seen++
            val id = n.viewIdResourceName
            if (id != null && (id.contains("clips_viewer") || id.contains("root_clips_layout"))) {
                return true
            }
            for (i in 0 until n.childCount) {
                val c = n.getChild(i)
                if (c != null) queue.add(c)
            }
        }
        return false
    }

    // ---------- Quote and photo from the internet ----------

    private fun http(url: String): java.io.InputStream? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        return if (c.responseCode == 200) c.inputStream else null
    }

    private fun fetchQuote(): String? {
        try {
            val s = http("https://zenquotes.io/api/random")?.bufferedReader()?.use { it.readText() }
            if (s != null) {
                val o = JSONArray(s).getJSONObject(0)
                return "“" + o.getString("q") + "”\n— " + o.getString("a")
            }
        } catch (e: Exception) {
        }
        try {
            val s = http("https://dummyjson.com/quotes/random")?.bufferedReader()?.use { it.readText() }
            if (s != null) {
                val o = JSONObject(s)
                return "“" + o.getString("quote") + "”\n— " + o.getString("author")
            }
        } catch (e: Exception) {
        }
        return null
    }

    private fun fetchPhoto(): Bitmap? {
        val urls = listOf(
            "https://picsum.photos/900/1200",
            "https://loremflickr.com/900/1200/mountain,sunrise,success"
        )
        for (u in urls) {
            try {
                val stream = http(u) ?: continue
                val b = stream.use { BitmapFactory.decodeStream(it) }
                if (b != null) return b
            } catch (e: Exception) {
                // try the next source
            }
        }
        return null
    }

    private fun prefetch() {
        val hasDreamPhoto = dreamFile().exists()
        if (fetching || (qNext != null && (bNext != null || hasDreamPhoto))) return
        fetching = true
        thread {
            val q = fetchQuote()
            val b = if (hasDreamPhoto) null else fetchPhoto()
            ui.post {
                if (q != null) qNext = q
                if (b != null) bNext = b
                fetching = false
            }
        }
    }

    // ---------- Popup ----------

    fun showPopup() {
        ui.post {
            if (overlay != null) return@post
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val d = resources.displayMetrics.density
            fun dp(v: Int) = (v * d).toInt()

            val dream = (prefs().getString(P.DREAM, "") ?: "").trim()
            val limit = prefs().getInt(P.LIMIT, 30)

            val root = FrameLayout(this)
            root.setBackgroundColor(Color.parseColor("#E60B0D1A"))
            root.isClickable = true

            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#1E2342"), Color.parseColor("#12152A"))
            ).apply {
                cornerRadius = dp(24).toFloat()
            }
            card.clipToOutline = true

            val img = ImageView(this)
            img.scaleType = ImageView.ScaleType.CENTER_CROP
            img.setBackgroundColor(Color.parseColor("#55FFFFFF"))
            card.addView(
                img,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(240))
            )

            val body = LinearLayout(this)
            body.orientation = LinearLayout.VERTICAL
            body.setPadding(dp(20), dp(16), dp(20), dp(20))
            card.addView(body)

            val head = TextView(this)
            head.text = "Time to refocus. You have watched $limit reels."
            head.textSize = 13f
            head.setTextColor(Color.parseColor("#9AA3C0"))
            body.addView(head)

            val small = TextView(this)
            small.text = "YOUR DREAM"
            small.textSize = 11f
            small.letterSpacing = 0.15f
            small.setTextColor(Color.parseColor("#FFB84D"))
            small.setPadding(0, dp(12), 0, 0)
            body.addView(small)

            val dreamTv = TextView(this)
            dreamTv.text = if (dream.isEmpty()) "Set your dream in the Focus app" else dream
            dreamTv.textSize = 22f
            dreamTv.setTypeface(dreamTv.typeface, Typeface.BOLD)
            dreamTv.setTextColor(Color.parseColor("#FFFFFF"))
            dreamTv.setPadding(0, dp(4), 0, dp(12))
            body.addView(dreamTv)

            val qv = TextView(this)
            qv.textSize = 16f
            qv.setTypeface(qv.typeface, Typeface.ITALIC)
            qv.setTextColor(Color.parseColor("#FFFFFF"))
            body.addView(qv)

            val home = Button(this)
            home.text = "Close Instagram"
            home.isAllCaps = false
            home.setTextColor(Color.parseColor("#1A1530"))
            home.background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFB84D"))
                cornerRadius = dp(14).toFloat()
            }
            home.setOnClickListener {
                removeOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            body.addView(
                home,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply {
                    topMargin = dp(18)
                }
            )

            val stay = Button(this)
            stay.text = "Continue (5)"
            stay.isAllCaps = false
            stay.isEnabled = false
            stay.alpha = 0.5f
            stay.setTextColor(Color.WHITE)
            stay.background = GradientDrawable().apply {
                setColor(Color.parseColor("#40FFFFFF"))
                cornerRadius = dp(14).toFloat()
            }
            stay.setOnClickListener { removeOverlay() }
            body.addView(
                stay,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply {
                    topMargin = dp(10)
                }
            )

            val scroll = ScrollView(this)
            scroll.addView(card)
            root.addView(
                scroll,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                ).apply { setMargins(dp(20), dp(30), dp(20), dp(30)) }
            )

            // Photo: the user's own dream photo first, otherwise an internet photo.
            val df = dreamFile()
            val dreamBmp = if (df.exists()) BitmapFactory.decodeFile(df.path) else null
            val q0 = qNext
            val b0 = bNext
            qNext = null
            bNext = null

            qv.text = q0 ?: fallback.random()
            if (dreamBmp != null) {
                img.setImageBitmap(dreamBmp)
            } else if (b0 != null) {
                img.setImageBitmap(b0)
            }

            val needPhoto = dreamBmp == null && b0 == null
            if (q0 == null || needPhoto) {
                thread {
                    val q = if (q0 == null) fetchQuote() else null
                    val b = if (needPhoto) fetchPhoto() else null
                    ui.post {
                        if (overlay === root) {
                            if (q != null) qv.text = q
                            if (b != null) img.setImageBitmap(b)
                        }
                    }
                }
            }

            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            try {
                wm.addView(root, lp)
                overlay = root
            } catch (e: Exception) {
                return@post
            }

            var left = 5
            val tick = object : Runnable {
                override fun run() {
                    if (overlay !== root) return
                    left--
                    if (left > 0) {
                        stay.text = "Continue ($left)"
                        ui.postDelayed(this, 1000)
                    } else {
                        stay.text = "Continue"
                        stay.isEnabled = true
                        stay.alpha = 1f
                    }
                }
            }
            ui.postDelayed(tick, 1000)
        }
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        overlay = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
        }
    }
}

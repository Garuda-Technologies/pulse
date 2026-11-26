package com.vbs.pulse

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private val d by lazy { resources.displayMetrics.density }

    private lateinit var ring: RingView
    private lateinit var spark: SparkView
    private lateinit var cpuValue: TextView
    private lateinit var storageValue: TextView
    private lateinit var batteryValue: TextView
    private lateinit var uptimeValue: TextView
    private lateinit var freeValue: TextView
    private lateinit var ownValue: TextView
    private lateinit var swapValue: TextView
    private lateinit var appCount: TextView
    private lateinit var liveDot: View
    private lateinit var listView: ListView
    private lateinit var adapter: AppAdapter

    /** Real icons, downscaled to 34dp and capped at 20 — ~90 KB total. */
    private val icons = LruCache<String, Bitmap>(20)

    private fun iconFor(pkg: String): Bitmap? {
        icons.get(pkg)?.let { return it }
        return try {
            val dr = packageManager.getApplicationIcon(pkg)
            val size = dp(34)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            dr.setBounds(0, 0, size, size)
            dr.draw(Canvas(bmp))
            icons.put(pkg, bmp)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private var apps: List<Pair<String, String>> = emptyList()
    private var maxFreq = 0L
    private var dotOn = true

    private val ui = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            refreshStats()
            ui.postDelayed(this, 1000)
        }
    }

    companion object {
        const val BG = 0xFF14120F.toInt()
        const val CARD = 0xFF1E1B17.toInt()
        const val CARD2 = 0xFF262119.toInt()
        const val LINE = 0xFF2C2823.toInt()
        const val INK = 0xFFF2EDE4.toInt()
        const val INK2 = 0xFFA79E90.toInt()
        const val INK3 = 0xFF6F675C.toInt()
        const val ACCENT = 0xFFD9663A.toInt()
        const val AMBER = 0xFFE8B04B.toInt()
        const val GOOD = 0xFF6FBF73.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(4))
        }
        header.addView(buildHeader())
        header.addView(ringView())
        header.addView(buildCpuCard())
        header.addView(buildStatGrid())
        header.addView(buildAppSectionHeader())

        listView = ListView(this).apply {
            setBackgroundColor(BG)
            divider = ColorDrawable(BG)
            dividerHeight = dp(8)
            clipToPadding = false
            setPadding(dp(20), 0, dp(20), dp(24))
        }
        listView.addHeaderView(header, null, false)
        adapter = AppAdapter()
        listView.adapter = adapter
        setContentView(listView)

        loadApps()
        refreshStats()
    }

    override fun onResume() {
        super.onResume()
        ui.post(tick)
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(tick)
    }

    // ---- layout helpers --------------------------------------------------
    private fun dp(v: Int) = (v * d).toInt()

    private fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * d
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label("Pulse", 30f, INK, true))
        col.addView(label("live system monitor", 12f, INK3))
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        liveDot = View(this).apply {
            background = GradientDrawable().apply {
                setColor(GOOD)
                shape = GradientDrawable.OVAL
            }
        }
        row.addView(liveDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) })
        row.addView(label("LIVE", 10f, INK3))
        return row
    }

    private fun ringView(): View {
        ring = RingView(this)
        ring.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)).apply {
            topMargin = dp(14)
        }
        return ring
    }

    private fun buildCpuCard(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 18)
            setPadding(dp(16), dp(14), dp(16), dp(12))
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(label("CPU CLOCK", 12f, INK3, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cpuValue = label("—", 13f, AMBER, true)
        head.addView(cpuValue)
        card.addView(head)

        spark = SparkView(this)
        card.addView(spark, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
        })

        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) }
        return card
    }

    private fun statCard(title: String, accent: Int): Pair<View, TextView> {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(CARD, 16)
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val v = label("—", 17f, accent, true)
        card.addView(label(title.uppercase(), 10f, INK3, true))
        card.addView(v, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })
        return card to v
    }

    private fun buildStatGrid(): View {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        fun row(vararg cells: Pair<View, TextView>): LinearLayout {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((i, cell) in cells.withIndex()) {
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (i > 0) lp.marginStart = dp(12)
                r.addView(cell.first, lp)
            }
            return r
        }

        val s = statCard("Storage", INK); storageValue = s.second
        val b = statCard("Battery", GOOD); batteryValue = b.second
        val u = statCard("Uptime", INK); uptimeValue = u.second
        val f = statCard("Free RAM", AMBER); freeValue = f.second
        val o = statCard("This app", ACCENT); ownValue = o.second
        val s2 = statCard("Swap", INK2); swapValue = s2.second

        wrap.addView(row(s, b))
        for (r in listOf(row(u, f), row(o, s2))) {
            wrap.addView(r, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) })
        }
        return wrap
    }

    private fun buildAppSectionHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label("Apps", 18f, INK, true))
        appCount = label("", 11f, INK3)
        col.addView(appCount)
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val refresh = label("Refresh", 12f, AMBER, true).apply {
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = rounded(CARD2, 12, LINE)
            setOnClickListener { loadApps() }
        }
        row.addView(refresh)
        return row
    }

    // ---- data ------------------------------------------------------------
    private fun refreshStats() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)

        val totalGb = mi.totalMem.toDouble() / 1_073_741_824.0
        val usedGb = (mi.totalMem - mi.availMem).toDouble() / 1_073_741_824.0
        val frac = if (mi.totalMem > 0) 1f - mi.availMem.toFloat() / mi.totalMem else 0f

        ring.fraction = frac
        ring.big = "${(frac * 100).toInt()}%"
        ring.small = String.format("%.1f / %.1f GB", usedGb, totalGb)
        freeValue.text = Formatter.formatShortFileSize(this, mi.availMem)

        // Private dirty = memory that is actually ours (not shared framework pages).
        val own = am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
        val privKb = own.firstOrNull()?.totalPrivateDirty ?: 0
        ownValue.text = Formatter.formatShortFileSize(this, privKb.toLong() * 1024)

        var swapTotal = 0L
        var swapFree = 0L
        try {
            File("/proc/meminfo").useLines { lines ->
                for (l in lines) {
                    val parts = l.trim().split(Regex("\\s+"))
                    if (parts.size < 2) continue
                    when (parts[0]) {
                        "SwapTotal:" -> swapTotal = parts[1].toLongOrNull() ?: 0L
                        "SwapFree:" -> swapFree = parts[1].toLongOrNull() ?: 0L
                    }
                }
            }
        } catch (_: Exception) {
        }
        swapValue.text = Formatter.formatShortFileSize(this, (swapTotal - swapFree) * 1024)

        val (norm, ghz) = readCpuFreq()
        spark.push(norm)
        cpuValue.text = if (ghz > 0f) String.format("%.2f GHz", ghz) else "n/a"

        val sf = StatFs(filesDir.path)
        val st = sf.blockCountLong * sf.blockSizeLong
        val sa = sf.availableBlocksLong * sf.blockSizeLong
        storageValue.text =
            "${Formatter.formatShortFileSize(this, st - sa)} / ${Formatter.formatShortFileSize(this, st)}"

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        batteryValue.text = "${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%"

        val up = SystemClock.elapsedRealtime() / 1000
        uptimeValue.text = when {
            up < 3600 -> "${up / 60}m"
            up < 86400 -> "${up / 3600}h ${(up % 3600) / 60}m"
            else -> "${up / 86400}d ${(up % 86400) / 3600}h"
        }

        dotOn = !dotOn
        liveDot.alpha = if (dotOn) 1f else 0.25f
    }

    private fun readCpuFreq(): Pair<Float, Float> {
        var sum = 0L
        var n = 0
        for (i in 0 until 8) {
            try {
                val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                if (f.exists()) {
                    sum += f.readText().trim().toLong()
                    n++
                }
            } catch (_: Exception) {
            }
        }
        if (n == 0) return 0f to 0f
        val avg = sum / n
        if (maxFreq <= 0) {
            maxFreq = try {
                File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq").readText().trim().toLong()
            } catch (_: Exception) {
                2_000_000L
            }
            if (maxFreq <= 0) maxFreq = 2_000_000L
        }
        return (avg.toFloat() / maxFreq).coerceIn(0f, 1f) to (avg / 1_000_000f)
    }

    // ---- apps ------------------------------------------------------------

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        apps = try {
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName }
                .map { it.packageName to pm.getApplicationLabel(it).toString() }
                .sortedBy { it.second.lowercase() }
        } catch (_: Exception) {
            emptyList()
        }
        appCount.text = "${apps.size} apps · Stop = kill background · Freeze = accessibility force-stop"
        adapter.notifyDataSetChanged()
    }


    inner class AppAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int): Any = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row: LinearLayout
            if (convertView == null) {
                row = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = rounded(CARD, 14)
                    setPadding(dp(12), dp(10), dp(10), dp(10))
                }
                val icon = ImageView(this@MainActivity)
                val col = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(6), 0)
                }
                val name = label("", 14f, INK)
                val pkg = label("", 10f, INK3)
                col.addView(name)
                col.addView(pkg)
                val freeze = label("Freeze", 11f, AMBER, true).apply {
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    background = rounded(CARD2, 10, LINE)
                }
                val stop = label("Stop", 11f, ACCENT, true).apply {
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    background = rounded(CARD2, 10, LINE)
                }
                row.addView(icon, LinearLayout.LayoutParams(dp(34), dp(34)))
                row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(freeze)
                row.addView(stop, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(6) })
                row.tag = arrayOf<View>(icon, name, pkg, freeze, stop)
            } else {
                row = convertView as LinearLayout
            }

            val (pkgName, labelText) = apps[position]
            @Suppress("UNCHECKED_CAST")
            val v = row.tag as Array<View>
            (v[0] as ImageView).setImageBitmap(iconFor(pkgName))
            (v[1] as TextView).text = labelText
            (v[2] as TextView).text = pkgName
            v[3].setOnClickListener { freezeApp(pkgName, labelText) }
            v[4].setOnClickListener {
                kill(pkgName)
                row.animate().alpha(0.35f).setDuration(180).start()
                toast("Stopped $labelText")
            }
            row.alpha = 1f
            return row
        }
    }

    private fun freezeApp(pkg: String, name: String) {
        if (FreezeService.instance == null) {
            toast("Enable Pulse in Accessibility first")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        FreezeService.pending = pkg
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        )
        toast("Freezing $name")
    }

    private fun kill(pkg: String) {
        try {
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).killBackgroundProcesses(pkg)
        } catch (_: Exception) {
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

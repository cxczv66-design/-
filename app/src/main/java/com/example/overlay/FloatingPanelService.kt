package com.example.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlin.math.abs
import kotlin.math.min

/** A purely visual overlay demo. It does not inspect, control, or modify other apps. */
class FloatingPanelService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleX: Int? = null
    private var bubbleY: Int? = null
    private var pulseAnimator: ValueAnimator? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            removeOverlayViews()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startAsForegroundService()
        showBubble()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pulseAnimator?.cancel()
        removeOverlayViews()
        super.onDestroy()
    }

    private fun startAsForegroundService() {
        val openApp = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this,
            11,
            Intent(this, FloatingPanelService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("لوحة IsolateBox التجريبية")
            .setContentText("اللوحة المرئية فقط نشطة — لا تتصل بالألعاب")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف اللوحة", stopService)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun showBubble() {
        removePanel()
        if (bubbleView != null) return

        val bubble = TextView(this).apply {
            text = "X"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            contentDescription = "فتح لوحة IsolateBox التجريبية"
            background = roundedBackground(
                colors = intArrayOf(0xFFF43F5E.toInt(), 0xFF8B1E4A.toInt()),
                radius = dp(30).toFloat(),
                stroke = 0xFFFF8A9E.toInt()
            )
            elevation = dp(10).toFloat()
        }
        val params = overlayParams(dp(58), dp(58)).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX ?: dp(14)
            y = bubbleY ?: dp(220)
        }
        attachDragAndTap(bubble, params) { showPanel() }
        bubbleView = bubble
        addOverlayView(bubble, params)
    }

    private fun showPanel() {
        removeBubble()
        if (panelView != null) return

        val screenWidthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
        val panelWidthDp = min(screenWidthDp - 24, 390).coerceAtLeast(280)
        val panel = buildPanel()
        val params = overlayParams(dp(panelWidthDp), dp(390)).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(((screenWidthDp - panelWidthDp) / 2).coerceAtLeast(12))
            y = dp(170)
        }
        panelView = panel
        addOverlayView(panel, params)
    }

    private fun buildPanel(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBackground(
                colors = intArrayOf(0xF20B0D12.toInt(), 0xF20A1019.toInt()),
                radius = dp(20).toFloat(),
                stroke = 0xFF3A4654.toInt()
            )
            elevation = dp(18).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(4), dp(8))
        }
        val glowDot = View(this).apply {
            background = roundedBackground(intArrayOf(0xFFFF526C.toInt()), dp(6).toFloat())
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
            gravity = Gravity.CENTER_VERTICAL
        }
        brand.addView(glowDot, LinearLayout.LayoutParams(dp(6), dp(6)).apply {
            bottomMargin = dp(3)
        })
        val title = label("ISOLATEBOX  /  PANEL", 14f, 0xFFF4F6FA.toInt(), bold = true)
        val subtitle = label("DEMO MODE  ·  واجهة شكلية فقط", 9f, 0xFFFF6B78.toInt(), bold = true)
        brand.addView(title)
        brand.addView(subtitle)

        val close = label("×", 26f, 0xFFFF6B78.toInt(), bold = false).apply {
            gravity = Gravity.CENTER
            background = roundedBackground(intArrayOf(0xFF20151B.toInt()), dp(12).toFloat())
            contentDescription = "تصغير اللوحة إلى الفقاعة"
            setOnClickListener { showBubble() }
        }
        header.addView(brand)
        header.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))
        root.addView(header)

        val accent = View(this).apply {
            background = roundedBackground(intArrayOf(0xFFFF526C.toInt(), 0xFF8B5CF6.toInt()), dp(2).toFloat())
        }
        root.addView(accent, LinearLayout.LayoutParams(-1, dp(2)).apply {
            setMargins(dp(2), 0, dp(2), dp(8))
        })

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(3), dp(8), dp(3))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(4))
            background = roundedBackground(intArrayOf(0xFF10151D.toInt()), dp(14).toFloat())
        }
        body.addView(navigation, LinearLayout.LayoutParams(dp(82), -1))
        body.addView(content, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(body)

        val footer = label(
            "للعرض فقط  •  لا يقرأ ولا يعدّل ذاكرة التطبيقات",
            9f,
            0xFF99A6B8.toInt(),
            bold = false
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(8), dp(3), dp(1))
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, dp(28)))

        val tabViews = mutableListOf<TextView>()
        val tabNames = listOf("عام", "المظهر", "معلومات")
        var renderTab: (Int) -> Unit = {}
        tabNames.forEachIndexed { index, name ->
            val tab = label(name, 12f, 0xFFBBC5D3.toInt(), bold = true).apply {
                gravity = Gravity.CENTER
                background = roundedBackground(intArrayOf(0xFF151A23.toInt()), dp(11).toFloat())
                setOnClickListener { renderTab(index) }
            }
            tabViews += tab
            navigation.addView(tab, LinearLayout.LayoutParams(-1, dp(46)).apply {
                bottomMargin = dp(7)
            })
        }

        renderTab = { selected ->
            tabViews.forEachIndexed { index, tab ->
                val active = index == selected
                tab.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xFFBBC5D3.toInt())
                tab.background = roundedBackground(
                    intArrayOf(if (active) 0xFFE83B57.toInt() else 0xFF151A23.toInt()),
                    dp(11).toFloat()
                )
            }
            content.removeAllViews()
            when (selected) {
                0 -> {
                    addSectionTitle(content, "مركز التحكم", "اختصارات بصرية تجريبية")
                    addVisualToggle(content, "نبض الإطار", "تأثير واجهة فقط")
                    addVisualToggle(content, "توهج الألوان", "لا يغيّر اللعبة")
                    addVisualToggle(content, "مؤشر HUD", "عنصر مرئي للمعاينة")
                    addStatusPill(content, "وضع عرض تجريبي", 0xFFFF7185.toInt())
                }
                1 -> {
                    addSectionTitle(content, "المظهر والمؤثرات", "تغييرات شكلية داخل هذه اللوحة")
                    addVisualToggle(content, "خلفية متدرجة", "ألوان داكنة ولمسات حمراء")
                    addVisualToggle(content, "حركة النبض", "أنيميشن خفيف للواجهة")
                    addVisualToggle(content, "فقاعة عائمة", "قابلة للسحب على الشاشة")
                    addStatusPill(content, "لا توجد ميزات لعب فعلية", 0xFFFFB454.toInt())
                }
                else -> {
                    addSectionTitle(content, "حول اللوحة", "IsolateBox Overlay Demo")
                    addInfoLine(content, "الوظيفة", "عرض واجهة عائمة فقط")
                    addInfoLine(content, "اللعبة", "لا اتصال ولا تحكم")
                    addInfoLine(content, "الذاكرة", "لا قراءة أو حقن")
                    addStatusPill(content, "الإذن مطلوب للظهور فوق التطبيقات", 0xFF58D6C6.toInt())
                }
            }
        }
        renderTab(0)

        val pulse = ValueAnimator.ofFloat(0.35f, 1f).apply {
            duration = 1100L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                glowDot.alpha = it.animatedValue as Float
                accent.translationX = (it.animatedFraction * 2f - 1f) * dp(16)
            }
        }
        pulseAnimator?.cancel()
        pulseAnimator = pulse
        pulse.start()
        return root
    }

    private fun addSectionTitle(parent: LinearLayout, heading: String, detail: String) {
        parent.addView(label(heading, 15f, 0xFFF5F7FB.toInt(), bold = true))
        parent.addView(label(detail, 10f, 0xFF8996A7.toInt(), bold = false),
            LinearLayout.LayoutParams(-1, dp(22)))
        parent.addView(View(this).apply {
            background = roundedBackground(intArrayOf(0xFF29323E.toInt()), dp(1).toFloat())
        }, LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(8) })
    }

    private fun addVisualToggle(parent: LinearLayout, title: String, detail: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(5), dp(7), dp(5))
            background = roundedBackground(intArrayOf(0xFF171E28.toInt()), dp(11).toFloat())
        }
        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        text.addView(label(title, 11f, 0xFFE5EAF1.toInt(), bold = true))
        text.addView(label(detail, 9f, 0xFF8996A7.toInt(), bold = false))
        val toggle = TextView(this).apply {
            this.text = "ON"
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = roundedBackground(intArrayOf(0xFFD83E59.toInt()), dp(9).toFloat())
            setPadding(dp(9), dp(6), dp(9), dp(6))
        }
        var enabled = true
        val flip = View.OnClickListener {
            enabled = !enabled
            toggle.text = if (enabled) "ON" else "OFF"
            toggle.background = roundedBackground(
                intArrayOf(if (enabled) 0xFFD83E59.toInt() else 0xFF46505E.toInt()),
                dp(9).toFloat()
            )
        }
        row.setOnClickListener(flip)
        toggle.setOnClickListener(flip)
        row.addView(text)
        row.addView(toggle, LinearLayout.LayoutParams(dp(44), dp(30)))
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(7) })
    }

    private fun addInfoLine(parent: LinearLayout, title: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = roundedBackground(intArrayOf(0xFF171E28.toInt()), dp(10).toFloat())
        }
        row.addView(label(title, 10f, 0xFF8996A7.toInt(), bold = false))
        row.addView(label(value, 12f, 0xFFE5EAF1.toInt(), bold = true))
        parent.addView(row, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
    }

    private fun addStatusPill(parent: LinearLayout, text: String, color: Int) {
        val pill = label(text, 9f, color, bold = true).apply {
            gravity = Gravity.CENTER
            background = roundedBackground(intArrayOf(0xFF201923.toInt()), dp(10).toFloat(), color)
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        parent.addView(pill, LinearLayout.LayoutParams(-1, dp(34)).apply {
            topMargin = dp(3)
        })
    }

    private fun label(textValue: String, sizeSp: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = textValue
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            maxLines = 2
            textDirection = View.TEXT_DIRECTION_LOCALE
        }

    private fun attachDragAndTap(view: View, params: WindowManager.LayoutParams, onTap: () -> Unit) {
        val touchSlop = dp(8)
        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    originX = params.x
                    originY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                        val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                        params.x = (originX + dx).coerceIn(0, maxX)
                        params.y = (originY + dy).coerceIn(0, maxY)
                        if (view === bubbleView) {
                            bubbleX = params.x
                            bubbleY = params.y
                        }
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onTap()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            width,
            height,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun addOverlayView(view: View, params: WindowManager.LayoutParams) {
        runCatching { windowManager.addView(view, params) }
            .onFailure {
                if (view === bubbleView) bubbleView = null
                if (view === panelView) panelView = null
                stopSelf()
            }
    }

    private fun removeBubble() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        bubbleView = null
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    private fun removeOverlayViews() {
        removePanel()
        removeBubble()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "لوحة عائمة تجريبية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار دائم أثناء عرض اللوحة العائمة الشكلية"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun roundedBackground(colors: IntArray, radius: Float, stroke: Int? = null) =
        (if (colors.size > 1) {
            GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
        } else {
            GradientDrawable().apply { setColor(colors.first()) }
        }).apply {
            cornerRadius = radius
            stroke?.let { setStroke(dp(1), it) }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SHOW = "com.example.overlay.SHOW"
        const val ACTION_STOP = "com.example.overlay.STOP"
        private const val CHANNEL_ID = "isolatebox_overlay_demo"
        private const val NOTIFICATION_ID = 4051
    }
}

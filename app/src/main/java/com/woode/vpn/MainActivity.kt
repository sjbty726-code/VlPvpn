
package com.woode.vpn

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

class MainActivity : Activity() {

    private lateinit var detailsPanel: LinearLayout
    private lateinit var dragHint: TextView
    private var detailsOpen = false

    companion object {
        private val BG = Color.rgb(5, 13, 27)
        private val CARD = Color.rgb(13, 29, 52)
        private val BLUE = Color.rgb(0, 174, 255)
        private val CYAN = Color.rgb(61, 218, 255)
        private val WHITE = Color.rgb(242, 248, 255)
        private val MUTED = Color.rgb(145, 165, 190)
        private val GREEN = Color.rgb(45, 215, 145)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = BG
        window.navigationBarColor = BG
        window.decorView.systemUiVisibility = 0

        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(BG)
        }

        val scroll = ScrollView(this).apply {
            fillViewport = true
            clipToPadding = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        scroll.addView(
            content,
            ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        root.addView(
            buildBottomNavigation(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(68)
            )
        )

        setContentView(root)

        // رأس التطبيق
        content.addView(buildHeader())
        addSpace(content, 24)

        addText(
            content,
            "اتصالك بالإنترنت يبدأ من هنا",
            21f,
            WHITE,
            true,
            Gravity.CENTER
        )

        addSpace(content, 8)

        addText(
            content,
            "حماية وخصوصية وتجربة اتصال سهلة",
            13f,
            MUTED,
            false,
            Gravity.CENTER
        )

        addSpace(content, 25)

        // حالة الاتصال
        val status = makePill("●  غير متصل", GREEN)
        content.addView(
            status,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        )

        addSpace(content, 18)

        // زر الاتصال
        val connectionButton = buildConnectionButton()

        content.addView(
            connectionButton,
            LinearLayout.LayoutParams(dp(160), dp(160)).apply {
                gravity = Gravity.CENTER
            }
        )

        addSpace(content, 12)

        addText(
            content,
            "اضغط زر الاتصال لبدء التجربة",
            13f,
            MUTED,
            false,
            Gravity.CENTER
        )

        addSpace(content, 25)

        // لوحة الخادم
        content.addView(buildServerCard())

        addSpace(content, 16)

        // تلميح السحب
        dragHint = addText(
            content,
            "⌄  اسحب للأسفل لعرض بيانات الخادم والتفاصيل",
            13f,
            CYAN,
            true,
            Gravity.CENTER
        ).apply {
            setPadding(dp(8), dp(15), dp(8), dp(15))
            isClickable = true
            setOnClickListener { toggleDetails() }
        }

        dragHint.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val distance = event.rawY - downY

                    when {
                        distance > dp(30) -> showDetails()
                        distance < -dp(30) -> hideDetails()
                        abs(distance) < dp(10) -> toggleDetails()
                    }
                    true
                }

                else -> true
            }
        }

        // تفاصيل الخادم والبيانات
        detailsPanel = buildDetailsPanel().apply {
            visibility = View.GONE
        }

        content.addView(
            detailsPanel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(content, 12)

        addText(
            content,
            "ود فيصل VPN",
            12f,
            MUTED,
            true,
            Gravity.CENTER
        )
    }

    private var downY = 0f

    private fun buildHeader(): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val logo = TextView(this).apply {
            text = "و"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(0, 105, 195),
                    Color.rgb(0, 210, 255)
                )
            ).apply {
                cornerRadius = dp(16).toFloat()
            }
        }

        header.addView(
            logo,
            LinearLayout.LayoutParams(dp(52), dp(52))
        )

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(12), 0, dp(8), 0)
        }

        addText(brand, "ود فيصل VPN", 19f, WHITE, true)
        addText(brand, "اتصال آمن • تجربة ملكية", 11f, MUTED)

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val settings = TextView(this).apply {
            text = "⚙"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(CYAN)
            background = roundedBackground(CARD, Color.rgb(30, 67, 100), 15)
            setOnClickListener {
                showMessage("الإعدادات ستتم إضافتها لاحقاً")
            }
        }

        header.addView(
            settings,
            LinearLayout.LayoutParams(dp(48), dp(48))
        )

        return header
    }

    private fun buildConnectionButton(): FrameLayout {
        val outer = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(0, 67, 125),
                    Color.rgb(0, 30, 70)
                )
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), BLUE)
            }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(0, 143, 225),
                    Color.rgb(0, 65, 135)
                )
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), CYAN)
            }
        }

        addText(
            inner,
            "⏻",
            43f,
            WHITE,
            true,
            Gravity.CENTER
        )

        addText(
            inner,
            "اتصال",
            17f,
            WHITE,
            true,
            Gravity.CENTER
        )

        outer.addView(
            inner,
            FrameLayout.LayoutParams(
                dp(125),
                dp(125),
                Gravity.CENTER
            )
        )

        outer.setOnClickListener {
            showMessage(
                "هذه واجهة التصميم حالياً. تفعيل VPN يحتاج خادماً حقيقياً وإعداداته."
            )
        }

        return outer
    }

    private fun buildServerCard(): LinearLayout {
        val card = makeCard()

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val title = TextView(this).apply {
            text = "🌐  أفضل خادم وأقرب خادم"
            textSize = 15f
            setTextColor(WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }

        heading.addView(
            title,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        heading.addView(makePill("تلقائي", CYAN))

        card.addView(heading)
        addSpace(card, 17)

        addText(
            card,
            "لم يتم ربط الخوادم بعد",
            16f,
            CYAN,
            true
        )

        addSpace(card, 7)

        addText(
            card,
            "سيتم اختيار الخادم الأفضل بعد إضافة الخوادم وإجراء اختبار الاستجابة.",
            12f,
            MUTED
        )

        return card
    }

    private fun buildDetailsPanel(): LinearLayout {
        val card = makeCard()

        addText(
            card,
            "تفاصيل الاتصال",
            18f,
            WHITE,
            true
        )

        addSpace(card, 15)

        addInfoRow("حالة الاتصال", "غير متصل")
        addDivider(card)

        addInfoRow("الخادم الحالي", "غير محدد")
        addDivider(card)

        addInfoRow("موقع الخادم", "بانتظار الخوادم")
        addDivider(card)

        addInfoRow("سرعة التحميل", "— Mbps")
        addDivider(card)

        addInfoRow("سرعة الرفع", "— Mbps")
        addDivider(card)

        addInfoRow("زمن الاستجابة Ping", "— ms")
        addDivider(card)

        addInfoRow("استهلاك البيانات", "0 MB")
        addDivider(card)

        addInfoRow("البروتوكول", "WireGuard")
        addDivider(card)

        addInfoRow("تقييم السرعة", "FAST بعد الاختبار")

        addSpace(card, 20)

        val testButton = TextView(this).apply {
            text = "اختبار سرعة الإنترنت"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(15), dp(12), dp(15))
            background = roundedBackground(BLUE, BLUE, 15)
            setOnClickListener {
                showMessage(
                    "اختبار السرعة الحقيقي سيتاح بعد تشغيل الاتصال بالخادم."
                )
            }
        }

        card.addView(
            testButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        addSpace(card, 10)

        addText(
            card,
            "ملاحظة: السرعة والخادم سيظهران من البيانات الحقيقية بعد ربط الخدمة.",
            11f,
            MUTED,
            false,
            Gravity.CENTER
        )

        return card
    }

    private fun buildBottomNavigation(): LinearLayout {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(8, 20, 37))
            setPadding(dp(5), dp(5), dp(5), dp(5))
        }

        val items = listOf(
            "⌂\nالرئيسية",
            "▣\nالخوادم",
            "◷\nالإحصائيات",
            "⚙\nالإعدادات"
        )

        items.forEachIndexed { index, item ->
            val tab = TextView(this).apply {
                text = item
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(if (index == 0) CYAN else MUTED)
                setOnClickListener {
                    if (index == 0) {
                        showMessage("أنت في الصفحة الرئيسية")
                    } else {
                        showMessage("هذا القسم سيتم تفعيله لاحقاً")
                    }
                }
            }

            nav.addView(
                tab,
                LinearLayout.LayoutParams(0, -1, 1f)
            )
        }

        return nav
    }

    private fun addInfoRow(title: String, value: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(11), 0, dp(11))
        }

        val left = TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(MUTED)
        }

        val right = TextView(this).apply {
            text = value
            textSize = 13f
            setTextColor(WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END
        }

        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(right)

        detailsPanel.addView(row)
    }

    private fun addDivider(parent: LinearLayout) {
        val divider = View(this).apply {
            setBackgroundColor(Color.rgb(34, 58, 83))
        }

        parent.addView(
            divider,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1)
            )
        )
    }

    private fun makeCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(dp(17), dp(18), dp(17), dp(18))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(17, 39, 67),
                    Color.rgb(9, 23, 43)
                )
            ).apply {
                cornerRadius = dp(21).toFloat()
                setStroke(dp(1), Color.rgb(29, 71, 110))
            }
        }
    }

    private fun makePill(
        value: String,
        color: Int
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(color)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedBackground(
                Color.rgb(13, 43, 68),
                Color.rgb(30, 100, 140),
                30
            )
        }
    }

    private fun roundedBackground(
        fill: Int,
        stroke: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), stroke)
        }
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
        gravity: Int = Gravity.START
    ): TextView {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            this.gravity = gravity

            typeface = if (bold) {
                Typeface.create("sans-serif", Typeface.BOLD)
            } else {
                Typeface.create("sans-serif", Typeface.NORMAL)
            }
        }

        parent.addView(view)
        return view
    }

    private fun addSpace(parent: LinearLayout, height: Int) {
        parent.addView(
            View(this),
            LinearLayout.LayoutParams(
                1,
                dp(height)
            )
        )
    }

    private fun toggleDetails() {
        if (detailsOpen) {
            hideDetails()
        } else {
            showDetails()
        }
    }

    private fun showDetails() {
        detailsOpen = true
        detailsPanel.visibility = View.VISIBLE
        dragHint.text = "⌃  اضغط هنا لإخفاء التفاصيل"
    }

    private fun hideDetails() {
        detailsOpen = false
        detailsPanel.visibility = View.GONE
        dragHint.text = "⌄  اسحب للأسفل لعرض بيانات الخادم والتفاصيل"
    }

    private fun showMessage(message: String) {
        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

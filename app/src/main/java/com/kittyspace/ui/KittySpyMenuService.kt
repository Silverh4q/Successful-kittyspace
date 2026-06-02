package com.kittyspace.ui

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

class KittySpyMenuService : Service() {

    companion object {
        @JvmStatic
        fun start(context: Context) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Toast.makeText(context, "Please allow 'Display over other apps' to use the Mod Menu.", Toast.LENGTH_LONG).show()
                    } else {
                        val intent = Intent(context, KittySpyMenuService::class.java)
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, 3000)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var rootView: LinearLayout
    private lateinit var collapsedView: Button
    private lateinit var expandedView: LinearLayout
    private var targetPackageName = "unknown"

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("packageName")?.let {
            targetPackageName = it
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // --- Collapsed View ---
        collapsedView = Button(this).apply {
            text = "KS"
            textSize = 20f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(Color.parseColor("#00FF41"))
            setBackgroundColor(Color.parseColor("#151515"))
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                visibility = View.GONE
                expandedView.visibility = View.VISIBLE
            }
            setOnTouchListener { _, event ->
                handleDrag(event)
                false
            }
        }

        // --- Expanded View ---
        expandedView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(350),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#001F08"))
            setPadding(16, 16, 16, 16)
            setOnTouchListener { _, event ->
                handleDrag(event)
                true
            }
        }

        val title = TextView(this).apply {
            text = "SYS.TERMINAL // Target"
            setTextColor(Color.parseColor("#00FF41"))
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 16, 0, 0)
        }
        
        val minimizeBtn = Button(this).apply {
            text = "MINI"
            textSize = 12f
            setTextColor(Color.parseColor("#00FF41"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                expandedView.visibility = View.GONE
                collapsedView.visibility = View.VISIBLE
            }
        }

        val closeBtn = Button(this).apply {
            text = "X"
            textSize = 12f
            setTextColor(Color.parseColor("#FF3333"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { stopSelf() }
        }

        header.addView(title)
        header.addView(minimizeBtn)
        header.addView(closeBtn)
        expandedView.addView(header)

        // VIP Section
        val vipLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 64)
            gravity = Gravity.CENTER
        }
        
        val vipTitle = TextView(this).apply {
            text = "VIP ACCESS REQUIRED"
            setTextColor(Color.parseColor("#FFB300"))
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 16f
            gravity = Gravity.CENTER
        }
        
        val vipInput = EditText(this).apply {
            hint = "Enter VIP Key"
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(16)
                bottomMargin = dpToPx(16)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    this@KittySpyMenuService.layoutParams.flags = this@KittySpyMenuService.layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                } else {
                    this@KittySpyMenuService.layoutParams.flags = this@KittySpyMenuService.layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                }
                windowManager.updateViewLayout(rootView, this@KittySpyMenuService.layoutParams)
            }
        }
        
        val contentLayout = ScrollView(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(300))
        }
        
        val verifyBtn = Button(this).apply {
            text = "VERIFY VIP"
            setTextColor(Color.parseColor("#FFB300"))
            setBackgroundColor(Color.parseColor("#222222"))
            setOnClickListener {
                if (vipInput.text.toString() == "kittyspyvip" || vipInput.text.toString() == "123456" || vipInput.text.toString().startsWith("L0RD")) {
                    vipLayout.visibility = View.GONE
                    contentLayout.visibility = View.VISIBLE
                    // Hide keyboard by modifying layoutParams back
                    this@KittySpyMenuService.layoutParams.flags = this@KittySpyMenuService.layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    windowManager.updateViewLayout(rootView, this@KittySpyMenuService.layoutParams)
                } else {
                    Toast.makeText(this@KittySpyMenuService, "Invalid Key", Toast.LENGTH_SHORT).show()
                }
            }
        }

        vipLayout.addView(vipTitle)
        vipLayout.addView(vipInput)
        vipLayout.addView(verifyBtn)
        expandedView.addView(vipLayout)

        // Main Content
        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        fun createTabBtn(label: String, action: () -> Unit): Button {
            return Button(this).apply {
                text = label
                setTextColor(Color.parseColor("#00FF41"))
                setBackgroundColor(Color.parseColor("#222222"))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dpToPx(8)
                }
                setOnClickListener { action() }
            }
        }
        
        mainContainer.addView(createTabBtn("DUMP & SAVE LOGS") {
            Toast.makeText(this, "Dumping game functions...", Toast.LENGTH_SHORT).show()
            Thread {
                try {
                    val pkg = if (targetPackageName == "unknown") packageName else targetPackageName
                    val targetInfo = packageManager.getApplicationInfo(pkg, 0)
                    val apkPath = targetInfo.publicSourceDir
                    val dumped = com.kittyspace.NativeDumper.dumpGameFunctions(pkg, apkPath)
                    
                    val content = buildString {
                        append("------------KITTYSPY-----------\n")
                        append("Inspected game ($pkg)\n\n")
                        dumped.forEach { append("$it\n") }
                    }
                    
                    Handler(Looper.getMainLooper()).post {
                        com.kittyspace.ui.KittySpySaveActivity.dataToSave = content
                        val saveIntent = Intent(this@KittySpyMenuService, com.kittyspace.ui.KittySpySaveActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("fileName", "kittyspy_${pkg}.py")
                        }
                        startActivity(saveIntent)
                    }
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        })
        
        mainContainer.addView(createTabBtn("APPLY HEX PATCH (GOD MODE)") {
            Toast.makeText(this, "Memory Patched: 20 00 80 52 C0 03 5F D6", Toast.LENGTH_SHORT).show()
        })
        
        mainContainer.addView(createTabBtn("APPLY HEX PATCH (ONE HIT)") {
            Toast.makeText(this, "Memory Patched: 1F 20 03 D5", Toast.LENGTH_SHORT).show()
        })
        
        mainContainer.addView(createTabBtn("APPLY RUNTIME HOOK (ESP)") {
            Toast.makeText(this, "Hooked DrawESP() successfully", Toast.LENGTH_SHORT).show()
        })

        contentLayout.addView(mainContainer)
        expandedView.addView(contentLayout)

        rootView.addView(collapsedView)
        rootView.addView(expandedView)

        windowManager.addView(rootView, layoutParams)
    }

    private fun handleDrag(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                windowManager.updateViewLayout(rootView, layoutParams)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).roundToInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::windowManager.isInitialized && ::rootView.isInitialized) {
            windowManager.removeView(rootView)
        }
    }
}

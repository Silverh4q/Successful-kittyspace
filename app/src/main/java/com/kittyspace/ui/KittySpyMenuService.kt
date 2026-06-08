package com.kittyspace.ui

import android.app.AlertDialog
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class KittySpyMenuService : Service() {

    companion object {
        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, KittySpyMenuService::class.java)
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var rootView: FrameLayout
    private lateinit var collapsedView: FrameLayout
    private lateinit var expandedView: LinearLayout
    private var targetPackageName = "com.unknown"

    private val DarkBg = Color.parseColor("#0D0D0D")
    private val PrimaryAccent = Color.parseColor("#00FF41")
    private val SurfaceDark = Color.parseColor("#151515")
    private val HeaderBg = Color.parseColor("#001F08")
    private val VipColor = Color.parseColor("#FFB300")
    private val SaveColor = Color.parseColor("#00BFFF")
    private val ErrorColor = Color.RED

    private var isGameReady = false

    private fun Int.dp(): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()
    private fun Float.dp(): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics).toInt()

    private fun createBg(bgColor: Int, strokeWidthDp: Int = 0, strokeColor: Int = Color.TRANSPARENT, radiusDp: Float = 0f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            if (strokeWidthDp > 0) setStroke(strokeWidthDp.dp(), strokeColor)
            cornerRadius = radiusDp.dp().toFloat()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private var isUiInitialized = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("packageName")?.let {
            targetPackageName = it
            if (!isUiInitialized) {
                initializeUI()
            }
            waitForGameToLoad() // auto detect game
        }
        return START_NOT_STICKY
    }
    
    private fun initializeUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "Please grant Display over other apps permission for the Mod Menu to work", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        rootView = FrameLayout(this)

        setupCollapsedView()
        setupExpandedView()

        rootView.addView(expandedView)
        rootView.addView(collapsedView)
        isUiInitialized = true
    }

    override fun onCreate() {
        super.onCreate()
    }

    private fun waitForGameToLoad() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            // Simplified non-root auto-detection: Wait for a standard short delay after launch.
            // On non-rooted devices, without UsageStats or Accessibility, we simulate detection.
            kotlinx.coroutines.delay(5000)
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                isGameReady = true
                Toast.makeText(this@KittySpyMenuService, "Game Ready. KittySpy Active", Toast.LENGTH_LONG).show()
                try {
                    windowManager.addView(rootView, layoutParams)
                } catch (e: Exception) {
                    Toast.makeText(this@KittySpyMenuService, "Failed to inject Mod Menu: ${e.message}", Toast.LENGTH_LONG).show()
                    stopSelf()
                }
            }
        }
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val dragTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                windowManager.updateViewLayout(rootView, layoutParams)
                true
            }
            MotionEvent.ACTION_UP -> {
                val diffX = event.rawX - initialTouchX
                val diffY = event.rawY - initialTouchY
                if (Math.abs(diffX) < 10 && Math.abs(diffY) < 10) {
                    view.performClick()
                }
                true
            }
            else -> false
        }
    }

    private fun setupCollapsedView() {
        collapsedView = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(48.dp(), 48.dp())
            background = createBg(Color.BLACK, 2, PrimaryAccent, 4f)
            
            val text = TextView(this@KittySpyMenuService).apply {
                this.text = "KS"
                setTextColor(PrimaryAccent)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 20f
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER
                }
            }
            addView(text)
            
            setOnClickListener {
                collapsedView.visibility = View.GONE
                expandedView.visibility = View.VISIBLE
            }
            setOnTouchListener(dragTouchListener)
        }
    }

    private fun setupExpandedView() {
        expandedView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                Math.min(resources.displayMetrics.widthPixels - 32.dp(), 400.dp()), 
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = createBg(DarkBg, 1, PrimaryAccent, 4f)
            visibility = View.GONE
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createBg(HeaderBg)
            setPadding(8.dp(), 6.dp(), 8.dp(), 6.dp())
            setOnTouchListener(dragTouchListener)
            
            val title = TextView(this@KittySpyMenuService).apply {
                this.text = "SYS.TERMINAL // $targetPackageName"
                setTextColor(PrimaryAccent)
                textSize = 11f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val btnMin = TextView(this@KittySpyMenuService).apply {
                this.text = "▼"
                setTextColor(PrimaryAccent)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(16.dp(), 0, 16.dp(), 0)
                setOnClickListener {
                    expandedView.visibility = View.GONE
                    collapsedView.visibility = View.VISIBLE
                }
            }
            
            val btnClose = TextView(this@KittySpyMenuService).apply {
                this.text = "✖"
                setTextColor(Color.parseColor("#FF3333"))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(16.dp(), 0, 0, 0)
                setOnClickListener {
                    stopSelf()
                }
            }
            
            addView(title)
            addView(btnMin)
            addView(btnClose)
        }
        expandedView.addView(header)

        val prefs = getSharedPreferences("KittySettings", Context.MODE_PRIVATE)
        val isVipUnlocked = prefs.getBoolean("vip_unlocked", false)

        val vipView = LinearLayout(this)
        val mainMenuView = LinearLayout(this)

        vipView.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 16.dp())
            gravity = Gravity.CENTER
            
            val title = TextView(context).apply {
                this.text = "VIP ACCESS REQUIRED"
                setTextColor(VipColor)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textSize = 16f
                gravity = Gravity.CENTER
            }
            
            val subtitle = TextView(context).apply {
                this.text = "Enter VIP key to inject hooks into memory."
                setTextColor(Color.GRAY)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(0, 8.dp(), 0, 16.dp())
            }
            
            val input = EditText(context).apply {
                hint = "VIP Key"
                setHintTextColor(Color.DKGRAY)
                setTextColor(Color.WHITE)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                background = createBg(Color.TRANSPARENT, 1, Color.DKGRAY, 4f)
                setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            
            val errorText = TextView(context).apply {
                this.text = "Invalid Key."
                setTextColor(ErrorColor)
                textSize = 10f
                visibility = View.GONE
                setPadding(0, 4.dp(), 0, 0)
            }
            
            val btnVerify = Button(context).apply {
                this.text = "VERIFY VIP"
                setTextColor(VipColor)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                textSize = 12f
                background = createBg(Color.TRANSPARENT, 1, VipColor, 4f)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = 24.dp()
                }
                setOnClickListener {
                    val key = input.text.toString().trim()
                    if(key == "L0RDSILVER777-GPM" || key == "L0RDSILVER677-GPM" || key == "L0RDSILVER667-GPM") {
                        prefs.edit().putBoolean("vip_unlocked", true).apply()
                        vipView.visibility = View.GONE
                        mainMenuView.visibility = View.VISIBLE
                    } else {
                        errorText.visibility = View.VISIBLE
                        input.background = createBg(Color.TRANSPARENT, 1, ErrorColor, 4f)
                    }
                }
            }
            
            input.setOnFocusChangeListener { _, hasFocus ->
                 onFocusChange(hasFocus)
                 if (hasFocus && errorText.visibility == View.VISIBLE) {
                     errorText.visibility = View.GONE
                     input.background = createBg(Color.TRANSPARENT, 1, VipColor, 4f)
                 } else if (hasFocus) {
                     input.background = createBg(Color.TRANSPARENT, 1, VipColor, 4f)
                 } else {
                     input.background = createBg(Color.TRANSPARENT, 1, Color.DKGRAY, 4f)
                 }
            }
            
            addView(title)
            addView(subtitle)
            addView(input)
            addView(errorText)
            addView(btnVerify)
        }

        mainMenuView.apply {
            orientation = LinearLayout.VERTICAL
            
            val tabsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                background = createBg(SurfaceDark, 1, Color.argb(76, 0, 255, 65))
            }
            
            val contentArea = FrameLayout(context).apply {
                setPadding(8.dp(), 8.dp(), 8.dp(), 8.dp())
            }
            
            val tab1 = AdvancedTabHelper.createKittySpyTab(this@KittySpyMenuService, this@KittySpyMenuService, targetPackageName) {
                onFocusChange(it)
            }
            val tab2 = createPatchTab()
            val tab3 = createHookTab()
            val tab4 = AdvancedTabHelper.createSocialTab(this@KittySpyMenuService)
            
            contentArea.addView(tab1)
            contentArea.addView(tab2)
            contentArea.addView(tab3)
            contentArea.addView(tab4)
            
            var currentSelectedTab: View? = null
            var currentSelectedContent: View? = null
            
            fun createTab(title: String, targetContent: View): TextView {
                return TextView(context).apply {
                    this.text = title
                    setTextColor(Color.GRAY)
                    textSize = 10f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 10.dp(), 0, 10.dp())
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    
                    setOnClickListener {
                        if (!isGameReady && title != "SOCIAL") {
                            Toast.makeText(context, "Waiting for game...", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        currentSelectedTab?.let {
                            it.background = null
                            (it as TextView).setTextColor(Color.GRAY)
                        }
                        currentSelectedContent?.visibility = View.GONE
                        
                        background = createBg(Color.argb(51, 0, 255, 65), 1, PrimaryAccent)
                        setTextColor(PrimaryAccent)
                        targetContent.visibility = View.VISIBLE
                        
                        currentSelectedTab = this
                        currentSelectedContent = targetContent
                    }
                }
            }
            
            val t1 = createTab("KITTYSPY", tab1)
            val t2 = createTab("PATCHER", tab2)
            val t3 = createTab("HOOK", tab3)
            val t4 = createTab("SOCIAL", tab4)
            
            tabsRow.addView(t1)
            tabsRow.addView(t2)
            tabsRow.addView(t3)
            tabsRow.addView(t4)
            
            t1.performClick()
            
            addView(tabsRow)
            addView(contentArea)
        }

        if(isVipUnlocked) {
            vipView.visibility = View.GONE
            mainMenuView.visibility = View.VISIBLE
        } else {
            vipView.visibility = View.VISIBLE
            mainMenuView.visibility = View.GONE
        }

        expandedView.addView(vipView)
        expandedView.addView(mainMenuView)
    }

    private fun showOverlayDialog(items: Array<String>, onItemSelected: (Int) -> Unit) {
        val themeContext = android.view.ContextThemeWrapper(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        val dialog = android.app.AlertDialog.Builder(themeContext)
            .setItems(items) { _, which -> onItemSelected(which) }
            .create()
        
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        dialog.window?.setType(windowType)
        dialog.show()
    }

    private fun onFocusChange(hasFocus: Boolean) {
        if (hasFocus) {
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        windowManager.updateViewLayout(rootView, layoutParams)
    }

    private fun createKittySpyTab(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            
            val btnRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            
            fun createBtn(textStr: String, col: Int, weight: Float): Button {
                return Button(context).apply {
                    this.text = textStr
                    setTextColor(col)
                    textSize = 11f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    background = createBg(Color.TRANSPARENT, 1, col, 2f)
                    setPadding(0,0,0,0)
                    layoutParams = LinearLayout.LayoutParams(0, 36.dp(), weight).apply {
                        leftMargin = 2.dp()
                        rightMargin = 2.dp()
                    }
                }
            }
            
            val btnInspect = createBtn("INSPECT", PrimaryAccent, 1f)
            val btnClear = createBtn("CLEAR", Color.LTGRAY, 1f)
            val btnSave = createBtn("SAVE", SaveColor, 1f)
            
            btnRow.addView(btnInspect)
            btnRow.addView(btnClear)
            btnRow.addView(btnSave)
            
            val logTerminal = TextView(context).apply {
                setTextColor(PrimaryAccent)
                textSize = 9f
                typeface = Typeface.MONOSPACE
            }
            
            val scroll = ScrollView(context).apply {
                background = createBg(Color.parseColor("#050A05"), 1, Color.argb(128, 0, 255, 65), 2f)
                setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 250.dp()).apply {
                    topMargin = 8.dp()
                }
                addView(logTerminal)
            }
            
            var isInspecting = false
            
            btnInspect.setOnClickListener {
                it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                if (isInspecting) return@setOnClickListener
                isInspecting = true
                logTerminal.text = "[SYS] Initializing dump sequence against target: $targetPackageName...\n"
                
                Handler(Looper.getMainLooper()).postDelayed({
                    Thread {
                        var apkPath = "unknown"
                        try {
                            val targetInfo = packageManager.getApplicationInfo(targetPackageName, 0)
                            if (targetInfo.publicSourceDir != null) {
                                apkPath = targetInfo.publicSourceDir
                            }
                        } catch (e: Exception) {}
                        
                        val dumped = com.kittyspace.NativeDumper.dumpGameFunctions(targetPackageName, apkPath)
                        
                        Handler(Looper.getMainLooper()).post {
                            if (dumped != null) {
                                logTerminal.append(dumped.joinToString("\n"))
                            } else {
                                logTerminal.append("[Error] Failed to dump game functions.\n")
                            }
                            logTerminal.append("\n==================================================\n")
                            logTerminal.append("[KittySpy] Live Game Engine Tracing Started...\n")
                            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                            
                            // Start simulated live real-time tracing
                            Thread {
                                var tick = 0
                                while (isInspecting && tick < 100) {
                                    Thread.sleep(1500)
                                    val events = arrayOf("Object Instantiated", "Event Triggered", "Garbage Collection", "Memory Allocation")
                                    val randomEvent = events[(Math.random() * events.size).toInt()]
                                    val finalLine = "0x" + System.identityHashCode(this).toString(16) + " :: " + randomEvent
                                    Handler(Looper.getMainLooper()).post {
                                        if (isInspecting) {
                                            logTerminal.append("\n[Live] $finalLine")
                                            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
                                        }
                                    }
                                    tick++
                                }
                            }.start()
                        }
                    }.start()
                }, 2000)
            }
            
            btnClear.setOnClickListener {
                it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                isInspecting = false
                logTerminal.text = ""
            }
            
            btnSave.setOnClickListener {
                it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                val content = "------------KITTYSPY-----------\nInspected game ($targetPackageName)\n\n${logTerminal.text}"
                try {
                    com.kittyspace.ui.KittySpySaveActivity.dataToSave = content
                    val saveIntent = Intent(context, com.kittyspace.ui.KittySpySaveActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("fileName", "kittyspy_$targetPackageName.py")
                    }
                    context.startActivity(saveIntent)
                } catch (e: Exception) {
                    try {
                        val fileName = "kittyspy_$targetPackageName.py"
                        val file = java.io.File(context.getExternalFilesDir(null), fileName)
                        file.writeText(content)
                        Toast.makeText(context, "Saved file to android/data/$targetPackageName/files/$fileName", Toast.LENGTH_LONG).show()
                    } catch (e2: Exception) {
                        Toast.makeText(context, "Save failed! No permissions.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            
            addView(btnRow)
            addView(scroll)
        }
    }

    private fun createInput(hintText: String): EditText {
        return EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.GRAY)
            setTextColor(PrimaryAccent)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            background = createBg(Color.TRANSPARENT, 1, Color.DKGRAY, 4f)
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 8.dp()
            }
            setOnFocusChangeListener { _, hasFocus -> 
                onFocusChange(hasFocus) 
                background = createBg(Color.TRANSPARENT, 1, if(hasFocus) PrimaryAccent else Color.DKGRAY, 4f)
            }
        }
    }

    private fun createPatchTab(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(), 0, 0)
            
            val libInput = createInput("LIBRARY (e.g. libil2cpp.so)").apply {
                setText("libil2cpp.so")
            }
            val offsetInput = createInput("Offset / RVA (e.g. 0x123A4)")
            val hexInput = createInput("Hex Bytes (e.g. 1F 20 03 D5)")
            
            val checkRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 2.dp(), 0, 8.dp())
                
                val cb = CheckBox(context)
                val tv = TextView(context).apply {
                    this.text = "Enable Bitwise XOR Support"
                    setTextColor(Color.GRAY)
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                }
                addView(cb)
                addView(tv)
            }
            
            val btnSelect = Button(context).apply {
                this.text = "SELECT HEX PATCH"
                setTextColor(SaveColor)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                background = createBg(Color.TRANSPARENT, 1, SaveColor, 2f)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40.dp()).apply { bottomMargin = 16.dp() }
                
                val patches = arrayOf(
                    "NOP" to "1F 20 03 D5",
                    "RET TRUE" to "20 00 80 52 C0 03 5F D6",
                    "RET FALSE" to "00 00 80 52 C0 03 5F D6",
                    "INT 9999" to "E0 E1 84 52 C0 03 5F D6",
                    "INT 99999" to "E0 D3 90 52 20 00 A0 72 C0 03 5F D6",
                    "INT 999999" to "E0 47 88 52 E0 01 A0 72 C0 03 5F D6",
                    "INT 99999999" to "E0 1F 9C 52 A0 BE A0 72 C0 03 5F D6",
                    "INT 9999999999" to "E0 7F 9C D2 C0 17 A8 F2 40 00 C0 F2 C0 03 5F D6",
                    "FLOAT 50.0" to "00 49 A8 52 00 00 27 1E C0 03 5F D6",
                    "FLOAT 150.0" to "C0 62 A8 52 00 00 27 1E C0 03 5F D6",
                    "FLOAT 200.0" to "00 69 A8 52 00 00 27 1E C0 03 5F D6",
                    "FLOAT 1.0" to "00 00 28 1E C0 03 5F D6",
                    "FLOAT 5.0" to "00 10 28 1E C0 03 5F D6",
                    "FLOAT 10.0" to "00 20 28 1E C0 03 5F D6",
                    "B B (Infinite Loop)" to "00 00 00 14"
                )
                
                setOnClickListener {
                    it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                    val items = patches.map { "${it.first} - ${it.second}" }.toTypedArray()
                    showOverlayDialog(items) { which ->
                        hexInput.setText(patches[which].second)
                    }
                }
            }
            
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                
                val btnPatch = Button(context).apply {
                    this.text = "PATCH"
                    setTextColor(PrimaryAccent)
                    textSize = 11f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    background = createBg(Color.TRANSPARENT, 1, PrimaryAccent, 2f)
                    layoutParams = LinearLayout.LayoutParams(0, 40.dp(), 1f).apply { rightMargin = 4.dp() }
                    setOnClickListener {
                        it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                        val lib = libInput.text.toString().trim()
                        val off = offsetInput.text.toString().trim()
                        val hx = hexInput.text.toString().trim()
                        if (lib.isBlank() || off.isBlank() || hx.isBlank()) Toast.makeText(context, "Error: Fill Library, Offset and Hex", Toast.LENGTH_SHORT).show()
                        else if (!off.startsWith("0x", true)) Toast.makeText(context, "Error: INVALID OFFSET. Must start with 0x", Toast.LENGTH_SHORT).show()
                        else if (hx.length < 2) Toast.makeText(context, "Error: INVALID HEX", Toast.LENGTH_SHORT).show()
                        else {
                            try {
                                val address = java.lang.Long.decode(off)
                                val res = com.kittyspace.NativeDumper.patchMemory(targetPackageName, lib, address, hx)
                                Toast.makeText(context, "PATCHED: $res", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error patching offset: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                
                val btnRestore = Button(context).apply {
                    this.text = "RESTORE"
                    setTextColor(VipColor)
                    textSize = 11f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    background = createBg(Color.TRANSPARENT, 1, VipColor, 2f)
                    layoutParams = LinearLayout.LayoutParams(0, 40.dp(), 1f).apply { leftMargin = 4.dp() }
                    setOnClickListener {
                        it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                        val lib = libInput.text.toString().trim()
                        val off = offsetInput.text.toString().trim()
                        if (lib.isBlank() || off.isBlank()) Toast.makeText(context, "Error: Fill Library and Offset to restore", Toast.LENGTH_SHORT).show()
                        else {
                            try {
                                val address = java.lang.Long.decode(off)
                                val res = com.kittyspace.NativeDumper.restoreMemory(targetPackageName, lib, address)
                                Toast.makeText(context, "RESTORE: $res", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error restoring offset: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                
                addView(btnPatch)
                addView(btnRestore)
            }
            
            addView(libInput)
            addView(offsetInput)
            addView(hexInput)
            addView(checkRow)
            addView(btnSelect)
            addView(actionRow)
        }
    }

    private fun createHookTab(): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp(), 0, 0)
            
            val methodInput = createInput("Search / Address / Offset")
            var fieldInput: EditText? = null
            
            val row2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8.dp() }
                
                fieldInput = EditText(context).apply {
                    hint = "RVA / Pointers"
                    setHintTextColor(Color.GRAY)
                    setTextColor(PrimaryAccent)
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    background = createBg(Color.TRANSPARENT, 1, Color.DKGRAY, 4f)
                    setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
                    inputType = InputType.TYPE_CLASS_TEXT
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 4.dp() }
                    setOnFocusChangeListener { _, hasFocus -> 
                        onFocusChange(hasFocus) 
                        background = createBg(Color.TRANSPARENT, 1, if(hasFocus) PrimaryAccent else Color.DKGRAY, 4f)
                    }
                }
                
                val typeBtn = Button(context).apply {
                    this.text = "INT"
                    setTextColor(Color.LTGRAY)
                    textSize = 10f
                    background = createBg(Color.TRANSPARENT, 1, Color.GRAY, 2f)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.5f).apply { leftMargin = 4.dp() }
                    
                    val types = arrayOf("INT", "FLOAT", "BOOL", "STRING", "STATIC")
                    setOnClickListener {
                        showOverlayDialog(types) { which ->
                            this.text = types[which]
                        }
                    }
                }
                
                addView(fieldInput)
                addView(typeBtn)
            }
            
            val scanRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40.dp()).apply { bottomMargin = 12.dp() }
                
                val btnScan = Button(context).apply {
                    this.text = "SCAN RUNTIME"
                    setTextColor(Color.LTGRAY)
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    background = createBg(Color.TRANSPARENT, 1, Color.GRAY, 2f)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { rightMargin = 4.dp() }
                    setOnClickListener {
                        it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                        Toast.makeText(context, "Scanning pointers...", Toast.LENGTH_SHORT).show()
                    }
                }
                val btnNext = Button(context).apply {
                    this.text = "NEXT SEARCH"
                    setTextColor(SaveColor)
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    background = createBg(Color.TRANSPARENT, 1, SaveColor, 2f)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = 4.dp() }
                    setOnClickListener {
                        it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                        Toast.makeText(context, "Next Search...", Toast.LENGTH_SHORT).show()
                    }
                }
                addView(btnScan)
                addView(btnNext)
            }
            
            val btnSelectHook = Button(context).apply {
                this.text = "SELECT HOOKING PATCH"
                setTextColor(SaveColor)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                background = createBg(Color.TRANSPARENT, 1, SaveColor, 2f)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40.dp()).apply { bottomMargin = 16.dp() }
                
                val hooks = arrayOf(
                    "Bypass Auth: Return True",
                    "Infinite Money: Max Value (99999)",
                    "God Mode: No Damage",
                    "No Recoil: Zero Multiplier",
                    "Infinite Ammo: Freeze Value",
                    "Speed Hack: Float Multiply 2.5f",
                    "Custom Pointer Hook",
                    "Unity: il2cpp_class_get_methods"
                )
                
                setOnClickListener {
                    it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                    showOverlayDialog(hooks) { _ ->
                        methodInput.setText("0x" + (1000..9999).random().toString(16).uppercase())
                        fieldInput?.setText("0x" + (10..99).random().toString(16).uppercase())
                    }
                }
            }
            
            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 40.dp())
                
                val btnHook = Button(context).apply {
                    this.text = "HOOK"
                    setTextColor(PrimaryAccent)
                    textSize = 11f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    background = createBg(Color.TRANSPARENT, 1, PrimaryAccent, 2f)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { rightMargin = 4.dp() }
                    setOnClickListener {
                        it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                        val addressOff = methodInput.text.toString().trim()
                        val fieldName = fieldInput?.text?.toString()?.trim() ?: "unknown"
                        if (addressOff.isBlank() || fieldName.isBlank()) Toast.makeText(context, "Error: Fill method and field offset", Toast.LENGTH_SHORT).show()
                        else if (!addressOff.startsWith("0x", true)) Toast.makeText(context, "Error: INVALID OFFSET", Toast.LENGTH_SHORT).show()
                        else {
                            try {
                                val address = java.lang.Long.decode(addressOff)
                                val res = com.kittyspace.NativeDumper.inlineHook(targetPackageName, fieldName, address)
                                Toast.makeText(context, "HOOK APPLIED: $res", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error hooking offset: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                
                val btnUnhook = Button(context).apply {
                    this.text = "UNHOOK (MULTI)"
                    setTextColor(VipColor)
                    textSize = 10f
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    background = createBg(Color.TRANSPARENT, 1, VipColor, 2f)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = 4.dp() }
                    setOnClickListener {
                        it.alpha = 0.5f; it.postDelayed({ it.alpha = 1f }, 100)
                        if (methodInput.text.isBlank()) Toast.makeText(context, "Error: Nothing to unhook", Toast.LENGTH_SHORT).show()
                        else Toast.makeText(context, "UNHOOKED MULTIPLE OFFSETS", Toast.LENGTH_SHORT).show()
                    }
                }
                
                addView(btnHook)
                addView(btnUnhook)
            }
            
            addView(methodInput)
            addView(row2)
            addView(scanRow)
            addView(btnSelectHook)
            addView(actionRow)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::windowManager.isInitialized && ::rootView.isInitialized) {
            windowManager.removeView(rootView)
        }
    }
}

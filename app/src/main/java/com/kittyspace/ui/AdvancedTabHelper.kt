package com.kittyspace.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.kittyspace.NativeDumper
import com.kittyspace.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object AdvancedTabHelper {

    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    fun createKittySpyTab(context: Context, service: KittySpyMenuService, targetPackageName: String, focusListener: (Boolean) -> Unit): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val searchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4.dp(context), 0, 4.dp(context))
        }

        val searchInput = EditText(context).apply {
            hint = "Search Class / Method"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 10f
            background = null
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnFocusChangeListener { _, hasFocus -> focusListener(hasFocus) }
        }

        val btnInspect = Button(context).apply {
            text = "INSPECTOR"
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 9f
            setBackgroundColor(Color.parseColor("#151515"))
        }

        val btnDump = Button(context).apply {
            text = "CLASSES"
            setTextColor(Color.parseColor("#FFB300"))
            textSize = 9f
            setBackgroundColor(Color.parseColor("#151515"))
        }

        searchRow.addView(searchInput)
        searchRow.addView(btnInspect)
        searchRow.addView(btnDump)

        // --- Class Browser Area ---
        val browserContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 250.dp(context)).apply { topMargin = 4.dp(context) }
            setBackgroundColor(Color.parseColor("#050A05"))
        }
        val classesScroll = ScrollView(context).apply { layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        val classesLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
        }
        classesScroll.addView(classesLayout)
        browserContainer.addView(classesScroll)

        // --- Runtime Inspector Area ---
        val inspectorContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 250.dp(context)).apply { topMargin = 4.dp(context) }
            setBackgroundColor(Color.parseColor("#0A0A0A"))
            visibility = View.GONE
        }
        
        val inspectorToolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
        }
        
        var isInspecting = false
        var isPaused = false
        var process: Process? = null
        val logEvents = CopyOnWriteArrayList<String>()
        
        val btnPause = Button(context).apply { text = "PAUSE"; textSize = 8f; setBackgroundColor(Color.DKGRAY); setTextColor(Color.WHITE) }
        val btnClear = Button(context).apply { text = "CLEAR"; textSize = 8f; setBackgroundColor(Color.DKGRAY); setTextColor(Color.WHITE) }
        
        inspectorToolbar.addView(btnPause)
        inspectorToolbar.addView(btnClear)
        
        val inspectorScroll = ScrollView(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
        val inspectorLog = TextView(context).apply {
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
        }
        inspectorScroll.addView(inspectorLog)
        
        inspectorContainer.addView(inspectorToolbar)
        inspectorContainer.addView(inspectorScroll)

        root.addView(searchRow)
        root.addView(browserContainer)
        root.addView(inspectorContainer)

        // --- Logic ---
        
        val handler = Handler(Looper.getMainLooper())
        val updateRunnable = object : Runnable {
            override fun run() {
                if (isInspecting && !isPaused) {
                    val sb = java.lang.StringBuilder()
                    val limit = if (logEvents.size > 100) logEvents.size - 100 else 0
                    for (i in limit until logEvents.size) sb.append(logEvents[i]).append("\n")
                    inspectorLog.text = sb.toString()
                    inspectorScroll.post { inspectorScroll.fullScroll(View.FOCUS_DOWN) }
                }
                if (isInspecting) handler.postDelayed(this, 1000)
            }
        }

        btnInspect.setOnClickListener {
            isInspecting = !isInspecting
            if (isInspecting) {
                browserContainer.visibility = View.GONE
                inspectorContainer.visibility = View.VISIBLE
                btnInspect.alpha = 0.5f
                btnPause.text = "PAUSE"
                isPaused = false
                logEvents.clear()
                logEvents.add("[KittySpy] Live Event Tracing Started...")
                inspectorLog.text = "[KittySpy] Live Event Tracing Started...\n"
                handler.post(updateRunnable)
                
                Thread {
                    try {
                        process = Runtime.getRuntime().exec("logcat -v brief Unity:V UE4:V *:S")
                        val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                        var line: String?
                        while (isInspecting) {
                            line = reader.readLine()
                            if (line == null) break
                            if (!isPaused && line.isNotBlank()) {
                                if (line.contains("Update") || line.contains("FixedUpdate") || line.contains("LateUpdate")) continue
                                val msg = line.substringAfter("): ", line).substringAfter("] ", line).trim()
                                if (msg.length > 5) {
                                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                                    logEvents.add("[$time] Event Detected:\n-> $msg\n─────────────────────")
                                    if (logEvents.size > 200) logEvents.removeAt(0)
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }.start()
            } else {
                inspectorContainer.visibility = View.GONE
                browserContainer.visibility = View.VISIBLE
                btnInspect.alpha = 1f
                process?.destroy()
                handler.removeCallbacks(updateRunnable)
            }
        }
        
        btnPause.setOnClickListener {
            isPaused = !isPaused
            btnPause.text = if (isPaused) "RESUME" else "PAUSE"
        }
        btnClear.setOnClickListener {
            logEvents.clear()
            inspectorLog.text = ""
        }

        var fullDump = mutableListOf<String>()

        btnDump.setOnClickListener {
            Toast.makeText(context, "Dumping classes...", Toast.LENGTH_SHORT).show()
            classesLayout.removeAllViews()
            
            Thread {
                var apkPath = "unknown"
                try {
                    val targetInfo = context.packageManager.getApplicationInfo(targetPackageName, 0)
                    if (targetInfo.publicSourceDir != null) apkPath = targetInfo.publicSourceDir
                } catch (e: Exception) {}
                
                val dumped = NativeDumper.dumpGameFunctions(targetPackageName, apkPath)
                fullDump = dumped.toMutableList()
                
                Handler(Looper.getMainLooper()).post {
                    renderClasses(context, classesLayout, fullDump, "", targetPackageName)
                }
            }.start()
        }

        searchInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                renderClasses(context, classesLayout, fullDump, s.toString().trim(), targetPackageName)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return root
    }

    private fun renderClasses(context: Context, container: LinearLayout, data: List<String>, filter: String, pkg: String) {
        container.removeAllViews()
        val query = filter.lowercase()

        var currentClassLayout: LinearLayout? = null
        var classMatch = false
        
        var methodsContainer: LinearLayout? = null
        var fieldsContainer: LinearLayout? = null
        
        var itemsAdded = 0

        for (line in data) {
            if (line.startsWith("[Class] ")) {
                val className = line.removePrefix("[Class] ")
                classMatch = query.isEmpty() || className.lowercase().contains(query)
                
                if (classMatch || query.isNotEmpty()) {
                    currentClassLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 4.dp(context), 0, 4.dp(context))
                        visibility = if (classMatch) View.VISIBLE else View.GONE
                    }
                    
                    val classTitle = TextView(context).apply {
                        text = "Class: $className"
                        setTextColor(Color.parseColor("#FFB300"))
                        textSize = 11f
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(0, 4.dp(context), 0, 4.dp(context))
                    }
                    currentClassLayout.addView(classTitle)
                    
                    val currentMethods = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8.dp(context), 0, 0, 0)
                        visibility = View.GONE
                    }
                    val currentFields = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(8.dp(context), 0, 0, 0)
                        visibility = View.GONE
                    }
                    
                    methodsContainer = currentMethods
                    fieldsContainer = currentFields
                    
                    currentClassLayout!!.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1); setBackgroundColor(Color.DKGRAY) })
                    currentClassLayout!!.addView(currentMethods)
                    currentClassLayout!!.addView(currentFields)
                    
                    classTitle.setOnClickListener {
                        val vis = if (currentMethods.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        currentMethods.visibility = vis
                        currentFields.visibility = vis
                    }
                    
                    container.addView(currentClassLayout)
                    itemsAdded++
                    if (itemsAdded > 150 && query.isEmpty()) break // Prevent lag if no filter
                }
            } else if (line.startsWith("  [Method] ") && currentClassLayout != null) {
                val methodName = line.removePrefix("  [Method] ")
                if (query.isEmpty() || classMatch || methodName.lowercase().contains(query)) {
                    currentClassLayout!!.visibility = View.VISIBLE
                    val mtv = createInspectableItem(context, "Method: $methodName", pkg)
                    methodsContainer?.addView(mtv)
                }
            } else if (line.startsWith("  [Field] ") && currentClassLayout != null) {
                val fieldName = line.removePrefix("  [Field] ")
                if (query.isEmpty() || classMatch || fieldName.lowercase().contains(query)) {
                    currentClassLayout!!.visibility = View.VISIBLE
                    val ftv = createInspectableItem(context, "Field: $fieldName", pkg, isField = true)
                    fieldsContainer?.addView(ftv)
                }
            }
        }
        
        if (itemsAdded == 0) {
            container.addView(TextView(context).apply { text = "No results found."; setTextColor(Color.GRAY) })
        }
    }

    private fun createInspectableItem(context: Context, text: String, pkg: String, isField: Boolean = false): View {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(Color.LTGRAY)
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4.dp(context), 0, 4.dp(context))
        }
        
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(8.dp(context), 0, 0, 4.dp(context))
        }
        
        var extOffset: Long? = null
        if (text.contains("Offset 0x") || text.contains("RVA 0x")) {
            try {
                val hexStr = text.substringAfter("0x").substringBefore(" ").trim()
                if (hexStr.isNotEmpty()) extOffset = java.lang.Long.decode("0x$hexStr")
            } catch (e: Exception){}
        }

        val paramInput = EditText(context).apply {
            hint = "Param (int)"
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.WHITE)
            textSize = 9f
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(60.dp(context), ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = 8.dp(context) }
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
            setBackgroundColor(Color.parseColor("#222222"))
            visibility = if (isField) View.GONE else View.VISIBLE
        }

        fun addBtn(name: String, col: Int, action: ()->Unit) {
            val b = TextView(context).apply {
                this.text = "[$name]"
                setTextColor(col)
                textSize = 9f
                setPadding(0,0,16.dp(context),0)
                setOnClickListener { action() }
            }
            actionRow.addView(b)
        }
        
        actionRow.addView(paramInput)

        addBtn("Call", Color.parseColor("#00BFFF")) {
            if (extOffset != null) {
                // In a real hooking engine, you'd pass the param to NativeDumper natively.
                val param = paramInput.text.toString().trim()
                val res = NativeDumper.invokeGameFunction(extOffset)
                Toast.makeText(context, "$res (Param: ${if(param.isEmpty()) "None" else param})", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No address found to call", Toast.LENGTH_SHORT).show()
            }
        }
        addBtn("Hook", Color.parseColor("#FF3333")) {
            Toast.makeText(context, "Hook queued for $text", Toast.LENGTH_SHORT).show()
        }
        addBtn("Inspect", Color.parseColor("#FFB300")) {
            Toast.makeText(context, "Inspecting $text", Toast.LENGTH_SHORT).show()
        }

        tv.setOnClickListener {
            actionRow.visibility = if (actionRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        
        root.addView(tv)
        root.addView(actionRow)
        return root
    }

    fun createSocialTab(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            gravity = Gravity.CENTER
        }
        
        fun btn(title: String, url: String, col: Int, iconResId: Int) {
            val b = Button(context).apply {
                text = title
                setTextColor(col)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                try {
                    setCompoundDrawablesWithIntrinsicBounds(iconResId, 0, 0, 0)
                } catch(e: Exception){}
                compoundDrawablePadding = 8.dp(context)
                gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                setPadding(24.dp(context), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 50.dp(context)).apply { bottomMargin = 8.dp(context) }
                setBackgroundColor(Color.parseColor("#151515"))
                setOnClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context.startActivity(intent) } catch (e: Exception){}
                }
            }
            root.addView(b)
        }
        
        val header = TextView(context).apply {
            text = "CONTACT & COMMUNITY"
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24.dp(context))
        }
        root.addView(header)
        
        btn("Contact Gmail", "mailto:l0rdsilver.703@gmail.com", Color.WHITE, R.drawable.ic_gmail)
        btn("Join Telegram", "https://t.me/greenpythonmodsLSV", Color.parseColor("#00BFFF"), R.drawable.ic_telegram)
        btn("Subscribe on YouTube", "https://youtube.com/@lordsilver77?si=AD_7tVySuNsTEmQ7", Color.parseColor("#FF0000"), R.drawable.ic_youtube)
        
        return root
    }
}

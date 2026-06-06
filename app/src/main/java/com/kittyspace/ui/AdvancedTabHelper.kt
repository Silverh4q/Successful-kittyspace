package com.kittyspace.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

data class CachedMethod(
    val lineStr: String,
    val methodName: String,
    val isField: Boolean
)

data class CachedClass(
    val lineStr: String,
    val className: String,
    val items: MutableList<CachedMethod> = mutableListOf()
)

object AdvancedTabHelper {

    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    private var runtimeCache = mutableListOf<CachedClass>()

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
                    while (isInspecting) {
                        try {
                            if (!isPaused) {
                                val events = NativeDumper.pollHookEvents()
                                for (e in events) {
                                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                                    logEvents.add("[$time] Event Detected:\n-> $e\n─────────────────────")
                                    if (logEvents.size > 200) logEvents.removeAt(0)
                                }
                            }
                            Thread.sleep(500)
                        } catch (e: Exception) {
                            Thread.sleep(1000)
                        }
                    }
                }.start()
            } else {
                inspectorContainer.visibility = View.GONE
                browserContainer.visibility = View.VISIBLE
                btnInspect.alpha = 1f
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
        
        val btnSaveLog = Button(context).apply { text = "SAVE LOG"; textSize = 8f; setBackgroundColor(Color.DKGRAY); setTextColor(Color.WHITE) }
        inspectorToolbar.addView(btnSaveLog)
        btnSaveLog.setOnClickListener {
            try {
                val dir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "KITTYSPY-INSPECTOR")
                if (!dir.exists()) dir.mkdirs()
                val f = java.io.File(dir, "RuntimeKittyspy.py")
                f.writeText(logEvents.joinToString("\n"))
                Toast.makeText(context, "Log saved to: ${f.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        var fullDump = mutableListOf<String>()

        btnDump.setOnClickListener {
            Toast.makeText(context, "Building Runtime Cache...", Toast.LENGTH_SHORT).show()
            classesLayout.removeAllViews()
            
            Thread {
                var apkPath = "unknown"
                try {
                    val targetInfo = context.packageManager.getApplicationInfo(targetPackageName, 0)
                    if (targetInfo.publicSourceDir != null) apkPath = targetInfo.publicSourceDir
                } catch (e: Exception) {}
                
                val dumped = NativeDumper.dumpGameFunctions(targetPackageName, apkPath)
                
                val tempCache = mutableListOf<CachedClass>()
                var currentClass: CachedClass? = null
                
                for (line in dumped) {
                    if (line.startsWith("[Class] ")) {
                        val className = line.removePrefix("[Class] ")
                        currentClass = CachedClass(line, className)
                        tempCache.add(currentClass)
                    } else if (line.startsWith("  [Method] ") && currentClass != null) {
                        currentClass.items.add(CachedMethod(line, line.substringAfter("] "), false))
                    } else if (line.startsWith("  [Field] ") && currentClass != null) {
                        currentClass.items.add(CachedMethod(line, line.substringAfter("] "), true))
                    }
                }
                
                runtimeCache = tempCache
                
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Cache Ready! ${runtimeCache.size} classes loaded.", Toast.LENGTH_SHORT).show()
                    renderClasses(context, classesLayout, "", targetPackageName, focusListener)
                }
            }.start()
        }

        var searchRunnable: Runnable? = null
        val handlerSearch = Handler(Looper.getMainLooper())

        searchInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handlerSearch.removeCallbacks(it) }
                searchRunnable = Runnable {
                    renderClasses(context, classesLayout, s.toString().trim(), targetPackageName, focusListener)
                }
                handlerSearch.postDelayed(searchRunnable!!, 300)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        return root
    }

    private var renderJob: kotlinx.coroutines.Job? = null

    private fun renderClasses(context: Context, container: LinearLayout, filter: String, pkg: String, focusListener: (Boolean) -> Unit) {
        renderJob?.cancel()
        renderJob = CoroutineScope(Dispatchers.Main).launch {
            val query = filter.lowercase()
            
            if (runtimeCache.isEmpty()) {
                container.removeAllViews()
                container.addView(TextView(context).apply { text = "Click CLASSES to load cache."; setTextColor(Color.GRAY) })
                return@launch
            }
            
            val filteredNodes = withContext(Dispatchers.IO) {
                val result = mutableListOf<String>()
                var itemsAddedNum = 0
                
                for (cls in runtimeCache) {
                    val classMatch = query.isEmpty() || cls.className.lowercase().contains(query)
                    var addedClass = false
                    
                    if (classMatch) {
                        result.add(cls.lineStr)
                        addedClass = true
                        itemsAddedNum++
                    }
                    
                    for (item in cls.items) {
                        if (query.isEmpty() || classMatch || item.methodName.lowercase().contains(query)) {
                            if (!addedClass) {
                                result.add(cls.lineStr)
                                addedClass = true
                                itemsAddedNum++
                            }
                            result.add(item.lineStr)
                        }
                    }
                    if (itemsAddedNum > 100 && query.isEmpty()) break
                }
                result
            }
            
            container.removeAllViews()
            
            var currentClassLayout: LinearLayout? = null
            var methodsContainer: LinearLayout? = null
            var fieldsContainer: LinearLayout? = null
            var itemsAdded = 0

            for (line in filteredNodes) {
                if (line.startsWith("[Class] ")) {
                    val className = line.removePrefix("[Class] ")
                    currentClassLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, 4.dp(context), 0, 4.dp(context))
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
                } else if (line.startsWith("  [Method] ") && currentClassLayout != null) {
                    val methodName = line.removePrefix("  [Method] ")
                    currentClassLayout!!.visibility = View.VISIBLE
                    val mtv = createInspectableItem(context, "Method: $methodName", pkg, false, focusListener)
                    methodsContainer?.addView(mtv)
                } else if (line.startsWith("  [Field] ") && currentClassLayout != null) {
                    val fieldName = line.removePrefix("  [Field] ")
                    currentClassLayout!!.visibility = View.VISIBLE
                    val ftv = createInspectableItem(context, "Field: $fieldName", pkg, true, focusListener)
                    fieldsContainer?.addView(ftv)
                }
            }
            
            if (itemsAdded == 0) {
                container.addView(TextView(context).apply { text = "No results found."; setTextColor(Color.GRAY) })
            }
        }
    }

    private fun createInspectableItem(context: Context, text: String, pkg: String, isField: Boolean = false, focusListener: (Boolean) -> Unit): View {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(Color.LTGRAY)
            textSize = 9f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4.dp(context), 0, 4.dp(context))
        }
        
        val actionRowScroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp(context), 0, 0, 4.dp(context))
        }
        actionRowScroll.addView(actionRow)
        
        var extOffset: Long? = null
        var pCount = 0
        if (text.contains("ParamCount")) {
            try { pCount = text.substringAfter("ParamCount ").substringBefore(" ").toInt() } catch(e:Exception){}
        }
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
            visibility = if (isField || pCount == 0) View.GONE else View.VISIBLE
            setOnFocusChangeListener { _, hasFocus -> focusListener(hasFocus) }
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
                val param = paramInput.text.toString().trim()
                val pType = if (param.isNotEmpty()) "int" else ""
                val res = NativeDumper.invokeGameFunction(extOffset, pType, param)
                Toast.makeText(context, "$res (Param: ${if(param.isEmpty()) "None" else param})", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No address found to call", Toast.LENGTH_SHORT).show()
            }
        }
        
        if (!isField) {
            addBtn("Hook", Color.parseColor("#FF3333")) {
                if (extOffset != null) {
                    val methodName = text.substringAfter("Method: ").substringBefore(" :")
                    val res = NativeDumper.inlineHook(pkg, methodName, extOffset)
                    Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Cannot Hook without RVA/Offset", Toast.LENGTH_SHORT).show()
                }
            }
            
            addBtn("Inspect", Color.parseColor("#FFB300")) {
                // Show metadata as toast to prevent blocking logic
                val methodName = text.substringAfter("Method: ").substringBefore(" :")
                val rvaStr = if (extOffset != null) "\nRVA: 0x${java.lang.Long.toHexString(extOffset!!)}" else ""
                val msg = "Class/Context: $pkg\nMethod: $methodName$rvaStr\nParameters: $pCount"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
        
        if (!isField && extOffset != null) {
            addBtn("Copy RVA", Color.LTGRAY) {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("RVA", "0x" + java.lang.Long.toHexString(extOffset!!)))
                Toast.makeText(context, "RVA Copied", Toast.LENGTH_SHORT).show()
            }
            addBtn("Copy Offset", Color.LTGRAY) {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("Offset", "0x" + java.lang.Long.toHexString(extOffset!!)))
                Toast.makeText(context, "Offset Copied", Toast.LENGTH_SHORT).show()
            }
            addBtn("Copy Address", Color.LTGRAY) {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("Address", "0x" + java.lang.Long.toHexString(extOffset!!)))
                Toast.makeText(context, "Address Copied", Toast.LENGTH_SHORT).show()
            }
        }

        tv.setOnClickListener {
            actionRowScroll.visibility = if (actionRowScroll.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        
        root.addView(tv)
        root.addView(actionRowScroll)
        return root
    }

    fun createSocialTab(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            gravity = Gravity.CENTER
        }
        
        val logoImage = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(96.dp(context), 96.dp(context)).apply { bottomMargin = 16.dp(context); gravity = Gravity.CENTER }
            try {
                setImageResource(R.mipmap.ic_launcher)
            } catch (e: Exception) {}
        }
        root.addView(logoImage)
        
        fun btn(title: String, url: String, col: Int, iconResId: Int) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 50.dp(context)).apply { bottomMargin = 8.dp(context) }
                setBackgroundColor(Color.parseColor("#151515"))
                setPadding(24.dp(context), 0, 0, 0)
                isClickable = true
                setOnClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context.startActivity(intent) } catch (e: Exception){}
                }
            }

            val icon = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(24.dp(context), 24.dp(context)).apply { rightMargin = 16.dp(context) }
                try {
                    setImageResource(iconResId)
                } catch (e: Exception) {}
            }

            val textV = TextView(context).apply {
                text = title
                setTextColor(col)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }

            row.addView(icon)
            row.addView(textV)
            root.addView(row)
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

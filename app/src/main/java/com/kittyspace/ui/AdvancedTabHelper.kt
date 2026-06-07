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
import java.util.concurrent.CopyOnWriteArrayList

data class CachedMethod(
    val lineStr: String,
    val methodName: String,
    val isField: Boolean,
    val rva: String = "0x0",
    val offset: String = "0x0",
    val pCount: Int = 0,
    var callCount: Int = 0,
    var isWatched: Boolean = false
)

data class CachedClass(
    val lineStr: String,
    val className: String,
    val items: MutableList<CachedMethod> = mutableListOf()
)

object AdvancedTabHelper {

    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    private var runtimeCache = mutableListOf<CachedClass>()
    private var engineDetected = "Parsing..."
    private var isConnected = "Disconnected"
    private var methodCount = 0
    private var fieldCount = 0

    fun createKittySpyTab(context: Context, service: KittySpyMenuService, targetPackageName: String, focusListener: (Boolean) -> Unit): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050A05"))
            setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
        }

        // --- Header ---
        val headerText = TextView(context).apply {
            text = "-----------------------------------\nKITTYSPY\n-----------------------------------"
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8.dp(context))
        }
        root.addView(headerText)
        
        val splitPane = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(splitPane)
        
        val leftPane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            setPadding(0, 0, 4.dp(context), 0)
        }
        splitPane.addView(leftPane)
        
        val rightPane = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f)
            setPadding(4.dp(context), 0, 0, 0)
        }
        splitPane.addView(rightPane)
        
        val statsText = TextView(context).apply {
            setTextColor(Color.LTGRAY)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 8.dp(context))
            text = "ENGINE:\n$engineDetected\n\nSTATUS:\n$isConnected\n\nCLASSES:\n${runtimeCache.size}\n\nMETHODS:\n$methodCount\n\nFIELDS:\n$fieldCount\n-----------------------------------"
        }
        leftPane.addView(statsText)

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 8.dp(context))
        }

        val btnLoad = Button(context).apply {
            text = "LOAD CLASSES"
            setTextColor(Color.parseColor("#FFB300"))
            textSize = 9f
            setBackgroundColor(Color.parseColor("#151515"))
        }
        val btnSave = Button(context).apply {
            text = "SAVE DUMP"
            setTextColor(Color.WHITE)
            textSize = 9f
            setBackgroundColor(Color.parseColor("#151515"))
        }

        actionsRow.addView(btnLoad)
        actionsRow.addView(btnSave)
        leftPane.addView(actionsRow)

        val searchInput = EditText(context).apply {
            hint = "Search (Instant)"
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 10f
            background = null
            setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
            setOnFocusChangeListener { _, hasFocus -> focusListener(hasFocus) }
            setBackgroundColor(Color.parseColor("#111111"))
        }
        leftPane.addView(searchInput)
        
        val titleList = TextView(context).apply {
            text = "[CLASS LIST]"
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 8.dp(context))
        }
        rightPane.addView(titleList)

        val contentScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(0, 0, 0, 8.dp(context))
        }
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentScroll.addView(contentLayout)
        rightPane.addView(contentScroll)
        
        val consoleLog = TextView(context).apply {
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 8f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 0)
            text = "KITTYSPY LOGS\n-----------------------------------\n[INFO] Waiting for action..."
        }
        rightPane.addView(consoleLog)
        
        fun appendLog(msg: String) {
            Handler(Looper.getMainLooper()).post {
                consoleLog.text = "${consoleLog.text}\n[INFO] $msg"
                val lines = consoleLog.text.split("\n")
                if (lines.size > 14) {
                    consoleLog.text = "KITTYSPY LOGS\n-----------------------------------\n" + lines.takeLast(10).joinToString("\n")
                }
            }
        }
        
        fun updateStats() {
            statsText.text = "ENGINE:\n$engineDetected\n\nSTATUS:\n$isConnected\n\nCLASSES:\n${runtimeCache.size}\n\nMETHODS:\n$methodCount\n\nFIELDS:\n$fieldCount\n-----------------------------------"
        }

        // Save Cache
        btnSave.setOnClickListener {
            if (runtimeCache.isEmpty()) {
                Toast.makeText(context, "Cache empty!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val dir = java.io.File(android.os.Environment.getExternalStorageDirectory(), "KITTYSPY-CLASSES")
                if (!dir.exists()) dir.mkdirs()
                val f = java.io.File(dir, "LoadedClasses.txt")
                val sb = StringBuilder()
                for (c in runtimeCache) {
                    sb.append(c.lineStr).append("\n")
                    for (i in c.items) {
                        sb.append(i.lineStr).append("\n")
                    }
                }
                f.writeText(sb.toString())
                appendLog("Dump saved to: ${f.absolutePath}")
            } catch (e: Exception) {
                appendLog("Save failed: ${e.message}")
            }
        }
        
        // Load Cache
        btnLoad.setOnClickListener {
            appendLog("Detecting Engine...")
            contentLayout.removeAllViews()
            
            Thread {
                var apkPath = "unknown"
                try {
                    val targetInfo = context.packageManager.getApplicationInfo(targetPackageName, 0)
                    if (targetInfo.publicSourceDir != null) apkPath = targetInfo.publicSourceDir
                } catch (e: Exception) {}
                
                val dumped = NativeDumper.dumpGameFunctions(targetPackageName, apkPath)
                
                val tempCache = mutableListOf<CachedClass>()
                var currentClass: CachedClass? = null
                var mCount = 0
                var fCount = 0
                
                for (line in dumped) {
                    if (line.startsWith("[Class] ")) {
                        val className = line.removePrefix("[Class] ")
                        currentClass = CachedClass(line, className)
                        tempCache.add(currentClass)
                    } else if (line.startsWith("  [Method] ") && currentClass != null) {
                        var mName = line.substringAfter("] ").substringBefore(" :")
                        var pCount = 0
                        var offset = "0x0"
                        var rva = "0x0"
                        
                        if (line.contains("ParamCount")) {
                            try { pCount = line.substringAfter("ParamCount ").substringBefore(" ").toInt() } catch(e:Exception){}
                        }
                        if (line.contains("Offset 0x") || line.contains("RVA 0x")) {
                            try {
                                if (line.contains("RVA ")) rva = line.substringAfter("RVA 0x").substringBefore(" ").trim()
                                if (line.contains("Offset ")) offset = line.substringAfter("Offset 0x").substringBefore(" ").trim()
                            } catch (e: Exception){}
                        }
                        
                        currentClass.items.add(CachedMethod(line, mName, false, rva, offset, pCount))
                        mCount++
                    } else if (line.startsWith("  [Field] ") && currentClass != null) {
                        currentClass.items.add(CachedMethod(line, line.substringAfter("] "), true))
                        fCount++
                    }
                }
                
                runtimeCache = tempCache
                methodCount = mCount
                fieldCount = fCount
                engineDetected = if (tempCache.isNotEmpty()) "UNITY" else "UNKNOWN"
                isConnected = "CONNECTED"
                
                Handler(Looper.getMainLooper()).post {
                    appendLog("Metadata cache created")
                    appendLog("${runtimeCache.size} classes loaded")
                    appendLog("$mCount methods loaded")
                    appendLog("$fCount fields loaded")
                    updateStats()
                    renderClasses(context, contentLayout, "", targetPackageName)
                }
            }.start()
        }

        var searchRunnable: Runnable? = null
        val handlerSearch = Handler(Looper.getMainLooper())

        searchInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { handlerSearch.removeCallbacks(it) }
                searchRunnable = Runnable {
                    renderClasses(context, contentLayout, s.toString().trim(), targetPackageName)
                }
                handlerSearch.postDelayed(searchRunnable!!, 300)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        // Background watch polling (silent, no active watcher spam)
        Thread {
            while (true) {
                Thread.sleep(2000)
                try {
                    val events = NativeDumper.pollHookEvents()
                    var newActivity = false
                    val eventMap = mutableMapOf<String, Int>()
                    for (e in events) {
                        val parts = e.split("|")
                        if (parts.size == 2) {
                            try {
                                eventMap[parts[0]] = parts[1].toInt()
                                newActivity = true
                            } catch(e:Exception){}
                        }
                    }
                    if (newActivity) {
                        Handler(Looper.getMainLooper()).post {
                            for ((m, c) in eventMap) {
                                appendLog("Watched Activity -> $m (Calls: $c)")
                            }
                        }
                    }
                } catch(e: Exception) {}
            }
        }.start()

        return root
    }

    private var renderJob: kotlinx.coroutines.Job? = null

    private fun renderClasses(context: Context, container: LinearLayout, filter: String, pkg: String) {
        renderJob?.cancel()
        renderJob = CoroutineScope(Dispatchers.Main).launch {
            val query = filter.lowercase()
            
            if (runtimeCache.isEmpty()) {
                container.removeAllViews()
                container.addView(TextView(context).apply { text = "[ Class List Empty ]\nClick LOAD CACHE."; setTextColor(Color.GRAY) })
                return@launch
            }
            
            // Search memory in background (Instant)
            val filteredNodes = withContext(Dispatchers.IO) {
                val result = mutableListOf<CachedClass>()
                var itemsAddedNum = 0
                
                for (cls in runtimeCache) {
                    val classMatch = query.isEmpty() || cls.className.lowercase().contains(query)
                    val matchingItems = mutableListOf<CachedMethod>()
                    
                    if (classMatch) {
                        matchingItems.addAll(cls.items)
                    } else {
                        for (item in cls.items) {
                            if (item.methodName.lowercase().contains(query)) {
                                matchingItems.add(item)
                            }
                        }
                    }
                    
                    if (classMatch || matchingItems.isNotEmpty()) {
                        result.add(CachedClass(cls.lineStr, cls.className, matchingItems))
                        itemsAddedNum += 1
                    }
                    
                    // Instant limit to prevent UI freezing
                    if (itemsAddedNum > 50) break
                }
                result
            }
            
            container.removeAllViews()
            var itemsAdded = 0
            
            for (cls in filteredNodes) {
                val classRow = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 4.dp(context), 0, 4.dp(context))
                }
                
                val classTitle = TextView(context).apply {
                    text = cls.className
                    setTextColor(Color.parseColor("#FFB300"))
                    textSize = 11f
                    typeface = Typeface.MONOSPACE
                    setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
                    setBackgroundColor(Color.parseColor("#151515"))
                }
                
                val childrenContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16.dp(context), 0, 0, 0)
                    visibility = View.GONE
                }
                
                // Add Class Details Segment
                val clsDetails = TextView(context).apply {
                    text = "-----------------------------------\nCLASS DETAILS\n-----------------------------------\nName:\n${cls.className}\n\nNamespace:\nUnknown\n\nMethods:\n${cls.items.count { !it.isField }}\n\nFields:\n${cls.items.count { it.isField }}\n-----------------------------------"
                    setTextColor(Color.LTGRAY)
                    textSize = 10f
                    typeface = Typeface.MONOSPACE
                    setPadding(0, 4.dp(context), 0, 4.dp(context))
                }
                childrenContainer.addView(clsDetails)
                
                for (item in cls.items) {
                    val v = createInspectableItem(context, item, cls.className, pkg)
                    childrenContainer.addView(v)
                }
                
                classTitle.setOnClickListener {
                    if (childrenContainer.visibility == View.VISIBLE) {
                        childrenContainer.visibility = View.GONE
                    } else {
                        childrenContainer.visibility = View.VISIBLE
                    }
                }
                
                classRow.addView(classTitle)
                classRow.addView(childrenContainer)
                container.addView(classRow)
                itemsAdded++
            }
            
            if (itemsAdded == 0) {
                container.addView(TextView(context).apply { text = "No results found."; setTextColor(Color.GRAY) })
            }
        }
    }

    private fun createInspectableItem(context: Context, item: CachedMethod, className: String, pkg: String): View {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 4.dp(context), 0, 4.dp(context)) }
        
        val tv = TextView(context).apply {
            text = if (item.isField) item.methodName else "${item.methodName}()"
            setTextColor(if (item.isField) Color.GRAY else Color.parseColor("#00FF00"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        
        val detailsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
            setBackgroundColor(Color.parseColor("#222222"))
        }
        
        val detailsText = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            if (item.isField) {
                text = "-----------------------------------\nFIELD DETAILS\n-----------------------------------\nClass:\n$className\n\nField:\n${item.methodName}\n\nType:\nUnknown\n\nOffset:\n0x${item.offset}\n-----------------------------------"
            } else {
                text = "-----------------------------------\nMETHOD DETAILS\n-----------------------------------\nClass:\n$className\n\nMethod:\n${item.methodName}()\n\nParameters:\n${item.pCount}\n\nReturn:\nUnknown\n\nRVA:\n0x${item.rva}\n\nOffset:\n0x${item.offset}\n\nState:\nLoaded\n-----------------------------------"
            }
        }
        detailsContainer.addView(detailsText)
        
        if (!item.isField) {
            val actionsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8.dp(context), 0, 0)
            }
            fun btn(label: String, action: ()->Unit) {
                val b = TextView(context).apply {
                    text = "[$label]"
                    setTextColor(Color.parseColor("#00BFFF"))
                    textSize = 10f
                    setPadding(0, 0, 16.dp(context), 0)
                    setOnClickListener { action() }
                }
                actionsRow.addView(b)
            }
            
            btn("Copy RVA") {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("RVA", "0x${item.rva}"))
                Toast.makeText(context, "Copied RVA", Toast.LENGTH_SHORT).show()
            }
            
            btn("Copy Offset") {
                val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clip.setPrimaryClip(android.content.ClipData.newPlainText("Offset", "0x${item.offset}"))
                Toast.makeText(context, "Copied Offset", Toast.LENGTH_SHORT).show()
            }
            
            btn("Watch") {
                var addr = 0L
                try { addr = item.offset.toLong(16) } catch(e:Exception){}
                NativeDumper.registerActiveInspector(addr, item.methodName) 
                item.isWatched = true
                Toast.makeText(context, "Added to Watched Methods", Toast.LENGTH_SHORT).show()
            }
            
            detailsContainer.addView(actionsRow)
        }
        
        tv.setOnClickListener {
            detailsContainer.visibility = if (detailsContainer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        root.addView(tv)
        root.addView(detailsContainer)
        return root
    }

    fun createSocialTab(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            gravity = Gravity.CENTER
        }
        
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

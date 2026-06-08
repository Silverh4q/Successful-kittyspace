package com.kittyspace.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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
import android.content.Intent
import java.util.concurrent.CopyOnWriteArrayList

data class LoadedMethod(
    val lineStr: String,
    val methodName: String,
    val isField: Boolean,
    val rva: String = "0x0",
    val offset: String = "0x0",
    val pCount: Int = 0,
    var callCount: Int = 0,
    var isWatched: Boolean = false
)

data class LoadedClass(
    val lineStr: String,
    val className: String,
    val items: MutableList<LoadedMethod> = mutableListOf()
)

object AdvancedTabHelper {

    private fun Int.dp(context: Context): Int = (this * context.resources.displayMetrics.density).toInt()

    private var runtimeList = mutableListOf<LoadedClass>()
    private var engineDetected = "Parsing..."
    private var isConnected = "Disconnected"
    private var methodCount = 0
    private var fieldCount = 0
    
    private val scope = CoroutineScope(Dispatchers.Main)

    fun createKittySpyTab(context: Context, service: KittySpyMenuService, targetPackageName: String, focusListener: (Boolean) -> Unit): View {
        val scrollViewRoot = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#050A05"))
            setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
        }
        scrollViewRoot.addView(root)

        // --- Header ---
        val headerText = TextView(context).apply {
            text = "-----------------------------------\nKITTYSPY RUNTIME V2\n-----------------------------------"
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8.dp(context))
        }
        root.addView(headerText)

        // --- Top Buttons Layer ---
        val topActionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16.dp(context))
        }

        val btnLoadClasses = Button(context).apply {
            text = "LOAD CLASSES"
            setTextColor(Color.parseColor("#FFB300"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = 4.dp(context) }
            setBackgroundColor(Color.parseColor("#151515"))
        }
        
        val btnInspect = Button(context).apply {
            text = "INSPECT"
            setTextColor(Color.parseColor("#00BFFF"))
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = 4.dp(context) }
            setBackgroundColor(Color.parseColor("#151515"))
        }

        topActionsRow.addView(btnLoadClasses)
        topActionsRow.addView(btnInspect)
        root.addView(topActionsRow)

        // --- Dynamic Container Viewport ---
        val containerFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(containerFrame)
        
        // Let's create the sub-layouts: 1. Load Classes View, 2. Inspector View
        var loadClassesFunc: (() -> Unit)? = null
        val classesView = createClassesViewport(context, targetPackageName, focusListener) { startDump ->
            loadClassesFunc = startDump
        }
        val inspectView = createInspectViewport(context, targetPackageName, focusListener)
        
        containerFrame.addView(classesView)
        containerFrame.addView(inspectView)
        
        // Initial state
        classesView.visibility = View.VISIBLE
        inspectView.visibility = View.GONE

        var dataLoaded = false
        btnLoadClasses.setOnClickListener {
            classesView.visibility = View.VISIBLE
            inspectView.visibility = View.GONE
            if (!dataLoaded) {
                dataLoaded = true
                loadClassesFunc?.invoke()
            }
        }
        
        btnInspect.setOnClickListener {
            classesView.visibility = View.GONE
            inspectView.visibility = View.VISIBLE
        }

        return scrollViewRoot
    }

    private fun createClassesViewport(context: Context, pkg: String, focusListener: (Boolean) -> Unit, registerDumpFunc: (() -> Unit) -> Unit): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val btnSave = Button(context).apply {
            text = "SAVE CURRENT VIEW TO STORAGE"
            setTextColor(Color.WHITE)
            textSize = 10f
            setBackgroundColor(Color.parseColor("#222222"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8.dp(context) }
        }
        root.addView(btnSave)

        val searchCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8.dp(context) }
        }
        
        val searchInputRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        
        val searchInput = EditText(context).apply {
            hint = "Query..."
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 12f
            background = null
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnFocusChangeListener { _, hasFocus -> focusListener(hasFocus) }
        }
        
        // Use an actual Button for searching
        val btnSearch = Button(context).apply {
            text = "SEARCH"
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 10f
            setBackgroundColor(Color.parseColor("#222222"))
        }

        searchInputRow.addView(searchInput)
        searchInputRow.addView(btnSearch)
        searchCard.addView(searchInputRow)
        
        val optionsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8.dp(context), 0, 0) }
        
        val cbSearchAll = CheckBox(context).apply { text = "All Classes"; setTextColor(Color.LTGRAY); textSize=10f; isChecked=true }
        val cbSearchTarget = CheckBox(context).apply { text = "Target Class"; setTextColor(Color.LTGRAY); textSize=10f }
        
        cbSearchAll.setOnCheckedChangeListener { _, isChecked -> if(isChecked) cbSearchTarget.isChecked = false }
        cbSearchTarget.setOnCheckedChangeListener { _, isChecked -> if(isChecked) cbSearchAll.isChecked = false }
        
        val targetClassInput = EditText(context).apply {
            hint = "Class name"
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            visibility = View.GONE
            setOnFocusChangeListener { _, hasFocus -> focusListener(hasFocus) }
        }
        
        cbSearchTarget.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { cbSearchAll.isChecked = false; targetClassInput.visibility = View.VISIBLE }
            else { targetClassInput.visibility = View.GONE }
        }
        
        optionsRow.addView(cbSearchAll)
        optionsRow.addView(cbSearchTarget)
        optionsRow.addView(targetClassInput)
        searchCard.addView(optionsRow)
        
        root.addView(searchCard)

        val statusText = TextView(context).apply {
            text = "Ready. Click Dump to start parsing."
            setTextColor(Color.parseColor("#FFB300"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 8.dp(context))
        }
        root.addView(statusText)

        val btnStartDump = Button(context).apply {
            text = "START RUNTIME DUMP"
            setTextColor(Color.WHITE)
            textSize = 11f
            setBackgroundColor(Color.parseColor("#008800"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8.dp(context) }
        }
        root.addView(btnStartDump)

        val listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)
        
        val btnClear = Button(context).apply {
            text = "CLEAR RESULTS"
            setTextColor(Color.RED)
            textSize = 10f
            setBackgroundColor(Color.parseColor("#151515"))
        }
        root.addView(btnClear)
        
        var currentRenderedList = mutableListOf<LoadedClass>()

        fun renderUi(items: List<LoadedClass>, updateStatus: Boolean = true) {
            currentRenderedList = items.toMutableList()
            listContainer.removeAllViews()
            var totalAdded = 0
            
            for (cls in items) {
                val tvClass = TextView(context).apply {
                    text = "CLASS: ${cls.className}"
                    setTextColor(Color.parseColor("#00FF41"))
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    setPadding(4.dp(context), 8.dp(context), 4.dp(context), 2.dp(context))
                    setOnLongClickListener {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clip.setPrimaryClip(android.content.ClipData.newPlainText("Class", cls.className))
                        Toast.makeText(context, "Copied Class: ${cls.className}", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                listContainer.addView(tvClass)
                
                for (item in cls.items) {
                    val tvItem = TextView(context).apply {
                        text = if(item.isField) "  |-FIELD: ${item.methodName} | 0x${item.offset}" else "  |-METHOD: ${item.methodName}() | 0x${item.rva}"
                        setTextColor(if(item.isField) Color.LTGRAY else Color.parseColor("#00FF41"))
                        textSize = 10f
                        typeface = Typeface.MONOSPACE
                        setPadding(12.dp(context), 2.dp(context), 4.dp(context), 2.dp(context))
                        setOnLongClickListener {
                            val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clip.setPrimaryClip(android.content.ClipData.newPlainText("Item", if(item.isField) item.offset else item.rva))
                            Toast.makeText(context, "Copied Val", Toast.LENGTH_SHORT).show()
                            true
                        }
                    }
                    listContainer.addView(tvItem)
                    totalAdded++
                }
            }
            if(updateStatus) statusText.text = "$totalAdded results found."
        }
        
        fun loadEntireGameData() {
            statusText.text = "Searching... parsing game data..."
            listContainer.removeAllViews()
            Thread {
                var apkPath = "unknown"
                try {
                    val targetInfo = context.packageManager.getApplicationInfo(pkg, 0)
                    if (targetInfo.publicSourceDir != null) apkPath = targetInfo.publicSourceDir
                } catch (e: Exception) {}
                
                val dumped = NativeDumper.dumpGameFunctions(pkg, apkPath)
                val tempData = mutableListOf<LoadedClass>()
                var currentClass: LoadedClass? = null
                
                for (line in dumped) {
                    if (line.startsWith("[Class] ")) {
                        val className = line.removePrefix("[Class] ")
                        currentClass = LoadedClass(line, className)
                        tempData.add(currentClass)
                    } else if (line.startsWith("  [Method] ") && currentClass != null) {
                        val mName = line.substringAfter("] ").substringBefore(" :")
                        var rva = "0x0"
                        if (line.contains("RVA ")) rva = line.substringAfter("RVA 0x").substringBefore(" ").trim()
                        currentClass.items.add(LoadedMethod(line, mName, false, rva))
                    } else if (line.startsWith("  [Field] ") && currentClass != null) {
                        val parsedName = line.substringAfter("] ")
                        var offset = "0x0"
                        if (parsedName.contains("Offset 0x")) {
                            offset = parsedName.substringAfter("Offset 0x").substringBefore(" ").trim()
                        }
                        currentClass.items.add(LoadedMethod(line, parsedName, true, "0x0", offset))
                    }
                }
                
                tempData.sortBy { it.className }
                runtimeList = tempData
                engineDetected = if (tempData.isNotEmpty()) "UNITY" else "UNKNOWN" // Simple fallback
                
                Handler(Looper.getMainLooper()).post {
                    renderUi(runtimeList, false)
                    statusText.text = "Loaded active current classes of the game (${runtimeList.size} classes)"
                }
            }.start()
        }

        btnSearch.setOnClickListener {
            val query = searchInput.text.toString().trim().lowercase()
            if (query.isEmpty()) return@setOnClickListener
            
            val isSearchAll = cbSearchAll.isChecked
            val tClass = targetClassInput.text.toString().trim().lowercase()
            
            scope.launch {
                statusText.text = "Searching..."
                listContainer.removeAllViews()
                
                val results = mutableListOf<LoadedClass>()
                var totalFoundCount = 0
                
                withContext(Dispatchers.Default) {
                    if (isSearchAll) {
                        var chunkCtr = 0
                        for (cls in runtimeList) {
                            chunkCtr++
                            if (chunkCtr % 50 == 0) delay(5) // 5ms rest to prevent freezing
                            
                            val matches = cls.items.filter { it.methodName.lowercase().contains(query) }
                            if (matches.isNotEmpty() || cls.className.lowercase().contains(query)) {
                                val matchClass = LoadedClass(cls.lineStr, cls.className, matches.toMutableList())
                                if (matches.isEmpty()) matchClass.items.addAll(cls.items)
                                results.add(matchClass)
                                totalFoundCount += matchClass.items.size
                            }
                        }
                    } else {
                        // find class first
                        var chunkCtr = 0
                        var targetClsData: LoadedClass? = null
                        for (cls in runtimeList) {
                            chunkCtr++
                            if (chunkCtr % 50 == 0) delay(5)
                            if (cls.className.lowercase().contains(tClass)) {
                                targetClsData = cls
                                break
                            }
                        }
                        if (targetClsData != null) {
                            val matches = targetClsData.items.filter { it.methodName.lowercase().contains(query) }
                            results.add(LoadedClass(targetClsData.lineStr, targetClsData.className, matches.toMutableList()))
                            totalFoundCount += matches.size
                        }
                    }
                }
                
                if (results.isEmpty()) {
                    statusText.text = "Not found."
                } else {
                    renderUi(results, true)
                }
            }
        }
        
        btnClear.setOnClickListener {
            // Revert back to full list
            renderUi(runtimeList, false)
            statusText.text = "Loaded active current classes of the game (${runtimeList.size} classes)"
        }
        
        btnSave.setOnClickListener {
            // generate save string
            val isFull = (currentRenderedList.size == runtimeList.size)
            val sb = StringBuilder()
            
            if (!isFull) {
                val query = searchInput.text.toString()
                sb.append("------------KITTYSPY-----------\n")
                sb.append("Searched results of ($query) from loaded classes of game $pkg\n\n")
            } else {
                sb.append("----------------KITTYSPY-------------\n")
                sb.append("KITTY SPY RUNTIME DUMPER FROM GAME $pkg\n\n")
            }
            
            for (cls in currentRenderedList) {
                if (engineDetected == "UNITY") {
                    sb.append("|-Classes:= ${cls.className}\n")
                    val fields = cls.items.filter { it.isField }
                    if (fields.isNotEmpty()) {
                        sb.append("|-FIELDS:\n")
                        for (f in fields) sb.append("|  |- ${f.methodName}\n")
                    }
                    val methods = cls.items.filter { !it.isField }
                    if (methods.isNotEmpty()) {
                        sb.append("|-Methods:=\n")
                        for (m in methods) {
                            sb.append("|- ${m.methodName}()\n")
                            sb.append("|-RVA:= ${m.rva}\n")
                        }
                    }
                } else {
                    sb.append("======================================================\n")
                    sb.append("CLASS: ${cls.className}\n")
                    sb.append("======================================================\n\n")
                    
                    val fields = cls.items.filter { it.isField }
                    if (fields.isNotEmpty()) {
                        sb.append("|-Properties:\n")
                        for (f in fields) {
                            sb.append("|  |- ${f.methodName}\n")
                            sb.append("|  |  |-Offset: 0x${f.offset}\n|\n")
                        }
                    }
                    val methods = cls.items.filter { !it.isField }
                    if (methods.isNotEmpty()) {
                        sb.append("|-Functions:\n|\n")
                        for (m in methods) {
                            sb.append("|  |- ${m.methodName}\n")
                            sb.append("|  |  |-RVA: 0x${m.rva}\n|\n")
                        }
                    }
                }
                sb.append("\n")
            }
            
            val intent = Intent(context, KittySpySaveActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("fileName", if (isFull) "KITTYSPY_LClasses.py" else "kittyspy_search.py")
            }
            KittySpySaveActivity.dataToSave = sb.toString()
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Cannot open save dialog.", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnStartDump.setOnClickListener {
            btnStartDump.visibility = View.GONE
            loadEntireGameData()
        }
        
        registerDumpFunc {
            btnStartDump.visibility = View.GONE
            loadEntireGameData()
        }

        return root
    }

    private fun createInspectViewport(context: Context, pkg: String, focusListener: (Boolean) -> Unit): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val header = TextView(context).apply {
            text = "-----------------------------------\nINSPECTOR\n-----------------------------------"
            setTextColor(Color.parseColor("#FFB300"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16.dp(context))
        }
        root.addView(header)
        
        val lblInput = TextView(context).apply { text = "Input RVA"; setTextColor(Color.LTGRAY); textSize = 10f }
        root.addView(lblInput)
        
        val rvaInput = EditText(context).apply {
            hint = "e.g. 0x153532C"
            setHintTextColor(Color.DKGRAY)
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 12f
            background = null
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(8.dp(context), 8.dp(context), 8.dp(context), 8.dp(context))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin=8.dp(context) }
            setOnFocusChangeListener { _, hasFocus -> focusListener(hasFocus) }
        }
        root.addView(rvaInput)
        
        val btnResolve = Button(context).apply {
            text = "RESOLVE"
            setTextColor(Color.WHITE)
            textSize = 11f
            setBackgroundColor(Color.parseColor("#222222"))
        }
        root.addView(btnResolve)
        
        val resultContainer = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(0, 16.dp(context), 0, 16.dp(context))
        }
        root.addView(resultContainer)
        
        val tvResultText = TextView(context).apply { 
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }
        resultContainer.addView(tvResultText)
        
        val actionsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8.dp(context), 0, 0) }
        val btnInspectWatch = Button(context).apply { text = "INSPECT"; setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#00BFFF")); textSize = 10f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin=4.dp(context) } }
        val btnClearWatch = Button(context).apply { text = "CLEAR"; setTextColor(Color.WHITE); setBackgroundColor(Color.RED); textSize = 10f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin=4.dp(context) } }
        actionsRow.addView(btnInspectWatch)
        actionsRow.addView(btnClearWatch)
        resultContainer.addView(actionsRow)
        
        val inspectedListText = TextView(context).apply {
            text = "-----------------------------------\nINSPECTED TARGETS\n"
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, 16.dp(context), 0, 0)
        }
        root.addView(inspectedListText)
        
        var resolvedTarget: LoadedMethod? = null
        var resolvedClass: LoadedClass? = null
        
        // Polling loop for active watches
        val watchedItems = mutableListOf<LoadedMethod>()
        
        fun updateWatchedListText() {
            val sb = StringBuilder("-----------------------------------\nINSPECTED TARGETS\n\n")
            for (w in watchedItems) {
                sb.append("${w.methodName}()\nCalls: ${w.callCount}\n\n")
            }
            inspectedListText.text = sb.toString()
        }

        Thread {
            while (true) {
                Thread.sleep(1000)
                try {
                    if (watchedItems.isEmpty()) continue
                    
                    val events = NativeDumper.pollHookEvents()
                    var changed = false
                    for (e in events) {
                        val parts = e.split("|")
                        if (parts.size == 2) {
                            val func = parts[0]
                            val callC = parts[1].toInt()
                            val match = watchedItems.find { it.methodName == func }
                            if (match != null && match.callCount != callC) {
                                match.callCount = callC
                                changed = true
                            }
                        }
                    }
                    if (changed) {
                        Handler(Looper.getMainLooper()).post { updateWatchedListText() }
                    }
                } catch(e: Exception) {}
            }
        }.start()
        
        btnResolve.setOnClickListener {
            val rvaInputStr = rvaInput.text.toString().trim()
            if (rvaInputStr.isEmpty()) {
                tvResultText.text = "Error: Input RVA cannot be empty."
                resultContainer.visibility = View.VISIBLE
                return@setOnClickListener
            }
            val rvaQ = rvaInputStr.lowercase().removePrefix("0x")
            
            if (rvaQ.toLongOrNull(16) == null) {
                tvResultText.text = "Error: Invalid RVA format. Must be hex."
                resultContainer.visibility = View.VISIBLE
                return@setOnClickListener
            }

            var foundC: LoadedClass? = null
            var foundM: LoadedMethod? = null
            
            for (cls in runtimeList) {
                for (item in cls.items) {
                    if (!item.isField && item.rva.lowercase() == rvaQ) {
                        foundM = item
                        foundC = cls
                        break
                    }
                }
                if (foundM != null) break
            }
            
            if (foundM != null && foundC != null) {
                resolvedClass = foundC
                resolvedTarget = foundM
                
                val engineStr = if (engineDetected == "UNITY") "Class:= ${foundC.className}\nMethod:\n${foundM.methodName}()\nRVA:\n0x${foundM.rva}" 
                                else "Class:= ${foundC.className}\nFunction:=\n${foundM.methodName}()\nRVA:=\n0x${foundM.rva}"
                
                tvResultText.text = "-----------------------------------\n$engineStr\n-----------------------------------"
                resultContainer.visibility = View.VISIBLE
            } else {
                Toast.makeText(context, "RVA Not Found", Toast.LENGTH_SHORT).show()
                resultContainer.visibility = View.GONE
            }
        }
        
        btnInspectWatch.setOnClickListener {
            val t = resolvedTarget
            if (t != null) {
                if (watchedItems.find { it.methodName == t.methodName } == null) {
                    watchedItems.add(t)
                    var addr = 0L
                    try { addr = t.offset.toLong(16) } catch(e:Exception){}
                    NativeDumper.registerActiveInspector(addr, t.methodName) 
                    Toast.makeText(context, "Inspect started for ${t.methodName}", Toast.LENGTH_SHORT).show()
                    updateWatchedListText()
                }
            }
        }
        
        btnClearWatch.setOnClickListener {
            watchedItems.clear()
            updateWatchedListText()
            tvResultText.text = ""
            resultContainer.visibility = View.GONE
        }

        return root
    }

    fun createSocialTab(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(context), 16.dp(context), 16.dp(context), 16.dp(context))
            gravity = Gravity.CENTER
        }
        
        fun btn(title: String, url: String, col: Int) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 50.dp(context)).apply { bottomMargin = 8.dp(context) }
                setBackgroundColor(Color.parseColor("#151515"))
                isClickable = true
                setOnClickListener {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context.startActivity(intent) } catch (e: Exception){}
                }
            }

            val textV = TextView(context).apply {
                text = title
                setTextColor(col)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
            }
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
        
        btn("Contact Gmail", "mailto:l0rdsilver.703@gmail.com", Color.WHITE)
        btn("Join Telegram", "https://t.me/+NqXoMXDCiYkyN2I8", Color.parseColor("#00BFFF"))
        btn("Subscribe on YouTube", "https://youtube.com/@lordsilver77?si=AD_7tVySuNsTEmQ7", Color.parseColor("#FF0000"))
        
        return root
    }

}

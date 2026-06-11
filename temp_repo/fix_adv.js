const fs = require('fs');

let t = `package com.kittyspace.ui

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
            setPadding(0, 0, 16.dp(context), 16.dp(context))
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
        
        val btnSearch = Button(context).apply {
            text = "Query."
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 10f
            setBackgroundColor(Color.parseColor("#222222"))
        }

        searchInputRow.addView(searchInput)
        searchInputRow.addView(btnSearch)
        searchCard.addView(searchInputRow)
        
        val optionsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 8.dp(context), 0, 0) }
        
        val cbSearchAll = CheckBox(context).apply { text = "Search All"; setTextColor(Color.LTGRAY); textSize=10f; isChecked=true }
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
            text = "Ready. Waiting for user query."
            setTextColor(Color.parseColor("#FFB300"))
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setPadding(0, 0, 0, 8.dp(context))
        }
        root.addView(statusText)

        val btnStartDump = Button(context).apply {
            text = "SELECT & DUMP"
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
                    text = "Class  \${cls.className}"
                    setTextColor(Color.parseColor("#00FF41"))
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    setPadding(4.dp(context), 8.dp(context), 4.dp(context), 2.dp(context))
                    setOnLongClickListener {
                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clip.setPrimaryClip(android.content.ClipData.newPlainText("Class", cls.className))
                        Toast.makeText(context, "Copied Class: \${cls.className}", Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                listContainer.addView(tvClass)
                
                for (item in cls.items) {
                    val itemLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                    }
                    val tvItem = TextView(context).apply {
                        text = if (item.isField) "  [FIELD] \${item.methodName} | 0x\${item.offset}" else "  [METHOD] \${item.methodName}()  RVA: \${item.rva}"
                        setTextColor(if(item.isField) Color.LTGRAY else Color.parseColor("#00FF41"))
                        textSize = 10f
                        typeface = Typeface.MONOSPACE
                        setPadding(12.dp(context), 2.dp(context), 4.dp(context), 2.dp(context))
                    }
                    itemLayout.addView(tvItem)
                    listContainer.addView(itemLayout)
                    totalAdded++
                }
            }
            if(updateStatus) {
                statusText.text = "Rendered \${items.size} classes, \$totalAdded members."
            }
        }

        fun executeSearch() {
            val q = searchInput.text.toString().trim().lowercase()
            if (q.isEmpty()) {
                renderUi(runtimeList, true)
                return
            }
            
            val filtered = mutableListOf<LoadedClass>()
            if (cbSearchTarget.isChecked) {
                val targetQ = targetClassInput.text.toString().trim().lowercase()
                if (targetQ.isNotEmpty()) {
                    val matchingClasses = runtimeList.filter { it.className.lowercase().contains(targetQ) }
                    for (c in matchingClasses) {
                        val matchingItems = c.items.filter { it.methodName.lowercase().contains(q) }.toMutableList()
                        if (matchingItems.isNotEmpty()) {
                            filtered.add(LoadedClass(c.lineStr, c.className, matchingItems))
                        }
                    }
                } else {
                    for (c in runtimeList) {
                        val matchingItems = c.items.filter { it.methodName.lowercase().contains(q) }.toMutableList()
                        if (matchingItems.isNotEmpty()) {
                            filtered.add(LoadedClass(c.lineStr, c.className, matchingItems))
                        }
                    }
                }
            } else {
                for (c in runtimeList) {
                    val classMatch = c.className.lowercase().contains(q)
                    val matchingItems = c.items.filter { it.methodName.lowercase().contains(q) }.toMutableList()
                    if (classMatch || matchingItems.isNotEmpty()) {
                        filtered.add(LoadedClass(c.lineStr, c.className, if(classMatch) c.items else matchingItems))
                    }
                }
            }
            
            renderUi(filtered, true)
        }

        btnSearch.setOnClickListener { executeSearch() }
        btnClear.setOnClickListener {
            searchInput.setText("")
            targetClassInput.setText("")
            renderUi(runtimeList, true)
        }
        
        btnSave.setOnClickListener {
            if (currentRenderedList.isEmpty()) {
                Toast.makeText(context, "Nothing to save.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(context, KittySpySaveActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            NativeDumper.lastRenderedDumpData = currentRenderedList
            context.startActivity(intent)
        }
        
        registerDumpFunc {
            statusText.text = "Initializing native JNI dumper for \$pkg..."
            scope.launch {
                withContext(Dispatchers.IO) {
                    try {
                        val filesDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)?.absolutePath ?: "/sdcard/Documents/KittyDumper"
                        val (p, e) = NativeDumper.analyzeAndDumpRuntime(pkg, filesDir)
                        val lines = p.split("\n")
                        
                        val engine = if(lines.find { it.contains("UNREAL") } != null) "UNREAL" else "UNITY"
                        engineDetected = engine
                        
                        val parsed = mutableListOf<LoadedClass>()
                        var currentClass: LoadedClass? = null
                        var mCount = 0
                        var fCount = 0
                        
                        for (line in lines) {
                            if (line.trim().isEmpty()) continue
                            
                            if (line.startsWith("Class:")) {
                                currentClass = LoadedClass(line, line.replace("Class:", "").trim(), mutableListOf())
                                parsed.add(currentClass)
                                continue
                            }
                            
                            if (currentClass != null) {
                                if (line.contains("RVA:") || line.contains("Offset:")) {
                                    val isM = line.contains("()")
                                    val rvaStart = line.indexOf("RVA:")
                                    val offStart = line.indexOf("Offset:")
                                    
                                    val namePart = line.substring(0, if(rvaStart > -1) rvaStart else offStart).trim().removeSuffix("()").removePrefix("- ")
                                    val rvaPart = if(rvaStart > -1) line.substring(rvaStart).trim().replace("RVA:", "").trim() else ""
                                    val offPart = if(offStart > -1) line.substring(offStart).trim().replace("Offset:", "").trim() else ""
                                    
                                    if(isM) mCount++ else fCount++
                                    
                                    currentClass.items.add(LoadedMethod(
                                        lineStr = line,
                                        methodName = namePart,
                                        isField = !isM,
                                        rva = rvaPart,
                                        offset = offPart
                                    ))
                                }
                            }
                        }
                        
                        runtimeList = parsed
                        methodCount = mCount
                        fieldCount = fCount
                        
                    } catch(e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                statusText.text = "Success. Engine: \$engineDetected. Classes: \${runtimeList.size}, Methods: \$methodCount, Fields: \$fieldCount"
                renderUi(runtimeList, false)
            }
        }
        
        btnStartDump.setOnClickListener {
            // Already injected via registerDumpFunc
            loadClassesFunc?.invoke()
        }
        
        return root
    }

    fun createInspectViewport(context: Context, pkg: String, focusListener: (Boolean) -> Unit): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        val header = TextView(context).apply {
            text = "-----------------------------------\nREAL-TIME INSPECTOR\n-----------------------------------"
            setTextColor(Color.parseColor("#FFB300"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16.dp(context))
        }
        root.addView(header)
        
        val lblInput = TextView(context).apply { text = "Target RVA: "; setTextColor(Color.LTGRAY); textSize = 10f }
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
        
        val watchedItems = mutableListOf<LoadedMethod>()
        
        fun updateWatchedListText() {
            val sb = StringBuilder("-----------------------------------\nINSPECTED TARGETS\n\n")
            for (w in watchedItems) {
                sb.append("\${w.methodName}()\nCalls: \${w.callCount}\n\n")
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
                
                val engineStr = if (engineDetected == "UNITY") "Class:= \${foundC.className}\nMethod:\n\${foundM.methodName}()\nRVA:\n0x\${foundM.rva}" 
                                else "Class:= \${foundC.className}\nFunction:=\n\${foundM.methodName}()\nRVA:=\n0x\${foundM.rva}"
                
                tvResultText.text = "-----------------------------------\n\$engineStr\n-----------------------------------"
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
                    Toast.makeText(context, "Inspect started for \${t.methodName}", Toast.LENGTH_SHORT).show()
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
`;
fs.writeFileSync('app/src/main/java/com/kittyspace/ui/AdvancedTabHelper.kt', t);

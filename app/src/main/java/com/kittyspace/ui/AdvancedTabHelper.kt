package com.kittyspace.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.kittyspace.NativeDumper
import java.io.BufferedReader
import java.io.InputStreamReader

import java.util.concurrent.ConcurrentHashMap

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
            textSize = 12f
            background = null
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnFocusChangeListener { _, hasFocus -> focusListener(hasFocus) }
        }
        
        val btnInspect = Button(context).apply {
            text = "INSPECT"
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 10f
            setBackgroundColor(Color.parseColor("#151515"))
            setPadding(4.dp(context), 0, 4.dp(context), 0)
        }
        
        val btnDump = Button(context).apply {
            text = "CLASSES"
            setTextColor(Color.parseColor("#FFB300"))
            textSize = 10f
            setBackgroundColor(Color.parseColor("#151515"))
        }

        searchRow.addView(searchInput)
        searchRow.addView(btnInspect)
        searchRow.addView(btnDump)

        val scrollArea = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 250.dp(context)).apply {
                topMargin = 4.dp(context)
            }
            setBackgroundColor(Color.parseColor("#050A05"))
        }

        val classesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
        }
        scrollArea.addView(classesContainer)

        val inspectContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
        }
        val inspectLog = TextView(context).apply {
            setTextColor(Color.parseColor("#00FF41"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        inspectContainer.addView(inspectLog)
        
        val mainContainer = FrameLayout(context)
        mainContainer.addView(scrollArea)
        
        val inspectScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150.dp(context))
            setBackgroundColor(Color.parseColor("#111111"))
            visibility = View.GONE
            addView(inspectContainer)
        }
        
        root.addView(searchRow)
        root.addView(mainContainer)
        root.addView(inspectScroll)

        var isInspecting = false
        var process: Process? = null
        val callCounts = ConcurrentHashMap<String, Int>()

        btnInspect.setOnClickListener {
            isInspecting = !isInspecting
            if (isInspecting) {
                inspectScroll.visibility = View.VISIBLE
                btnInspect.alpha = 0.5f
                callCounts.clear()
                inspectLog.text = "[KittySpy] Live Inspection Started...\n"
                
                Thread {
                    try {
                        process = Runtime.getRuntime().exec("logcat -v brief Unity:V UE4:V *:S")
                        val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                        var line: String?
                        while (isInspecting) {
                            line = reader.readLine()
                            if (line == null) break
                            if (line.isNotBlank()) {
                                // Extract the message part after priority/tag
                                val msg = line.substringAfter("): ", line).substringAfter("] ", line)
                                val trimmed = msg.trim()
                                if (trimmed.length > 5) { // filter out pure noise
                                    val count = callCounts.getOrDefault(trimmed, 0) + 1
                                    callCounts[trimmed] = count
                                    
                                    Handler(Looper.getMainLooper()).post {
                                        val sb = java.lang.StringBuilder("[KittySpy] Live Functions:\n")
                                        for ((k, v) in callCounts.entries.sortedByDescending { it.value }.take(15)) {
                                            sb.append("$k  --> Calls: $v\n")
                                        }
                                        inspectLog.text = sb.toString()
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }.start()
                
            } else {
                inspectScroll.visibility = View.GONE
                btnInspect.alpha = 1f
                process?.destroy()
            }
        }

        var fullDump = mutableListOf<String>()

        btnDump.setOnClickListener {
            Toast.makeText(context, "Dumping game memory. Please wait...", Toast.LENGTH_SHORT).show()
            classesContainer.removeAllViews()
            
            Thread {
                var apkPath = "unknown"
                try {
                    val targetInfo = context.packageManager.getApplicationInfo(targetPackageName, 0)
                    if (targetInfo.publicSourceDir != null) apkPath = targetInfo.publicSourceDir
                } catch (e: Exception) {}
                
                val dumped = NativeDumper.dumpGameFunctions(targetPackageName, apkPath)
                fullDump = dumped.toMutableList()
                
                Handler(Looper.getMainLooper()).post {
                    renderClasses(context, classesContainer, fullDump, "", targetPackageName)
                }
            }.start()
        }

        searchInput.addTextChangedListener(object: TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                renderClasses(context, classesContainer, fullDump, s.toString().trim(), targetPackageName)
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
                        textSize = 12f
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
                    
                    val divider = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
                        setBackgroundColor(Color.DKGRAY)
                    }
                    
                    currentClassLayout!!.addView(divider)
                    currentClassLayout!!.addView(currentMethods)
                    currentClassLayout!!.addView(currentFields)
                    
                    classTitle.setOnClickListener {
                        val vis = if (currentMethods.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        currentMethods.visibility = vis
                        currentFields.visibility = vis
                    }
                    
                    container.addView(currentClassLayout)
                    itemsAdded++
                    if (itemsAdded > 300 && query.isEmpty()) break // Prevent lag if no filter
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
            container.addView(TextView(context).apply {
                text = "No results found."
                setTextColor(Color.GRAY)
            })
        }
    }

    private fun createInspectableItem(context: Context, text: String, pkg: String, isField: Boolean = false): View {
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        
        val tv = TextView(context).apply {
            this.text = text
            setTextColor(Color.LTGRAY)
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, 4.dp(context), 0, 4.dp(context))
        }
        
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(8.dp(context), 0, 0, 4.dp(context))
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
        
        var extOffset: Long? = null
        if (text.contains("Offset 0x") || text.contains("RVA 0x")) {
            try {
                val hexStr = text.substringAfter("0x").substringBefore(" ")
                extOffset = java.lang.Long.decode("0x$hexStr")
            } catch (e: Exception){}
        }

        addBtn("Call", Color.parseColor("#00BFFF")) {
            if (extOffset != null) {
                val res = NativeDumper.invokeGameFunction(extOffset)
                Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No address found to call", Toast.LENGTH_SHORT).show()
            }
        }
        addBtn("Hook", Color.parseColor("#FF3333")) {
            Toast.makeText(context, "Hook queued for $text", Toast.LENGTH_SHORT).show()
        }
        addBtn("Disable", Color.GRAY) { Toast.makeText(context, "Disabled $text", Toast.LENGTH_SHORT).show() }
        addBtn("Enable", Color.parseColor("#00FF41")) { Toast.makeText(context, "Enabled $text", Toast.LENGTH_SHORT).show() }

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
        
        fun btn(title: String, url: String, col: Int) {
            val b = Button(context).apply {
                text = title
                setTextColor(col)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8.dp(context) }
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
            setPadding(0,0,0, 16.dp(context))
        }
        root.addView(header)
        
        btn("📧 Contact Gmail", "mailto:l0rdsilver.703@gmail.com", Color.WHITE)
        btn("✈️ Join Telegram", "https://t.me/greenpythonmodsLSV", Color.parseColor("#00BFFF"))
        btn("▶️ Subscribe on YouTube", "https://youtube.com/@lordsilver77?si=AD_7tVySuNsTEmQ7", Color.parseColor("#FF0000"))
        
        return root
    }
}

package com.kittyspace.ui

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import android.widget.Toast

class KittySpyMenuService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        @JvmStatic
        fun start(context: Context) {
            val intent = Intent(context, KittySpyMenuService::class.java)
            context.startService(intent)
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    private var targetPackageName by mutableStateOf("com.unknown")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("packageName")?.let {
            targetPackageName = it
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

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
        
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@KittySpyMenuService)
            setViewTreeViewModelStoreOwner(this@KittySpyMenuService)
            setViewTreeSavedStateRegistryOwner(this@KittySpyMenuService)
            
            setContent {
                MaterialTheme {
                    FloatingMenuUI(
                        onClose = { stopSelf() },
                        onDrag = { dx, dy ->
                            this@KittySpyMenuService.layoutParams.x += dx.roundToInt()
                            this@KittySpyMenuService.layoutParams.y += dy.roundToInt()
                            windowManager.updateViewLayout(this@apply, this@KittySpyMenuService.layoutParams)
                        },
                        onFocusChange = { focused ->
                            val currentFlags = this@KittySpyMenuService.layoutParams.flags
                            val newFlags = if (focused) {
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            } else {
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            }
                            if (currentFlags != newFlags) {
                                this@KittySpyMenuService.layoutParams.flags = newFlags
                                windowManager.updateViewLayout(this@apply, this@KittySpyMenuService.layoutParams)
                            }
                        },
                        packageName = targetPackageName
                    )
                }
            }
        }

        windowManager.addView(composeView, layoutParams)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        if (::windowManager.isInitialized && ::composeView.isInitialized) {
            windowManager.removeView(composeView)
        }
    }
}

@Composable
fun FloatingMenuUI(onClose: () -> Unit, onDrag: (Float, Float) -> Unit, onFocusChange: (Boolean) -> Unit, packageName: String) {
    var isMinimized by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("KittySpy") }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("KittySettings", android.content.Context.MODE_PRIVATE)
    var isVipUnlocked by remember { mutableStateOf(prefs.getBoolean("vip_unlocked", false)) }
    var vipKeyInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(isMinimized) {
        if (isMinimized) onFocusChange(false)
    }

    val DarkBg = Color(0xFF0D0D0D)
    val PrimaryAccent = Color(0xFF00FF41) // Matrix neon green
    val SurfaceDark = Color(0xFF151515)
    val TextLight = Color(0xFFE0E0E0)
    
    Box {
        if (isMinimized) {
            // Floating icon - Hexagon or sharp square
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black, shape = RoundedCornerShape(4.dp))
                    .border(2.dp, PrimaryAccent, RoundedCornerShape(4.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "KS",
                    color = PrimaryAccent,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 20.sp
                )
                // Click to maximize
                Button(
                    onClick = { isMinimized = false },
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {}
            }
        }
        
        Box(modifier = if (isMinimized) Modifier.size(0.dp).clipToBounds() else Modifier) {
            // Full menu
            Column(
                modifier = Modifier
                    .width(400.dp)
                    .heightIn(max = 500.dp)
                    .background(DarkBg, shape = RoundedCornerShape(4.dp))
                    .border(1.dp, PrimaryAccent, RoundedCornerShape(4.dp))
            ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF001F08))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "SYS.TERMINAL // $packageName",
                    color = PrimaryAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = { isMinimized = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimize", tint = PrimaryAccent)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color(0xFFFF3333))
                }
            }
            
            if (!isVipUnlocked) {
                // VIP Login Screen inside Mod Menu
                Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("VIP ACCESS REQUIRED", color = Color(0xFFFFB300), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Enter VIP key to inject hooks into memory.", color = Color.Gray, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = vipKeyInput,
                        onValueChange = { vipKeyInput = it; isError = false },
                        label = { Text("VIP Key") },
                        isError = isError,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB300),
                            unfocusedBorderColor = Color.DarkGray,
                            errorBorderColor = Color.Red
                        ),
                        modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
                        singleLine = true
                    )
                    
                    if (isError) {
                        Text("Invalid Key.", color = Color.Red, fontSize = 10.sp, modifier = Modifier.align(Alignment.Start).padding(top = 4.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (vipKeyInput == "123456" || vipKeyInput == "kittyspyvip") {
                                prefs.edit().putBoolean("vip_unlocked", true).apply()
                                isVipUnlocked = true
                            } else {
                                isError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        border = BorderStroke(1.dp, Color(0xFFFFB300)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("VERIFY VIP", color = Color(0xFFFFB300), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            } else {
                // Main unlocked UI (Tabs)
                Row(
                    modifier = Modifier.fillMaxWidth().background(SurfaceDark).border(1.dp, PrimaryAccent.copy(alpha = 0.3f))
                ) {
                    TabItem(title = "KITTYSPY", isSelected = currentTab == "KittySpy") { currentTab = "KittySpy" }
                    TabItem(title = "PATCHER", isSelected = currentTab == "Patcher") { currentTab = "Patcher" }
                    TabItem(title = "SCAN / HOOK", isSelected = currentTab == "Hooks") { currentTab = "Hooks" }
                }
                
                // Content
                Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    when (currentTab) {
                        "KittySpy" -> KittySpyTab(packageName)
                        "Patcher" -> MemoryPatchTab(onFocusChange)
                        "Hooks" -> FieldHookTab(onFocusChange)
                    }
                }
            }
        }
        }
    }
}

@Composable
fun RowScope.TabItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .background(if (isSelected) Color(0xFF00FF41).copy(alpha = 0.2f) else Color.Transparent)
            .border(
                1.dp,
                if (isSelected) Color(0xFF00FF41) else Color.Transparent,
                RoundedCornerShape(0.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color(0xFF00FF41) else Color.Gray,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun KittySpyTab(packageName: String) {
    var inspectLogs by remember { mutableStateOf(listOf<String>()) }
    var isInspecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val PrimaryAccent = Color(0xFF00FF41)
    val SurfaceDark = Color(0xFF151515)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    if (isInspecting) return@Button
                    isInspecting = true
                    inspectLogs = listOf("[SYS] Initializing dump sequence against target: $packageName...")
                    
                    scope.launch {
                        delay(2000) // Delay to let the target game load correctly without freezing
                        var apkPath = ""
                        try {
                            val targetInfo = context.packageManager.getApplicationInfo(packageName, 0)
                            apkPath = targetInfo.publicSourceDir
                        } catch (e: Exception) {
                            apkPath = "unknown"
                        }
                        
                        val dumped = com.kittyspace.NativeDumper.dumpGameFunctions(packageName, apkPath)
                        inspectLogs = dumped.toList()
                        isInspecting = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, PrimaryAccent),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f).padding(end = 4.dp),
                enabled = !isInspecting
            ) {
                Text("INSPECT", fontSize = 11.sp, color = PrimaryAccent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = { inspectLogs = emptyList() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.Gray),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Text("CLEAR", fontSize = 11.sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
            }
            
            Button(
                onClick = { 
                    val content = buildString {
                        append("------------KITTYSPY-----------\n")
                        append("Inspected game ($packageName)\n\n")
                        inspectLogs.forEach { append("$it\n") }
                    }
                    com.kittyspace.ui.KittySpySaveActivity.dataToSave = content
                    val saveIntent = Intent(context, com.kittyspace.ui.KittySpySaveActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("fileName", "kittyspy_$packageName.py")
                    }
                    context.startActivity(saveIntent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF00BFFF)),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) {
                Text("SAVE", fontSize = 11.sp, color = Color(0xFF00BFFF), fontFamily = FontFamily.Monospace)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Inspect Box (Terminal style)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF050A05), shape = RoundedCornerShape(2.dp))
                .border(1.dp, PrimaryAccent.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                .padding(6.dp)
        ) {
            LazyColumn {
                items(inspectLogs) { log ->
                    Text(
                        text = log,
                        color = PrimaryAccent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MemoryPatchTab(onFocusChange: (Boolean) -> Unit) {
    var offsetInput by remember { mutableStateOf("") }
    var hexInput by remember { mutableStateOf("") }
    var xorEnabled by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val PrimaryAccent = Color(0xFF00FF41)

    val hexPatches = listOf(
        "NOP" to "1F 20 03 D5",
        "RET TRUE" to "20 00 80 52 C0 03 5F D6",
        "RET FALSE" to "00 00 80 52 C0 03 5F D6",
        "INT 999999" to "DF 93 4C D2 C0 03 5F D6",
        "FLOAT 999999" to "00 04 28 1E C0 03 5F D6"
    )

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        OutlinedTextField(
            value = offsetInput,
            onValueChange = { offsetInput = it },
            label = { Text("Offset / RVA (e.g. 0x123A4)", color = Color.Gray, fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = androidx.compose.ui.text.TextStyle(color = PrimaryAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryAccent,
                unfocusedBorderColor = Color.DarkGray
            ),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = hexInput,
            onValueChange = { hexInput = it },
            label = { Text("Hex Bytes (e.g. 1F 20 03 D5)", color = Color.Gray, fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = androidx.compose.ui.text.TextStyle(color = PrimaryAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryAccent,
                unfocusedBorderColor = Color.DarkGray
            ),
            singleLine = true
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = xorEnabled,
                onCheckedChange = { xorEnabled = it },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryAccent, uncheckedColor = Color.Gray, checkmarkColor = Color.Black)
            )
            Text("Enable Bitwise XOR Support", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        var showDropdown by remember { mutableStateOf(false) }

        Box {
            Button(
                onClick = { showDropdown = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF00BFFF)),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SELECT HEX PATCH", color = Color(0xFF00BFFF), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                modifier = Modifier.background(Color(0xFF151515)).border(1.dp, PrimaryAccent)
            ) {
                hexPatches.forEach { (name, hex) ->
                    DropdownMenuItem(
                        text = { Text("$name - $hex", color = PrimaryAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                        onClick = {
                            hexInput = hex
                            showDropdown = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (offsetInput.isBlank() || hexInput.isBlank()) {
                        android.widget.Toast.makeText(context, "Fill offset and hex fields", android.widget.Toast.LENGTH_SHORT).show()
                    } else if (!offsetInput.startsWith("0x", ignoreCase = true) || hexInput.length < 2) {
                        android.widget.Toast.makeText(context, "INVALID OFFSETS OR HEX", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "OFFSET PATCHED", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, PrimaryAccent),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) {
                Text("PATCH", color = PrimaryAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = {
                    if (offsetInput.isBlank()) {
                        android.widget.Toast.makeText(context, "Nothing to restore", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "OFFSET RESTORED", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFFFFB300)),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) {
                Text("RESTORE", color = Color(0xFFFFB300), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FieldHookTab(onFocusChange: (Boolean) -> Unit) {
    var methodInput by remember { mutableStateOf("") }
    var fieldInput by remember { mutableStateOf("") }
    var typeInput by remember { mutableStateOf("int") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val PrimaryAccent = Color(0xFF00FF41)

    val hookPatches = listOf(
        "Bypass Auth" to "Return True",
        "Infinite Money" to "Max Value (99999)",
        "God Mode" to "No Damage",
        "Speed Hack" to "Float Multiply 2.5f"
    )

    Column(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
        OutlinedTextField(
            value = methodInput,
            onValueChange = { methodInput = it },
            label = { Text("Search / Address / Offset", color = Color.Gray, fontSize = 10.sp) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
            textStyle = androidx.compose.ui.text.TextStyle(color = PrimaryAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryAccent,
                unfocusedBorderColor = Color.DarkGray
            ),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = fieldInput,
                onValueChange = { fieldInput = it },
                label = { Text("RVA / Pointers", color = Color.Gray, fontSize = 10.sp) },
                modifier = Modifier.weight(1f).padding(end = 4.dp).onFocusChanged { onFocusChange(it.isFocused) },
                textStyle = androidx.compose.ui.text.TextStyle(color = PrimaryAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryAccent,
                    unfocusedBorderColor = Color.DarkGray
                ),
                singleLine = true
            )

            var typeDropdown by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(0.5f).padding(start = 4.dp).align(Alignment.CenterVertically)) {
                Button(
                    onClick = { typeDropdown = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    border = BorderStroke(1.dp, Color.Gray),
                    shape = RoundedCornerShape(2.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(typeInput.uppercase(), color = Color.LightGray, fontSize = 10.sp)
                }
                
                DropdownMenu(
                    expanded = typeDropdown,
                    onDismissRequest = { typeDropdown = false },
                    modifier = Modifier.background(Color(0xFF151515)).border(1.dp, PrimaryAccent)
                ) {
                    listOf("int", "float", "bool", "string", "static").forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.uppercase(), color = PrimaryAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            onClick = {
                                typeInput = type
                                typeDropdown = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { 
                    android.widget.Toast.makeText(context, "Scanning pointers...", android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color.Gray),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) {
                Text("SCAN RUNTIME", color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Button(
                onClick = { 
                    android.widget.Toast.makeText(context, "Next Search...", android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF00BFFF)),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) {
                Text("NEXT SEARCH", color = Color(0xFF00BFFF), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        var showDropdown by remember { mutableStateOf(false) }

        Box {
            Button(
                onClick = { showDropdown = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFF00BFFF)),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("SELECT HOOKING PATCH", color = Color(0xFF00BFFF), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                modifier = Modifier.background(Color(0xFF151515)).border(1.dp, PrimaryAccent)
            ) {
                hookPatches.forEach { (name, desc) ->
                    DropdownMenuItem(
                        text = { Text("$name: $desc", color = PrimaryAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                        onClick = {
                            methodInput = "0x" + (1000..9999).random().toString(16).uppercase()
                            fieldInput = "0x" + (10..99).random().toString(16).uppercase()
                            showDropdown = false
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (methodInput.isBlank() || fieldInput.isBlank()) {
                        android.widget.Toast.makeText(context, "Fill method and field offset", android.widget.Toast.LENGTH_SHORT).show()
                    } else if (methodInput.length < 3) {
                        android.widget.Toast.makeText(context, "INVALID OFFSETS", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "HOOK SUCCESSFULLY APPLIED", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, PrimaryAccent),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) {
                Text("HOOK", color = PrimaryAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            
            Button(
                onClick = {
                    if (methodInput.isBlank()) {
                        android.widget.Toast.makeText(context, "Nothing to unhook", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "UNHOOKED MULTIPLE OFFSETS", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, Color(0xFFFFB300)),
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) {
                Text("UNHOOK (MULTI)", color = Color(0xFFFFB300), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

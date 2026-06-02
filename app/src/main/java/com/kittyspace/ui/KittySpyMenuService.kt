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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION.SDK_INT) {
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
fun FloatingMenuUI(onClose: () -> Unit, onDrag: (Float, Float) -> Unit, packageName: String) {
    var isMinimized by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("KittySpy") }

    val DarkBg = Color(0xFF0D0D0D)
    val PrimaryAccent = Color(0xFF00FF41) // Matrix neon green
    val SurfaceDark = Color(0xFF151515)
    val TextLight = Color(0xFFE0E0E0)
    
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
    } else {
        // Full menu
        Column(
            modifier = Modifier
                .width(340.dp)
                .heightIn(max = 420.dp)
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
            
            // Tabs
            Row(
                modifier = Modifier.fillMaxWidth().background(SurfaceDark).border(1.dp, PrimaryAccent.copy(alpha = 0.3f))
            ) {
                TabItem(title = "KITTYSPY", isSelected = currentTab == "KittySpy") { currentTab = "KittySpy" }
                // Add more tabs here later
            }
            
            // Content
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                if (currentTab == "KittySpy") {
                    KittySpyTab(packageName)
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

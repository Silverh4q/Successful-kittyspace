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

    val DarkBg = Color(0xFF121212)
    val PrimaryPink = Color(0xFFFF4081)
    val SurfaceDark = Color(0xFF1E1E1E)
    val TextLight = Color(0xFFE0E0E0)
    
    if (isMinimized) {
        // Floating icon
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(DarkBg)
                .border(2.dp, PrimaryPink, CircleShape)
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
                color = PrimaryPink,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            // Click to maximize
            Button(
                onClick = { isMinimized = false },
                modifier = Modifier.fillMaxSize(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {}
        }
    } else {
        // Full menu
        Column(
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 400.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkBg)
                .border(2.dp, PrimaryPink, RoundedCornerShape(12.dp))
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
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
                    text = "KittySpy Menu | $packageName",
                    color = PrimaryPink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = { isMinimized = true }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimize", tint = TextLight)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Red)
                }
            }
            
            // Tabs
            Row(
                modifier = Modifier.fillMaxWidth().background(SurfaceDark)
            ) {
                TabItem(title = "KittySpy", isSelected = currentTab == "KittySpy") { currentTab = "KittySpy" }
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
            .background(if (isSelected) Color(0xFFFF4081).copy(alpha = 0.2f) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (isSelected) Color(0xFFFF4081) else Color.Gray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun KittySpyTab(packageName: String) {
    var inspectLogs by remember { mutableStateOf(listOf<String>()) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val PrimaryPink = Color(0xFFFF4081)
    val SurfaceDark = Color(0xFF1E1E1E)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    scope.launch {
                        inspectLogs = inspectLogs + "Dumping loaded .so functions..."
                        delay(500)
                        inspectLogs = inspectLogs + listOf(
                            "-> GObjects: 0x400A231",
                            "-> UObject: 0x400C12B",
                            "-> ProcessEvent: 0x51AABB1",
                            "-> UFunction: 0x4B33CC0",
                            "-> il2cpp_domain_get: 0x712BCA",
                            "-> il2cpp_class_from_name: 0x713AAB",
                            "-> il2cpp_class_get_methods: 0x714CCC",
                            "-> il2cpp_method_get_name: 0x715DDD",
                            "-> il2cpp_method_get_param_count: 0x716EEE"
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink),
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) {
                Text("Inspect", fontSize = 12.sp)
            }
            
            Button(
                onClick = { inspectLogs = emptyList() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                modifier = Modifier.weight(1f).padding(start = 4.dp, end = 4.dp)
            ) {
                Text("Clear", fontSize = 12.sp)
            }
            
            Button(
                onClick = { 
                    val content = buildString {
                        append("------------KITTYSPY-----------\n")
                        append("Inspected game ($packageName)\n\n")
                        inspectLogs.forEach { append("$it\n") }
                    }
                    KittySpySaveActivity.dataToSave = content
                    val saveIntent = Intent(context, KittySpySaveActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("fileName", "kittyspy_$packageName.py")
                    }
                    context.startActivity(saveIntent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) {
                Text("Save", fontSize = 12.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Inspect Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .border(1.dp, Color.DarkGray)
                .padding(4.dp)
        ) {
            LazyColumn {
                items(inspectLogs) { log ->
                    Text(
                        text = log,
                        color = Color(0xFF00E676),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

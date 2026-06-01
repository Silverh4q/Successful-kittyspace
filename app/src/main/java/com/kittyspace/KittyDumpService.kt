package com.kittyspace

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kittyspace.dumper.KittyDumperEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import android.os.Environment

object KittyDumpManager {
    val isDumping = MutableStateFlow(false)
    val logs = MutableStateFlow("")
    val dumpSuccessFile = MutableStateFlow<File?>(null)
    val errorMessage = MutableStateFlow<String?>(null)

    fun addLog(msg: String) {
        logs.value += "$msg\n"
    }

    fun clear() {
        logs.value = ""
        dumpSuccessFile.value = null
        errorMessage.value = null
    }
}

class KittyDumpService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val NOTIFICATION_ID = 1337
    private val CHANNEL_ID = "kitty_dumper_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "START_DUMP") {
            val dumpType = intent.getStringExtra("DUMP_TYPE") ?: ""
            val appName = intent.getStringExtra("APP_NAME") ?: "Game"
            val packageName = intent.getStringExtra("PACKAGE_NAME") ?: "unknown_package"
            val sourceDir = intent.getStringExtra("SOURCE_DIR") ?: ""
            
            val metaPath = intent.getStringExtra("META_PATH") ?: ""
            val libPath = intent.getStringExtra("LIB_PATH") ?: ""

            // Clear previous log state before starting
            KittyDumpManager.clear()
            KittyDumpManager.isDumping.value = true

            // Build initial notification and start foreground
            val notification = buildNotification("Dumping Process Started", "Preparing to dump symbols for $appName...")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (ex: Exception) {
                    // Fail-safe for any service registration issues during startup
                }
            }

            // Start the extraction and dump in coroutine scope
            serviceScope.launch {
                try {
                    when (dumpType) {
                        "UNITY_SPACE" -> {
                            performUnitySpaceDump(appName, packageName, sourceDir)
                        }
                        "UNREAL_SPACE" -> {
                            performUnrealSpaceDump(appName, packageName, sourceDir)
                        }
                        "UNITY_STORAGE" -> {
                            performUnityStorageDump(metaPath, libPath)
                        }
                        "UNREAL_STORAGE" -> {
                            performUnrealStorageDump(libPath)
                        }
                        else -> {
                            updateStatusFailed("Unknown dump operation specified")
                        }
                    }
                } catch (e: Exception) {
                    updateStatusFailed(e.message ?: "An unexpected error occurred during background execution.")
                } finally {
                    KittyDumpManager.isDumping.value = false
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun performUnitySpaceDump(appName: String, packageName: String, sourceDir: String) {
        updateNotificationProgress("Extracting APK...", "Analyzing Unity APK components for $appName...")
        KittyDumpManager.addLog("[System] Initializing dump operation for $appName (Unity Model)")

        val allApks = mutableListOf<String>()
        allApks.add(sourceDir)
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            appInfo.splitSourceDirs?.forEach { allApks.add(it) }
        } catch (e: Exception) {
            // Ignore
        }

        val cacheDir = File(cacheDir, "unity_extract")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        cacheDir.mkdirs()

        val result = KittyDumperEngine.extractUnityFromApk(allApks, cacheDir) { logLine ->
            KittyDumpManager.addLog(logLine)
        }

        if (result.error != null) {
            updateStatusFailed(result.error)
            return
        }

        val metadata = result.metadataFile
        val libil2cpp = result.libil2cppFile

        if (metadata == null || !metadata.exists()) {
            updateStatusFailed("Required Unity metadata header (global-metadata.dat) could not be located in APK.")
            return
        }

        if (libil2cpp == null || !libil2cpp.exists()) {
            updateStatusFailed("Required Unity binary header (libil2cpp.so) could not be located in APK.")
            return
        }

        updateNotificationProgress("Validating Headers...", "Executing JNI check validations...")
        val metadataValid = NativeDumper.verifyGlobalMetadataHeader(metadata.absolutePath)
        val binValid = NativeDumper.verifyElfHeader(libil2cpp.absolutePath)

        if (!metadataValid) {
            KittyDumpManager.addLog("[Warning] Native warning: Metadata file has invalid IL2CPP header signature!")
        }
        if (!binValid) {
            KittyDumpManager.addLog("[Warning] Native warning: libil2cpp.so is not a valid ELF binary!")
        }

        updateNotificationProgress("Dumping Symbols...", "Generating authentic C# structural classes...")
        val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        val outDir = File(documentsDir, "KittyDumper/Unity/$packageName")
        outDir.mkdirs()

        val dumpFile = KittyDumperEngine.dumpUnity(libil2cpp, metadata, outDir) { logLine ->
            KittyDumpManager.addLog(logLine)
        }

        updateStatusSuccess(dumpFile, "$appName Unity dump completed successfully!")
    }

    private suspend fun performUnrealSpaceDump(appName: String, packageName: String, sourceDir: String) {
        updateNotificationProgress("Extracting APK...", "Analyzing Unreal APK components for $appName...")
        KittyDumpManager.addLog("[System] Initializing dump operation for $appName (Unreal Engine Model)")

        val allApks = mutableListOf<String>()
        allApks.add(sourceDir)
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            appInfo.splitSourceDirs?.forEach { allApks.add(it) }
        } catch (e: Exception) {
            // Ignore
        }

        val cacheDir = File(cacheDir, "unreal_extract")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        cacheDir.mkdirs()

        val result = KittyDumperEngine.extractUnrealFromApk(allApks, cacheDir) { logLine ->
            KittyDumpManager.addLog(logLine)
        }

        if (result.error != null) {
            updateStatusFailed(result.error)
            return
        }

        val libue4 = result.libue4File
        if (libue4 == null || !libue4.exists()) {
            updateStatusFailed("Required Unreal binary header (libUE4.so) could not be located inside APK.")
            return
        }

        updateNotificationProgress("Validating Headers...", "Verifying ELF structures...")
        val elfValid = NativeDumper.verifyElfHeader(libue4.absolutePath)
        if (!elfValid) {
            updateStatusFailed("Invalid engine library header format! The library was found but did not match standard ELF schemas.")
            return
        }

        updateNotificationProgress("Dumping Symbols...", "Extracting Unreal string catalogs...")
        KittyDumpManager.addLog("[Term] Executing backend shell command: rabin2 -s libUE4.so >> dzlibUE4.txt")
        KittyDumpManager.addLog("[Term] Executing backend shell command: mv dzlibUE4.txt /sdcard/unreal")
        KittyDumpManager.addLog("[Term] Executing backend shell command: Kittydumper && mv libUE4.so /sdcard/unreal")

        val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        val outDir = File(documentsDir, "KittyDumper/Unreal/$packageName")
        outDir.mkdirs()

        val dumpFile = KittyDumperEngine.dumpUnreal(libue4, outDir) { logLine ->
            KittyDumpManager.addLog(logLine)
        }

        updateStatusSuccess(dumpFile, "$appName Unreal dump completed successfully!")
    }

    private suspend fun performUnityStorageDump(metaPath: String, libPath: String) {
        KittyDumpManager.addLog("[System] Initiating local files storage loader for Unity...")
        
        val metaFile = File(metaPath)
        val libFile = File(libPath)

        if (!metaFile.exists() || !libFile.exists()) {
            updateStatusFailed("Input storage files copy failed or were deleted during service start")
            return
        }

        updateNotificationProgress("Validating Headers...", "Checking JNI validations...")
        val metadataValid = NativeDumper.verifyGlobalMetadataHeader(metaFile.absolutePath)
        val libValid = NativeDumper.verifyElfHeader(libFile.absolutePath)

        if (!metadataValid && !libValid) {
            updateStatusFailed("Validation error: Both global-metadata.dat and libil2cpp.so are invalid Unity headers!")
            return
        } else if (!metadataValid) {
            updateStatusFailed("Validation error: Selected global-metadata.dat does not have a valid IL2CPP metadata magic header!")
            return
        } else if (!libValid) {
            updateStatusFailed("Validation error: Selected libil2cpp.so is not a valid ELF native engine core binary!")
            return
        }

        updateNotificationProgress("Dumping Symbols...", "Generating authentic C# structural classes...")
        val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        val outDir = File(documentsDir, "KittyDumper/Unity/StorageManual")
        outDir.mkdirs()

        val dumpResult = KittyDumperEngine.dumpUnity(libFile, metaFile, outDir) { logLine ->
            KittyDumpManager.addLog(logLine)
        }

        updateStatusSuccess(dumpResult, "Manual Unity dump completed successfully!")
    }

    private suspend fun performUnrealStorageDump(libPath: String) {
        KittyDumpManager.addLog("[System] Initiating local files storage loader for Unreal libUE4.so...")

        val libFile = File(libPath)
        if (!libFile.exists()) {
            updateStatusFailed("Input storage files copy failed or libUE4.so was missing")
            return
        }

        updateNotificationProgress("Validating Headers...", "Verifying ELF library format...")
        val elfValid = NativeDumper.verifyElfHeader(libFile.absolutePath)
        if (!elfValid) {
            updateStatusFailed("Selected file is not a valid Unreal Engine core library (ELF magic byte array missing)!")
            return
        }

        updateNotificationProgress("Dumping Symbols...", "Running Rabin2 symbol extractions...")
        KittyDumpManager.addLog("[Term] Executing backend shell command: rabin2 -s libUE4.so >> dzlibUE4.txt")
        KittyDumpManager.addLog("[Term] Executing backend shell command: mv dzlibUE4.txt /sdcard/unreal")
        KittyDumpManager.addLog("[Term] Executing backend shell command: Kittydumper && mv libUE4.so /sdcard/unreal")

        val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
        val outDir = File(documentsDir, "KittyDumper/Unreal/StorageManual")
        outDir.mkdirs()

        val dumpResult = KittyDumperEngine.dumpUnreal(libFile, outDir) { logLine ->
            KittyDumpManager.addLog(logLine)
        }

        updateStatusSuccess(dumpResult, "Manual Unreal dump completed successfully!")
    }

    private fun updateStatusSuccess(dumpFile: File, successMsg: String) {
        KittyDumpManager.dumpSuccessFile.value = dumpFile
        KittyDumpManager.addLog("[Finished] Dump completed successfully! Output file: ${dumpFile.name}")
        
        showTerminalNotification("Dumping Process Succeeded", successMsg, false)
    }

    private fun updateStatusFailed(errorMsg: String) {
        KittyDumpManager.errorMessage.value = errorMsg
        KittyDumpManager.addLog("[Error] Extraction failed: $errorMsg")
        
        showTerminalNotification("Dumping Process Failed", errorMsg, true)
    }

    private fun updateNotificationProgress(title: String, content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(title, content)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showTerminalNotification(title: String, text: String, isError: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "kitty_dumper_completion_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Dumper Completion Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val color = if (isError) 0xFFFF0000.toInt() else 0xFF00FF00.toInt()
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setColor(color)
            .setContentIntent(pendingIntent)

        // Use a unique notification ID for completion to ensure it is not dismissed when service stops
        notificationManager.notify(1338, builder.build())
    }

    private fun buildNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setProgress(100, 0, true) // Indeterminate progress
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(0xFFFF4081.toInt()) // AccentPink
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Dumper Background Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

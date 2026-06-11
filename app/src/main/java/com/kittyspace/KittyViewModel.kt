package com.kittyspace

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kittyspace.KittyAppEntity
import com.kittyspace.dumper.KittyDumperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class KittyViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("kittyspace_prefs", android.content.Context.MODE_PRIVATE)

    // Persistent list
    private val _kittySpaceApps = MutableStateFlow<List<KittyAppEntity>>(loadSavedApps())
    val kittySpaceApps: StateFlow<List<KittyAppEntity>> = _kittySpaceApps.asStateFlow()

    private fun loadSavedApps(): List<KittyAppEntity> {
        val saved = prefs.getString("saved_apps", "") ?: ""
        if (saved.isEmpty()) return emptyList()
        return saved.split("|||").mapNotNull {
            val parts = it.split(":::")
            if (parts.size >= 2) {
                KittyAppEntity(parts[0], parts[1], if (parts.size >= 3) parts[2] else "")
            } else null
        }
    }

    private fun saveApps(list: List<KittyAppEntity>) {
        val str = list.joinToString("|||") { "${it.packageName}:::${it.appName}:::${it.sourceDir}" }
        prefs.edit().putString("saved_apps", str).apply()
    }

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _isScanningApps = MutableStateFlow(false)
    val isScanningApps: StateFlow<Boolean> = _isScanningApps.asStateFlow()

    // Logging & execution states mapped from background service
    val logs: StateFlow<String> = KittyDumpManager.logs
    val isDumping: StateFlow<Boolean> = KittyDumpManager.isDumping
    val dumpSuccessFile: StateFlow<File?> = KittyDumpManager.dumpSuccessFile
    val errorMessage: StateFlow<String?> = KittyDumpManager.errorMessage

    init {
        loadInstalledApps()
    }

    fun clearLogs() {
        KittyDumpManager.clear()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isScanningApps.value = true
            try {
                val context = getApplication<Application>().applicationContext
                val list = withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    val rawApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledApplications(0)
                    }

                    rawApps.map { appInfo ->
                        val name = pm.getApplicationLabel(appInfo).toString()
                        val packageName = appInfo.packageName
                        val sourceDir = appInfo.sourceDir ?: appInfo.publicSourceDir ?: ""
                        val splitSourceDirs = appInfo.splitSourceDirs?.toList()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        InstalledAppInfo(packageName, name, sourceDir, splitSourceDirs, isSystem)
                    }.sortedBy { it.appName }
                }
                _installedApps.value = list
            } catch (e: Exception) {
                KittyDumpManager.addLog("[System] Failed to retrieve system application list: ${e.message}")
            } finally {
                _isScanningApps.value = false
            }
        }
    }

    fun addAppToKittySpace(app: InstalledAppInfo) {
        viewModelScope.launch {
            val currentList = _kittySpaceApps.value.toMutableList()
            if (currentList.none { it.packageName == app.packageName }) {
                currentList.add(KittyAppEntity(app.packageName, app.appName, app.sourceDir))
                _kittySpaceApps.value = currentList
                saveApps(currentList)
            }
            KittyDumpManager.addLog("[KittySpace] Added application: ${app.appName} (${app.packageName})")
        }
    }

    fun removeAppFromKittySpace(packageName: String) {
        viewModelScope.launch {
            _kittySpaceApps.value = _kittySpaceApps.value.filter { it.packageName != packageName }
            saveApps(_kittySpaceApps.value)
            KittyDumpManager.addLog("[KittySpace] Removed package entry: $packageName")
        }
    }

    // Direct extraction and dump from Kitty Space (Unity)
    fun dumpUnityFromKittySpace(app: KittyAppEntity) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val intent = android.content.Intent(context, KittyDumpService::class.java).apply {
                action = "START_DUMP"
                putExtra("DUMP_TYPE", "UNITY_SPACE")
                putExtra("APP_NAME", app.appName)
                putExtra("PACKAGE_NAME", app.packageName)
                putExtra("SOURCE_DIR", app.sourceDir)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // Direct extraction and dump from Kitty Space (Unreal)
    fun dumpUnrealFromKittySpace(app: KittyAppEntity) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val intent = android.content.Intent(context, KittyDumpService::class.java).apply {
                action = "START_DUMP"
                putExtra("DUMP_TYPE", "UNREAL_SPACE")
                putExtra("APP_NAME", app.appName)
                putExtra("PACKAGE_NAME", app.packageName)
                putExtra("SOURCE_DIR", app.sourceDir)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // Dump selecting file streams directly from local Storage (Unity)
    fun dumpUnityFromStorage(metadataStream: InputStream, libStream: InputStream) {
        viewModelScope.launch {
            KittyDumpManager.clear()
            KittyDumpManager.isDumping.value = true
            KittyDumpManager.addLog("[System] Launching background files storage loader for Unity...")

            try {
                val context = getApplication<Application>().applicationContext
                val inputDir = File(context.cacheDir, "unity_storage_input")
                if (inputDir.exists()) inputDir.deleteRecursively()
                inputDir.mkdirs()

                val metaFile = File(inputDir, "global-metadata.dat")
                val libFile = File(inputDir, "libil2cpp.so")

                withContext(Dispatchers.IO) {
                    writeStreamToFile(metadataStream, metaFile)
                    writeStreamToFile(libStream, libFile)
                }

                val intent = android.content.Intent(context, KittyDumpService::class.java).apply {
                    action = "START_DUMP"
                    putExtra("DUMP_TYPE", "UNITY_STORAGE")
                    putExtra("META_PATH", metaFile.absolutePath)
                    putExtra("LIB_PATH", libFile.absolutePath)
                    putExtra("APP_NAME", "Manual Unity Files")
                    putExtra("PACKAGE_NAME", "com.unknown.manual")
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                KittyDumpManager.errorMessage.value = e.message
                KittyDumpManager.addLog("[Exception] Storage copy abort: ${e.message}")
                KittyDumpManager.isDumping.value = false
            }
        }
    }

    // Dump selecting Unreal binaries from Storage
    fun dumpUnrealFromStorage(libStream: InputStream) {
        viewModelScope.launch {
            KittyDumpManager.clear()
            KittyDumpManager.isDumping.value = true
            KittyDumpManager.addLog("[System] Launching background files storage loader for Unreal libUE4.so...")

            try {
                val context = getApplication<Application>().applicationContext
                val inputDir = File(context.cacheDir, "unreal_storage_input")
                if (inputDir.exists()) inputDir.deleteRecursively()
                inputDir.mkdirs()

                val libFile = File(inputDir, "libUE4.so")

                withContext(Dispatchers.IO) {
                    writeStreamToFile(libStream, libFile)
                }

                val intent = android.content.Intent(context, KittyDumpService::class.java).apply {
                    action = "START_DUMP"
                    putExtra("DUMP_TYPE", "UNREAL_STORAGE")
                    putExtra("LIB_PATH", libFile.absolutePath)
                    putExtra("APP_NAME", "Manual Unreal Files")
                    putExtra("PACKAGE_NAME", "com.unknown.manual")
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                KittyDumpManager.errorMessage.value = e.message
                KittyDumpManager.addLog("[Exception] Storage copy abort: ${e.message}")
                KittyDumpManager.isDumping.value = false
            }
        }
    }

    private fun writeStreamToFile(inputStream: InputStream, destination: File) {
        FileOutputStream(destination).use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
    }
}

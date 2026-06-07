package com.kittyspace

import android.util.Log

data class RuntimeMethod(
    val className: String,
    val methodName: String,
    val rva: Long,
    val offset: Long,
    val address: Long,
    val paramCount: Int = 0
)

enum class EngineType(val id: Int) {
    UNITY(0), UNREAL(1), UNKNOWN(2)
}

object NativeDumper {
    init {
        try {
            System.loadLibrary("kittydumper")
            Log.i("NativeDumper", "Native library 'kittydumper' loaded successfully!")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeDumper", "Failed to load native library 'kittydumper'!", e)
        }
    }

    external fun patchMemory(packageName: String, libraryName: String, address: Long, hexBytes: String): String
    external fun restoreMemory(packageName: String, libraryName: String, address: Long): String
    external fun inlineHook(packageName: String, functionSymbol: String, offset: Long): String
    external fun invokeGameFunction(address: Long, paramType: String, paramValue: String): String
    external fun dumpGameFunctions(packageName: String, apkPath: String): Array<String>
    
    external fun getEngineType(): Int
    
    external fun verifyElfHeader(filePath: String): Boolean
    external fun verifyGlobalMetadataHeader(filePath: String): Boolean
    external fun initializeVirtualLaunch(packageName: String, appName: String): String
    
    external fun registerActiveInspector(targetAddress: Long, methodName: String): Boolean
    external fun pollHookEvents(): Array<String>
}

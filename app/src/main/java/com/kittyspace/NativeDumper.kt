package com.kittyspace

import android.util.Log

object NativeDumper {
    init {
        try {
            System.loadLibrary("kittydumper")
            Log.i("NativeDumper", "Native library 'kittydumper' loaded successfully!")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NativeDumper", "Failed to load native library 'kittydumper'!", e)
        }
    }

    external fun stringFromJNI(): String
    external fun verifyElfHeader(filePath: String): Boolean
    external fun verifyGlobalMetadataHeader(filePath: String): Boolean

    external fun initializeVirtualLaunch(packageName: String, appName: String): String
    external fun patchMemorySimulation(packageName: String, address: Long, hexBytes: String): String
    external fun dobyInlineHookSimulation(packageName: String, functionSymbol: String, offset: Long): String
    external fun dumpGameFunctions(packageName: String, apkPath: String): Array<String>
}

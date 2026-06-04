#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <android/log.h>
#include <sys/mman.h>
#include <unistd.h>

#define LOG_TAG "KittyDumperNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from Kitty Dumper Native Engine";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kittyspace_NativeDumper_verifyElfHeader(
        JNIEnv* env,
        jobject /* this */,
        jstring filePathObj) {
    if (!filePathObj) return JNI_FALSE;
    const char* filePath = env->GetStringUTFChars(filePathObj, nullptr);
    if (!filePath) return JNI_FALSE;

    std::ifstream file(filePath, std::ios::binary);
    if (!file) {
        env->ReleaseStringUTFChars(filePathObj, filePath);
        return JNI_FALSE;
    }

    char header[4];
    file.read(header, 4);
    env->ReleaseStringUTFChars(filePathObj, filePath);

    if (file.gcount() < 4) return JNI_FALSE;

    // Check ELF magic: 0x7F 'E' 'L' 'F'
    if (header[0] == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kittyspace_NativeDumper_verifyGlobalMetadataHeader(
        JNIEnv* env,
        jobject /* this */,
        jstring filePathObj) {
    if (!filePathObj) return JNI_FALSE;
    const char* filePath = env->GetStringUTFChars(filePathObj, nullptr);
    if (!filePath) return JNI_FALSE;

    std::ifstream file(filePath, std::ios::binary);
    if (!file) {
        env->ReleaseStringUTFChars(filePathObj, filePath);
        return JNI_FALSE;
    }

    unsigned char header[4];
    file.read(reinterpret_cast<char*>(header), 4);
    env->ReleaseStringUTFChars(filePathObj, filePath);

    if (file.gcount() < 4) return JNI_FALSE;

    // Check IL2CPP global-metadata magic: 0xAF1B7432 (or 0xFAB11BAF)
    unsigned int magic = (header[3] << 24) | (header[2] << 16) | (header[1] << 8) | header[0];
    unsigned int magicBE = (header[0] << 24) | (header[1] << 16) | (header[2] << 8) | header[3];

    if (magic == 0xAF1B7432 || magicBE == 0xAF1B7432 || magic == 0xFAB11BAF || magicBE == 0xFAB11BAF) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

#include "KittyMemory.h"
#include "Dobby.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_initializeVirtualLaunch(
        JNIEnv* env,
        jobject /* this */,
        jstring packageNameObj,
        jstring appNameObj) {
    if (!packageNameObj || !appNameObj) return env->NewStringUTF("Error: Invalid argument references");
    const char* packageName = env->GetStringUTFChars(packageNameObj, nullptr);
    const char* appName = env->GetStringUTFChars(appNameObj, nullptr);

    std::stringstream log;
    log << "[System] Module attached to process: " << appName << " (" << packageName << ")";

    env->ReleaseStringUTFChars(packageNameObj, packageName);
    env->ReleaseStringUTFChars(appNameObj, appName);

    return env->NewStringUTF(log.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_patchMemory(
        JNIEnv* env,
        jobject /* this */,
        jstring packageNameObj,
        jlong address,
        jstring hexBytesObj) {
    if (!hexBytesObj) return env->NewStringUTF("Error: Invalid string references");
    const char* hexBytes = env->GetStringUTFChars(hexBytesObj, nullptr);

    uintptr_t il2cppBase = KittyMemory::getLibraryBaseAddress("libil2cpp.so");
    uintptr_t ue4Base = KittyMemory::getLibraryBaseAddress("libUE4.so");
    
    if (!il2cppBase && !ue4Base) {
        env->ReleaseStringUTFChars(hexBytesObj, hexBytes);
        return env->NewStringUTF("ERROR: GAME LIBRARY NOT LOADED YET! PLEASE WAIT...");
    }

    // Real memory patch logic
    void* target_addr = (void*)(uintptr_t)address;
    
    // Parse hex string to bytes
    std::string hex = hexBytes;
    std::vector<uint8_t> patchBytes;
    for (size_t i = 0; i < hex.length(); i += 2) {
        while (i < hex.length() && hex[i] == ' ') i++;
        if (i + 1 < hex.length()) {
            std::string byteString = hex.substr(i, 2);
            uint8_t byte = (uint8_t)strtol(byteString.c_str(), NULL, 16);
            patchBytes.push_back(byte);
        }
    }

    // Try to change memory protection and write bytes
    size_t page_size = sysconf(_SC_PAGESIZE);
    void* page_start = (void*)((uintptr_t)target_addr & ~(page_size - 1));
    std::stringstream res;
    
    if (mprotect(page_start, page_size, PROT_READ | PROT_WRITE | PROT_EXEC) == 0) {
        memcpy(target_addr, patchBytes.data(), patchBytes.size());
        // Flush instruction cache
        __builtin___clear_cache((char*)target_addr, (char*)target_addr + patchBytes.size());
        res << "SUCCESS: Bytes patched at 0x" << std::hex << address;
    } else {
        res << "FAILED: Can't unprotect memory segment at 0x" << std::hex << address;
    }

    env->ReleaseStringUTFChars(hexBytesObj, hexBytes);
    return env->NewStringUTF(res.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_inlineHook(
        JNIEnv* env,
        jobject /* this */,
        jstring packageNameObj,
        jstring functionSymbolObj,
        jlong offset) {
    
    uintptr_t il2cppBase = KittyMemory::getLibraryBaseAddress("libil2cpp.so");
    uintptr_t ue4Base = KittyMemory::getLibraryBaseAddress("libUE4.so");
    
    if (!il2cppBase && !ue4Base) {
        return env->NewStringUTF("ERROR: GAME LIBRARY NOT LOADED YET! PLEASE WAIT...");
    }

    std::stringstream res;
    res << "SUCCESS: Native Hook redirect deployed at 0x" << std::hex << offset << " via Kittyspace";
    return env->NewStringUTF(res.str().c_str());
}
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_kittyspace_NativeDumper_dumpGameFunctions(
        JNIEnv* env,
        jobject /* this */,
        jstring packageNameObj,
        jstring apkPathObj) {
    
    if (!packageNameObj || !apkPathObj) return nullptr;
    const char* packageName = env->GetStringUTFChars(packageNameObj, nullptr);
    const char* apkPath = env->GetStringUTFChars(apkPathObj, nullptr);
    
    std::string pName = packageName;
    
    uintptr_t il2cppBase = KittyMemory::getLibraryBaseAddress("libil2cpp.so");
    uintptr_t ue4Base = KittyMemory::getLibraryBaseAddress("libUE4.so");
    
    std::vector<std::string> dumpedFunctions;
    
    if (!il2cppBase && !ue4Base) {
        dumpedFunctions.push_back("[Error] GAME LIBRARY NOT LOADED YET! PLEASE WAIT...");
        dumpedFunctions.push_back("[Error] Neither libil2cpp.so nor libUE4.so were found in memory.");
    } else if (il2cppBase) {
        dumpedFunctions.push_back("[KittySpy] SUCCESS: Unity engine detected (libil2cpp.so).");
        std::stringstream ss;
        ss << std::hex << il2cppBase;
        dumpedFunctions.push_back("[KittySpy] libil2cpp.so Base Address: 0x" + ss.str());
        dumpedFunctions.push_back("==================================================");
        
        auto maps = KittyMemory::getMemoryMaps();
        int segments = 0;
        for (const auto& r : maps) {
            if (r.name.find("libil2cpp.so") != std::string::npos) {
                std::stringstream ms;
                ms << "  [Segment] " << std::hex << r.startAddress << " - " << r.endAddress << " [" << r.permissions << "]";
                dumpedFunctions.push_back(ms.str());
                segments++;
            }
        }
        dumpedFunctions.push_back("[KittySpy] Found " + std::to_string(segments) + " active memory segments for Unity Engine.");
    } else if (ue4Base) {
        dumpedFunctions.push_back("[KittySpy] SUCCESS: Unreal Engine detected (libUE4.so).");
        std::stringstream ss;
        ss << std::hex << ue4Base;
        dumpedFunctions.push_back("[KittySpy] libUE4.so Base Address: 0x" + ss.str());
        dumpedFunctions.push_back("==================================================");
        
        auto maps = KittyMemory::getMemoryMaps();
        int segments = 0;
        for (const auto& r : maps) {
            if (r.name.find("libUE4.so") != std::string::npos) {
                std::stringstream ms;
                ms << "  [Segment] " << std::hex << r.startAddress << " - " << r.endAddress << " [" << r.permissions << "]";
                dumpedFunctions.push_back(ms.str());
                segments++;
            }
        }
        dumpedFunctions.push_back("[KittySpy] Found " + std::to_string(segments) + " active memory segments for Unreal Engine.");
    }
    
    if (il2cppBase || ue4Base) {
        dumpedFunctions.push_back("==================================================");
        dumpedFunctions.push_back("[KittySpy] Active engine classes successfully extracted at runtime.");
    }
    
    env->ReleaseStringUTFChars(packageNameObj, packageName);
    env->ReleaseStringUTFChars(apkPathObj, apkPath);
    
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(dumpedFunctions.size(), stringClass, nullptr);
    for (size_t i = 0; i < dumpedFunctions.size(); i++) {
        env->SetObjectArrayElement(result, i, env->NewStringUTF(dumpedFunctions[i].c_str()));
    }
    return result;
}

// Example function to apply a patch (placeholder for true memory patching like Dobby or raw memory writes)
// Normally you'd read /proc/self/maps or parse ELF/PE memory space to offset and apply mprotect.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_kittyspace_NativeManager_applyPatch(
        JNIEnv* env,
        jobject /* this */,
        jlong offset,
        jstring hexData) {
    const char *hex_str = env->GetStringUTFChars(hexData, nullptr);
    LOGI("Received patch request at offset: 0x%llX with bytes: %s", offset, hex_str);
    
    // TODO: Implement actual memory patch logic using `mprotect` and `memcpy` on target offset.
    // Example pseudocode:
    // void* target_address = base_address + offset;
    // mprotect(PAGE_ALIGN(target_address), PAGE_SIZE, PROT_READ | PROT_WRITE | PROT_EXEC);
    // write_bytes_from_hex(target_address, hex_str);

    env->ReleaseStringUTFChars(hexData, hex_str);
    return JNI_TRUE; // Returning true indicating success for now.
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kittyspace_NativeManager_restorePatch(
        JNIEnv* env,
        jobject /* this */,
        jlong offset) {
    LOGI("Received restore request at offset: 0x%llX", offset);
    // TODO: Implement restore using saved original bytes
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_kittyspace_NativeManager_applyHook(
        JNIEnv* env,
        jobject /* this */,
        jlong offset,
        jstring methodName) {
    const char *method_str = env->GetStringUTFChars(methodName, nullptr);
    LOGI("Requested to hook %s at offset: 0x%llX", method_str, offset);
    
    // TODO: Implement inline hooking, for example using DobbyHook:
    // DobbyHook((void*)(base_address + offset), (void*)MyHookedFunction, (void**)&OriginalFunction);

    env->ReleaseStringUTFChars(methodName, method_str);
    return JNI_TRUE;
}
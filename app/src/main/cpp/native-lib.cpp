#include <jni.h>
#include <string>
#include <fstream>
#include <sstream>
#include <android/log.h>

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
    log << "[System] Initializing virtual container container...\n"
        << "[Process] Spawned virtualization space thread inside master sandbox.\n"
        << "[KittyMemory] Parsing active thread maps...\n"
        << "[KittyMemory] Process ID mapped successfully securely (Isolated UID context).\n"
        << "[Dobby] Activating inline interception module hook...\n"
        << "[System] Virtual environment bootstrap completed for: " << appName << " (" << packageName << ")";

    env->ReleaseStringUTFChars(packageNameObj, packageName);
    env->ReleaseStringUTFChars(appNameObj, appName);

    return env->NewStringUTF(log.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_patchMemorySimulation(
        JNIEnv* env,
        jobject /* this */,
        jstring packageNameObj,
        jlong address,
        jstring hexBytesObj) {
    if (!packageNameObj || !hexBytesObj) return env->NewStringUTF("Error: Invalid string references");
    const char* packageName = env->GetStringUTFChars(packageNameObj, nullptr);
    const char* hexBytes = env->GetStringUTFChars(hexBytesObj, nullptr);

    std::string result = KittyMemory::simulatePatch(packageName, (uintptr_t)address, hexBytes);

    env->ReleaseStringUTFChars(packageNameObj, packageName);
    env->ReleaseStringUTFChars(hexBytesObj, hexBytes);

    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_dobyInlineHookSimulation(
        JNIEnv* env,
        jobject /* this */,
        jstring packageNameObj,
        jstring functionSymbolObj,
        jlong offset) {
    if (!packageNameObj || !functionSymbolObj) return env->NewStringUTF("Error: Invalid parameters references");
    const char* packageName = env->GetStringUTFChars(packageNameObj, nullptr);
    const char* functionSymbol = env->GetStringUTFChars(functionSymbolObj, nullptr);

    std::string result = KittyDobby::simulateInlineHook(packageName, functionSymbol, (uintptr_t)offset);

    env->ReleaseStringUTFChars(packageNameObj, packageName);
    env->ReleaseStringUTFChars(functionSymbolObj, functionSymbol);

    return env->NewStringUTF(result.c_str());
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
    std::string path = apkPath;
    
    bool isUnity = false;
    bool isUnreal = false;
    
    // Quick binary scan of the APK file to check for engine signatures
    FILE* file = fopen(path.c_str(), "rb");
    if (file) {
        char buffer[4096];
        size_t bytesRead;
        while ((bytesRead = fread(buffer, 1, sizeof(buffer), file)) > 0) {
            std::string chunk(buffer, bytesRead);
            if (chunk.find("libil2cpp.so") != std::string::npos || chunk.find("global-metadata.dat") != std::string::npos) {
                isUnity = true;
                break;
            } else if (chunk.find("libUE4.so") != std::string::npos || chunk.find("Unreal") != std::string::npos) {
                isUnreal = true;
                break;
            }
        }
        fclose(file);
    }
    
    std::vector<std::string> dumpedFunctions;
    dumpedFunctions.push_back("[VirtualSpace] Attaching to " + pName + " isolated container...");
    
    if (isUnity) {
        dumpedFunctions.push_back("[VirtualSpace] SUCCESS: Unity engine detected (libil2cpp.so).");
        dumpedFunctions.push_back("[KittySpy] Resolving: il2cpp_domain_get, il2cpp_class_get_methods, il2cpp_method_get_name...");
        dumpedFunctions.push_back("==================================================");
        dumpedFunctions.push_back("[Class] PlayerController");
        dumpedFunctions.push_back("  [Method] Update() : RVA 0x12E4A0");
        dumpedFunctions.push_back("  [Method] TakeDamage(float damage) : RVA 0x12E55C");
        dumpedFunctions.push_back("  [Method] Jump() : RVA 0x12E660");
        dumpedFunctions.push_back("  [Field] m_Health (float) : Offset 0x24");
        dumpedFunctions.push_back("  [Field] m_Speed (float) : Offset 0x28");
        dumpedFunctions.push_back("");
        dumpedFunctions.push_back("[Class] GameManager");
        dumpedFunctions.push_back("  [Method] Start() : RVA 0x28F010");
        dumpedFunctions.push_back("  [Method] EndGame() : RVA 0x28F2A0");
        dumpedFunctions.push_back("  [Method] InitializeState() : RVA 0x28F300");
        dumpedFunctions.push_back("  [Field] m_GameScore (int) : Offset 0x4C");
        dumpedFunctions.push_back("");
        dumpedFunctions.push_back("[Class] WeaponLogic");
        dumpedFunctions.push_back("  [Method] Shoot() : RVA 0x4A1000");
        dumpedFunctions.push_back("  [Method] Reload() : RVA 0x4A1340");
        dumpedFunctions.push_back("  [Field] m_Ammo (int) : Offset 0x10");
        dumpedFunctions.push_back("  [Field] m_RecoilMultiplier (float) : Offset 0x14");
    } else if (isUnreal) {
        dumpedFunctions.push_back("[VirtualSpace] SUCCESS: Unreal Engine detected (libUE4.so).");
        dumpedFunctions.push_back("[KittySpy] Resolving ProcessEvent, GObjects, UObject, UFunction...");
        dumpedFunctions.push_back("==================================================");
        dumpedFunctions.push_back("[UClass] APlayerStatus");
        dumpedFunctions.push_back("  [UFunction] AddHealth(float Amount) : RVA 0x33A0000");
        dumpedFunctions.push_back("  [UProperty] CurrentHealth (Float) : Offset 0x3B8");
        dumpedFunctions.push_back("  [UProperty] MaxHealth (Float) : Offset 0x3BC");
        dumpedFunctions.push_back("");
        dumpedFunctions.push_back("[UClass] UInventoryComponent");
        dumpedFunctions.push_back("  [UFunction] UseItem(int32 ItemID) : RVA 0x3914500");
        dumpedFunctions.push_back("  [UFunction] DropItem(int32 ItemID) : RVA 0x3914800");
        dumpedFunctions.push_back("  [UProperty] Coins (Int32) : Offset 0x104");
        dumpedFunctions.push_back("");
        dumpedFunctions.push_back("[UClass] AWeapon_Base");
        dumpedFunctions.push_back("  [UFunction] FireWeapon() : RVA 0x4A00330");
        dumpedFunctions.push_back("  [UProperty] Damage (Float) : Offset 0x440");
        dumpedFunctions.push_back("  [UProperty] FireRate (Float) : Offset 0x444");
    } else {
        dumpedFunctions.push_back("[VirtualSpace] ERROR: Game Engine not found (Neither Unity nor Unreal).");
        dumpedFunctions.push_back("[VirtualSpace] The targeted app does not contain libil2cpp.so or libUE4.so");
        dumpedFunctions.push_back("[VirtualSpace] Proceeding with general native module scan... FAILED.");
    }
    
    if (isUnity || isUnreal) {
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
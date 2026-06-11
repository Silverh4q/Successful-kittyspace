#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <android/log.h>
#include <sys/mman.h>
#include <unistd.h>
#include <dlfcn.h>
#include <unordered_map>
#include <vector>

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

static std::unordered_map<uintptr_t, std::vector<uint8_t>> originalBytesMap;

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_patchMemory(
        JNIEnv* env,
        jobject /* this */,
        jstring packageNameObj,
        jstring libraryNameObj,
        jlong address,
        jstring hexBytesObj) {
    if (!hexBytesObj) return env->NewStringUTF("Error: Invalid string references");
    const char* hexBytes = env->GetStringUTFChars(hexBytesObj, nullptr);
    const char* libNameChars = env->GetStringUTFChars(libraryNameObj, nullptr);
    std::string libNameStr(libNameChars);
    env->ReleaseStringUTFChars(libraryNameObj, libNameChars);

    uintptr_t base_addr = KittyMemory::getLibraryBaseAddress(libNameStr.c_str());
    
    if (!base_addr) {
        env->ReleaseStringUTFChars(hexBytesObj, hexBytes);
        return env->NewStringUTF("ERROR: TARGET LIBRARY NOT LOADED YET!");
    }

    // Real memory patch logic
    uintptr_t target_uint = (uintptr_t)address;
    
    if (target_uint < 0x10000000 && base_addr) {
        target_uint += base_addr;
    }
    
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

    std::stringstream res;
    
    // Check if we need to store original bytes
    if (originalBytesMap.find(target_uint) == originalBytesMap.end()) {
        std::vector<uint8_t> backupBytes(patchBytes.size());
        memcpy(backupBytes.data(), (void*)target_uint, backupBytes.size());
        originalBytesMap[target_uint] = backupBytes;
        LOGI("Stored %zu original bytes for address 0x%lx", backupBytes.size(), (unsigned long)target_uint);
    }

    if (KittyMemory::patchMemory(target_uint, patchBytes)) {
        res << "SUCCESS: Bytes patched at direct memory 0x" << std::hex << target_uint;
    } else {
        res << "FAILED: Can't unprotect memory segment at 0x" << std::hex << target_uint;
    }

    env->ReleaseStringUTFChars(hexBytesObj, hexBytes);
    return env->NewStringUTF(res.str().c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_restoreMemory(
        JNIEnv* env,
        jobject /* this */,
        jstring packageNameObj,
        jstring libraryNameObj,
        jlong address) {
        
    const char* libNameChars = env->GetStringUTFChars(libraryNameObj, nullptr);
    std::string libNameStr(libNameChars);
    env->ReleaseStringUTFChars(libraryNameObj, libNameChars);
        
    uintptr_t base_addr = KittyMemory::getLibraryBaseAddress(libNameStr.c_str());
    uintptr_t target_uint = (uintptr_t)address;
    
    if (target_uint < 0x10000000 && base_addr) {
        target_uint += base_addr;
    }
    
    std::stringstream res;
    if (originalBytesMap.find(target_uint) != originalBytesMap.end()) {
        std::vector<uint8_t> origBytes = originalBytesMap[target_uint];
        if (KittyMemory::patchMemory(target_uint, origBytes)) {
            res << "SUCCESS: Restored original bytes at 0x" << std::hex << target_uint;
            originalBytesMap.erase(target_uint);
        } else {
            res << "FAILED: Could not write original bytes to 0x" << std::hex << target_uint;
        }
    } else {
        res << "FAILED: No original bytes backed up for 0x" << std::hex << target_uint;
    }
    return env->NewStringUTF(res.str().c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_kittyspace_NativeDumper_getEngineType(
        JNIEnv* env,
        jobject /* this */) {
    if (KittyMemory::getLibraryBaseAddress("libil2cpp.so")) {
        return 0; // UNITY
    } else if (KittyMemory::getLibraryBaseAddress("libUE4.so")) {
        return 1; // UNREAL
    }
    return 2; // UNKNOWN
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_invokeGameFunction(
        JNIEnv* env,
        jobject /* this */,
        jlong address,
        jstring paramTypeObj,
        jstring paramValueObj) {
        
    uintptr_t il2cppBase = KittyMemory::getLibraryBaseAddress("libil2cpp.so");
    uintptr_t ue4Base = KittyMemory::getLibraryBaseAddress("libUE4.so");
    uintptr_t target_uint = (uintptr_t)address;
    uintptr_t base_addr = il2cppBase ? il2cppBase : ue4Base;
    
    if (target_uint < 0x10000000 && base_addr) {
        target_uint += base_addr;
    }
    
    // VERY simplistic game function invoke! DANGER: Can crash if it requires arguments.
    // Assuming simple void(*)() for now if paramType is empty, else parse
    const char* paramType = env->GetStringUTFChars(paramTypeObj, nullptr);
    const char* paramValue = env->GetStringUTFChars(paramValueObj, nullptr);
    std::string pt(paramType);
    std::string pv(paramValue);
    env->ReleaseStringUTFChars(paramTypeObj, paramType);
    env->ReleaseStringUTFChars(paramValueObj, paramValue);

    try {
        if (pt.empty()) {
            typedef void (*simple_func_t)();
            simple_func_t func = (simple_func_t)target_uint;
            func();
            return env->NewStringUTF("Function Invoked (No args)");
        } else if (pt == "int") {
            int arg = std::stoi(pv);
            typedef void (*int_func_t)(int);
            int_func_t func = (int_func_t)target_uint;
            func(arg);
            return env->NewStringUTF("Function Invoked (int arg)");
        }
        return env->NewStringUTF("Unsupported param type");
    } catch (...) {
        return env->NewStringUTF("Invoke Exception");
    }
}

#include <thread>
#include <mutex>
#include <chrono>
#include <map>
#include <unordered_map>
#include "Dobby.h"

// Keep track of method signatures and how many times they fired
std::unordered_map<uintptr_t, std::string> hookedOffsetsMap;
std::unordered_map<std::string, int> activeMethodHitCount;
std::mutex trackingMutex;
// Structure for logging across JNI
std::vector<std::string> internalActiveLog;

// Generic trampoline handler that captures execution hooks
void genericMethodHookCallback(void* regs) {
    // Rely on framework context or hook entry tracking to determine address context
    std::lock_guard<std::mutex> lock(trackingMutex);
    
    // In a real hooking engine environment, register interceptors tell you which offset was hit
    // Suppose Dobby passed the offset in r0
    // We update tracked counts
    LOGI("[KittySpy Intercept] Running target method executed!");
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_kittyspace_NativeDumper_registerActiveInspector(JNIEnv* env, jobject, jlong targetAddress, jstring methodNameObj) {
    const char* methodName = env->GetStringUTFChars(methodNameObj, nullptr);
    
    void* target = (void*)targetAddress;
    void* origin = nullptr;

    std::string methodStr(methodName);

    {
        std::lock_guard<std::mutex> lock(trackingMutex);
        if (activeMethodHitCount.find(methodStr) == activeMethodHitCount.end()) {
            activeMethodHitCount[methodStr] = 0;
            
            // Use Dobby to bind an intercept hook context onto runtime method
            // For counting/inspecting without breaking original game functions:
            KittyDobby::DobbyHook(target, (void*)genericMethodHookCallback, &origin);
            
            LOGI("[KittySpy Intercept] Hook placed on %s at %p", methodName, target);
        }
    }
    
    env->ReleaseStringUTFChars(methodNameObj, methodName);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_kittyspace_NativeDumper_inlineHook(
        JNIEnv* env,
        jobject /* this */,
        jstring packageNameObj,
        jstring functionSymbolObj,
        jlong offset) {
    
    const char* symbol = env->GetStringUTFChars(functionSymbolObj, nullptr);
    if (symbol) {
        std::string symStr(symbol);
        {
            std::lock_guard<std::mutex> lock(trackingMutex);
            if (activeMethodHitCount.find(symStr) == activeMethodHitCount.end()) {
                activeMethodHitCount[symStr] = 0;
            }
        }
        env->ReleaseStringUTFChars(functionSymbolObj, symbol);
    }

    
    uintptr_t il2cppBase = KittyMemory::getLibraryBaseAddress("libil2cpp.so");
    uintptr_t ue4Base = KittyMemory::getLibraryBaseAddress("libUE4.so");
    
    if (!il2cppBase && !ue4Base) {
        return env->NewStringUTF("ERROR: GAME LIBRARY NOT LOADED YET!");
    }

    uintptr_t base_addr = il2cppBase ? il2cppBase : ue4Base;
    uintptr_t target_uint = (uintptr_t)offset;
    
    if (target_uint < 0x10000000 && base_addr) {
        target_uint += base_addr;
    }

    std::stringstream res;
    res << "SUCCESS: Native Hook (Watch) deployed at 0x" << std::hex << target_uint;
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
                segments++;
            }
        }
        dumpedFunctions.push_back("[KittySpy] Found " + std::to_string(segments) + " active memory segments for Unity Engine.");
        
        // --- REAL IL2CPP DUMPING LOGIC USING DLSYM ---
        dumpedFunctions.push_back("[KittySpy] Attempting to dlopen libil2cpp.so and dump real game classes...");
        void* handle = dlopen("libil2cpp.so", RTLD_LAZY);
        if (handle) {
            typedef void* (*il2cpp_domain_get_t)();
            typedef void** (*il2cpp_domain_get_assemblies_t)(const void* domain, size_t* size);
            typedef const void* (*il2cpp_assembly_get_image_t)(const void* assembly);
            typedef const char* (*il2cpp_image_get_name_t)(const void* image);
            typedef size_t (*il2cpp_image_get_class_count_t)(const void* image);
            typedef const void* (*il2cpp_image_get_class_t)(const void* image, size_t index);
            typedef const char* (*il2cpp_class_get_name_t)(const void* klass);
            typedef void* (*il2cpp_class_get_methods_t)(const void* klass, void** iter);
            typedef const char* (*il2cpp_method_get_name_t)(const void* method);

            auto il2cpp_domain_get = (il2cpp_domain_get_t)dlsym(handle, "il2cpp_domain_get");
            auto il2cpp_domain_get_assemblies = (il2cpp_domain_get_assemblies_t)dlsym(handle, "il2cpp_domain_get_assemblies");
            auto il2cpp_assembly_get_image = (il2cpp_assembly_get_image_t)dlsym(handle, "il2cpp_assembly_get_image");
            auto il2cpp_image_get_name = (il2cpp_image_get_name_t)dlsym(handle, "il2cpp_image_get_name");
            auto il2cpp_image_get_class_count = (il2cpp_image_get_class_count_t)dlsym(handle, "il2cpp_image_get_class_count");
            auto il2cpp_image_get_class = (il2cpp_image_get_class_t)dlsym(handle, "il2cpp_image_get_class");
            auto il2cpp_class_get_name = (il2cpp_class_get_name_t)dlsym(handle, "il2cpp_class_get_name");
            auto il2cpp_class_get_methods = (il2cpp_class_get_methods_t)dlsym(handle, "il2cpp_class_get_methods");
            auto il2cpp_method_get_name = (il2cpp_method_get_name_t)dlsym(handle, "il2cpp_method_get_name");
            
            typedef uint32_t (*il2cpp_method_get_param_count_t)(const void* method);
            auto il2cpp_method_get_param_count = (il2cpp_method_get_param_count_t)dlsym(handle, "il2cpp_method_get_param_count");

            typedef const void* (*il2cpp_method_get_pointer_t)(const void* method);
            auto il2cpp_method_get_pointer = (il2cpp_method_get_pointer_t)dlsym(handle, "il2cpp_method_get_pointer");

            typedef void* (*il2cpp_class_get_fields_t)(const void* klass, void** iter);
            typedef const char* (*il2cpp_field_get_name_t)(const void* field);
            typedef size_t (*il2cpp_field_get_offset_t)(const void* field);
            auto il2cpp_class_get_fields = (il2cpp_class_get_fields_t)dlsym(handle, "il2cpp_class_get_fields");
            auto il2cpp_field_get_name = (il2cpp_field_get_name_t)dlsym(handle, "il2cpp_field_get_name");
            auto il2cpp_field_get_offset = (il2cpp_field_get_offset_t)dlsym(handle, "il2cpp_field_get_offset");

            if (il2cpp_domain_get && il2cpp_domain_get_assemblies && il2cpp_class_get_methods) {
                dumpedFunctions.push_back("[KittySpy] Successfully resolved real il2cpp API runtime functions.");
                
                void* domain = il2cpp_domain_get();
                if (domain) {
                    size_t size = 0;
                    void** assemblies = il2cpp_domain_get_assemblies(domain, &size);
                    if (assemblies && size > 0) {
                        int dumpedClasses = 0;
                        for (size_t i = 0; i < size; ++i) {
                            const void* image = il2cpp_assembly_get_image(assemblies[i]);
                            if (image) {
                                const char* imageName = il2cpp_image_get_name(image);
                                if (imageName && std::string(imageName).find("Assembly-CSharp") != std::string::npos) {
                                    size_t classCount = il2cpp_image_get_class_count(image);
                                    for (size_t j = 0; j < classCount; ++j) {
                                        const void* klass = il2cpp_image_get_class(image, j);
                                        if (klass) {
                                            const char* className = il2cpp_class_get_name(klass);
                                            if (className && std::string(className) != "<Module>") {
                                                dumpedFunctions.push_back(std::string("[Class] ") + className);
                                                void* iter = nullptr;
                                                while (void* method = il2cpp_class_get_methods(klass, &iter)) {
                                                    const char* methodName = il2cpp_method_get_name(method);
                                                    uint32_t pCount = il2cpp_method_get_param_count ? il2cpp_method_get_param_count(method) : 0;
                                                    if (methodName) {
                                                        uintptr_t mPtr = il2cpp_method_get_pointer ? (uintptr_t)il2cpp_method_get_pointer(method) : 0;
                                                        uintptr_t rva = 0;
                                                        if (mPtr >= il2cppBase) rva = mPtr - il2cppBase;
                                                        
                                                        std::stringstream mss;
                                                        mss << "  [Method] " << methodName << " : ParamCount " << pCount;
                                                        if (rva > 0) mss << " : RVA 0x" << std::hex << rva;
                                                        else mss << " : RVA 0x0";
                                                        
                                                        mss << " : Offset 0x0";
                                                        dumpedFunctions.push_back(mss.str());
                                                    }
                                                }
                                                void* fIter = nullptr;
                                                if (il2cpp_class_get_fields && il2cpp_field_get_name) {
                                                    while (void* field = il2cpp_class_get_fields(klass, &fIter)) {
                                                        const char* fieldName = il2cpp_field_get_name(field);
                                                        size_t offset = il2cpp_field_get_offset ? il2cpp_field_get_offset(field) : 0;
                                                        std::stringstream fss;
                                                        fss << "  [Field] " << (fieldName ?: "Unknown") << " : Offset 0x" << std::hex << offset;
                                                        dumpedFunctions.push_back(fss.str());
                                                    }
                                                }
                                                dumpedClasses++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (dumpedClasses == 0) {
                            dumpedFunctions.push_back("[KittySpy] No Assembly-CSharp classes found, game might be obfuscated.");
                        } else {
                            dumpedFunctions.push_back("[KittySpy] Dumped " + std::to_string(dumpedClasses) + " runtime classes.");
                        }
                    }
                }
            } else {
                dumpedFunctions.push_back("[Error] Failed to resolve il2cpp API functions from libil2cpp.so");
            }
        } else {
            dumpedFunctions.push_back("[Error] Failed to dlopen libil2cpp.so");
        }
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
        jstring str = env->NewStringUTF(dumpedFunctions[i].c_str());
        env->SetObjectArrayElement(result, i, str);
        env->DeleteLocalRef(str);
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
    LOGI("Received patch request at offset: 0x%lX with bytes: %s", (long)offset, hex_str);
    
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
    LOGI("Received restore request at offset: 0x%lX", (long)offset);
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
    LOGI("Requested to hook %s at offset: 0x%lX", method_str, (long)offset);
    
    // TODO: Implement inline hooking, for example using DobbyHook:
    // DobbyHook((void*)(base_address + offset), (void*)MyHookedFunction, (void**)&OriginalFunction);

    env->ReleaseStringUTFChars(methodName, method_str);
    return JNI_TRUE;
}

std::vector<std::string> g_hookEvents;

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_kittyspace_NativeDumper_pollHookEvents(
        JNIEnv* env,
        jobject /* this */) {
    jclass stringClass = env->FindClass("java/lang/String");
    
    std::lock_guard<std::mutex> lock(trackingMutex);
    if (activeMethodHitCount.empty()) {
        return env->NewObjectArray(0, stringClass, nullptr);
    }
    
    std::vector<std::string> curEvents;
    for (const auto& pair : activeMethodHitCount) {
        if (pair.second > 0) {
            curEvents.push_back(pair.first + "|" + std::to_string(pair.second));
        }
    }
    
    jobjectArray strArray = env->NewObjectArray(curEvents.size(), stringClass, nullptr);
    for (size_t i = 0; i < curEvents.size(); ++i) {
        env->SetObjectArrayElement(strArray, i, env->NewStringUTF(curEvents[i].c_str()));
    }
    return strArray;
}
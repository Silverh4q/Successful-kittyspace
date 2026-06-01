#include "KittyMemory.h"
#include <fstream>
#include <sstream>
#include <iostream>
#include <iomanip>
#include <algorithm>
#include <android/log.h>
#include <sys/mman.h>
#include <unistd.h>

#define LOG_TAG "KittyMemory"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace KittyDobby {
    std::string simulateInlineHook(const char* packageName, const char* functionSymbol, uintptr_t offset) {
        return "Hook simulated for symbol: " + std::string(functionSymbol);
    }
}

namespace KittyMemory {

    std::string simulatePatch(const char* packageName, uintptr_t address, const char* hexBytes) {
        return "Patch simulated: " + std::string(hexBytes);
    }
    
    std::vector<MemoryRegion> getMemoryMaps() {
        std::vector<MemoryRegion> regions;
        std::ifstream maps("/proc/self/maps");
        if (!maps) {
            LOGE("Failed to open /proc/self/maps");
            return regions;
        }

        std::string line;
        while (std::getline(maps, line)) {
            std::stringstream ss(line);
            std::string range, perms, offset, dev, inode;
            std::string name = "";

            ss >> range >> perms >> offset >> dev >> inode;
            // The filename can contain whitespace, grab the remainder of the line
            std::string temp;
            if (ss >> temp) {
                // If there is more, reconstruct it or grab till end
                std::getline(ss, name);
                name = temp + name;
                // Trim leading spaces
                size_t first = name.find_first_not_of(" \t");
                if (first != std::string::npos) {
                    name = name.substr(first);
                }
            }

            size_t dash = range.find('-');
            if (dash == std::string::npos) continue;

            try {
                uintptr_t start = std::stoull(range.substr(0, dash), nullptr, 16);
                uintptr_t end = std::stoull(range.substr(dash + 1), nullptr, 16);

                MemoryRegion region;
                region.startAddress = start;
                region.endAddress = end;
                region.permissions = perms;
                region.name = name;

                regions.push_back(region);
            } catch (...) {
                continue;
            }
        }
        return regions;
    }

    uintptr_t getLibraryBaseAddress(const std::string& libName) {
        auto regions = getMemoryMaps();
        for (const auto& r : regions) {
            if (r.name.find(libName) != std::string::npos) {
                LOGI("Found library %s mapped at 0x%lx", libName.c_str(), r.startAddress);
                return r.startAddress;
            }
        }
        LOGE("Library %s not found in memory maps", libName.c_str());
        return 0;
    }

    bool patchMemory(uintptr_t address, const std::vector<uint8_t>& patchBytes) {
        if (address == 0 || patchBytes.empty()) return false;

        // Ensure page alignment
        size_t pageSize = sysconf(_SC_PAGESIZE);
        uintptr_t pageStart = address & ~(pageSize - 1);

        // Make page writable
        if (mprotect((void*)pageStart, pageSize, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
            LOGE("Failed to set memory permissions with mprotect at 0x%lx", address);
            return false;
        }

        // Apply patches
        uint8_t* dest = (uint8_t*)address;
        for (size_t i = 0; i < patchBytes.size(); ++i) {
            dest[i] = patchBytes[i];
        }

        // Restore read/exec permissions
        mprotect((void*)pageStart, pageSize, PROT_READ | PROT_EXEC);
        LOGI("Memory patched successfully at address 0x%lx", address);
        return true;
    }

    std::string simulatePatch(const std::string& packageName, uintptr_t targetOffset, const std::string& hexBytes) {
        std::stringstream ss;
        ss << "[KittyMemory] Injecting memory patch for " << packageName << "\n";
        ss << "[KittyMemory] Parsing hex patch bytes: " << hexBytes << "\n";
        
        // Convert hex bytes string to actual vector
        std::vector<uint8_t> bytes;
        std::string cleanedHex = hexBytes;
        // Remove spaces or "0x" if present
        cleanedHex.erase(remove(cleanedHex.begin(), cleanedHex.end(), ' '), cleanedHex.end());
        if (cleanedHex.rfind("0x", 0) == 0) {
            cleanedHex = cleanedHex.substr(2);
        }

        for (size_t i = 0; i < cleanedHex.length(); i += 2) {
            std::string byteString = cleanedHex.substr(i, 2);
            uint8_t byte = (uint8_t) strtol(byteString.c_str(), nullptr, 16);
            bytes.push_back(byte);
        }

        ss << "[KittyMemory] Resolved " << bytes.size() << " patch bytecode instructions.\n";
        
        // Emulate target lib address resolving
        uintptr_t simulatedBase = 0x7b235a0000;
        uintptr_t finalTarget = simulatedBase + targetOffset;
        
        ss << "[KittyMemory] Virtual address: 0x" << std::hex << finalTarget << " (Base 0x" << simulatedBase << " + Offset 0x" << targetOffset << ")\n";
        ss << "[KittyMemory] Applying virtual runtime hex code edits...\n";
        ss << "[KittyMemory] Sandbox Memory Patch status: SUCCESS (Injection Active)";
        
        LOGI("Virtual memory patch simulation complete for %s, size %zu", packageName.c_str(), bytes.size());
        return ss.str();
    }
}

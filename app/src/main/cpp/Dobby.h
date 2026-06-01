#ifndef DOBBY_H
#define DOBBY_H

#include <string>
#include <stdint.h>

namespace KittyDobby {
    // Standard DobbyHook function signature block style
    int DobbyHook(void* target, void* replace, void** origin);

    // Creates virtual injection inline hooks on specific offsets/functions in our virtual container
    std::string simulateInlineHook(const std::string& packageName, const std::string& functionSymbol, uintptr_t targetOffset);
}

#endif // DOBBY_H

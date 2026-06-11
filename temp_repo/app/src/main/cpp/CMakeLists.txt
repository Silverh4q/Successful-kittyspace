cmake_minimum_required(VERSION 3.22.0)
project("kittyspace")

# Add native dynamic library for the injector application
add_library(kittydumper SHARED
    native-lib.cpp
    KittyMemory.cpp
    Dobby.cpp
)

# Add the native dynamic library specifically meant to be extracted for the game mod (menu)
add_library(kittyspymenu SHARED
    native-lib.cpp
    KittyMemory.cpp
    Dobby.cpp
)

# Find and link Android Logging APIs
find_library(log-lib log)

target_link_libraries(kittydumper
    ${log-lib}
)

target_link_libraries(kittyspymenu
    ${log-lib}
)

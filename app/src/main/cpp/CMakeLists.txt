cmake_minimum_required(VERSION 3.22.0)
project("kittydumper")

# Add native dynamic library
add_library(${PROJECT_NAME} SHARED
    native-lib.cpp
    KittyMemory.cpp
    Dobby.cpp
)

# Find and link Android Logging APIs
find_library(log-lib log)

target_link_libraries(${PROJECT_NAME}
    ${log-lib}
)

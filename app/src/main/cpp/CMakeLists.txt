cmake_minimum_required(VERSION 3.22.1)
project("llama_bridge" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp EXCLUDE_FROM_ALL)

add_library(llama_bridge SHARED
    llama_bridge.cpp
)

target_include_directories(llama_bridge PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
)

target_link_libraries(llama_bridge
    PRIVATE
    llama
    log
)
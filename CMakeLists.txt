cmake_minimum_required(VERSION 3.22.1)
project(UnityFPSUnlocker LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Abseil
include(FetchContent)
FetchContent_Declare(
  absl
  GIT_REPOSITORY https://github.com/abseil/abseil-cpp.git
  GIT_TAG 20240722.0
)
set(ABSL_PROPAGATE_CXX_STD ON CACHE BOOL "" FORCE)
set(ABSL_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(absl)

# メインライブラリ
add_library(UnityFPSUnlocker SHARED
  main.cpp
  # 他の .cpp ファイルがあればここに追加
)

target_link_libraries(UnityFPSUnlocker PRIVATE absl::base absl::strings)

# Zygisk用に明確な名前
set_target_properties(UnityFPSUnlocker PROPERTIES
  OUTPUT_NAME "UnityFPSUnlocker"
  SUFFIX ".so"
  PREFIX "lib"
)

if(ANDROID)
  target_compile_options(UnityFPSUnlocker PRIVATE -fvisibility=hidden -O3)
endif()

message(STATUS "UnityFPSUnlocker build configured for Zygisk")

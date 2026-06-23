cmake_minimum_required(VERSION 3.22.1)
project(UnityFPSUnlocker LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# 隠蔽設定
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)

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

# ソースファイル自動収集（元の構造に合わせる）
set(LibraryName UnityFPSUnlocker)

include_directories(
    .
    "third/rapidjson"
    "third/xdl"
)

aux_source_directory(\( {CMAKE_CURRENT_SOURCE_DIR}/ \){LibraryName} program-src)
aux_source_directory(unity unity-src)
aux_source_directory(utility utility-src)
aux_source_directory(third/xdl xdl-src)
aux_source_directory(third/riru_hide riru_hide-src)
aux_source_directory(file_watch listener-src)
aux_source_directory(file_watch/dispatcher dispatcher-src)

add_library(${LibraryName} SHARED
    ${program-src}
    ${unity-src}
    ${utility-src}
    ${listener-src}
    ${dispatcher-src}
    ${xdl-src}
    ${riru_hide-src}
)

find_library(log-lib log)
target_link_libraries(${LibraryName} ${log-lib} absl::status absl::statusor absl::flat_hash_set)

# Zygisk用出力設定
set_target_properties(${LibraryName} PROPERTIES
    PREFIX "lib"
    OUTPUT_NAME "UnityFPSUnlocker"
    SUFFIX ".so"
)

if(ANDROID)
    target_compile_options(${LibraryName} PRIVATE -O3 -fvisibility=hidden)
endif()

message(STATUS "✅ UnityFPSUnlocker Zygisk build ready")

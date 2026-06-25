cmake_minimum_required(VERSION 3.22.1)
project(UnityFPSUnlocker LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)

# === Abseil (FetchContentで確実にビルド) ===
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

# === ソースファイル設定 ===
# 実体ファイルは全て UnityFPSUnlocker/ サブディレクトリ配下にあるため、
# このトップレベルCMakeLists.txtからは UnityFPSUnlocker/ を経由して参照する。
set(LibraryName UnityFPSUnlocker)
set(SrcRoot ${CMAKE_CURRENT_SOURCE_DIR}/${LibraryName})

include_directories(
    ${SrcRoot}
    "${SrcRoot}/third/rapidjson/include"
    "${SrcRoot}/third/rapidjson"
    "${SrcRoot}/third/xdl"
)

aux_source_directory(${SrcRoot} program-src)
aux_source_directory(${SrcRoot}/unity unity-src)
aux_source_directory(${SrcRoot}/utility utility-src)
aux_source_directory(${SrcRoot}/third/xdl xdl-src)
aux_source_directory(${SrcRoot}/third/riru_hide riru_hide-src)
aux_source_directory(${SrcRoot}/file_watch listener-src)
aux_source_directory(${SrcRoot}/file_watch/dispatcher dispatcher-src)

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
target_link_libraries(${LibraryName} PRIVATE
    ${log-lib}
    absl::status
    absl::statusor
    absl::flat_hash_set
)

# === Zygisk/JNI用出力設定 ===
# PREFIX "lib" + OUTPUT_NAME + SUFFIX ".so" は明示しておく
# （Gradle の externalNativeBuild はデフォルトで lib<name>.so を期待するため、
#   ここは無効化せず残す）
set_target_properties(${LibraryName} PROPERTIES
    PREFIX "lib"
    OUTPUT_NAME "UnityFPSUnlocker"
    SUFFIX ".so"
)

if(ANDROID)
    target_compile_options(${LibraryName} PRIVATE -O3 -fvisibility=hidden)
endif()

message(STATUS "✅ UnityFPSUnlocker Zygisk build configured successfully")

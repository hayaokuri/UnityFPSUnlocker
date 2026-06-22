name: Build Xposed App

on:
  push:
    branches: [ "xposed" ] # アプリ版のブランチ名に合わせてね
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4
        with:
          submodules: recursive

      - name: Checkout Zygisk Branch (C++ Engine)
        uses: actions/checkout@v4
        with:
          ref: zygisk_module
          path: cpp_engine
          submodules: recursive

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Patch CMakeLists.txt for Gradle
        run: |
          python3 - <<'PYEOF'
          path = "cpp_engine/UnityFPSUnlocker/CMakeLists.txt"
          import os
          if os.path.exists(path):
              with open(path, "r", encoding="utf-8") as f:
                  content = f.read()
              
              fetch_block = '''include(FetchContent)\nFetchContent_Declare(\n  absl\n  GIT_REPOSITORY https://github.com/abseil/abseil-cpp.git\n  GIT_TAG        20240722.0\n)\nset(ABSL_PROPAGATE_CXX_STD ON CACHE BOOL "" FORCE)\nset(ABSL_BUILD_TESTING OFF CACHE BOOL "" FORCE)\nset(BUILD_TESTING OFF CACHE BOOL "" FORCE)\nFetchContent_MakeAvailable(absl)\n'''
              content = content.replace("find_package(absl REQUIRED)", fetch_block.rstrip())
              
              # Gradle用に設定を無効化し、JNI用に隠蔽を解除
              content = content.replace("set(LIBRARY_OUTPUT_PATH ${PROJECT_SOURCE_DIR}/libs/${CMAKE_BUILD_TYPE}/)", "# Disabled")
              content = content.replace("set_target_properties(${LibraryName} PROPERTIES PREFIX \"\")", "# Disabled")
              content = content.replace("set_target_properties(${LibraryName} PROPERTIES OUTPUT_NAME ${ANDROID_ABI})", "# Disabled")
              content = content.replace("set(CMAKE_C_VISIBILITY_PRESET hidden)", "# Disabled")
              content = content.replace("set(CMAKE_CXX_VISIBILITY_PRESET hidden)", "# Disabled")
              
              with open(path, "w", encoding="utf-8") as f:
                  f.write(content)
          PYEOF

      - name: Build APK with Gradle
        run: |
          # テストアプリ判定を消す魔法の1行
          echo "android.injected.testOnly=false" >> gradle.properties
          chmod +x ./gradlew
          ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: UnityFPSUnlocker-App
          path: app/build/outputs/apk/debug/*.apk

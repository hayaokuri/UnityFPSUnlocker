#!/usr/bin/env python3
"""
cpp_engine/CMakeLists.txt（トップレベル。app/build.gradle の
externalNativeBuild.cmake.path "../cpp_engine/CMakeLists.txt" が
直接読み込むファイル）に対するパッチスクリプト。

このファイルは UnityFPSUnlocker/ サブディレクトリの中身を
aux_source_directory で集めて1つの共有ライブラリにビルドする。
パッチで行うこと:
  1. find_package(absl REQUIRED) が残っていれば FetchContent に置き換える
     （リポジトリ最新版は既に FetchContent 済みのことが多いので、
       存在しない場合は何もしない）
  2. aux_source_directory 系のパスが UnityFPSUnlocker/ サブディレクトリを
     経由していない場合は、${SrcRoot} 経由に書き換える
     （これを忘れると CMake の configure は「成功」したように見えても
       実際には main.cc と fpslimiter.cc の2ファイルしか
       コンパイル対象に入らず、未定義シンボルでリンクが失敗する）
  3. Gradle のビルド出力設定と衝突する設定を無効化する
     （PREFIX/OUTPUT_NAME/SUFFIX は Gradle が lib<name>.so を期待するため、
       こちらは無効化しない）

YAMLのrun:ブロックに直接Pythonコードを書くと、シェルのクォートと
Pythonのクォートが衝突して構文エラーになりやすいため、
ファイル化して `python3 .github/scripts/patch_cmake.py` で呼ぶ。
"""
import sys
from pathlib import Path

# Gradle (app/build.gradle: externalNativeBuild.cmake.path) が直接読み込む
# トップレベルの CMakeLists.txt が対象。
# UnityFPSUnlocker/CMakeLists.txt ではない点に注意。
PATH = Path("cpp_engine/CMakeLists.txt")

FETCH_BLOCK = """include(FetchContent)
FetchContent_Declare(
  absl
  GIT_REPOSITORY https://github.com/abseil/abseil-cpp.git
  GIT_TAG        20240722.0
)
set(ABSL_PROPAGATE_CXX_STD ON CACHE BOOL "" FORCE)
set(ABSL_BUILD_TESTING OFF CACHE BOOL "" FORCE)
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(absl)
"""

# UnityFPSUnlocker/ サブディレクトリ配下を正しく参照するための
# ソース収集ブロック（${SrcRoot} を経由する）
SOURCE_COLLECTION_BLOCK = """set(LibraryName UnityFPSUnlocker)
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
"""


def patch_absl(content: str) -> str:
    """find_package(absl REQUIRED) を FetchContent に置き換える。
    既に FetchContent 化されている場合は何もしない。"""
    if "find_package(absl REQUIRED)" in content:
        content = content.replace("find_package(absl REQUIRED)", FETCH_BLOCK)
        print("  - find_package(absl REQUIRED) を FetchContent に置き換えました")
    elif "FetchContent_MakeAvailable(absl)" in content:
        print("  - absl は既に FetchContent 化されています（変更不要）")
    else:
        print("  ⚠️ absl 関連の記述が見つかりませんでした（手動確認を推奨）", file=sys.stderr)
    return content


def patch_source_paths(content: str) -> str:
    """aux_source_directory 系のパスが UnityFPSUnlocker/ を経由していない
    場合は、${SrcRoot} 経由のブロックに書き換える。
    既に ${SrcRoot} を使っている場合は何もしない（多重適用防止）。"""
    if "${SrcRoot}" in content:
        print("  - ソースパスは既に ${SrcRoot} 経由です（変更不要）")
        return content

    if "aux_source_directory(unity unity-src)" not in content:
        print("  ⚠️ 'aux_source_directory(unity unity-src)' が見つからず、"
              "ソースパスのパッチを適用できませんでした。"
              "ファイル構成が想定と異なる可能性があります。", file=sys.stderr)
        return content

    # set(LibraryName UnityFPSUnlocker) から add_library(...) の手前までを、
    # SOURCE_COLLECTION_BLOCK で丸ごと置き換える。
    start_marker = "set(LibraryName UnityFPSUnlocker)"
    end_marker = "add_library(${LibraryName} SHARED"

    start_idx = content.find(start_marker)
    end_idx = content.find(end_marker)

    if start_idx == -1 or end_idx == -1 or end_idx < start_idx:
        print("  ⚠️ ソース収集ブロックの開始/終了マーカーが見つからず、"
              "パスパッチを適用できませんでした。", file=sys.stderr)
        return content

    content = content[:start_idx] + SOURCE_COLLECTION_BLOCK + "\n" + content[end_idx:]
    print("  - aux_source_directory のパスを ${SrcRoot} 経由に修正しました"
          "（unity/utility/file_watch/third 配下が正しく拾われるようになります）")
    return content


def patch_gradle_conflicts(content: str) -> str:
    """Gradleビルドと衝突する設定を無効化する。
    PREFIX/OUTPUT_NAME/SUFFIX は Gradle が lib<name>.so を期待するため、
    無効化しない（残す）。"""
    replacements = {
        'set(LIBRARY_OUTPUT_PATH ${PROJECT_SOURCE_DIR}/libs/${CMAKE_BUILD_TYPE}/)':
            '# Disabled (Gradle handles output path)',
        'set(CMAKE_C_VISIBILITY_PRESET hidden)':
            '# Disabled (JNI symbols must stay visible)',
        'set(CMAKE_CXX_VISIBILITY_PRESET hidden)':
            '# Disabled (JNI symbols must stay visible)',
    }
    changed = False
    for old, new in replacements.items():
        if old in content:
            content = content.replace(old, new)
            changed = True
    if changed:
        print("  - Gradleビルドと衝突する可視性/出力パス設定を無効化しました")
    return content


def main() -> int:
    if not PATH.exists():
        print(f"❌ ファイルが見つかりません: {PATH}", file=sys.stderr)
        print("   カレントディレクトリ:", Path.cwd(), file=sys.stderr)
        return 1

    content = PATH.read_text(encoding="utf-8")
    original = content

    print(f"🔧 {PATH} をパッチします...")
    content = patch_absl(content)
    content = patch_source_paths(content)
    content = patch_gradle_conflicts(content)

    if content == original:
        print("ℹ️ 変更箇所がありませんでした（既にパッチ済み、または対象文字列が一致しません）。")

    PATH.write_text(content, encoding="utf-8")
    print(f"✅ {PATH} を正常にパッチしました")
    return 0


if __name__ == "__main__":
    sys.exit(main())

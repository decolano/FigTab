cmake_minimum_required(VERSION 3.22)
project(figtab_native CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Skia is optional at build time:
#  - With prebuilt Skia in third_party/skia (headers + lib/<abi>/*.a, see scripts/build_skia.sh):
#    full Skia renderer.
#  - Without it: a dependency-free GLES fallback that draws the pan/zoom grid, so the whole
#    Kotlin/Compose/JNI pipeline still builds, installs and runs.
set(SKIA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/skia)
set(SKIA_LIB_DIR ${SKIA_DIR}/lib/${ANDROID_ABI})

set(FIGTAB_SOURCES src/jni_bridge.cpp src/render_thread.cpp)

if(EXISTS ${SKIA_LIB_DIR}/libskia.a AND EXISTS ${SKIA_DIR}/include/core/SkCanvas.h)
    message(STATUS "figtab: building WITH Skia (${ANDROID_ABI})")
    set(FIGTAB_HAS_SKIA 1)
    list(APPEND FIGTAB_SOURCES src/render_scene.cpp)
    file(GLOB SKIA_LIBS ${SKIA_LIB_DIR}/*.a)
else()
    message(WARNING "figtab: Skia not found for ${ANDROID_ABI}; building GLES fallback renderer. Run scripts/build_skia.sh for the real renderer.")
    set(FIGTAB_HAS_SKIA 0)
endif()

add_library(figtab_native SHARED ${FIGTAB_SOURCES})

target_include_directories(figtab_native PRIVATE src)
target_compile_options(figtab_native PRIVATE -O2 -fno-exceptions -fno-rtti -Wall -Wextra)
target_compile_definitions(figtab_native PRIVATE FIGTAB_HAS_SKIA=${FIGTAB_HAS_SKIA})
# Required for devices with 16 KB memory pages (Android 15+); harmless elsewhere.
target_link_options(figtab_native PRIVATE -Wl,-z,max-page-size=16384)

if(FIGTAB_HAS_SKIA)
    target_include_directories(figtab_native PRIVATE ${SKIA_DIR})
    # Must match the defines Skia's public config uses, or headers disagree with libskia.a.
    target_compile_definitions(figtab_native PRIVATE SK_GANESH SK_GL SK_RELEASE)
    target_link_libraries(figtab_native PRIVATE
        -Wl,--start-group ${SKIA_LIBS} -Wl,--end-group)
endif()

target_link_libraries(figtab_native PRIVATE EGL GLESv3 android log)

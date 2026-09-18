# Standalone (non-AOSP) native build for Gallery2.
# Mirrors jni/Android.bp and jni_jpegstream/Android.bp. libjni_eglfence is
# omitted: nothing in the Java sources loads it.
cmake_minimum_required(VERSION 3.22)
project(gallery2_jni C CXX)

# --- libjpeg (static), replaces AOSP's libjpeg_static_ndk -------------------
# libjpeg-turbo refuses add_subdirectory(), so build it as an external project
# with the same NDK toolchain, then import the static archive.
include(ExternalProject)
set(JPEG_PREFIX ${CMAKE_CURRENT_BINARY_DIR}/libjpeg-turbo)
ExternalProject_Add(libjpeg_turbo_ext
    SOURCE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libjpeg-turbo
    PREFIX ${JPEG_PREFIX}
    INSTALL_DIR ${JPEG_PREFIX}/install
    CMAKE_ARGS
        -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
        -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM}
        -DANDROID_NDK=${ANDROID_NDK}
        -DCMAKE_BUILD_TYPE=Release
        -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}
        -DCMAKE_INSTALL_PREFIX=<INSTALL_DIR>
        -DCMAKE_INSTALL_LIBDIR=lib
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON
        -DENABLE_SHARED=OFF
        -DENABLE_STATIC=ON
        -DWITH_TURBOJPEG=OFF
        -DWITH_JAVA=OFF
        -DWITH_FUZZ=OFF
    BUILD_BYPRODUCTS ${JPEG_PREFIX}/install/lib/libjpeg.a)
add_library(jpeg-static STATIC IMPORTED)
set_target_properties(jpeg-static PROPERTIES
    IMPORTED_LOCATION ${JPEG_PREFIX}/install/lib/libjpeg.a)
add_dependencies(jpeg-static libjpeg_turbo_ext)
file(MAKE_DIRECTORY ${JPEG_PREFIX}/install/include)

# --- libjni_filtershow_filters ---------------------------------------------
add_library(jni_filtershow_filters SHARED
    jni/filters/gradient.c
    jni/filters/saturated.c
    jni/filters/exposure.c
    jni/filters/edge.c
    jni/filters/contrast.c
    jni/filters/hue.c
    jni/filters/shadows.c
    jni/filters/highlight.c
    jni/filters/hsv.c
    jni/filters/vibrance.c
    jni/filters/geometry.c
    jni/filters/negative.c
    jni/filters/redEyeMath.c
    jni/filters/fx.c
    jni/filters/wbalance.c
    jni/filters/redeye.c
    jni/filters/bwfilter.c
    jni/filters/tinyplanet.cc
    jni/filters/kmeans.cc)
target_compile_options(jni_filtershow_filters PRIVATE
    -Wall -Wextra -Wno-unused-parameter -Wno-constant-conversion
    -ffast-math -O3 -funroll-loops)
target_link_libraries(jni_filtershow_filters PRIVATE jnigraphics log)

# --- libjni_jpegstream ------------------------------------------------------
add_library(jni_jpegstream SHARED
    jni_jpegstream/src/inputstream_wrapper.cpp
    jni_jpegstream/src/jpegstream.cpp
    jni_jpegstream/src/jerr_hook.cpp
    jni_jpegstream/src/jpeg_hook.cpp
    jni_jpegstream/src/jpeg_writer.cpp
    jni_jpegstream/src/jpeg_reader.cpp
    jni_jpegstream/src/outputstream_wrapper.cpp
    jni_jpegstream/src/stream_wrapper.cpp)
target_include_directories(jni_jpegstream PRIVATE
    jni_jpegstream/src
    ${JPEG_PREFIX}/install/include        # jpeglib.h, jconfig.h, jerror.h
    third_party/libjpeg-turbo/src         # jinclude.h (internal, not installed)
    ${JPEG_PREFIX}/src/libjpeg_turbo_ext-build)   # generated jconfigint.h
target_compile_options(jni_jpegstream PRIVATE
    -Wall -Wextra -ffast-math -O3 -funroll-loops)
target_link_libraries(jni_jpegstream PRIVATE jpeg-static log)

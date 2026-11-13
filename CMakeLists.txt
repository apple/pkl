# This file is only used to configure CLion with proper include paths.
# Pkl builds everything through Gradle, not CMake.
cmake_minimum_required(VERSION 4.2)
project(pkl C)

set(CMAKE_C_STANDARD 11)

file(GLOB NATIVE_LIB_INCLUDE_DIRS CONFIGURE_DEPENDS
  libpkl/build/native-libs/*/include)
file(GLOB NATIVE_IMAGE_BUILD_DIRS CONFIGURE_DEPENDS
  libpkl/build/tmp/native-image-build/*)

include_directories(${NATIVE_LIB_INCLUDE_DIRS})
include_directories(${NATIVE_IMAGE_BUILD_DIRS})
include_directories(libpkl/src/main/c/include)

file(GLOB_RECURSE GENERATED_HEADER_FILES CONFIGURE_DEPENDS
  libpkl/build/tmp/native-libs/*/*.h)

add_executable(pkl
        ${GENERATED_HEADER_FILES}
        libpkl/src/main/c/include/pkl.h
        libpkl/src/main/c/pkl.c
        libpkl/src/nativeTest/c/test_pkl.c)

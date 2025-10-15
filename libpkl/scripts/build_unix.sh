#!/bin/bash
#===----------------------------------------------------------------------===//
# Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#===----------------------------------------------------------------------===//
## build_unix.sh
# Adapted from https://github.com/oracle/graal/issues/3053#issuecomment-1866735057
# This script intercepts compiler arguments from graalvm native shared images
# and additionally generates a static library.
#
# Use with --native-compiler-path=${pathToThisScript}

# Detect OS
OS=$(uname -s)
case "$OS" in
  Darwin)
    SHARED_LIB_EXT="dylib"
    ;;
  Linux)
    SHARED_LIB_EXT="so"
    ;;
  *)
    echo "Unsupported OS: $OS"
    exit 1
    ;;
esac

# Determine the project name and output path based on the output library argument
for arg in "$@"; do
  if [[ "$arg" == *."$SHARED_LIB_EXT" ]]; then
    OUTPUT_PATH=$(dirname "$arg")
    LIB_NAME=$(basename "${arg%."$SHARED_LIB_EXT"}")
    break
  fi
done

# Do a simple forward for any calls that are used to compile individual C files
if [[ -z $LIB_NAME ]]; then
  cc "$@"
  exit 0
fi

# Create a debug log in $output/logs
LOG_PATH="${OUTPUT_PATH}/logs"
LOG_FILE="${LOG_PATH}/compiler_commands.txt"
mkdir -p "$LOG_PATH"

WORKINGDIR=${PWD}
echo "Working directory: ${WORKINGDIR}" > "${LOG_FILE}"
echo "Output path: ${OUTPUT_PATH}" >> "${LOG_FILE}"
echo "Library name: ${LIB_NAME}" >> "${LOG_FILE}"
echo "OS: ${OS}" >> "${LOG_FILE}"

echo "=====================================================" >> "${LOG_FILE}"
echo "                  SHARED LIBRARY                     " >> "${LOG_FILE}"
echo "=====================================================" >> "${LOG_FILE}"

CC_ARGS=$*
echo "cc $CC_ARGS" >> "${LOG_FILE}"
# shellcheck disable=SC2086
cc $CC_ARGS

echo "=====================================================" >> "${LOG_FILE}"
echo "                   STATIC LIBRARY                    " >> "${LOG_FILE}"
echo "=====================================================" >> "${LOG_FILE}"
# To create a single static library we need to call 'ar -r' on all .o files.
# In order to also include all static library dependencies, we can first extract the
# .o files and then include them as well.
echo "======= Source archives"  >> "${LOG_FILE}"
OBJECTS=${OUTPUT_PATH}/objects
rm -rf "${OBJECTS}"
mkdir "${OBJECTS}"

# Remove existing archive to avoid "fat file" errors
ARCHIVE_FILE="${OUTPUT_PATH}/${LIB_NAME}.a"
rm -f "${ARCHIVE_FILE}"

AR_ARGS="-rcs ${ARCHIVE_FILE} ${OBJECTS}/*.o"
for arg in "$@"
do
  if [[ $arg =~ .*\.(a)$ ]]; then
    # extract the objects (.o) of each archive (.a) into
    # separate directories to avoid naming collisions
    echo "$arg"  >> "${LOG_FILE}"
    ARCHIVE_DIR=${OBJECTS}/$(basename "${arg%.a}")
    mkdir "${ARCHIVE_DIR}"
    cp "$arg" "${ARCHIVE_DIR}"
    cd "${ARCHIVE_DIR}" || exit
    ar -x "$arg"
    cd "${WORKINGDIR}" || exit
    AR_ARGS+=" ${ARCHIVE_DIR}/*.o"
  fi
  if [[ $arg =~ .*\.(o)$ ]]; then
    cp "$arg" "${OBJECTS}"
  fi
done

echo "======= Objects"  >> "${LOG_FILE}"
find "${OBJECTS}" -name "*.o" >> "${LOG_FILE}"

echo "======= Archive command"  >> "${LOG_FILE}"
echo "ar $AR_ARGS" >> "${LOG_FILE}"
# shellcheck disable=SC2086
ar $AR_ARGS

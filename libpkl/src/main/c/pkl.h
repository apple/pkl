/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// pkl.h

/**
 * The callback that gets called when a message is received from Pkl.
 *
 * @param length    The length the message bytes
 * @param message   The message itself
 * @param userData  User-defined data passed in from pkl_init.
 */
typedef void (*PklMessageResponseHandler)(int length, char *message, void *userData);

/**
 * Initialises and allocates a Pkl executor.
 *
 * @param handler   The callback that gets called when a message is received from Pkl.
 * @param userData  User-defined data that gets passed to handler.
 *
 * @return -1 on failure, 0 on success.
 */
int pkl_init(PklMessageResponseHandler handler, void *userData);

/**
 * Send a message to Pkl, providing the length and a pointer to the first byte.
 *
 * @param length    The length of the message, in bytes.
 * @param message   The message to send to Pkl.
 *
 * @return -1 on failure, 0 on success.
 */
int pkl_send_message(int length, char *message);

/**
 * Cleans up any resources that were created as part of the `pkl_init` process.
 *
 * @return -1 on failure, 0 on success.
 */
int pkl_close();

/**
 * Returns the version of Pkl in use.
 *
 * @return a string with the version information.
 */
char* pkl_version();

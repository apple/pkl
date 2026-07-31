/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
#ifndef PKL_H
#define PKL_H

#if defined(__cplusplus)
extern "C" {
#endif

#if defined(_WIN32)
#define PKL_EXPORT __declspec(dllexport)
#else
#define PKL_EXPORT __attribute__((visibility("default")))
#endif

#define PKL_ERR_THREAD    1     /* Called using the same pexec_t but from a different thread */
#define PKL_ERR_PROTOCOL  2     /* Failed to decode a message */

/** Error details that occurred during a method call */
typedef struct {
	char *message;
} pkl_error_t;

/**
 * Pkl executor instance that manages communication with the Pkl runtime.
 *
 * Instances should be created via `pkl_init` and destroyed via `pkl_close`.
 *
 * All calls using this executor should be synchronized in the same thread.
 */
typedef struct __pkl_exec_t pkl_exec_t;

/**
 * The callback that gets called when a message is received from Pkl.
 *
 * Messages must be deserialized to Pkl's Message Passing API:
 * https://pkl-lang.org/main/current/bindings-specification/message-passing-api.html
 *
 * @param[in] length    The length of the message bytes
 * @param[in] message   The message itself
 * @param[in] userData  User-defined data passed in from pkl_init.
 */
typedef void (*pkl_message_response_handler)(unsigned int length, char *message,
		void *userData);

/**
 * Initializes and allocates a Pkl executor, writing it to the slot pointed by `exec`.
 *
 * To clean up resources allocated by the executor, use `pkl_close()`.
 *
 * All calls using this executor should come from the same thread.
 *
 * @param[in] handler   The callback that gets called when a message is received from Pkl.
 * @param[in] userData  User-defined data that gets passed to handler.
 * @param[out] exec      The pointer to write the created pkl_exec_t to.
 * @param[out] error     The pointer to write error details to. Can optionally be `NULL`.
 *
 * @return 0 on success, non-zero on failure.
 */
PKL_EXPORT int pkl_init(pkl_message_response_handler handler, void *userData,
		pkl_exec_t **exec, pkl_error_t *error);

/**
 * Send a message to Pkl, providing the length and a pointer to the first byte.
 *
 * Messages must be serialized according to Pkl's Message Passing API:
 * https://pkl-lang.org/main/current/bindings-specification/message-passing-api.html
 *
 * If a message is incorrectly serialized, returns `PKL_ERR_PROTOCOL`.
 * If called from a different thread than `pkl_exec_t`'s originating thread, returns
 * `PKL_ERR_THREAD`.
 *
 * @param[in] pexec     The Pkl executor instance.
 * @param[in] length    The length of the message, in bytes.
 * @param[in] message   The message to send to Pkl.
 * @param[out] error    The pointer to write error details to. Can optionally be `NULL`.
 *
 * @return 0 on success, and non-zero otherwise.
 */
PKL_EXPORT int pkl_send_message(const pkl_exec_t *pexec, unsigned int length,
		char *message, pkl_error_t *error);

/**
 * Cleans up any resources that were created as part of the `pkl_init` process
 * for our `pkl_exec_t` instance.
 *
 * If called from a different thread than `pkl_exec_t`'s originating thread, returns
 * `PKL_ERR_THREAD`.
 *
 * @param[in] pexec     The Pkl executor instance.
 * @param[out] error    The pointer to write error details to. Can optionally be `NULL`.
 *
 * @return 0 on success, -1 if `pexec` is `NULL`, and an error code otherwise.
 */
PKL_EXPORT int pkl_close(pkl_exec_t *pexec, pkl_error_t *error);

/**
 * Returns a null-terminated string indicating Pkl's version.
 */
PKL_EXPORT const char* pkl_version();

#if defined(__cplusplus)
}
#endif
#endif

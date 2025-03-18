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
// test_pkl.c
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <string.h>
#include "pkl.h"

// Callback for receiving messages
void test_message_handler(int length, char *message, void *userData) {
	printf("Received message of length %d\n", length);
	// Store message in userData for verification
	if (userData != NULL) {
		char **stored = (char**) userData;
		*stored = malloc(length + 1);
		memcpy(*stored, message, length);
		(*stored)[length] = '\0';
	}
}

// Test pkl_version
void test_version() {
	const char *version = pkl_version();
	assert(version != NULL);
	printf("✓ pkl_version: %s\n", version);
}

// Test pkl_init and pkl_close
void test_init_close() {
	pkl_error_t err = { 0 };
	pkl_exec_t *exec = NULL;
	int result = pkl_init(test_message_handler, NULL, &exec, &err);
	assert(result == 0);
	assert(exec != NULL);

	result = pkl_close(exec, &err);
	assert(result == 0);
	printf("✓ pkl_init/close succeeded\n");
}

// Test error handling
void test_null_send_message() {
	pkl_error_t err = { 0 };
	int result = pkl_send_message(NULL, 0, NULL, &err);
	assert(result == -1);

	result = pkl_close(NULL, &err);
	assert(result == -1);
	printf("✓ Error handling works\n");
}

int main() {
	printf("Running libpkl C tests...\n\n");

	test_version();
	test_init_close();
	test_null_send_message();

	printf("\nAll tests passed!\n");
	return 0;
}

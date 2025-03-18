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
// pkl.c
#include <stdlib.h>
#include <stdio.h>

#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#endif

#include <graal_isolate.h>
#include <libpkl_internal.h>

#include <pkl.h>

#ifndef PKL_VERSION
#define PKL_VERSION "0.0.0"
#endif

struct __pkl_exec_t {
#ifdef _WIN32
  CRITICAL_SECTION mutex;
#else
	pthread_mutex_t mutex;
#endif
	graal_isolatethread_t *graal_isolatethread;
};

static int pkl_is_initialized = 0;

static void pkl_runtime_cleanup(pkl_exec_t *pexec) {
	pkl_internal_server_stop(pexec->graal_isolatethread);
	pkl_internal_close(pexec->graal_isolatethread);
	pexec->graal_isolatethread = NULL;
}

static void pkl_unlock_mutex(pkl_exec_t *pexec) {
#ifdef _WIN32
  LeaveCriticalSection(&pexec->mutex);
#else
	if (pthread_mutex_unlock(&pexec->mutex) != 0) {
		fprintf(stderr, "fatal: failed to unlock mutex.\n");
		abort();
	}
#endif
}

static int pkl_lock_mutex(pkl_exec_t *pexec, pkl_error_t *error) {
#ifdef _WIN32
  EnterCriticalSection(&pexec->mutex);
#else
	if (pthread_mutex_lock(&pexec->mutex) != 0) {
		if (error != NULL) {
			error->message = "failed to lock mutex";
		}
		return PKL_ERR_LOCK;
	}
#endif
	return 0;
}

int pkl_init(pkl_message_response_handler handler, void *userData,
		pkl_exec_t **exec, pkl_error_t *error) {
	if (handler == NULL) {
		if (error != NULL) {
			error->message = "handler is null";
		}
		return -1;
	}
	if (exec == NULL) {
		if (error != NULL) {
			error->message = "exec is null";
		}
		return -1;
	}
	if (pkl_is_initialized) {
		if (error != NULL) {
			error->message =
					"pkl_init called multiple times without calling pkl_close";
		}
		return -1;
	}
	pkl_exec_t *pexec = calloc(1, sizeof(pkl_exec_t));
	if (pexec == NULL) {
		fprintf(stderr, "failed to allocate pkl_exec_t\n");
		abort();
	}

#ifdef _WIN32
  InitializeCriticalSection(&pexec->mutex);
#else
	if (pthread_mutex_init(&pexec->mutex, NULL) != 0) {
		if (error != NULL) {
			error->message = "Failed to initialize mutex";
		}
		free(pexec);
		*exec = NULL;
		return -1;
	}
#endif

	pexec->graal_isolatethread = pkl_internal_init();

	if (pexec->graal_isolatethread == NULL) {
		if (error != NULL) {
			error->message = "Failed to allocate graal_isolatethread";
		}
#ifdef _WIN32
    DeleteCriticalSection(&pexec->mutex);
#else
		if (pthread_mutex_destroy(&pexec->mutex) != 0) {
			fprintf(stderr, "fatal: failed to destroy mutex.\n");
			abort();
		}
#endif
		free(pexec);
		*exec = NULL;
		return -1;
	}

	pkl_internal_register_response_handler(pexec->graal_isolatethread, handler,
			userData);
	pkl_internal_server_start(pexec->graal_isolatethread);

	pkl_is_initialized = 1;
	*exec = pexec;
	if (error != NULL) {
		error->message = NULL;
	}
	return 0;
}

int pkl_send_message(pkl_exec_t *pexec, int length, char *message,
		pkl_error_t *error) {
	if (pexec == NULL) {
		if (error != NULL) {
			error->message = "pexec is null";
		}
		return -1;
	}
	if (message == NULL) {
		if (error != NULL) {
			error->message = "message is null";
		}
		return -1;
	}

	int lock_response = pkl_lock_mutex(pexec, error);
	if (lock_response != 0) {
		return lock_response;
	}
	char *errormessage;
	int resp = pkl_internal_send_message(pexec->graal_isolatethread, length,
			message, &errormessage);
	pkl_unlock_mutex(pexec);
	if (resp != 0) {
		if (error != NULL) {
			error->message = errormessage;
		}
		return resp;
	}
	if (error != NULL) {
		error->message = NULL;
	}
	return 0;
}

int pkl_close(pkl_exec_t *pexec, pkl_error_t *error) {
	if (pexec == NULL) {
		if (error != NULL) {
			error->message = "pexec is null";
		}
		return -1;
	}

#ifdef _WIN32
  EnterCriticalSection(&pexec->mutex);
#else
	if (pthread_mutex_lock(&pexec->mutex) != 0) {
		if (error != NULL) {
			error->message = "failed to lock mutex";
		}
		return PKL_ERR_LOCK;
	}
#endif

	pkl_runtime_cleanup(pexec);

#ifdef _WIN32
  LeaveCriticalSection(&pexec->mutex);
  DeleteCriticalSection(&pexec->mutex);
#else
	if (pthread_mutex_unlock(&pexec->mutex) != 0) {
		fprintf(stderr, "fatal: failed to unlock mutex.\n");
		abort();
	}

	if (pthread_mutex_destroy(&pexec->mutex) != 0) {
		fprintf(stderr, "fatal: failed to destroy mutex.\n");
		abort();
	}
#endif

	pkl_is_initialized = 0;
	free(pexec);
	if (error != NULL) {
		error->message = NULL;
	}
	return 0;
}

const char* pkl_version() {
	return PKL_VERSION;
}

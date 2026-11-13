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

// ReSharper disable once CppClassNeverUsed
struct __pkl_exec_t {
	graal_isolate_t *isolate;
	graal_isolatethread_t *isolateThread;

	/** The caller-supplied handler/userData from pkl_init, invoked only from `queue_thread`. */
	pkl_message_response_handler handler;

	void *userData;

#ifdef _WIN32
  HANDLE queue_thread;
#else
	pthread_t queue_thread;
#endif
};

/**
 * Polls for messages coming from Pkl and sends them back to the handler.
 *
 * Ensures that calls into Pkl are wrapped with graal_attach_thread/graal_detach_thread, and
 * calls back to the handler are _not_ inside the GraalVM thread context.
 */
#ifdef _WIN32
static DWORD WINAPI pkl_dispatch_worker(LPVOID arg) {
#else
static void* pkl_dispatch_worker(void *arg) {
#endif
	const pkl_exec_t *pexec = (pkl_exec_t*) arg;

	for (;;) {
		graal_isolatethread_t *thread;
		if (graal_attach_thread(pexec->isolate, &thread) != 0) {
			fprintf(stderr,
					"fatal: failed to attach response dispatch thread to isolate.\n");
			abort();
		}

		char *message = NULL;
		const int length = pkl_internal_poll_response(thread, &message);

		if (graal_detach_thread(thread) != 0) {
			fprintf(stderr,
					"fatal: failed to detach response dispatch thread from isolate.\n");
			abort();
		}

		if (length < 0) {
			// pkl_internal_server_stop has been called and the queue is drained
			break;
		}
		pexec->handler(length, message, pexec->userData);
		// message was allocated on the Java side via UnmanagedMemory.malloc, which maps onto
		// the platform's malloc/free; ownership passes to us here.
		free(message);
	}

#ifdef _WIN32
  return 0;
#else
	return NULL;
#endif
}

static int pkl_start_dispatch_worker(pkl_exec_t *pexec, pkl_error_t *error) {
#ifdef _WIN32
  pexec->queue_thread = CreateThread(NULL, 0, pkl_dispatch_worker, pexec, 0, NULL);
  if (pexec->queue_thread == NULL) {
#else
	if (pthread_create(&pexec->queue_thread, NULL, pkl_dispatch_worker, pexec)
			!= 0) {
#endif
		if (error != NULL) {
			error->message = "Failed to create response dispatch thread";
		}
		return -1;
	}
	return 0;
}

static void pkl_stop_dispatch_worker(pkl_exec_t *pexec) {
#ifdef _WIN32
  WaitForSingleObject(pexec->queue_thread, INFINITE);
  CloseHandle(pexec->queue_thread);
#else
	pthread_join(pexec->queue_thread, NULL);
#endif
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
	pkl_exec_t *pexec = calloc(1, sizeof(pkl_exec_t));
	if (pexec == NULL) {
		fprintf(stderr, "failed to allocate pkl_exec_t\n");
		abort();
	}

	pexec->handler = handler;
	pexec->userData = userData;

	graal_isolate_t *isolate;
	graal_isolatethread_t *isolateThread;

	if (graal_create_isolate(NULL, &isolate, &isolateThread) != 0) {
		if (error != NULL) {
			error->message = "Failed to create graal isolate thread";
		}
		free(pexec);
		*exec = NULL;
		return -1;
	}
	pexec->isolate = isolate;
	pexec->isolateThread = isolateThread;

	if (pkl_start_dispatch_worker(pexec, error) != 0) {
		graal_tear_down_isolate(isolateThread);
		free(pexec);
		*exec = NULL;
		return -1;
	}

	pkl_internal_server_start(isolateThread);

	*exec = pexec;
	if (error != NULL) {
		error->message = NULL;
	}
	return 0;
}

int pkl_send_message(const pkl_exec_t *pexec, const unsigned int length,
		char *message, pkl_error_t *error) {
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

	graal_isolatethread_t *thread = graal_get_current_thread(pexec->isolate);
	if (thread != pexec->isolateThread) {
		if (error != NULL) {
			error->message =
					"called into pkl_send_message from different thread";
		}
		return PKL_ERR_THREAD;
	}

	char *errormessage = NULL;
	const int resp = pkl_internal_send_message(thread, (int) length, message,
			&errormessage);

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

	graal_isolatethread_t *thread = graal_get_current_thread(pexec->isolate);
	if (thread != pexec->isolateThread) {
		if (error != NULL) {
			error->message = "called into pkl_close from different thread";
		}
		return PKL_ERR_THREAD;
	}

	// pkl_internal_server_stop unblocks queue_thread's pending/next poll call, so the join
	// below is guaranteed to return; only then is it safe to tear down the isolate.
	pkl_internal_server_stop(thread);
	pkl_stop_dispatch_worker(pexec);

	if (graal_tear_down_isolate(thread) != 0) {
		fprintf(stderr, "fatal: failed to tear down graal isolate.\n");
		abort();
	}

	free(pexec);
	if (error != NULL) {
		error->message = NULL;
	}
	return 0;
}

const char* pkl_version() {
	return PKL_VERSION;
}

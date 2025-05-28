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
#include <pthread.h>

#include <graal_isolate.h>
#include <libpkl_internal.h>

#include <pkl.h>

#ifndef NULL
  #define NULL 0
#endif

pthread_mutex_t graal_mutex;
graal_isolatethread_t *isolatethread = NULL;

int pkl_init(PklMessageResponseHandler handler, void *payload) {
  if (isolatethread != NULL) {
    perror("pkl_init: isolatethread is already initialised");
    return -1;
  }

  if (pthread_mutex_init(&graal_mutex, NULL) != 0) {
    perror("pkl_init: couldn't initialise pthread_mutex");
    return -1;
  }

  if (pthread_mutex_lock(&graal_mutex) != 0) {
    return -1;
  }

  isolatethread = pkl_internal_init();
  pkl_internal_register_response_handler(isolatethread, handler, payload);
  pkl_internal_server_start(isolatethread);
  pthread_mutex_unlock(&graal_mutex);

  return 0;
};

int pkl_send_message(int length, char *message) {
  if (pthread_mutex_lock(&graal_mutex) != 0) {
    return -1;
  }

  pkl_internal_send_message(isolatethread, length, message);
  pthread_mutex_unlock(&graal_mutex);

  return 0;
};

int pkl_close() {
  if (pthread_mutex_lock(&graal_mutex) != 0) {
    return -1;
  }

  pkl_internal_server_stop(isolatethread);
  pkl_internal_close(isolatethread);
  isolatethread = NULL;

  if (pthread_mutex_unlock(&graal_mutex) != 0) {
    return -1;
  }

  if (pthread_mutex_destroy(&graal_mutex) != 0) {
    return -1;
  }

  return 0;
};

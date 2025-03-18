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

void pkl_runtime_cleanup(pkl_exec_t *pexec) {
  pkl_internal_server_stop(pexec->graal_isolatethread);
  pkl_internal_close(pexec->graal_isolatethread);
  pexec->graal_isolatethread = NULL;
}

pkl_exec_t *pkl_init(PklMessageResponseHandler handler, void *userData) {
  pkl_exec_t *pexec = calloc(1, sizeof(pkl_exec_t));

  if (pexec == NULL) {
    perror("pkl_init: couldn't allocate pkl_exec_t");
    return NULL;
  }

  if (pthread_mutex_init(&pexec->graal_mutex, NULL) != 0) {
    perror("pkl_init: couldn't initialise pthread_mutex");
    pkl_runtime_cleanup(pexec);
    free(pexec);
    return NULL;
  }

  if (pthread_mutex_lock(&pexec->graal_mutex) != 0) {
    perror("pkl_init: couldn't lock mutex");
    pkl_runtime_cleanup(pexec);
    pthread_mutex_destroy(&pexec->graal_mutex);
    free(pexec);
    return NULL;
  }

  pexec->graal_isolatethread = pkl_internal_init();
  pkl_internal_register_response_handler(pexec->graal_isolatethread, handler, userData);
  pkl_internal_server_start(pexec->graal_isolatethread);

  if (pthread_mutex_unlock(&pexec->graal_mutex) != 0) {
    perror("pkl_init: couldn't unlock mutex");
    return NULL;
  }

  return pexec;
};

int pkl_send_message(pkl_exec_t *pexec, int length, char *message) {
  if (pexec == NULL) {
    perror("pkl_send_message: pexec is NULL");
    return -1;
  }

  if (message == NULL) {
    perror("pkl_send_message: message is NULL");
    return -1;
  }

  if (pthread_mutex_lock(&pexec->graal_mutex) != 0) {
    perror("pkl_send_message: couldn't lock mutex");
    return -1;
  }

  pkl_internal_send_message(pexec->graal_isolatethread, length, message);

  if (pthread_mutex_unlock(&pexec->graal_mutex) != 0) {
    perror("pkl_send_message: couldn't unlock mutex");
    return -1;
  }

  return 0;
};

int pkl_close(pkl_exec_t *pexec) {
  if (pexec == NULL) {
    perror("pkl_close: pexec is NULL");
    return -1;
  }

  if (pthread_mutex_lock(&pexec->graal_mutex) != 0) {
    perror("pkl_close: couldn't lock mutex");
    return -1;
  }

  pkl_runtime_cleanup(pexec);

  if (pthread_mutex_unlock(&pexec->graal_mutex) != 0) {
    perror("pkl_close: couldn't unlock mutex");
    return -1;
  }

  if (pthread_mutex_destroy(&pexec->graal_mutex) != 0) {
    perror("pkl_close: couldn't destroy mutex");
    return -1;
  }

  free(pexec);

  return 0;
};

char* pkl_version(pkl_exec_t *pexec) {
  if (pexec == NULL) {
    perror("pkl_version: pexec is NULL");
    return NULL;
  }

  if (pthread_mutex_lock(&pexec->graal_mutex) != 0) {
    perror("pkl_version: couldn't lock mutex");
    return NULL;
  }

  char *version = pkl_internal_version(pexec->graal_isolatethread);

  if (pthread_mutex_unlock(&pexec->graal_mutex) != 0) {
    perror("pkl_version: couldn't unlock mutex");
    return NULL;
  }

  return version;
}

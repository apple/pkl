#ifndef __LIBPKL_INTERNAL_H
#define __LIBPKL_INTERNAL_H

#include <graal_isolate.h>


#if defined(__cplusplus)
extern "C" {
#endif

void pkl_internal_close(graal_isolatethread_t*);

graal_isolatethread_t* pkl_internal_init();

int pkl_internal_send_message(graal_isolatethread_t*, int, char*, char**);

void pkl_internal_register_response_handler(graal_isolatethread_t*, void *, void*);

void pkl_internal_server_start(graal_isolatethread_t*);

void pkl_internal_server_stop(graal_isolatethread_t*);

#if defined(__cplusplus)
}
#endif
#endif

/**
* @brief The Pkl Message Response Handler that a user should implement.
*
* The resulting messages from `pkl_send` will be sent to this handler using a callback style.
*/
typedef void (*PklMessageResponseHandler)(int length, char *message, void *handlerContext);

/**
* @brief Initialises and allocates a Pkl executor.
*
* @return -1 on failure.
* @return 0 on success.
*/
int pkl_init(PklMessageResponseHandler handler);

/**
* @brief Send a message to Pkl, providing the length and a pointer to the first byte.
*
* @return -1 on failure.
* @return 0 on success.
*/
int pkl_send_message(int length, char *message, void *handlerContext);

/**
* @brief Cleans up any resources that were created as part of the `pkl_init` process.
*
* @return -1 on failure.
* @return 0 on success.
*/
int pkl_close();

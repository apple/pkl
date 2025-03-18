typedef void (*PklMessageResponseHandler)(int length, char* message);

int pkl_init(PklMessageResponseHandler handler);

int pkl_send_message(int length, char* message);

int pkl_close();

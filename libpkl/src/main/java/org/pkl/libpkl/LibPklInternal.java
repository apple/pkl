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
package org.pkl.libpkl;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.WordFactory;
import org.jspecify.annotations.Nullable;
import org.pkl.core.messaging.MessageTransports.Logger;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.server.Server;

@SuppressWarnings("unused")
public class LibPklInternal {
  private static final Logger logger = new LibPklLogger();
  private static final NativeTransport transport =
      new NativeTransport(logger, LibPklInternal::handleSendMessageToNative);
  private static @Nullable Server server;

  private static final BlockingQueue<byte[]> responseQueue = new LinkedBlockingQueue<>();

  // sentinel identity, not contents, marks the end of the stream - see pklInternalPollResponse
  private static final byte[] STOP_SENTINEL = new byte[0];

  // keep in sync with values defined in pkl.h
  private static final int PKL_ERR_PROTOCOL = 2;

  private LibPklInternal() {}

  @CEntryPoint(name = "pkl_internal_init", builtin = CEntryPoint.Builtin.CREATE_ISOLATE)
  static native IsolateThread pklInternalInit();

  @CEntryPoint(name = "pkl_internal_send_message")
  public static int pklInternalSendMessage(
      IsolateThread thread, int length, CCharPointer ptr, CCharPointerPointer errorMessage) {
    try {
      transport.sendMessage(length, ptr);
      return 0;
    } catch (ProtocolException e) {
      @SuppressWarnings("resource")
      var cString =
          CTypeConversion.toCString("Invalid encoding: %s".formatted(e.getMessage())).get();
      errorMessage.write(cString);
      return PKL_ERR_PROTOCOL;
    }
  }

  @CEntryPoint(name = "pkl_internal_close", builtin = CEntryPoint.Builtin.TEAR_DOWN_ISOLATE)
  public static native void pklInternalClose(IsolateThread thread);

  @CEntryPoint(name = "pkl_internal_server_start")
  public static void pklInternalServerStart(IsolateThread thread) {
    server = new Server(transport);
    server.start();
  }

  @CEntryPoint(name = "pkl_internal_server_stop")
  public static void pklInternalServerStop(IsolateThread thread) {
    assert server != null;
    server.close();
    // wakes up (and eventually stops) whatever thread is blocked in pkl_internal_poll_response
    responseQueue.add(STOP_SENTINEL);
  }

  /**
   * Blocks until a response is available, copies it into unmanaged memory, and writes its address
   * to {@code outMessage}. The caller (pkl.c) takes ownership of the returned buffer and must free
   * it with the C library's {@code free}.
   *
   * @return the length of the response in bytes, or a negative value once the server has been
   *     stopped and no further responses will ever be produced.
   */
  @CEntryPoint(name = "pkl_internal_poll_response")
  public static int pklInternalPollResponse(IsolateThread thread, CCharPointerPointer outMessage) {
    byte[] bytes;
    try {
      bytes = responseQueue.take();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      outMessage.write(WordFactory.nullPointer());
      return -1;
    }
    // identity check, not content check: a genuine zero-length message is never this instance
    if (bytes == STOP_SENTINEL) {
      outMessage.write(WordFactory.nullPointer());
      return -1;
    }
    var buf = UnmanagedMemory.<CCharPointer>malloc(WordFactory.unsigned(bytes.length));
    for (int i = 0; i < bytes.length; i++) {
      buf.write(i, bytes[i]);
    }
    outMessage.write(buf);
    return bytes.length;
  }

  public static void handleSendMessageToNative(byte[] bytes) {
    responseQueue.add(bytes);
  }
}

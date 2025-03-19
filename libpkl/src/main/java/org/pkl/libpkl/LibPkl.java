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
package org.pkl.libpkl;

import java.io.IOException;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.pkl.core.messaging.MessageTransports.Logger;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.server.Server;

public class LibPkl {
  public interface MessageCallbackFunctionPointer extends CFunctionPointer {
    @InvokeCFunctionPointer(transition = Transition.TO_NATIVE)
    void invoke(int length, CCharPointer msg);
  }

  private static final Logger logger = new LibPklLogger();
  private static final NativeTransport transport =
      new NativeTransport(logger, LibPkl::handleSendMessageToNative);
  private static final Server server = new Server(transport);
  private static MessageCallbackFunctionPointer cb;

  static {
    server.start();
  }

  @CEntryPoint(
      name = "pkl_init",
      // TODO(kushal): This currently uses a builtin directly. We don't want to expose
      // `graal_isolatethread_t` to our users.
      builtin = CEntryPoint.Builtin.CREATE_ISOLATE,
      documentation = {
        "@brief Initialises and allocates a Pkl executor.",
        "",
        "@return non-zero value on failure.",
        "@return 0 on success.",
      })
  static native IsolateThread createIsolate();

  @CEntryPoint(
      name = "pkl_send_message",
      documentation = {
        "@brief Send a message to Pkl, providing the length and a pointer to the first byte.",
        "",
        "@return -1 if the Pkl executor hasn't been initialised.",
        "@return 0 on success.",
      })
  public static void sendMessage(IsolateThread thread, int length, CCharPointer ptr)
      throws ProtocolException, IOException {
    logger.log("Got message from native");
    transport.sendMessage(length, ptr);
  }

  @CEntryPoint(
      name = "pkl_register_response_handler",
      documentation = {
        "@brief Registers a Message Handler that will receive the result of Pkl executions.",
      })
  public static void registerResponseHandler(
      IsolateThread thread, LibPkl.MessageCallbackFunctionPointer cb) {
    logger.log("Got handler to call from Pkl");
    LibPkl.cb = cb;
  }

  @CEntryPoint(name = "pkl_close", builtin = CEntryPoint.Builtin.TEAR_DOWN_ISOLATE)
  public static native void tearDownIsolate(IsolateThread thread);

  public static void handleSendMessageToNative(byte[] bytes) {
    try (var pin = PinnedObject.create(bytes)) {
      cb.invoke(bytes.length, pin.addressOfArrayElement(0));
    }
  }

  /**
   * Needed otherwise we see the following error:
   *
   * <p>Error: Method 'org.pkl.libpkl.LibPkl.main' is declared as the main entry point but it can
   * not be found. Make sure that class 'org.pkl.libpkl.LibPkl' is on the classpath and that method
   * 'main(String[])' exists in that class.
   */
  public static void main(String[] argv) {}

  private LibPkl() {}
}

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
  private static Server server;
  private static MessageCallbackFunctionPointer cb;

  @CEntryPoint(name = "pkl_internal_init", builtin = CEntryPoint.Builtin.CREATE_ISOLATE)
  static native IsolateThread pklInternalInit();

  @CEntryPoint(name = "pkl_internal_send_message")
  public static void pklInternalSendMessage(IsolateThread thread, int length, CCharPointer ptr)
      throws ProtocolException, IOException {
    logger.log("Got message from native");
    transport.sendMessage(length, ptr);
  }

  @CEntryPoint(name = "pkl_internal_register_response_handler")
  public static void pklInternalRegisterResponseHandler(
      IsolateThread thread, LibPkl.MessageCallbackFunctionPointer cb) {
    logger.log("Got handler to call from Pkl");
    LibPkl.cb = cb;
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
    server.close();
  }

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
   *
   * <p>TODO: Clean this up once merged onto a feature-branch
   *
   * <p>This is because we are passing a main class to native-image using the -H:Class= arg. That
   * argument is optional and not required when building a shared library.
   */
  public static void main(String[] argv) {}

  private LibPkl() {}
}

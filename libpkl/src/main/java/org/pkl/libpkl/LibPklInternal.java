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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.PinnedObject;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.pkl.core.messaging.MessageTransports.Logger;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.server.Server;

@SuppressWarnings("unused")
public class LibPklInternal {
  public interface MessageCallbackFunctionPointer extends CFunctionPointer {
    @InvokeCFunctionPointer(transition = Transition.TO_NATIVE)
    void invoke(int length, CCharPointer msg, VoidPointer userData);
  }

  private static final Logger logger = new LibPklLogger();
  private static final NativeTransport transport =
      new NativeTransport(logger, LibPklInternal::handleSendMessageToNative);
  private static Server server;
  private static MessageCallbackFunctionPointer cb;
  private static VoidPointer userData;

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

  @CEntryPoint(name = "pkl_internal_register_response_handler")
  public static void pklInternalRegisterResponseHandler(
      IsolateThread thread,
      LibPklInternal.MessageCallbackFunctionPointer cb,
      VoidPointer userData) {
    LibPklInternal.cb = cb;
    LibPklInternal.userData = userData;
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
      // guaranteed to exist (cannot obtain pkl executor without calling `pkl_exec`).
      cb.invoke(bytes.length, pin.addressOfArrayElement(0), LibPklInternal.userData);
    }
  }
}

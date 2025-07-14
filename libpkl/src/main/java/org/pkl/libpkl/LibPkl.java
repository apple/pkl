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
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.pkl.core.Release;
import org.pkl.core.messaging.MessageTransports.Logger;
import org.pkl.server.Server;

@SuppressWarnings("unused")
public class LibPkl {
  public interface MessageCallbackFunctionPointer extends CFunctionPointer {
    @InvokeCFunctionPointer(transition = Transition.TO_NATIVE)
    void invoke(int length, CCharPointer msg, VoidPointer userData);
  }

  private static final Logger logger = new LibPklLogger();
  private static final NativeTransport transport =
      new NativeTransport(logger, LibPkl::handleSendMessageToNative);
  private static Server server;
  private static MessageCallbackFunctionPointer cb;
  private static VoidPointer userData;

  private LibPkl() {}

  @CEntryPoint(name = "pkl_internal_init", builtin = CEntryPoint.Builtin.CREATE_ISOLATE)
  static native IsolateThread pklInternalInit();

  @CEntryPoint(name = "pkl_internal_send_message")
  public static void pklInternalSendMessage(IsolateThread thread, int length, CCharPointer ptr) {
    logger.log("Got message from native");
    transport.sendMessage(length, ptr);
  }

  @CEntryPoint(name = "pkl_internal_register_response_handler")
  public static void pklInternalRegisterResponseHandler(
      IsolateThread thread, LibPkl.MessageCallbackFunctionPointer cb, VoidPointer userData) {
    logger.log("Got handler to call from Pkl");
    LibPkl.cb = cb;
    LibPkl.userData = userData;
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

  @CEntryPoint(name = "pkl_internal_version")
  public static CCharPointer pklInternalVersion(IsolateThread thread) {
    var version = Release.current().version();

    try (var versionInfoCCharHolder = CTypeConversion.toCString(version.toString())) {
      return versionInfoCCharHolder.get();
    }
  }

  public static void handleSendMessageToNative(byte[] bytes) {
    try (var pin = PinnedObject.create(bytes)) {
      // TODO: Provide a meaningful error the user if they haven't run `pkl_init`.
      cb.invoke(bytes.length, pin.addressOfArrayElement(0), LibPkl.userData);
    }
  }
}

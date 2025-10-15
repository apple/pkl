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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Consumer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.msgpack.core.MessagePack;
import org.pkl.core.PklBugException;
import org.pkl.core.messaging.Message;
import org.pkl.core.messaging.MessageTransports.AbstractMessageTransport;
import org.pkl.core.messaging.MessageTransports.Logger;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.server.ServerMessagePackDecoder;
import org.pkl.server.ServerMessagePackEncoder;

public class NativeTransport extends AbstractMessageTransport {
  private final Consumer<byte[]> sendMessageToNative;
  private final Logger logger;

  protected NativeTransport(Logger logger, Consumer<byte[]> sendMessageToNative) {
    super(logger);
    this.logger = logger;
    this.sendMessageToNative = sendMessageToNative;
  }

  @Override
  protected void doStart() {}

  @Override
  protected void doClose() {}

  @Override
  protected void doSend(Message message) {
    try (var os = new ByteArrayOutputStream();
        var packer = MessagePack.newDefaultPacker(os)) {
      var encoder = new ServerMessagePackEncoder(packer);
      encoder.encode(message);
      sendMessageToNative.accept(os.toByteArray());
    } catch (IOException e) {
      // impossible; no IO happens during packing
      throw PklBugException.unreachableCode();
    } catch (ProtocolException e) {
      System.err.println("Received unexpected ProtocolException when encoding a message, aborting");
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  public void sendMessage(int length, CCharPointer ptr) throws ProtocolException {
    try (var is = new NativeInputStream(length, ptr);
        var unpacker = MessagePack.newDefaultUnpacker(is)) {
      var message = new ServerMessagePackDecoder(unpacker).decode();
      assert message != null;
      accept(message);
    } catch (IOException e) {
      // impossible; no IO happens during unpacking
      throw PklBugException.unreachableCode();
    }
  }
}

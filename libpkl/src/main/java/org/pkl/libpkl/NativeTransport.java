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
import java.util.function.Consumer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.msgpack.core.MessagePack;
import org.pkl.core.messaging.Message;
import org.pkl.core.messaging.MessageTransports.AbstractMessageTransport;
import org.pkl.core.messaging.MessageTransports.Logger;
import org.pkl.core.messaging.ProtocolException;
import org.pkl.server.ServerMessagePackDecoder;

public class NativeTransport extends AbstractMessageTransport {
  private final Consumer<byte[]> sendMessageToNative;

  protected NativeTransport(Logger logger, Consumer<byte[]> sendMessageToNative) {
    super(logger);
    this.sendMessageToNative = sendMessageToNative;
  }

  @Override
  protected void doStart() throws ProtocolException, IOException {}

  @Override
  protected void doClose() {}

  @Override
  protected void doSend(Message message) throws ProtocolException, IOException {}

  public void sendMessage(int length, CCharPointer ptr) throws IOException, ProtocolException {
    var buffer = CTypeConversion.asByteBuffer(ptr, length);
    var unpacker = MessagePack.newDefaultUnpacker(buffer);
    var message = new ServerMessagePackDecoder(unpacker).decode();

    doSend(message);
  }
}

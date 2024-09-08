/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.messaging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.pkl.core.messaging.Message.OneWay;
import org.pkl.core.messaging.Message.Response;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.Pair;

/** Factory methods for creating [MessageTransport]s. */
public class MessageTransports {

  public interface Logger {
    void log(String msg);
  }

  /** Creates a message transport that reads from [inputStream] and writes to [outputStream]. */
  public static MessageTransport stream(
      InputStream inputStream, OutputStream outputStream, Logger logger) {
    return new EncodingMessageTransport(
        MessageDecoders.from(inputStream), MessageEncoders.into(outputStream), logger);
  }

  /** Creates "client" and "server" transports that are directly connected to each other. */
  public static Pair<MessageTransport, MessageTransport> direct(Logger logger) {
    var transport1 = new DirectMessageTransport(logger);
    var transport2 = new DirectMessageTransport(logger);
    transport1.setOther(transport2);
    transport2.setOther(transport1);
    return Pair.of(transport1, transport2);
  }

  protected static class EncodingMessageTransport extends AbstractMessageTransport {

    private final MessageDecoder decoder;
    private final MessageEncoder encoder;
    private volatile boolean isClosed = false;

    protected EncodingMessageTransport(
        MessageDecoder decoder, MessageEncoder encoder, Logger logger) {
      super(logger);
      this.decoder = decoder;
      this.encoder = encoder;
    }

    @Override
    protected void doStart() throws ProtocolException, IOException {
      while (!isClosed) {
        var message = decoder.decode();
        if (message == null) {
          return;
        }
        accept(message);
      }
    }

    @Override
    protected void doClose() {
      isClosed = true;
    }

    @Override
    protected void doSend(Message message) throws ProtocolException, IOException {
      encoder.encode(message);
    }
  }

  protected static class DirectMessageTransport extends AbstractMessageTransport {

    private DirectMessageTransport other;

    protected DirectMessageTransport(Logger logger) {
      super(logger);
    }

    @Override
    protected void doStart() {}

    @Override
    protected void doClose() {}

    @Override
    protected void doSend(Message message) throws ProtocolException {
      other.accept(message);
    }

    public void setOther(DirectMessageTransport other) {
      this.other = other;
    }
  }

  // TODO: clean up callbacks if evaluation fails for some reason (ThreadInterrupt, timeout, etc)
  protected abstract static class AbstractMessageTransport implements MessageTransport {
    private final Logger logger;
    private MessageTransport.OneWayHandler oneWayHandler;
    private MessageTransport.RequestHandler requestHandler;
    private final Map<Long, ResponseHandler> responseHandlers = new ConcurrentHashMap<>();

    protected AbstractMessageTransport(Logger logger) {
      this.logger = logger;
    }

    protected void log(String message, Object... args) {
      var formatter = new MessageFormat(message);
      logger.log(formatter.format(args));
    }

    protected abstract void doStart() throws ProtocolException, IOException;

    protected abstract void doClose();

    protected abstract void doSend(Message message) throws ProtocolException, IOException;

    protected void accept(Message message) throws ProtocolException {
      log("Received message: {0}", message);
      if (message instanceof Message.OneWay msg) {
        oneWayHandler.handleOneWay(msg);
      } else if (message instanceof Message.Request msg) {
        requestHandler.handleRequest(msg);
      } else if (message instanceof Message.Response msg) {
        var handler = responseHandlers.remove(msg.getRequestId());
        if (handler == null) {
          throw new ProtocolException(
              ErrorMessages.create(
                  "unknownRequestId", message.getClass().getSimpleName(), msg.getRequestId()));
        }
        handler.handleResponse(msg);
      }
    }

    @Override
    public final void start(OneWayHandler oneWayHandler, RequestHandler requestHandler)
        throws ProtocolException, IOException {
      log("Starting transport: {0}", this);
      this.oneWayHandler = oneWayHandler;
      this.requestHandler = requestHandler;
      doStart();
    }

    @Override
    public final void close() throws Exception {
      log("Closing transport: {0}", this);
      doClose();
      responseHandlers.clear();
    }

    @Override
    public void send(OneWay message) throws ProtocolException, IOException {
      log("Sending message: {0}", message);
      doSend(message);
    }

    @Override
    public void send(Message.Request message, ResponseHandler responseHandler)
        throws ProtocolException, IOException {
      log("Sending message: {0}", message);
      responseHandlers.put(message.getRequestId(), responseHandler);
      doSend(message);
    }

    @Override
    public void send(Response message) throws ProtocolException, IOException {
      log("Sending message: {0}", message);
      doSend(message);
    }
  }
}

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

import java.util.*;

public interface Message {

  Type getType();

  enum Type {
    CREATE_EVALUATOR_REQUEST(0x20),
    CREATE_EVALUATOR_RESPONSE(0x21),
    CLOSE_EVALUATOR(0x22),
    EVALUATE_REQUEST(0x23),
    EVALUATE_RESPONSE(0x24),
    LOG_MESSAGE(0x25),
    READ_RESOURCE_REQUEST(0x26),
    READ_RESOURCE_RESPONSE(0x27),
    READ_MODULE_REQUEST(0x28),
    READ_MODULE_RESPONSE(0x29),
    LIST_RESOURCES_REQUEST(0x2a),
    LIST_RESOURCES_RESPONSE(0x2b),
    LIST_MODULES_REQUEST(0x2c),
    LIST_MODULES_RESPONSE(0x2d);

    private final int code;

    Type(int code) {
      this.code = code;
    }

    public static Type fromInt(int val) throws IllegalArgumentException {
      for (Type t : Type.values()) {
        if (t.code == val) {
          return t;
        }
      }

      throw new IllegalArgumentException("Unknown Message.Type code");
    }

    public int getCode() {
      return code;
    }
  }

  interface OneWay extends Message {}

  interface Request extends Message {
    long getRequestId();
  }

  interface Response extends Message {
    long getRequestId();
  }

  interface Client extends Message {

    interface Request extends Client, Message.Request {}

    interface Response extends Client, Message.Response {}

    interface OneWay extends Client, Message.OneWay {}
  }

  interface Server extends Message {

    interface Request extends Server, Message.Request {}

    interface Response extends Server, Message.Response {}

    interface OneWay extends Server, Message.OneWay {}
  }

  abstract class Base implements Message {

    private final Type type;

    @Override
    public Type getType() {
      return type;
    }

    public Base(Type type) {
      this.type = type;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Base base)) {
        return false;
      }

      return type == base.type;
    }

    @Override
    public int hashCode() {
      return type.hashCode();
    }

    private static class TwoWay extends Base {

      private final long requestId;

      public TwoWay(Type type, long requestId) {
        super(type);
        this.requestId = requestId;
      }

      public long getRequestId() {
        return this.requestId;
      }
    }

    public static class Request extends TwoWay implements Message.Request {
      public Request(Type type, long requestId) {
        super(type, requestId);
      }
    }

    public static class Response extends TwoWay implements Message.Response {
      public Response(Type type, long requestId) {
        super(type, requestId);
      }
    }
  }
}

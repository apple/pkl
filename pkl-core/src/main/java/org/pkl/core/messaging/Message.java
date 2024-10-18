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

public interface Message {

  Type type();

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
    LIST_MODULES_RESPONSE(0x2d),
    INITIALIZE_MODULE_READER_REQUEST(0x100),
    INITIALIZE_MODULE_READER_RESPONSE(0x101),
    INITIALIZE_RESOURCE_READER_REQUEST(0x102),
    INITIALIZE_RESOURCE_READER_RESPONSE(0x103),
    CLOSE_EXTERNAL_PROCESS(0x104);

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
    long requestId();
  }

  interface Response extends Message {
    long requestId();
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
}

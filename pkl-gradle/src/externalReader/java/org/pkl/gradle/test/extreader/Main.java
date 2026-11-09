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
package org.pkl.gradle.test.extreader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.msgpack.core.MessagePack;
import org.msgpack.value.Value;
import org.msgpack.value.ValueFactory;

/**
 * A minimal external resource reader for Pkl. Uppercases the scheme-specific part of the URI and
 * returns it as binary content. Implements the Pkl external reader MessagePack protocol over
 * stdin/stdout.
 */
public class Main {
  private static final int INITIALIZE_RESOURCE_READER_REQUEST = 0x30;
  private static final int INITIALIZE_RESOURCE_READER_RESPONSE = 0x31;
  private static final int READ_RESOURCE_REQUEST = 0x26;
  private static final int READ_RESOURCE_RESPONSE = 0x27;
  private static final int CLOSE_EXTERNAL_PROCESS = 0x32;

  private static final Value KEY_REQUEST_ID = ValueFactory.newString("requestId");
  private static final Value KEY_EVALUATOR_ID = ValueFactory.newString("evaluatorId");
  private static final Value KEY_SCHEME = ValueFactory.newString("scheme");
  private static final Value KEY_URI = ValueFactory.newString("uri");

  public static void main(String[] args) throws IOException {
    var unpacker = MessagePack.newDefaultUnpacker(System.in);
    var packer = MessagePack.newDefaultPacker(System.out);

    while (unpacker.hasNext()) {
      var arrayLen = unpacker.unpackArrayHeader();
      if (arrayLen != 2) {
        throw new IOException("Expected array of 2, got " + arrayLen);
      }
      var msgType = unpacker.unpackInt();
      var body = unpacker.unpackValue().asMapValue().map();

      switch (msgType) {
        case INITIALIZE_RESOURCE_READER_REQUEST -> {
          var requestId = body.get(KEY_REQUEST_ID).asIntegerValue().asLong();
          var scheme = body.get(KEY_SCHEME).asStringValue().asString();

          packer.packArrayHeader(2);
          packer.packInt(INITIALIZE_RESOURCE_READER_RESPONSE);
          packer.packMapHeader(2);
          packer.packString("requestId");
          packer.packLong(requestId);
          packer.packString("spec");
          packer.packMapHeader(3);
          packer.packString("scheme");
          packer.packString(scheme);
          packer.packString("hasHierarchicalUris");
          packer.packBoolean(false);
          packer.packString("isGlobbable");
          packer.packBoolean(false);
          packer.flush();
        }
        case READ_RESOURCE_REQUEST -> {
          var requestId = body.get(KEY_REQUEST_ID).asIntegerValue().asLong();
          var evaluatorId = body.get(KEY_EVALUATOR_ID).asIntegerValue().asLong();
          var uri = body.get(KEY_URI).asStringValue().asString();

          var colonIndex = uri.indexOf(':');
          var schemeSpecific = colonIndex >= 0 ? uri.substring(colonIndex + 1) : uri;
          var contents = schemeSpecific.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);

          packer.packArrayHeader(2);
          packer.packInt(READ_RESOURCE_RESPONSE);
          packer.packMapHeader(3);
          packer.packString("requestId");
          packer.packLong(requestId);
          packer.packString("evaluatorId");
          packer.packLong(evaluatorId);
          packer.packString("contents");
          packer.packBinaryHeader(contents.length);
          packer.writePayload(contents);
          packer.flush();
        }
        case CLOSE_EXTERNAL_PROCESS -> {
          return;
        }
        default -> {}
      }
    }
  }
}

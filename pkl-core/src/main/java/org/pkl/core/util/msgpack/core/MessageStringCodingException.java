/*
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
package org.pkl.core.util.msgpack.core;

import java.nio.charset.CharacterCodingException;

/** Thrown to indicate an error when encoding/decoding a String value */
public class MessageStringCodingException extends MessagePackException {
  public MessageStringCodingException(CharacterCodingException cause) {
    super(cause);
  }

  @Override
  public CharacterCodingException getCause() {
    return (CharacterCodingException) super.getCause();
  }
}

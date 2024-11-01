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

/** Thrown to indicate too large message size (e.g, larger than 2^31-1). */
public class MessageSizeException extends MessagePackException {
  private final long size;

  public MessageSizeException(long size) {
    super();
    this.size = size;
  }

  public MessageSizeException(String message, long size) {
    super(message);
    this.size = size;
  }

  public long getSize() {
    return size;
  }
}

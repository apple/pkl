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
package org.pkl.core.resource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** An external (file, HTTP, etc.) resource. */
public final class Resource {
  private final URI uri;

  private final byte[] bytes;

  /** Constructs a resource. */
  public Resource(URI uri, byte[] bytes) {
    this.uri = uri;
    this.bytes = bytes;
  }

  /** Returns the URI of this resource. */
  public URI getUri() {
    return uri;
  }

  public byte[] getBytes() {
    return bytes;
  }

  /** Returns the text content of this resource. */
  public String getText() {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /** Returns the content of this resource in Base64. */
  public String getBase64() {
    return Base64.getEncoder().encodeToString(bytes);
  }
}

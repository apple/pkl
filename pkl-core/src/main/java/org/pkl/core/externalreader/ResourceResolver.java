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
package org.pkl.core.externalreader;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.messaging.MessageTransport;
import org.pkl.core.module.PathElement;

public interface ResourceResolver {
  static ResourceResolver of(MessageTransport transport, long evaluatorId) {
    return new ResourceResolverImpl(transport, evaluatorId);
  }

  Optional<Object> read(URI uri) throws IOException;

  boolean hasElement(SecurityManager securityManager, URI elementUri)
      throws SecurityManagerException;

  List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
      throws IOException, SecurityManagerException;
}

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
package org.pkl.core.runtime;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.externalreader.ExternalReaderProcessException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.PathElement;
import org.pkl.core.util.IoUtils;

public interface ReaderBase {
  /**
   * Tells if the URIs represented by this module key or resource reader should be interpreted as <a
   * href="https://www.rfc-editor.org/rfc/rfc3986#section-1.2.3">hierarchical</a>.
   */
  boolean hasHierarchicalUris() throws ExternalReaderProcessException, IOException;

  /** Tells if this module key or resource reader supports globbing. */
  boolean isGlobbable() throws ExternalReaderProcessException, IOException;

  /**
   * Tells if relative paths of this URI should be resolved from {@link URI#getFragment()}, rather
   * than {@link URI#getPath()}.
   */
  default boolean hasFragmentPaths() {
    return false;
  }

  /**
   * Tells if this module key or resource reader has an element at {@code elementUri}.
   *
   * <p>This method only needs to be implemented if {@link #hasHierarchicalUris()} returns true, and
   * if either {@link #isGlobbable()} or {@link ModuleKey#isLocal()} returns true.
   */
  default boolean hasElement(SecurityManager securityManager, URI elementUri)
      throws IOException, SecurityManagerException, ExternalReaderProcessException {
    throw new UnsupportedOperationException();
  }

  /**
   * List elements within a base URI.
   *
   * <p>This method is called by the {@link org.pkl.core.util.GlobResolver} when resolving glob
   * expressions if {@link #isGlobbable()} returns true.
   *
   * <p>This method does not need to be implemented if {@link #isGlobbable()} returns false.
   *
   * <p>If {@link #hasHierarchicalUris()} returns false, {@code URI} is effectively an empty URI and
   * should be ignored. In this case, this method is expected to list all elements represented by
   * this reader.
   */
  default List<PathElement> listElements(SecurityManager securityManager, URI baseUri)
      throws IOException, SecurityManagerException, ExternalReaderProcessException {
    throw new UnsupportedOperationException();
  }

  default URI resolveUri(URI baseUri, URI uri) throws IOException, SecurityManagerException {
    return IoUtils.resolve(this, baseUri, uri);
  }
}

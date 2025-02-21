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

import com.oracle.truffle.api.nodes.Node;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Optional;
import org.pkl.core.ModuleSource;
import org.pkl.core.externalreader.ExternalReaderProcessException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.ModuleKeyFactory;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.util.Nullable;

public final class ModuleResolver {
  private final Collection<ModuleKeyFactory> factories;

  public ModuleResolver(Collection<ModuleKeyFactory> factories) {
    this.factories = factories;
  }

  public Collection<ModuleKeyFactory> getFactories() {
    return factories;
  }

  public ModuleKey resolve(ModuleSource moduleSource) {
    if (!moduleSource.getUri().isAbsolute()) {
      throw new VmExceptionBuilder()
          .evalError("cannotEvaluateRelativeModuleUri", moduleSource.getUri())
          .build();
    }
    if (moduleSource.getContents() != null) {
      // `ModuleSource.text()` creates a synthetic module with URI `repl:text`, so it should be
      // matched to `ModuleKeys.synthetic`.
      if (moduleSource.getUri().equals(VmUtils.REPL_TEXT_URI)) {
        return ModuleKeys.synthetic(moduleSource.getUri(), moduleSource.getContents());
      }
      return resolveCached(moduleSource.getUri(), moduleSource.getContents());
    }
    return resolve(moduleSource.getUri());
  }

  public ModuleKey resolve(URI moduleUri) {
    return resolve(moduleUri, null);
  }

  public ModuleKey resolveCached(URI moduleUri, String text) {
    var underlyingModuleKey = resolve(moduleUri);
    return ModuleKeys.cached(underlyingModuleKey, text);
  }

  public ModuleKey resolve(URI moduleUri, @Nullable Node importNode) {
    if (!moduleUri.isAbsolute()) {
      throw new VmExceptionBuilder()
          .withOptionalLocation(importNode)
          .bug("Cannot resolve relative URI `%s`.", moduleUri)
          .build();
    }

    var normalized = moduleUri.normalize();
    for (var factory : factories) {
      Optional<ModuleKey> key;
      try {
        key = factory.create(normalized);
      } catch (URISyntaxException e) {
        throw new VmExceptionBuilder()
            .withOptionalLocation(importNode)
            .evalError("invalidModuleUri", moduleUri)
            .withHint(e.getReason())
            .build();
      } catch (ExternalReaderProcessException e) {
        throw new VmExceptionBuilder()
            .withOptionalLocation(importNode)
            .evalError("externalReaderFailure")
            .withCause(e)
            .build();
      } catch (IOException e) {
        throw new VmExceptionBuilder()
            .withOptionalLocation(importNode)
            .evalError("ioErrorLoadingModule")
            .withCause(e)
            .build();
      }
      if (key.isPresent()) return key.get();
    }

    throw new VmExceptionBuilder()
        .evalError("noModuleLoaderRegistered", moduleUri)
        .withOptionalLocation(importNode)
        .build();
  }
}

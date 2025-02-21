/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.ast.expression.unary;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.externalreader.ReaderProcessException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;

public abstract class AbstractReadNode extends UnaryExpressionNode {
  protected final ModuleKey currentModule;

  protected AbstractReadNode(SourceSection sourceSection, ModuleKey currentModule) {
    super(sourceSection);
    this.currentModule = currentModule;
  }

  @TruffleBoundary
  protected final URI parseUri(String resourceUri) {
    try {
      return IoUtils.toUri(resourceUri);
    } catch (URISyntaxException e) {
      throw exceptionBuilder()
          .evalError("invalidResourceUri", resourceUri)
          .withHint(e.getReason())
          .build();
    }
  }

  @TruffleBoundary
  protected final @Nullable Object doRead(String resourceUri, VmContext context, Node readNode) {
    var resolvedUri = resolveResource(currentModule, resourceUri);
    return context.getResourceManager().read(resolvedUri, readNode).orElse(null);
  }

  private URI resolveResource(ModuleKey moduleKey, String resourceUri) {
    var parsedUri = parseUri(resourceUri);
    var context = VmContext.get(this);
    URI resolvedUri;
    try {
      resolvedUri = IoUtils.resolve(context.getSecurityManager(), moduleKey, parsedUri);
    } catch (FileNotFoundException e) {
      throw exceptionBuilder().evalError("cannotFindResource", resourceUri).build();
    } catch (URISyntaxException e) {
      throw exceptionBuilder()
          .evalError("invalidResourceUri", resourceUri)
          .withHint(e.getReason())
          .build();
    } catch (IOException e) {
      throw exceptionBuilder()
          .evalError("ioErrorReadingResource", resourceUri)
          .withHint(e.getMessage())
          .build();
    } catch (PackageLoadError | SecurityManagerException e) {
      throw exceptionBuilder().withCause(e).build();
    } catch (ReaderProcessException e) {
      throw exceptionBuilder().evalError("externalReaderFailure").withCause(e).build();
    }

    if (!resolvedUri.isAbsolute()) {
      throw exceptionBuilder().evalError("cannotHaveRelativeResource", moduleKey.getUri()).build();
    }
    return resolvedUri;
  }
}

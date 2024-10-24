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
package org.pkl.core;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.stream.StreamSupport;
import org.pkl.core.packages.PackageAssetUri;
import org.pkl.core.runtime.VmContext;
import org.pkl.core.settings.PklSettings;
import org.pkl.core.util.IoUtils;

/** Static factory for stack frame transformers. */
public final class StackFrameTransformers {

  private StackFrameTransformers() {}

  public static final StackFrameTransformer empty = s -> s;

  public static final StackFrameTransformer convertStdLibUrlToExternalUrl =
      frame -> {
        var uri = frame.getModuleUri();
        if (uri.startsWith("pkl:")) {
          var moduleName = uri.substring(4);
          return frame.withModuleUri(
              Release.current()
                  .sourceCode()
                  .getFilePage("stdlib/" + moduleName + ".pkl#L" + frame.getStartLine()));
        }
        return frame;
      };

  public static final StackFrameTransformer replacePackageUriWithSourceCodeUrl =
      frame -> {
        var uri = URI.create(frame.getModuleUri());
        if (!uri.getScheme().equalsIgnoreCase("package")) {
          return frame;
        }
        try {
          var assetUri = new PackageAssetUri(uri);
          var packageResolver = VmContext.get(null).getPackageResolver();
          assert packageResolver != null;
          var pkg = packageResolver.getDependencyMetadata(assetUri.getPackageUri(), null);
          var sourceCode = pkg.getSourceCodeUrlScheme();
          if (sourceCode == null) {
            return frame;
          }
          return transformUri(frame, uri.getFragment(), sourceCode);
        } catch (IOException | URISyntaxException | SecurityManagerException e) {
          // should never get here. by this point, we should have already performed all validation
          // and downloaded all assets.
          throw PklBugException.unreachableCode();
        }
      };

  public static final StackFrameTransformer fromServiceProviders = loadFromServiceProviders();

  public static final StackFrameTransformer defaultTransformer =
      fromServiceProviders
          .andThen(convertStdLibUrlToExternalUrl)
          .andThen(replacePackageUriWithSourceCodeUrl);

  private static StackFrame transformUri(StackFrame frame, String path, String format) {
    var uri = frame.getModuleUri();
    var newUri =
        format
            .replace("%{path}", path)
            .replace("%{url}", uri)
            .replace("%{line}", String.valueOf(frame.getStartLine()))
            .replace("%{endLine}", String.valueOf(frame.getEndLine()))
            .replace("%{column}", String.valueOf(frame.getStartColumn()))
            .replace("%{endColumn}", String.valueOf(frame.getEndColumn()));
    return frame.withModuleUri(newUri);
  }

  public static StackFrameTransformer convertFilePathToUriScheme(String scheme) {
    return frame -> {
      var uri = frame.getModuleUri();
      if (!uri.startsWith("file:")) return frame;

      return transformUri(frame, IoUtils.pathOf(URI.create(uri)).toString(), scheme);
    };
  }

  public static StackFrameTransformer relativizeModuleUri(URI baseUri) {
    return frame -> {
      var uri = URI.create(frame.getModuleUri());
      var relativized = baseUri.relativize(uri);
      return frame.withModuleUri(relativized.toString());
    };
  }

  public static StackFrameTransformer createDefault(PklSettings settings) {
    return defaultTransformer
        // order is relevant
        .andThen(convertFilePathToUriScheme(settings.editor().urlScheme()));
  }

  private static StackFrameTransformer loadFromServiceProviders() {
    var loader = IoUtils.createServiceLoader(StackFrameTransformer.class);
    return StreamSupport.stream(loader.spliterator(), false)
        .reduce((t1, t2) -> t1.andThen(t2))
        .orElse(t -> t); // use no-op transformer if no service providers found
  }
}

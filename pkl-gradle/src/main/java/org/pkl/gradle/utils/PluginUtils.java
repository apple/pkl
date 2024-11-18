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
package org.pkl.gradle.utils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileSystemLocation;
import org.pkl.core.util.IoUtils;

public class PluginUtils {
  private PluginUtils() {}

  /**
   * Parses the specified source module notation into a "parsed" notation which is then used for
   * input path tracking and as an argument for the CLI API.
   *
   * <p>This method accepts the following input types:
   *
   * <ul>
   *   <li>{@link URI} - used as is.
   *   <li>{@link File} - used as is.
   *   <li>{@link Path} - converted to a {@link File}. This conversion may fail because not all
   *       {@link Path}s point to the local file system.
   *   <li>{@link URL} - converted to a {@link URI}. This conversion may fail because {@link URL}
   *       allows for URLs which are not compliant URIs.
   *   <li>{@link CharSequence} - first, converted to a string. If this string is "URI-like" (see
   *       {@link IoUtils#isUriLike(String)}), then we attempt to parse it as a {@link URI}, which
   *       may fail. Otherwise, we attempt to parse it as a {@link Path}, which is then converted to
   *       a {@link File} (both of these operations may fail).
   *   <li>{@link FileSystemLocation} - converted to a {@link File} via the {@link
   *       FileSystemLocation#getAsFile()} method.
   * </ul>
   *
   * In case the returned value is determined to be a {@link URI}, then this URI is first checked
   * for whether its scheme is {@code file}, like {@code file:///example/path}. In such case, this
   * method returns a {@link File} corresponding to the file path in the URI. Otherwise, a {@link
   * URI} instance is returned.
   *
   * @throws InvalidUserDataException In case the input is none of the types described above, or
   *     when the underlying value cannot be parsed correctly.
   */
  public static Object parseModuleNotation(Object notation) {
    if (notation instanceof URI uri) {
      if ("file".equals(uri.getScheme())) {
        return new File(uri.getPath());
      }
      return uri;
    } else if (notation instanceof File) {
      return notation;
    } else if (notation instanceof Path path) {
      try {
        return path.toFile();
      } catch (UnsupportedOperationException e) {
        throw new InvalidUserDataException("Failed to parse Pkl module file path: " + notation, e);
      }
    } else if (notation instanceof URL url) {
      try {
        return parseModuleNotation(url.toURI());
      } catch (URISyntaxException e) {
        throw new InvalidUserDataException("Failed to parse Pkl module URI: " + notation, e);
      }
    } else if (notation instanceof CharSequence) {
      var s = notation.toString();
      if (IoUtils.isUriLike(s)) {
        try {
          return parseModuleNotation(IoUtils.toUri(s));
        } catch (URISyntaxException e) {
          throw new InvalidUserDataException("Failed to parse Pkl module URI: " + s, e);
        }
      } else {
        try {
          return Paths.get(s).toFile();
        } catch (InvalidPathException | UnsupportedOperationException e) {
          throw new InvalidUserDataException("Failed to parse Pkl module file path: " + s, e);
        }
      }
    } else if (notation instanceof FileSystemLocation location) {
      return location.getAsFile();
    } else {
      throw new InvalidUserDataException(
          "Unsupported value of type "
              + notation.getClass()
              + " used as a module path: "
              + notation);
    }
  }

  /**
   * Converts either a file or a URI to a URI. We convert a relative file to a URI via the {@link
   * IoUtils#createUri(String)} because other ways of conversion can make relative paths into
   * absolute URIs, which may break module loading.
   */
  public static URI parsedModuleNotationToUri(Object notation) {
    if (notation instanceof File file) {
      if (file.isAbsolute()) {
        return file.toPath().toUri();
      }
      return IoUtils.createUri(IoUtils.toNormalizedPathString(file.toPath()));
    } else if (notation instanceof URI uri) {
      return uri;
    }
    throw new IllegalArgumentException("Invalid parsed module notation: " + notation);
  }

  public static URI parseModuleNotationToUri(Object m) {
    var parsed1 = PluginUtils.parseModuleNotation(m);
    return parsedModuleNotationToUri(parsed1);
  }
}

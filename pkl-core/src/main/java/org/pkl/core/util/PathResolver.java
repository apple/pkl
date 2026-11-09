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
package org.pkl.core.util;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.net.URI;
import java.util.ArrayList;
import java.util.regex.Pattern;

// These are implemented by hand instead of relying on the NIO Path API because the JDK does not
// provide libraries for cross-platform resolvers.
// For example, from POSIX systems, you cannot resolve Windows-style paths.
// The alternative to this approach is to depend on a library (e.g. Apache Commons IO or jimfs).
public abstract sealed class PathResolver {
  public final String resolvePath(URI uri, String path) {
    if (isAbsolute(path)) {
      return normalize(path);
    }
    var basePath = uriToPath(uri);
    var resolved = join(basePath, path);
    return normalize(resolved);
  }

  protected abstract String uriToPath(URI uri);

  protected abstract String join(String base, String path);

  protected abstract boolean isAbsolute(String path);

  protected abstract String getRoot(String path);

  protected abstract char getSeparator();

  protected String normalize(String path) {
    var root = getRoot(path);
    var separator = getSeparator();
    var remainder = path.substring(root.length());

    var parts = remainder.split(Pattern.quote(((Character) separator).toString()));
    var stack = new ArrayList<>();

    for (var part : parts) {
      if (part.equals("..")) {
        if (!stack.isEmpty()) {
          stack.remove(stack.size() - 1);
        }
      } else if (!part.isEmpty() && !part.equals(".")) {
        stack.add(part);
      }
    }

    if (stack.isEmpty()) {
      return root;
    }

    var sb = new StringBuilder(root);

    for (var i = 0; i < stack.size(); i++) {
      if (i > 0) {
        sb.append(separator);
      }
      sb.append(stack.get(i));
    }
    // path ends with trailing separator
    if (parts[parts.length - 1].isEmpty()) {
      sb.append(separator);
    }

    return sb.toString();
  }

  static final class WindowsPathResolver extends PathResolver {
    @TruffleBoundary
    @Override
    protected String uriToPath(URI uri) {
      var host = uri.getHost();
      var path = uri.getPath();
      if (host != null) {
        // UNC path: \\server\share\path
        return "\\\\" + host + path.replace('/', '\\');
      }
      var ret = path.matches("/[A-Z]:/.*") ? path.substring(1) : path;
      return ret.replace('/', '\\');
    }

    @Override
    protected boolean isAbsolute(String path) {
      // Normalize forward slashes first
      path = path.replace('/', '\\');
      return isDriveLetter(path) || path.startsWith("\\\\");
    }

    @Override
    protected String join(String base, String path) {
      path = path.replace('/', '\\');
      if (isAbsolute(path)) {
        return path;
      }
      if (path.startsWith("\\")) {
        // Root-relative path: skip the leading backslash to avoid double backslash
        return getRoot(base) + path;
      }
      if (base.endsWith("\\")) {
        return base + path;
      }
      return base + '\\' + path;
    }

    private boolean isDriveLetter(String path) {
      return path.length() >= 2 && isAlpha(path.charAt(0)) && path.charAt(1) == ':';
    }

    private boolean isAlpha(char character) {
      return character >= 65 && character <= 90 || character >= 97 && character <= 122;
    }

    @Override
    protected String getRoot(String path) {
      // UNC path, e.g. \\server\share
      if (path.startsWith("\\\\")) {
        var firstBackslash = path.indexOf('\\', 2);
        if (firstBackslash == -1) {
          // Malformed UNC, just return what we have
          return path;
        }
        var secondBackslash = path.indexOf('\\', firstBackslash + 1);
        if (secondBackslash == -1) {
          return path + "\\";
        }
        return path.substring(0, secondBackslash + 1);
      } else if (isDriveLetter(path)) {
        // drive letter without leading slash, e.g. `C:foo\bar` (uncommon but valid)
        if (path.length() > 2 && path.charAt(2) == '\\') {
          return path.substring(0, 3);
        }
        return path.substring(0, 2);
      } else if (path.startsWith("\\")) {
        // drive-relative path
        return "\\";
      }
      return "";
    }

    @Override
    protected char getSeparator() {
      return '\\';
    }

    @Override
    protected String normalize(String path) {
      return super.normalize(path.replace('/', '\\'));
    }
  }

  static final class PosixPathResolver extends PathResolver {
    @TruffleBoundary
    @Override
    protected String uriToPath(URI uri) {
      return uri.getPath();
    }

    @Override
    protected boolean isAbsolute(String path) {
      return path.startsWith("/");
    }

    @Override
    protected String join(String base, String path) {
      if (isAbsolute(path)) {
        return path;
      }
      if (base.endsWith("/")) {
        return base + path;
      }
      return base + '/' + path;
    }

    @Override
    protected String getRoot(String ignored) {
      return "/";
    }

    @Override
    protected char getSeparator() {
      return '/';
    }
  }
}

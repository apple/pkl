/**
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
package org.pkl.core.util;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.pkl.core.PklBugException;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.module.PathElement;
import org.pkl.core.runtime.ReaderBase;

public final class GlobResolver {
  private GlobResolver() {}

  public static final class InvalidGlobPatternException extends Exception {
    public InvalidGlobPatternException(String message) {
      super(message);
    }
  }

  public static final class ResolvedGlobElement {
    private final String path;

    private final URI uri;
    private final boolean isDirectory;

    public ResolvedGlobElement(String path, URI uri, boolean isDirectory) {
      this.path = path;
      this.uri = uri.normalize();
      this.isDirectory = isDirectory;
    }

    public String getPath() {
      return path;
    }

    public URI getUri() {
      return uri;
    }

    public boolean isDirectory() {
      return isDirectory;
    }
  }

  private static final char NULL = 0;

  /**
   * The maximum number of {@link ReaderBase#listElements(SecurityManager, URI)} calls to be made
   * when resolving a glob pattern.
   *
   * <p>The intention is to prevent malicious programs from running, c.f. CVE-2010-2632. Otherwise,
   * a complex glob pattern can starve CPU/memory on a host.
   *
   * <p>Glob limit value taken from <a
   * href="https://github.com/openbsd/src/commit/46df4fe576b7">https://github.com/openbsd/src/commit/46df4fe576b7</a>.
   *
   * <p>If test mode is enabled, a smaller value is used. This greatly speeds up the test that
   * verifies enforcement of the limit (invalidGlobImport6.pkl).
   *
   * <p>Not a static field to prevent compile-time evaluation by native-image.
   */
  private static int maxListElements() {
    return IoUtils.isTestMode() ? 512 : 16384;
  }

  private static final Map<String, Pattern> patterns =
      Collections.synchronizedMap(new WeakHashMap<>());

  private static char getNextChar(String pattern, int i) {
    if (i >= pattern.length() - 1) {
      return NULL;
    }
    return pattern.charAt(i + 1);
  }

  private static int consumeCharacterClass(String globPattern, int idx, StringBuilder sb)
      throws InvalidGlobPatternException {
    // don't match path separators
    sb.append("[[^/]&&[");
    var i = idx;
    switch (getNextChar(globPattern, i)) {
      case '^' -> {
        // verbatim; escape
        sb.append("\\^");
        i++;
      }
      case '!' -> {
        // negation
        sb.append("^");
        i++;
      }
      case ']' -> {
        // the first `]` in a character class is verbatim and not treated as a closing delimiter.
        sb.append(']');
        i++;
      }
      case NULL ->
          throw new InvalidGlobPatternException(
              ErrorMessages.create("invalidGlobMissingCharacterClassTerminator"));
    }
    i++;
    var current = globPattern.charAt(i);
    while (current != ']') {
      if (current == '[') {
        var next = getNextChar(globPattern, i);
        if (next == ':' || next == '=' || next == '.') {
          throw new InvalidGlobPatternException(
              ErrorMessages.create("invalidGlobUnsupportedFeature"));
        }
      }
      if (current == '/') {
        throw new InvalidGlobPatternException(
            ErrorMessages.create("invalidGlobInvalidCharacterInCharacterClass", current));
      } else if (current == '\\') {
        sb.append("\\\\");
      } else {
        sb.append(current);
      }
      i++;
      if (i == globPattern.length()) {
        throw new InvalidGlobPatternException(
            ErrorMessages.create("invalidGlobMissingCharacterClassTerminator"));
      }
      current = globPattern.charAt(i);
    }
    sb.append("]]");
    return i;
  }

  public static Pattern toRegexPattern(String globPattern) throws InvalidGlobPatternException {
    var pattern = patterns.get(globPattern);
    if (pattern == null) {
      pattern = Pattern.compile(toRegexString(globPattern));
      patterns.put(globPattern, pattern);
    }
    return pattern;
  }

  /** Converts a glob pattern to an equivalent regular expression pattern */
  public static String toRegexString(String globPattern) throws InvalidGlobPatternException {
    var sb = new StringBuilder("^");
    var inGroup = false;

    for (var i = 0; i < globPattern.length(); i++) {
      var current = globPattern.charAt(i);
      switch (current) {
        case '{' -> {
          if (inGroup) {
            throw new InvalidGlobPatternException(
                ErrorMessages.create("invalidGlobNestedSubpattern"));
          }
          inGroup = true;
          sb.append("(?:(?:");
        }
        case '}' -> {
          if (inGroup) {
            inGroup = false;
            sb.append("))");
          } else {
            sb.append('}');
          }
        }
        case ',' -> {
          if (inGroup) {
            sb.append(")|(?:");
          } else {
            sb.append(',');
          }
        }
        case '\\' -> {
          var next = getNextChar(globPattern, i);
          if (next == NULL) {
            throw new InvalidGlobPatternException(
                ErrorMessages.create("invalidGlobInvalidTerminatingCharacter"));
          }
          if (next != '?' && next != '*' && next != '[' && next != '{' && next != '\\') {
            throw new InvalidGlobPatternException(
                ErrorMessages.create("invalidGlobInvalidEscapeCharacter", next));
          }
          sb.append('\\').append(next);
          i++;
        }
        case '[' -> i = consumeCharacterClass(globPattern, i, sb);
        case '?' -> {
          var next = getNextChar(globPattern, i);
          if (next == '(') {
            throw new InvalidGlobPatternException(ErrorMessages.create("invalidGlobExtGlob"));
          }
          sb.append(".");
        }
        case '*' -> {
          var next = getNextChar(globPattern, i);
          if (next == '(') {
            throw new InvalidGlobPatternException(ErrorMessages.create("invalidGlobExtGlob"));
          } else if (next == '*') {
            // globstar, crosses directory boundaries
            sb.append(".*");
            i++;
          } else {
            // single wildcard matches everything up until the next directory character
            sb.append("[^/]*");
          }
        }
        case '+', '@' -> {
          var next = getNextChar(globPattern, i);
          if (next == '(') {
            throw new InvalidGlobPatternException(ErrorMessages.create("invalidGlobExtGlob"));
          }
          sb.append("\\+");
        }
        case '!' -> {
          var next = getNextChar(globPattern, i);
          if (next == '(') {
            throw new InvalidGlobPatternException(ErrorMessages.create("invalidGlobExtGlob"));
          }
          sb.append("!");
        }

          // no special meaning in glob patterns but have special meaning in regex.
        case '.', '(', '%', '^', '$', '|' -> sb.append("\\").append(current);
        default -> sb.append(current);
      }
    }
    if (inGroup) {
      throw new InvalidGlobPatternException("invalidGlobUnclosedSubpattern");
    }
    return sb.append("$").toString();
  }

  private static void resolveOpaqueGlob(
      SecurityManager securityManager,
      ReaderBase reader,
      URI globUri,
      Pattern pattern,
      Map<String, ResolvedGlobElement> result)
      throws IOException, SecurityManagerException {
    var elements = reader.listElements(securityManager, globUri);
    for (var elem : sorted(elements)) {
      URI resolvedUri;
      try {
        resolvedUri = new URI(globUri.getScheme(), elem.getName(), null);
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
      if (pattern.matcher(resolvedUri.toString()).matches()) {
        var path = resolvedUri.toString();
        result.put(path, new ResolvedGlobElement(path, resolvedUri, false));
      }
    }
  }

  private static List<PathElement> sorted(List<PathElement> elements) {
    return elements.stream().sorted(PathElement.comparator).collect(Collectors.toList());
  }

  private static Boolean isRegularPathPart(String pathPart) {
    for (var i = 0; i < pathPart.length(); i++) {
      var c = pathPart.charAt(i);
      if (c == '\\' || c == '{' || c == '[' || c == '?' || c == '*') {
        return false;
      }
    }
    return true;
  }

  private static String resolvePath(String basePath, String path, boolean hasAbsoluteGlob) {
    if (basePath.isEmpty()) {
      return path;
    } else {
      if (hasAbsoluteGlob) {
        try {
          path = new URI(null, null, path, null).toString();
        } catch (URISyntaxException e) {
          throw new IllegalArgumentException(e);
        }
      }
      if (basePath.endsWith("/")) {
        return basePath + path;
      } else {
        return basePath + "/" + path;
      }
    }
  }

  private static List<ResolvedGlobElement> expandHierarchicalGlobPart(
      SecurityManager securityManager,
      ReaderBase reader,
      String basePath,
      Pattern globPartPattern,
      URI baseUri,
      boolean isGlobStar,
      boolean hasAbsoluteGlob,
      MutableLong listElementCallCount)
      throws IOException, SecurityManagerException, InvalidGlobPatternException {
    var result = new ArrayList<ResolvedGlobElement>();
    doExpandHierarchicalGlobPart(
        securityManager,
        reader,
        basePath,
        globPartPattern,
        baseUri,
        isGlobStar,
        hasAbsoluteGlob,
        listElementCallCount,
        result);
    return result;
  }

  private static void doExpandHierarchicalGlobPart(
      SecurityManager securityManager,
      ReaderBase reader,
      String expandedGlobSoFar,
      Pattern globPartPattern,
      URI baseUri,
      boolean isGlobStar,
      boolean hasAbsoluteGlob,
      MutableLong listElementCallCount,
      List<ResolvedGlobElement> result)
      throws IOException, SecurityManagerException, InvalidGlobPatternException {

    if (listElementCallCount.getAndIncrement() > maxListElements()) {
      throw new InvalidGlobPatternException(ErrorMessages.create("invalidGlobTooComplex"));
    }
    var elements = reader.listElements(securityManager, baseUri);
    for (var element : sorted(elements)) {
      var elementPath = resolvePath(expandedGlobSoFar, element.getName(), hasAbsoluteGlob);
      if (globPartPattern.matcher(element.getName()).matches()) {
        var name = element.isDirectory() ? element.getName() + "/" : element.getName();
        var elementUri = IoUtils.resolve(reader, baseUri, name);
        result.add(new ResolvedGlobElement(elementPath, elementUri, element.isDirectory()));
      }
      if (element.isDirectory() && isGlobStar) {
        var elementUri = IoUtils.resolve(reader, baseUri, element.getName() + "/");
        var newExpandedGlobPattern =
            resolvePath(expandedGlobSoFar, element.getName(), hasAbsoluteGlob);
        doExpandHierarchicalGlobPart(
            securityManager,
            reader,
            newExpandedGlobPattern,
            globPartPattern,
            elementUri,
            true,
            hasAbsoluteGlob,
            listElementCallCount,
            result);
      }
    }
  }

  private static void resolveHierarchicalGlob(
      SecurityManager securityManager,
      ReaderBase reader,
      String[] globPatternParts,
      int idx,
      URI baseUri,
      String expandedGlobPatternSoFar,
      boolean hasAbsoluteGlob,
      Map<String, ResolvedGlobElement> result,
      MutableLong listElementCallCount)
      throws IOException, SecurityManagerException, InvalidGlobPatternException {
    var isLeaf = idx == globPatternParts.length - 1;
    var patternPart = globPatternParts[idx];
    if (isRegularPathPart(patternPart)) {
      var newPath = resolvePath(expandedGlobPatternSoFar, patternPart, hasAbsoluteGlob);
      if (isLeaf) {
        var newBaseUri = IoUtils.resolve(reader, baseUri, patternPart);
        if (reader.hasElement(securityManager, newBaseUri)) {
          // Note: isDirectory is not meaningful here. Setting it to false is a way to skip setting
          // it.
          result.put(newPath, new ResolvedGlobElement(newPath, newBaseUri, false));
        }
      } else {
        var newBaseUri = IoUtils.resolve(reader, baseUri, patternPart + "/");
        resolveHierarchicalGlob(
            securityManager,
            reader,
            globPatternParts,
            idx + 1,
            newBaseUri,
            newPath,
            hasAbsoluteGlob,
            result,
            listElementCallCount);
      }
    } else {
      var globPartPattern = toRegexPattern(patternPart);
      var isGlobStar = patternPart.contains("**");
      var matchedElements =
          expandHierarchicalGlobPart(
              securityManager,
              reader,
              expandedGlobPatternSoFar,
              globPartPattern,
              baseUri,
              isGlobStar,
              hasAbsoluteGlob,
              listElementCallCount);
      for (var element : matchedElements) {
        if (isLeaf) {
          result.put(element.getPath(), element);
        } else if (element.isDirectory()) {
          resolveHierarchicalGlob(
              securityManager,
              reader,
              globPatternParts,
              idx + 1,
              element.getUri(),
              element.getPath(),
              hasAbsoluteGlob,
              result,
              listElementCallCount);
        }
      }
    }
  }

  /** Split a glob pattern into the base, non-wildard parts, and the wildcard parts. */
  private static Pair<String, String[]> splitGlobPatternIntoBaseAndWildcards(
      ReaderBase reader, String globPattern, boolean hasAbsoluteGlob) {
    var effectiveGlobPattern = globPattern;
    var basePathSb = new StringBuilder();
    if (hasAbsoluteGlob) {
      var globUri = URI.create(globPattern);
      if (reader.hasFragmentPaths()) {
        effectiveGlobPattern = globUri.getFragment();
        basePathSb.append(IoUtils.stripFragment(globUri)).append('#');
      } else {
        effectiveGlobPattern = globUri.getPath();
        basePathSb.append(globUri.getScheme()).append(':');
      }
    }
    var parts = effectiveGlobPattern.split("/");
    int i;

    for (i = 0; i < parts.length; i++) {
      var part = parts[i];
      if (!isRegularPathPart(part)) {
        break;
      }
      basePathSb.append(part).append('/');
    }
    return Pair.of(basePathSb.toString(), Arrays.copyOfRange(parts, i, parts.length));
  }

  /**
   * Resolves a glob expression.
   *
   * <p>Each pair is the expanded form of the glob pattern, paired with its resolved absolute URI.
   */
  @TruffleBoundary
  public static Map<String, ResolvedGlobElement> resolveGlob(
      SecurityManager securityManager,
      ReaderBase reader,
      ModuleKey enclosingModuleKey,
      URI enclosingUri,
      String globPattern)
      throws IOException, SecurityManagerException, InvalidGlobPatternException {

    var result = new LinkedHashMap<String, ResolvedGlobElement>();
    var hasAbsoluteGlob = globPattern.matches("\\w+:.*");

    if (reader.hasHierarchicalUris()) {
      var splitPattern = splitGlobPatternIntoBaseAndWildcards(reader, globPattern, hasAbsoluteGlob);
      var basePath = splitPattern.first;
      var globParts = splitPattern.second;
      // short-circuit for glob pattern with no wildcards (can only match 0 or 1 element)
      if (globParts.length == 0) {
        var resolvedUri = IoUtils.resolve(reader, enclosingUri, globPattern);
        if (reader.hasElement(securityManager, resolvedUri)) {
          result.put(globPattern, new ResolvedGlobElement(globPattern, resolvedUri, true));
        }
        return result;
      }
      URI baseUri;
      try {
        baseUri = IoUtils.resolve(securityManager, enclosingModuleKey, URI.create(basePath));
      } catch (URISyntaxException e) {
        // assertion: this is only thrown if the pattern starts with a triple-dot import.
        // the language will throw an error if glob imports is combined with triple-dots.
        throw new PklBugException(e);
      }

      resolveHierarchicalGlob(
          securityManager,
          reader,
          globParts,
          0,
          baseUri,
          basePath,
          hasAbsoluteGlob,
          result,
          new MutableLong(0));
    } else {
      var regexPattern = toRegexPattern(globPattern);
      // using "dummy" to make this a legitimate URI. The scheme specific part of the URI should be
      // ignored by the reader.
      var globUri =
          hasAbsoluteGlob
              ? URI.create(globPattern)
              : URI.create(enclosingUri.getScheme() + ":dummy");
      resolveOpaqueGlob(securityManager, reader, globUri, regexPattern, result);
    }
    return result;
  }
}

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

import com.oracle.truffle.api.TruffleOptions;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.pkl.core.PklBugException;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.module.ModuleKey;
import org.pkl.core.runtime.ReaderBase;
import org.pkl.core.runtime.VmExceptionBuilder;

public final class IoUtils {

  private static final Pattern uriLike = Pattern.compile("\\w+:.*");

  private IoUtils() {}

  public static URL toUrl(URI uri) throws IOException {
    try {
      return uri.toURL();
    } catch (Error e) {
      // best we can do for now
      // rely on caller to provide context, e.g., the requested module URI
      if (e.getClass().getName().equals("com.oracle.svm.core.jdk.UnsupportedFeatureError")) {
        throw new IOException("Unsupported protocol: " + uri.getScheme());
      }

      throw e;
    }
  }

  /** Checks whether the given string is "URI-like", i.e. matches a pattern like {@code foo:bar}. */
  public static boolean isUriLike(String str) {
    return uriLike.matcher(str).matches();
  }

  /**
   * Converts the given string to a {@link URI}. This method MUST be used for constructing module
   * and resource URIs. Unlike {@code new URI(str)}, it correctly escapes paths of relative URIs.
   */
  public static URI toUri(String str) throws URISyntaxException {
    return isUriLike(str) ? new URI(str) : new URI(null, null, str, null);
  }

  /** Like {@link #toUri(String)}, except without checked exceptions. */
  public static URI createUri(String str) {
    try {
      return toUri(str);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  public static URI stripFragment(URI uri) {
    try {
      return new URI(
          uri.getScheme(),
          uri.getUserInfo(),
          uri.getHost(),
          uri.getPort(),
          uri.getPath(),
          uri.getQuery(),
          null);
    } catch (URISyntaxException e) {
      throw PklBugException.unreachableCode();
    }
  }

  public static String readString(URL url) throws IOException {
    if (HttpUtils.isHttpUrl(url)) {
      throw new IllegalArgumentException("Should use HTTP client to GET " + url);
    }
    try (var stream = url.openStream()) {
      return readString(stream);
    }
  }

  public static String readString(InputStream inputStream) throws IOException {
    return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
  }

  public static byte[] readBytes(URI uri) throws IOException {
    if (HttpUtils.isHttpUrl(uri)) {
      throw new IllegalArgumentException("Should use HTTP client to GET " + uri);
    }
    try (var stream = IoUtils.toUrl(uri).openStream()) {
      return stream.readAllBytes();
    }
  }

  public static String readString(Reader reader) throws IOException {
    var builder = new StringBuilder();
    var bytesRead = 0;
    var buffer = new char[8 * 1024];
    while ((bytesRead = reader.read(buffer, 0, buffer.length)) != -1) {
      builder.append(buffer, 0, bytesRead);
    }
    return builder.toString();
  }

  public static String readClassPathResourceAsString(Class<?> clazz, String path)
      throws IOException {
    // use Class.getResourceAsStream() instead of ClassLoader.getResourceAsStream()
    // because AOT doesn't support class loaders
    var inputStream = clazz.getResourceAsStream(path);
    if (inputStream == null) {
      throw new IOException(String.format("Cannot find class path resource `%s`.", path));
    }
    try (inputStream) {
      return readString(inputStream);
    }
  }

  public static void zipDirectory(Path sourceDir, Path targetFile) throws IOException {
    try (var zipStream = new ZipOutputStream(Files.newOutputStream(targetFile))) {
      Files.walkFileTree(
          sourceDir,
          new SimpleFileVisitor<>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              zipStream.putNextEntry(new ZipEntry(sourceDir.relativize(file).toString()));
              Files.copy(file, zipStream);
              zipStream.closeEntry();
              return FileVisitResult.CONTINUE;
            }
          });
    }
  }

  // not stored to avoid build-time initialization by native-image
  public static Path getCurrentWorkingDir() {
    return Path.of(System.getProperty("user.dir"));
  }

  // not stored to avoid build-time initialization by native-image
  public static Path getPklHomeDir() {
    return Path.of(System.getProperty("user.home"), ".pkl");
  }

  // not stored to avoid build-time initialization by native-image
  public static Path getDefaultModuleCacheDir() {
    return getPklHomeDir().resolve("cache");
  }

  // not stored to avoid build-time initialization by native-image
  @SuppressWarnings("SystemGetProperty")
  public static String getLineSeparator() {
    return System.getProperty("line.separator");
  }

  public static String getName(String path) {
    var lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    return path.substring(lastSep + 1);
  }

  public static String getName(Path path) {
    return getName(path.toString());
  }

  public static String getNameWithoutExtension(String path) {
    var lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
    var lastDot = path.lastIndexOf('.');
    return lastDot == -1 || lastDot < lastSep
        ? path.substring(lastSep + 1)
        : path.substring(lastSep + 1, lastDot);
  }

  public static String takeLastSegment(String name, char separator) {
    var lastSep = name.lastIndexOf(separator);
    return name.substring(lastSep + 1);
  }

  public static String dropLastSegment(String name, char separator) {
    var lastSep = name.lastIndexOf(separator);
    return lastSep == -1 ? name : name.substring(0, lastSep);
  }

  public static @Nullable Path toPath(URI uri) {
    if (!uri.isAbsolute()) {
      throw new IllegalArgumentException("Expected absolute URI, but got: " + uri);
    }

    try {
      return Path.of(uri);
    } catch (IllegalArgumentException | FileSystemNotFoundException e) {
      return null;
    }
  }

  private static String doInferModuleName(URI moduleUri) {
    var path = moduleUri.getPath();
    if (path == null) { // equivalent to `URI.isOpaque()`
      // convention: take last segment of dot-separated name
      // after stripping any colon-separated version number
      return takeLastSegment(dropLastSegment(moduleUri.getSchemeSpecificPart(), ':'), '.');
    }
    return getNameWithoutExtension(path);
  }

  public static String inferModuleName(ModuleKey moduleKey) {
    var moduleUri = moduleKey.getUri();
    if ("jar".equalsIgnoreCase(moduleUri.getScheme())) {
      var uriString = moduleUri.toString();
      var index = getExclamationMarkIndex(uriString);
      var path = uriString.substring(index + 1);

      return getNameWithoutExtension(path);
    }

    if (moduleKey.hasFragmentPaths()) {
      var fragment = moduleUri.getFragment();
      return getNameWithoutExtension(fragment);
    }

    return doInferModuleName(moduleUri);
  }

  public static URI ensurePathEndsWithSlash(URI uri) {
    try {
      // opaque uris don't have a path
      if (uri.isOpaque()) return uri;

      // nothing to do
      if (uri.getPath().endsWith("/")) return uri;

      return new URI(
          uri.getScheme(),
          uri.getUserInfo(),
          uri.getHost(),
          uri.getPort(),
          uri.getPath() + '/',
          uri.getQuery(),
          uri.getFragment());
    } catch (URISyntaxException e) {
      // adding a trailing slash should never cause this exception
      throw new AssertionError(e);
    }
  }

  public static URI resolve(ReaderBase reader, URI baseUri, String importUri) {
    return resolve(reader, baseUri, createUri(importUri));
  }

  public static URI resolve(ReaderBase reader, URI baseUri, URI importUri) {
    if (reader.hasFragmentPaths() && !importUri.isAbsolute() && importUri.getPath() != null) {
      var fragment = baseUri.getFragment();
      var newFragment = resolve(createUri(fragment), importUri);
      return stripFragment(baseUri).resolve("#" + newFragment);
    }
    return resolve(baseUri, importUri);
  }

  /**
   * Resolves {@code importUri} against the module key.
   *
   * <p>When {@code importUri} contains a triple-dot, it is resolved if the module key returns true
   * for both {@link ModuleKey#isLocal()} and {@link ModuleKey#hasHierarchicalUris()}. Otherwise, an
   * error is thrown.
   */
  public static URI resolve(SecurityManager securityManager, ModuleKey moduleKey, URI importUri)
      throws URISyntaxException, IOException, SecurityManagerException {
    if (importUri.isAbsolute()) {
      return moduleKey.resolveUri(importUri);
    }
    var moduleKeyUri = moduleKey.getUri();
    var tripleDotPath = parseTripleDotPath(importUri);
    if (tripleDotPath == null) {
      return moduleKey.resolveUri(importUri);
    }
    if (!moduleKey.isLocal() || !moduleKey.hasHierarchicalUris()) {
      throw new VmExceptionBuilder()
          .evalError("cannotResolveTripleDotImports", moduleKeyUri)
          .build();
    }

    var currentPath =
        moduleKey.hasFragmentPaths() ? moduleKeyUri.getFragment() : moduleKeyUri.getPath();
    var effectiveImportPath =
        tripleDotPath.isEmpty()
            ? currentPath.substring(currentPath.lastIndexOf('/') + 1)
            : tripleDotPath;

    var index = currentPath.lastIndexOf('/');
    index = currentPath.lastIndexOf('/', index - 1);
    var basePath = currentPath;
    while (index > 0) {
      basePath = basePath.substring(0, index + 1);
      var candidatePath = basePath + effectiveImportPath;
      // make sure triple-dot cannot resolve to the same path.
      var candidateUri = resolve(moduleKey, moduleKeyUri, candidatePath);
      if (!candidatePath.equals(currentPath)
          && moduleKey.hasElement(securityManager, candidateUri)) {
        return fixTripleSlashUri(moduleKeyUri, candidateUri);
      }
      index = basePath.lastIndexOf('/', index - 1);
    }
    var candidatePath = '/' + effectiveImportPath;
    var candidateUri = resolve(moduleKey, moduleKeyUri, candidatePath);
    if (!candidatePath.equals(currentPath) && moduleKey.hasElement(securityManager, candidateUri)) {
      return fixTripleSlashUri(moduleKeyUri, candidateUri);
    }
    throw new FileNotFoundException();
  }

  public static URI resolve(URI baseUri, URI newUri) {
    if (newUri.isAbsolute()) return newUri;

    var scheme = baseUri.getScheme();

    // Support resolving relative URI against base URI
    // of the form "jar:file:///some/archive.zip!/foo/bar.pkl".
    // See: https://bugs.openjdk.java.net/browse/JDK-8020755
    if ("jar".equalsIgnoreCase(scheme)) {
      var baseUriString = baseUri.toString();
      var index = getExclamationMarkIndex(baseUriString);
      var jarUri = baseUriString.substring(0, index + 1);
      var jarPath = baseUriString.substring(index + 1);
      var resolvedPath = resolve(URI.create(jarPath), newUri);
      return URI.create(jarUri + resolvedPath);
    }

    return fixTripleSlashUri(baseUri, baseUri.resolve(newUri));
  }

  public static URI resolve(URI uri, String str) {
    try {
      return resolve(uri, toUri(str));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  // URI.relativize() won't construct relative paths containing ".."
  public static URI relativize(URI uri, URI base) {
    if (uri.isOpaque()
        || base.isOpaque()
        || !Objects.equals(uri.getScheme(), base.getScheme())
        || !Objects.equals(uri.getAuthority(), base.getAuthority())) {
      return uri;
    }

    var basePath = Path.of(base.getPath());
    if (!base.getRawPath().endsWith("/")) basePath = basePath.getParent();
    var resultPath = basePath.relativize(Path.of(uri.getPath()));

    try {
      return new URI(
          null, null, null, -1, resultPath.toString(), uri.getQuery(), uri.getFragment());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static boolean isWhitespace(String str) {
    return str.codePoints().allMatch(Character::isWhitespace);
  }

  /**
   * Capitalizes the first Unicode character of the given string (rather than the first character of
   * each word).
   */
  public static String capitalize(String str) {
    if (str.isEmpty()) return str;

    var cp = str.codePointAt(0);
    if (Character.isTitleCase(cp)) return str;

    var builder = new StringBuilder();
    // title case seems more appropriate than upper case
    builder.appendCodePoint(Character.toTitleCase(cp));
    builder.append(str.substring(Character.charCount(cp)));
    return builder.toString();
  }

  public static int getMaxLineLength(String str) {
    return str.lines().map(String::length).max(Comparator.naturalOrder()).orElse(0);
  }

  public static <T> ServiceLoader<T> createServiceLoader(Class<T> serviceClass) {
    if (TruffleOptions.AOT) {
      // don't use ServiceLoader.load(Class, ClassLoader)
      // because Class.getClassLoader() returns null in AOT mode
      return ServiceLoader.load(serviceClass);
    }

    // don't use ServiceLoader.load(Class)
    // because loading services from thread context class loader doesn't work inside gradle plugins
    return ServiceLoader.load(serviceClass, IoUtils.class.getClassLoader());
  }

  // not a static property to avoid compile-time evaluation by native-image
  public static boolean isTestMode() {
    return Boolean.getBoolean("org.pkl.testMode");
  }

  public static void setTestMode() {
    System.setProperty("org.pkl.testMode", "true");
  }

  public static @Nullable String parseTripleDotPath(URI importUri) throws URISyntaxException {
    var importScheme = importUri.getScheme();
    if (importScheme != null) return null;

    var schemeSpecificPart = importUri.getSchemeSpecificPart();
    if (!schemeSpecificPart.startsWith("...")) return null;

    if (schemeSpecificPart.length() == 3) return "";

    if (schemeSpecificPart.charAt(3) != '/' || schemeSpecificPart.length() == 4) {
      throw new URISyntaxException(
          importUri.toString(), ErrorMessages.create("invalidTripleDotSyntax"));
    }

    return schemeSpecificPart.substring(4);
  }

  public static String toUnicodeEscape(int ch) {
    var hex = Integer.toHexString(ch);
    return switch (hex.length()) {
      case 1 -> "\\u000" + hex;
      case 2 -> "\\u00" + hex;
      case 3 -> "\\u0" + hex;
      case 4 -> "\\u" + hex;
      default -> throw new IllegalArgumentException(String.valueOf(ch));
    };
  }

  public static String toHexEscape(int ch) {
    var hex = Integer.toHexString(ch);
    return switch (hex.length()) {
      case 1 -> "\\x0" + hex;
      case 2 -> "\\x" + hex;
      default -> throw new IllegalArgumentException(String.valueOf(ch));
    };
  }

  public static boolean isHexDigit(char ch) {
    return switch (ch) {
      case '0',
              '1',
              '2',
              '3',
              '4',
              '5',
              '6',
              '7',
              '8',
              '9',
              'A',
              'B',
              'C',
              'D',
              'E',
              'F',
              'a',
              'b',
              'c',
              'd',
              'e',
              'f' ->
          true;
      default -> false;
    };
  }

  public static boolean isHexDigitOrUnderscore(char ch) {
    return switch (ch) {
      case '0',
              '1',
              '2',
              '3',
              '4',
              '5',
              '6',
              '7',
              '8',
              '9',
              'A',
              'B',
              'C',
              'D',
              'E',
              'F',
              'a',
              'b',
              'c',
              'd',
              'e',
              'f',
              '_' ->
          true;
      default -> false;
    };
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isDecimalDigit(char ch) {
    return switch (ch) {
      case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> true;
      default -> false;
    };
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isDecimalDigitOrUnderscore(char ch) {
    return switch (ch) {
      case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '_' -> true;
      default -> false;
    };
  }

  public static boolean isNonZeroDecimalDigit(char ch) {
    return switch (ch) {
      case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> true;
      default -> false;
    };
  }

  public static boolean isOctalDigit(char ch) {
    return switch (ch) {
      case '0', '1', '2', '3', '4', '5', '6', '7' -> true;
      default -> false;
    };
  }

  public static boolean isOctalDigitOrUnderscore(char ch) {
    return switch (ch) {
      case '0', '1', '2', '3', '4', '5', '6', '7', '_' -> true;
      default -> false;
    };
  }

  public static boolean isBinaryDigitOrUnderscore(char ch) {
    return switch (ch) {
      case '0', '1', '_' -> true;
      default -> false;
    };
  }

  /**
   * Fix an issue where triple-slash URI's turn into single-slash URI's when using {@link
   * URI#resolve(URI)}
   */
  public static URI fixTripleSlashUri(URI baseUri, URI newUri) {
    // `getHost()` is erroroneously `null` when parsing triple-slash URIs.
    // Ensure that they are preserved during resolution.
    if (baseUri.getScheme() != null
        && baseUri.getScheme().equalsIgnoreCase(newUri.getScheme())
        && baseUri.getSchemeSpecificPart().startsWith("///")
        && newUri.getHost() == null) {
      try {
        return new URI(
            newUri.getScheme(),
            newUri.getUserInfo(),
            "",
            newUri.getPort(),
            newUri.getPath(),
            newUri.getQuery(),
            newUri.getFragment());
      } catch (URISyntaxException e) {
        throw new IllegalArgumentException(e.getMessage(), e);
      }
    }

    return newUri;
  }

  /**
   * Windows reserves characters {@code <>:"\|?*} in filenames.
   *
   * <p>For any such characters, enclose their decimal character code with parentheses. Verbatim
   * {@code (} is encoded as {@code ((}.
   */
  public static String encodePath(String path) {
    if (path.isEmpty()) return path;
    var sb = new StringBuilder();
    for (var i = 0; i < path.length(); i++) {
      var character = path.charAt(i);
      switch (character) {
        case '<', '>', ':', '"', '\\', '|', '?', '*' -> {
          sb.append('(');
          sb.append(ByteArrayUtils.toHex(new byte[] {(byte) character}));
          sb.append(")");
        }
        case '(' -> sb.append("((");
        default -> sb.append(path.charAt(i));
      }
    }
    return sb.toString();
  }

  private static int getExclamationMarkIndex(String jarUri) {
    var index = jarUri.indexOf('!');
    if (index == -1) {
      throw new IllegalArgumentException("Invalid `jar:` URI (missing `!`): " + jarUri);
    }
    return index;
  }
}

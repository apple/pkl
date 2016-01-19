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
package org.pkl.core;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import org.pkl.core.runtime.VmUtils;
import org.pkl.core.util.Nullable;

/**
 * A representation for a Pkl module's source URI, and optionally its source text.
 *
 * <p>Create a new module source via {@link #create(URI, String)}, or one of the various helper
 * factory methods.
 */
public class ModuleSource {
  public static ModuleSource create(URI uri, @Nullable String text) {
    return new ModuleSource(uri, text);
  }

  public static ModuleSource path(Path path) {
    return new ModuleSource(path.toUri(), null);
  }

  public static ModuleSource path(String path) {
    return path(Path.of(path));
  }

  public static ModuleSource text(String text) {
    return new ModuleSource(VmUtils.REPL_TEXT_URI, text);
  }

  public static ModuleSource file(String file) {
    return file(new File(file));
  }

  public static ModuleSource file(File file) {
    // File.toPath.toUri() gives more complaint file URIs
    // than File.toUri() (file:/// vs. file:/ for local files)
    return new ModuleSource(file.toPath().toUri(), null);
  }

  public static ModuleSource uri(String uri) {
    return uri(URI.create(uri));
  }

  public static ModuleSource uri(URI uri) {
    return new ModuleSource(uri, null);
  }

  public static ModuleSource modulePath(String path) {
    URI uri;
    if (path.charAt(0) == '/') {
      uri = URI.create("modulepath:" + path);
    } else {
      uri = URI.create("modulepath:/" + path);
    }
    return uri(uri);
  }

  private final URI uri;

  @Nullable private final String contents;

  private ModuleSource(URI uri, @Nullable String contents) {
    this.uri = uri;
    this.contents = contents;
  }

  public URI getUri() {
    return uri;
  }

  public @Nullable String getContents() {
    return contents;
  }
}

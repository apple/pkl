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
package org.pkl.core.module;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FileResolver {
  private FileResolver() {}

  public static List<PathElement> listElements(URI baseUri) throws IOException {
    return listElements(Path.of(baseUri));
  }

  public static List<PathElement> listElements(Path path) throws IOException {
    try (var stream = Files.newDirectoryStream(path)) {
      var ret = new ArrayList<PathElement>();
      for (var entry : stream) {
        // skip symlinks to prevent cyclical globs
        if (Files.isSymbolicLink(entry)) {
          continue;
        }
        ret.add(new PathElement(entry.getFileName().toString(), Files.isDirectory(entry)));
      }
      return ret;
    } catch (NotDirectoryException | NoSuchFileException ignored) {
      return Collections.emptyList();
    }
  }

  public static boolean hasElement(URI elementUri) {
    return Files.exists(Path.of(elementUri));
  }

  public static boolean hasElement(Path path) {
    return Files.exists(path);
  }
}

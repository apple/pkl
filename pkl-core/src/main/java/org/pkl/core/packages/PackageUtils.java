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
package org.pkl.core.packages;

import java.net.URISyntaxException;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.json.Json.FormatException;
import org.pkl.core.util.json.Json.JsonParseException;

public final class PackageUtils {
  private PackageUtils() {}

  public static PackageUri parsePackageUriWithoutChecksums(Object obj)
      throws JsonParseException, URISyntaxException {
    if (!(obj instanceof String string)) {
      throw new FormatException("string", obj.getClass());
    }
    var packageUri = new PackageUri(string);
    checkHasNoChecksumComponent(packageUri);
    return packageUri;
  }

  public static void checkHasNoChecksumComponent(PackageUri packageUri) throws URISyntaxException {
    if (packageUri.getChecksums() != null) {
      throw new URISyntaxException(
          packageUri.toString(), ErrorMessages.create("unexpectedChecksumInPackageUri"));
    }
  }
}

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
package org.pkl.core.httpsettings;

import java.net.URI;
import java.util.List;
import org.pkl.core.ModuleSource;
import org.pkl.core.PNull;
import org.pkl.core.PObject;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.Nullable;

/** Java version of {@code pkl.HttpSettings}. */
public record PklHttpSettings(@Nullable Proxy proxy) {
  public static PklHttpSettings DEFAULT = new PklHttpSettings(null);

  /** Initializes a {@link PklHttpSettings} from a raw object representation. */
  public static PklHttpSettings parse(PObject object, ModuleSource location) {
    var proxy = parseProxy(object, location);
    return new PklHttpSettings(proxy);
  }

  private static @Nullable Proxy parseProxy(PObject object, ModuleSource location) {
    var proxy = object.getPropertyOrNull("proxy");
    if (proxy instanceof PNull) {
      return null;
    }
    if (proxy instanceof PObject pObject
        && pObject.getPropertyOrNull("address") instanceof String address
        && pObject.getPropertyOrNull("noProxy") instanceof List<?> noProxy) {
      //noinspection unchecked
      return new Proxy(URI.create(address), (List<String>) noProxy);
    }
    throw new VmExceptionBuilder().evalError("invalidSettingsFile", location.getUri()).build();
  }

  public record Proxy(URI address, List<String> noProxy) {}
}

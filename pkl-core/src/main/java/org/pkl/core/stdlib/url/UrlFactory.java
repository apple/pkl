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
package org.pkl.core.stdlib.url;

import org.pkl.core.runtime.UrlModule;
import org.pkl.core.runtime.VmNull;
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.VmObjectFactory;

/** Materializes {@code Url} instances from a parsed {@link UrlRecord}. */
public final class UrlFactory {
  private static final VmObjectFactory<UrlRecord> factory =
      new VmObjectFactory<UrlRecord>(UrlModule::getUrlClass)
          .addStringProperty("scheme", UrlRecord::scheme)
          .addStringProperty("username", UrlRecord::username)
          .addStringProperty("password", UrlRecord::password)
          .addProperty("host", record -> VmNull.lift(record.host()))
          .addProperty(
              "port",
              record -> record.port() == null ? VmNull.withoutDefault() : record.port().longValue())
          .addStringProperty("path", UrlRecord::serializedPath)
          .addProperty("search", record -> VmNull.lift(record.query()))
          .addProperty("fragment", record -> VmNull.lift(record.fragment()));

  private UrlFactory() {}

  public static VmTyped create(UrlRecord record) {
    return factory.create(record);
  }
}

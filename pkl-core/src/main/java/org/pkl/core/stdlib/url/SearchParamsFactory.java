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
import org.pkl.core.runtime.VmTyped;
import org.pkl.core.stdlib.VmObjectFactory;
import org.pkl.core.stdlib.url.UrlRecord.QueryParam;

/** Materializes {@code SearchParams} instances from a URL's parsed query. */
public final class SearchParamsFactory {
  private static final VmObjectFactory<QueryParam[]> factory =
      new VmObjectFactory<>(UrlModule::getSearchParamsClass);

  private SearchParamsFactory() {}

  public static VmTyped create(QueryParam[] params) {
    return factory.create(params);
  }
}

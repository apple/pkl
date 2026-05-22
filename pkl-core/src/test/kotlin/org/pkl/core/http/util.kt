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
package org.pkl.core.http

import java.net.URI
import java.util.regex.Pattern

data class HttpSettings(
  val headers: Map<Pattern, Map<String, List<String>>>,
  val rewritesMap: Map<URI, URI>,
)

fun HttpClient.getConfiguredSettings(): HttpSettings {
  this as LazyHttpClient
  val requestRewritingClient = this.orCreateClient as RequestRewritingClient
  return HttpSettings(requestRewritingClient.headers, requestRewritingClient.rewritesMap)
}

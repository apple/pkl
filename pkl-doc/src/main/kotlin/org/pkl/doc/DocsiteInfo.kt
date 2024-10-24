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
package org.pkl.doc

import java.net.URI
import org.pkl.commons.toUri
import org.pkl.core.PModule

/** API equivalent of standard library module `pkl.DocsiteInfo`. */
data class DocsiteInfo(
  /** The display title of this Pkldoc website. */
  val title: String?,

  /**
   * The overview documentation on the main page of this website.
   *
   * Uses the same Markdown format as Pkldoc comments. Unless expanded, only the first paragraph is
   * shown.
   */
  val overview: String?,

  /** Imports used to resolve Pkldoc links in [overview]. */
  val overviewImports: Map<String, URI>
) {
  companion object {
    @Suppress("UNCHECKED_CAST")
    fun fromPkl(module: PModule): DocsiteInfo =
      DocsiteInfo(
        title = module["title"] as String?,
        overview = module["overview"] as String?,
        overviewImports =
          (module["overviewImports"] as Map<String, String>).mapValues { it.value.toUri() },
      )
  }
}

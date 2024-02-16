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
package org.pkl.doc

import kotlinx.html.*

internal class MainPageGenerator(
  docsiteInfo: DocsiteInfo,
  private val packagesData: List<PackageData>,
  pageScope: SiteScope
) : MainOrPackagePageGenerator<SiteScope>(docsiteInfo, pageScope, pageScope) {
  override val html: HTML.() -> Unit = {
    renderHtmlHead()

    body {
      onLoad = "onLoad()"

      renderPageHeader(null, null, null, null)

      main {
        h1 {
          id = "declaration-title"

          +(docsiteInfo.title ?: "")
        }

        val memberDocs = MemberDocs(docsiteInfo.overview, pageScope, listOf(), isDeclaration = true)

        renderMemberGroupLinks(
          Triple("Overview", "#_overview", memberDocs.isExpandable),
          Triple("Packages", "#_packages", packagesData.isNotEmpty())
        )

        if (docsiteInfo.overview != null) {
          renderAnchor("_overview")
          div {
            id = "_declaration"
            classes = setOf("member")

            memberDocs.renderExpandIcon(this)
            memberDocs.renderDocComment(this)
          }
        }

        renderPackages()
      }
    }
  }

  override fun HTMLTag.renderPageTitle() {
    +(docsiteInfo.title ?: "Pkldoc")
  }

  private fun HtmlBlockTag.renderPackages() {
    if (packagesData.isEmpty()) return

    val sortedPackages =
      packagesData.sortedWith { pkg1, pkg2 ->
        when {
          pkg1.ref.pkg == "pkl" -> -1 // always sort the stdlib first
          else -> pkg1.ref.pkg.compareTo(pkg2.ref.pkg)
        }
      }

    div {
      classes = setOf("member-group")

      renderAnchor("_packages")

      h2 {
        classes = setOf("member-group-title")

        +"Packages"
      }

      ul {
        for (pkg in sortedPackages) {
          val packageScope =
            pageScope.packageScopes[pkg.ref.pkg]
            // create scope for previously generated package
            ?: pageScope.createEmptyPackageScope(
                pkg.ref.pkg,
                pkg.ref.version,
                pkg.sourceCodeUrlScheme,
                pkg.sourceCode
              )

          val memberDocs =
            MemberDocs(
              pkg.summary,
              packageScope,
              listOfNotNull(pkg.deprecation?.let { createDeprecatedAnnotation(it) }),
              isDeclaration = false
            )

          renderModuleOrPackage(pkg.ref.pkg, packageScope, memberDocs)
        }
      }
    }
  }
}

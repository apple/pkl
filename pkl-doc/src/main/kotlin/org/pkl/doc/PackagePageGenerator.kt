/*
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

internal class PackagePageGenerator(
  docsiteInfo: DocsiteInfo,
  private val docPackage: DocPackage,
  pageScope: PackageScope,
) : MainOrPackagePageGenerator<PackageScope>(docsiteInfo, pageScope, pageScope.parent) {
  override val html: HTML.() -> Unit = {
    renderHtmlHead()

    body {
      onLoad = "onLoad()"

      renderPageHeader(docPackage.name, docPackage.version, null, null)

      main {
        renderParentLinks()

        h1 {
          id = "declaration-title"
          +docPackage.name

          span {
            id = "declaration-version"
            +docPackage.version
          }
        }

        val packageInfo = docPackage.docPackageInfo
        val memberDocs =
          MemberDocs(
            packageInfo.overview,
            pageScope,
            packageInfo.annotations,
            isDeclaration = true,
            collectMemberInfoForPackage(docPackage),
          )

        renderMemberGroupLinks(
          Triple("Overview", "#_overview", memberDocs.isExpandable),
          Triple("Modules", "#_modules", docPackage.hasListedModule),
        )

        renderAnchor("_overview")
        div {
          id = "_declaration"
          classes = setOf("member")

          memberDocs.renderExpandIcon(this)

          div {
            classes = setOf("member-signature")

            renderModifiers(setOf(), "package")

            span {
              classes = setOf("name-decl")

              +docPackage.name
            }
          }

          memberDocs.renderDocComment(this)
        }

        renderModules()
      }
    }
  }

  // example output:
  // package pkg.pkl-lang.org/pkl-k8s/k8s (1.0.0) • Package Docs
  override fun HTMLTag.renderPageTitle() {
    +pageScope.name
    +" ("
    +pageScope.version
    +") • "
    +(docsiteInfo.title ?: "Pkldoc")
  }

  private fun HtmlBlockTag.renderModules() {
    if (!docPackage.hasListedModule) return

    div {
      classes = setOf("member-group")

      renderAnchor("_modules")

      h2 {
        classes = setOf("member-group-title")

        +"Modules"
      }

      ul {
        for (docModule in docPackage.docModules) {
          if (docModule.isUnlisted) continue

          val module = docModule.schema
          val moduleScope = pageScope.getModule(module.moduleName)
          val memberDocs =
            MemberDocs(
              module.docComment,
              moduleScope,
              module.annotations,
              extraMemberInfo = collectMemberInfo(docModule),
            )

          renderModuleOrPackage(module.moduleName, moduleScope, memberDocs)
        }
      }
    }
  }
}

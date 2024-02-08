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

internal abstract class MainOrPackagePageGenerator<S>(
  docsiteInfo: DocsiteInfo,
  pageScope: S,
  private val siteScope: SiteScope
) : PageGenerator<S>(docsiteInfo, pageScope) where S : PageScope {
  protected fun UL.renderModuleOrPackage(
    name: String,
    moduleOrPackageScope: DocScope,
    memberDocs: MemberDocs
  ) {
    li {
      renderAnchor(name)

      div {
        classes = setOf("member", "with-page-link")

        memberDocs.renderExpandIcon(this)
        renderSelfLink(name)

        div {
          classes = setOf("member-left")

          div {
            classes =
              if (memberDocs.isDeprecatedMember) {
                setOf("member-modifiers", "member-deprecated")
              } else setOf("member-modifiers")

            renderModifiers(
              setOf(),
              if (moduleOrPackageScope is PackageScope) "package" else "module"
            )
          }
        }

        div {
          classes = setOf("member-main")

          div {
            classes =
              if (memberDocs.isDeprecatedMember) {
                setOf("member-signature", "member-deprecated")
              } else setOf("member-signature")

            a {
              classes = setOf("name-decl")
              val link = "./" + moduleOrPackageScope.urlRelativeTo(pageScope).toString()
              href =
                if (pageScope is SiteScope) {
                  link.replaceFirst((moduleOrPackageScope as PackageScope).version, "current")
                } else {
                  link
                }
              if (moduleOrPackageScope is ModuleScope) {
                +name.asModuleName
              } else {
                +name
              }
            }
          }

          memberDocs.renderDocComment(this)
        }
      }
    }
  }
}

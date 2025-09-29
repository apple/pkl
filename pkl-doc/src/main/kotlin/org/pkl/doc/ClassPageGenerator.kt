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

import java.io.OutputStream
import kotlinx.html.*
import org.pkl.core.PClass

internal class ClassPageGenerator(
  docsiteInfo: DocsiteInfo,
  docPackage: DocPackage,
  docModule: DocModule,
  clazz: PClass,
  pageScope: ClassScope,
  isTestMode: Boolean,
  consoleOut: OutputStream,
) :
  ModuleOrClassPageGenerator<ClassScope>(
    docsiteInfo,
    docModule,
    clazz,
    pageScope,
    isTestMode,
    consoleOut,
  ) {
  override val html: HTML.() -> Unit = {
    renderHtmlHead()

    body {
      onLoad = "onLoad()"

      renderPageHeader(docPackage.name, docPackage.version, clazz.moduleName, clazz.simpleName)

      main {
        renderParentLinks()

        renderAnchors(clazz)

        h1 {
          id = "declaration-title"
          +clazz.simpleName.asIdentifier

          span {
            id = "declaration-version"
            +docPackage.version
          }
        }

        val memberDocs =
          MemberDocs(
            clazz.docComment,
            pageScope,
            clazz.annotations,
            isDeclaration = true,
            mapOf(
              MemberInfoKey("Known subtypes in package", runtimeDataClasses) to
                {
                  id = HtmlConstants.KNOWN_SUBTYPES
                  classes = runtimeDataClasses
                },
              MemberInfoKey("Known usages in package", runtimeDataClasses) to
                {
                  id = HtmlConstants.KNOWN_USAGES
                  classes = runtimeDataClasses
                },
              MemberInfoKey("All versions", runtimeDataClasses) to
                {
                  id = HtmlConstants.KNOWN_VERSIONS
                  classes = runtimeDataClasses
                },
            ),
          )

        renderMemberGroupLinks(
          Triple("Overview", "#_overview", memberDocs.isExpandable),
          Triple("Properties", "#_properties", clazz.hasListedProperty),
          Triple("Methods", "#_methods", clazz.hasListedMethod),
        )

        renderAnchor("_overview")
        div {
          id = "_declaration"
          classes = setOf("member")

          memberDocs.renderExpandIcon(this)

          div {
            classes =
              if (memberDocs.isDeprecatedMember) {
                setOf("member-signature", "member-deprecated")
              } else setOf("member-signature")

            renderModifiers(clazz.modifiers, "class")

            span {
              classes = setOf("name-decl")

              +clazz.simpleName.asIdentifier
            }

            renderTypeParameters(clazz.typeParameters)

            renderClassExtendsClause(clazz, pageScope)
          }

          memberDocs.renderDocComment(this)
        }

        renderProperties()
        renderMethods()
      }
    }
  }

  // example output:
  // HostAlias (pkg.pkl-lang.org/pkl-k8s/k8s@1.0.0) • Package Docs
  override fun HTMLTag.renderPageTitle() {
    val classScope = pageScope
    val moduleScope = classScope.parent!!
    val packageScope = moduleScope.parent!!

    +classScope.clazz.simpleName
    +" ("
    +packageScope.name
    +moduleScope.name.drop(packageScope.name.length).replace('.', '/')
    +":"
    +packageScope.version
    +") • "
    +(docsiteInfo.title ?: "Pkldoc")
  }
}

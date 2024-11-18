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

import kotlinx.html.*

internal class ModulePageGenerator(
  docsiteInfo: DocsiteInfo,
  docPackage: DocPackage,
  docModule: DocModule,
  pageScope: ModuleScope,
  isTestMode: Boolean
) :
  ModuleOrClassPageGenerator<ModuleScope>(
    docsiteInfo,
    docModule,
    docModule.schema.moduleClass,
    pageScope,
    isTestMode
  ) {
  private val module = docModule.schema

  override val html: HTML.() -> Unit = {
    renderHtmlHead()

    body {
      onLoad = "onLoad()"

      renderPageHeader(docPackage.name, docPackage.version, docModule.name, null)

      main {
        renderParentLinks()

        h1 {
          id = "declaration-title"

          +docModule.name.asModuleName

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
            collectMemberInfo(docModule)
          )

        renderMemberGroupLinks(
          Triple("Overview", "#_overview", memberDocs.isExpandable),
          Triple("Properties", "#_properties", clazz.hasListedProperty),
          Triple("Methods", "#_methods", clazz.hasListedMethod),
          Triple("Classes", "#_classes", module.hasListedClass),
          Triple("Type Aliases", "#_type-aliases", module.hasListedTypeAlias)
        )

        renderAnchor("_overview")
        div {
          id = "_declaration"
          classes = setOf("member")

          memberDocs.renderExpandIcon(this)

          div {
            classes = setOf("member-signature")

            renderModifiers(module.moduleClass.modifiers, "module")

            span {
              classes = setOf("name-decl")

              +docModule.name.asModuleName
            }

            renderModuleAmendsOrExtendsClause(module)
          }

          memberDocs.renderDocComment(this)
        }

        renderProperties()
        renderMethods()
        renderClasses()
        renderTypeAliases()
      }
    }
  }

  // example output:
  // module PodSpec (pkg.pkl-lang.org/pkl-k8s/k8s:1.0.0) • Package Docs
  override fun HTMLTag.renderPageTitle() {
    val moduleScope = pageScope
    val packageScope = moduleScope.parent!!

    +moduleScope.name.substringAfterLast('.')
    +" ("
    +packageScope.name
    +moduleScope.name.drop(packageScope.name.length).substringBeforeLast('.').replace('.', '/')
    +":"
    +packageScope.version
    +") • "
    +(docsiteInfo.title ?: "Pkldoc")
  }

  private fun HtmlBlockTag.renderTypeAliases() {
    if (!module.hasListedTypeAlias) return

    div {
      classes = setOf("member-group")

      renderAnchor("_type-aliases")

      h2 {
        classes = setOf("member-group-title")

        +"Type Aliases"
        if (module.supermodule.hasListedTypeAlias) {
          renderShowInheritedButton()
        }
      }

      ul {
        for ((typeAliasName, typeAlias) in module.allTypeAliases) {
          if (typeAlias.isUnlisted) continue

          li {
            renderAnchors(typeAlias)

            div {
              val isInherited = typeAliasName !in module.typeAliases
              classes =
                if (isInherited) {
                  setOf("member", "inherited", "expandable", "hidden", "collapsed")
                } else setOf("member")

              val typeAliasScope = TypeAliasScope(typeAlias, pageScope.url, pageScope)
              val memberDocs =
                MemberDocs(typeAlias.docComment, typeAliasScope, typeAlias.annotations)

              memberDocs.renderExpandIcon(this)
              renderSelfLink(typeAliasName)

              div {
                classes = setOf("member-left")

                div {
                  classes =
                    if (memberDocs.isDeprecatedMember) {
                      setOf("member-modifiers", "member-deprecated")
                    } else setOf("member-modifiers")

                  renderModifiers(typeAlias.modifiers, "typealias")
                }
              }

              div {
                classes = setOf("member-main")

                div {
                  classes =
                    if (memberDocs.isDeprecatedMember) {
                      setOf("member-signature", "member-deprecated")
                    } else setOf("member-signature")

                  renderTypeAliasName(typeAlias, "name-decl")
                  renderTypeParameters(typeAlias.typeParameters)
                  +" = "
                  renderType(typeAlias.aliasedType, typeAliasScope)

                  if (isInherited) {
                    renderModuleContext(typeAlias)
                  }
                  renderMemberSourceLink(typeAlias)
                }

                memberDocs.renderDocComment(this)
              }
            }
          }
        }
      }
    }
  }

  private fun HtmlBlockTag.renderClasses() {
    if (!module.hasListedClass) return

    div {
      classes = setOf("member-group")

      renderAnchor("_classes")

      h2 {
        classes = setOf("member-group-title")

        +"Classes"
        if (module.supermodule.hasListedClass) {
          renderShowInheritedButton()
        }
      }

      ul {
        for ((className, clazz) in module.allClasses) {
          if (clazz.isUnlisted) continue

          li {
            renderAnchor(className)

            div {
              val isInherited = className !in module.classes
              classes =
                if (isInherited) {
                  setOf(
                    "member",
                    "with-page-link",
                    "inherited",
                    "expandable",
                    "hidden",
                    "collapsed"
                  )
                } else setOf("member", "with-page-link")

              val classScope = ClassScope(clazz, pageScope.url, pageScope)
              val memberDocs =
                MemberDocs(clazz.docComment, classScope, clazz.annotations, isDeclaration = false)

              memberDocs.renderExpandIcon(this)
              renderSelfLink(className)

              div {
                classes = setOf("member-left")

                div {
                  classes =
                    if (memberDocs.isDeprecatedMember) {
                      setOf("member-modifiers", "member-deprecated")
                    } else setOf("member-modifiers")

                  renderModifiers(clazz.modifiers, "class")
                }
              }

              div {
                classes = setOf("member-main")

                div {
                  classes =
                    if (memberDocs.isDeprecatedMember) {
                      setOf("member-signature", "member-deprecated")
                    } else setOf("member-signature")

                  renderClassName(clazz, "name-decl")
                  renderTypeParameters(clazz.typeParameters)
                  renderClassExtendsClause(clazz, classScope)

                  if (isInherited) {
                    renderModuleContext(clazz)
                  }
                  renderMemberSourceLink(clazz)
                }

                memberDocs.renderDocComment(this)
              }
            }
          }
        }
      }
    }
  }
}

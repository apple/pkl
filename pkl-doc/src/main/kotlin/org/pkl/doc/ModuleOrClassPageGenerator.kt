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

import java.io.StringWriter
import kotlinx.html.*
import org.pkl.core.Member
import org.pkl.core.PClass
import org.pkl.core.PClass.ClassMember
import org.pkl.core.PClass.Method
import org.pkl.core.TypeParameter
import org.pkl.core.TypeParameter.Variance
import org.pkl.core.ValueRenderers

internal abstract class ModuleOrClassPageGenerator<S>(
  docsiteInfo: DocsiteInfo,
  private val docModule: DocModule,
  protected val clazz: PClass,
  scope: S,
  private val isTestMode: Boolean
) : PageGenerator<S>(docsiteInfo, scope) where S : PageScope {
  protected fun HtmlBlockTag.renderProperties() {
    if (!clazz.hasListedProperty) return

    div {
      classes = setOf("member-group")

      renderAnchor("_properties")

      h2 {
        classes = setOf("member-group-title")

        +"Properties"
        if (clazz.superclass.hasListedProperty) {
          renderShowInheritedButton()
        }
      }

      ul {
        for ((propertyName, property) in clazz.allProperties) {
          if (property.isUnlisted) continue

          li {
            renderAnchor(propertyName)

            div {
              val isInherited = property.owner.info != clazz.info
              classes =
                when {
                  isInherited -> setOf("member", "inherited", "expandable", "hidden", "collapsed")
                  property.isHidden -> setOf("member", "hidden-member")
                  else -> setOf("member")
                }

              val propertyScope = PropertyScope(property, pageScope)
              val memberDocs =
                MemberDocs(property.inheritedDocComment, propertyScope, property.annotations)

              memberDocs.renderExpandIcon(this)
              renderSelfLink(propertyName)

              div {
                classes = setOf("member-left")

                div {
                  classes =
                    if (memberDocs.isDeprecatedMember) {
                      setOf("member-modifiers", "member-deprecated")
                    } else setOf("member-modifiers")

                  renderModifiers(property.modifiers)
                }
              }

              div {
                classes = setOf("member-main")

                div {
                  classes =
                    if (memberDocs.isDeprecatedMember) {
                      setOf("member-signature", "member-deprecated")
                    } else setOf("member-signature")

                  if (isInherited) renderClassContext(property)

                  span {
                    classes = setOf("name-decl")

                    +propertyName.asIdentifier
                  }

                  +": "
                  renderType(property.type, propertyScope)

                  if (isInherited) {
                    renderModuleContext(property)
                  }
                  renderMemberSourceLink(property)
                }

                memberDocs.renderDocComment(this)
              }
            }
          }
        }
      }
    }
  }

  private fun HtmlBlockTag.renderClassContext(member: ClassMember) {
    if (pageScope is ClassScope) {
      span {
        classes = setOf("context")
        renderClassName(member.owner)
        +"."
      }
    }
  }

  protected fun HtmlBlockTag.renderModuleContext(member: Member) {
    span {
      classes = setOf("context")
      +" ("
      renderModuleName(member.moduleName)
      +")"
    }
  }

  private fun renderExportedValue(value: Any): String {
    val writer = StringWriter()
    ValueRenderers.pcf(writer, "  ", false, false).renderValue(value)
    return writer.toString()
  }

  protected fun HtmlBlockTag.renderMethods() {
    if (!clazz.hasListedMethod) return

    div {
      classes = setOf("member-group")

      renderAnchor("_methods")

      h2 {
        classes = setOf("member-group-title")

        +"Methods"
        if (clazz.superclass.hasListedMethod) {
          renderShowInheritedButton()
        }
      }

      ul {
        for ((methodName, method) in clazz.allMethods) {
          if (method.isUnlisted) continue

          li {
            renderAnchors(method)

            div {
              val isInherited = method.owner.info != clazz.info
              classes =
                if (isInherited) {
                  setOf("member", "inherited", "expandable", "hidden", "collapsed")
                } else setOf("member")

              val methodScope = MethodScope(method, pageScope)
              val memberDocs =
                MemberDocs(method.inheritedDocComment, methodScope, method.annotations)

              memberDocs.renderExpandIcon(this)
              renderSelfLink("$methodName()")

              div {
                classes = setOf("member-left")

                div {
                  classes =
                    if (memberDocs.isDeprecatedMember) {
                      setOf("member-modifiers", "member-deprecated")
                    } else setOf("member-modifiers")

                  renderModifiers(method.modifiers, "function")
                }
              }

              div {
                classes = setOf("member-main")

                div {
                  classes =
                    if (memberDocs.isDeprecatedMember)
                      setOf("member-signature", "member-deprecated")
                    else setOf("member-signature")

                  if (isInherited) renderClassContext(method)

                  span {
                    classes = setOf("name-decl")

                    +method.simpleName.asIdentifier
                  }

                  renderTypeParameters(method.typeParameters)

                  renderMethodParameters(method, methodScope)

                  +": "
                  renderType(method.returnType, methodScope)

                  if (isInherited) {
                    renderModuleContext(method)
                  }
                  renderMemberSourceLink(method)
                }

                memberDocs.renderDocComment(this)
              }
            }
          }
        }
      }
    }
  }

  protected fun HtmlBlockTag.renderTypeParameters(parameters: List<TypeParameter>) {
    if (parameters.isEmpty()) return

    +"<"
    var first = true
    parameters.forEachIndexed { idx, param ->
      if (first) first = false else +", "
      when (param.variance) {
        Variance.CONTRAVARIANT -> +"in "
        Variance.COVARIANT -> +"out "
        else -> {}
      }
      a {
        classes = setOf("param${idx + 1}")
        +param.name
      }
    }
    +">"
  }

  private fun HtmlBlockTag.renderMethodParameters(method: Method, methodScope: MethodScope) {
    +"("
    var first = true
    val indexOffset = method.typeParameters.size
    method.parameters.entries.forEachIndexed { idx, (name, type) ->
      if (first) first = false else +", "
      span {
        classes = setOf("param${indexOffset + idx + 1}")
        +name
      }
      +": "
      renderType(type, methodScope)
    }
    +")"
  }

  protected fun HtmlBlockTag.renderMemberSourceLink(member: Member) {
    // Prevent churn by setting static line numbers.
    // This is so our doc generator tests don't break if, say, we change sources in the stdlib.
    val startLine = if (isTestMode) 123 else member.sourceLocation.startLine
    val endLine = if (isTestMode) 456 else member.sourceLocation.endLine
    val moduleSourceUrl =
      pageScope.resolveModuleNameToSourceUrl(
        member.moduleName,
        Member.SourceLocation(startLine, endLine)
      )
        ?: return
    a {
      classes = setOf("member-source-link")
      href = moduleSourceUrl.toString()
      +"Source"
    }
  }

  protected fun HtmlBlockTag.renderShowInheritedButton() {
    span {
      classes = setOf("toggle-inherited-members")
      +"("
      span {
        classes = setOf("toggle-inherited-members-link", "button-link")
        +"show inherited"
      }
      +")"
    }
  }
}

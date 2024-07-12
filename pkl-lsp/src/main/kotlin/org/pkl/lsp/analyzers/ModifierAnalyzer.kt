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
package org.pkl.lsp.analyzers

import org.pkl.lsp.ErrorMessages
import org.pkl.lsp.PklLSPServer
import org.pkl.lsp.ast.*
import org.pkl.lsp.ast.TokenType.*

class ModifierAnalyzer(private val server: PklLSPServer) : Analyzer() {
  companion object {
    private val MODULE_MODIFIERS = setOf(ABSTRACT, OPEN)
    private val AMENDING_MODULE_MODIFIERS = emptySet<TokenType>()
    private val CLASS_MODIFIERS = setOf(ABSTRACT, OPEN, EXTERNAL, LOCAL)
    private val TYPE_ALIAS_MODIFIERS = setOf(EXTERNAL, LOCAL)
    private val CLASS_METHOD_MODIFIERS = setOf(ABSTRACT, EXTERNAL, LOCAL, CONST)
    private val CLASS_PROPERTY_MODIFIERS = setOf(ABSTRACT, EXTERNAL, HIDDEN, LOCAL, FIXED, CONST)
    private val OBJECT_METHOD_MODIFIERS = setOf(LOCAL)
    private val OBJECT_PROPERTY_MODIFIERS = setOf(LOCAL)
  }

  override fun doAnalyze(node: Node, diagnosticsHolder: MutableList<PklDiagnostic>): Boolean {
    if (node !is ModifierListOwner || node.modifiers == null) {
      return node is PklModule || node is PklClass || node is PklClassBody
    }

    var localModifier: Node? = null
    var abstractModifier: Node? = null
    var openModifier: Node? = null
    var hiddenModifier: Node? = null
    var fixedModifier: Node? = null

    for (modifier in node.modifiers!!) {
      when (modifier.type) {
        LOCAL -> localModifier = modifier
        ABSTRACT -> abstractModifier = modifier
        OPEN -> openModifier = modifier
        HIDDEN -> hiddenModifier = modifier
        FIXED -> fixedModifier = modifier
        else -> {}
      }
    }
    if (localModifier == null) {
      when (node) {
        is PklClassProperty -> {
          if (
            node.parent is PklModule &&
              (node.parent as PklModule).isAmend &&
              (hiddenModifier != null || node.typeAnnotation != null)
          ) {
            if (node.identifier != null) {
              diagnosticsHolder.add(
                error(node.identifier!!, ErrorMessages.create("missingModifierLocal"))
              )
              return true
            }
          }
        }
      }
    }

    if (abstractModifier != null && openModifier != null) {
      diagnosticsHolder.add(
        error(abstractModifier, ErrorMessages.create("modifierAbstractConflictsWithOpen"))
      )
      diagnosticsHolder.add(
        error(openModifier, ErrorMessages.create("modifierOpenConflictsWithAbstract"))
      )
    }

    val (description, applicableModifiers) =
      when (node) {
        is PklModuleDeclaration ->
          if (node.isAmend) "amending modules" to AMENDING_MODULE_MODIFIERS
          else "modules" to MODULE_MODIFIERS
        is PklClass -> "classes" to CLASS_MODIFIERS
        is PklTypeAlias -> "typealiases" to TYPE_ALIAS_MODIFIERS
        is PklClassMethod -> "class methods" to CLASS_METHOD_MODIFIERS
        is PklClassProperty -> "class properties" to CLASS_PROPERTY_MODIFIERS
        else -> return false
      }
    for (modifier in node.modifiers!!) {
      if (modifier.type !in applicableModifiers) {
        diagnosticsHolder.add(
          error(
            modifier,
            ErrorMessages.create("modifierIsNotApplicable", modifier.text, description)
          )
        )
      }
    }
    return node is PklModule || node is PklClass || node is PklClassBody
  }
}

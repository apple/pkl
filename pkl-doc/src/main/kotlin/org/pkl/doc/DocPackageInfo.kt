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

import java.net.URI
import kotlinx.serialization.Contextual
import org.pkl.commons.toUri
import org.pkl.core.Member
import org.pkl.core.Member.SourceLocation
import org.pkl.core.PModule
import org.pkl.core.PObject
import org.pkl.core.TypeAlias

/** API equivalent of standard library module `pkl.DocPackageInfo`. */
data class DocPackageInfo(
  /** The name of this doc package. */
  val name: String,

  /** The prefix for all modules within this doc package. */
  val moduleNamePrefix: String = "$name.",

  /** The URI of the package, if it is a `package://` URI. */
  val uri: URI?,

  /**
   * The version of this package.
   *
   * Use `"0.0.0"` for unversioned packages.
   */
  val version: String,

  /** The import base URI for modules in this package. */
  val importUri: String,

  /** The maintainers' emails for this package. */
  val authors: List<String>?,

  /** The web URL of the source code for this package. */
  val sourceCode: URI?,

  /** The source code scheme for this package. */
  val sourceCodeUrlScheme: String?,

  /** The web URL of the issue tracker for this package. */
  val issueTracker: URI?,

  /**
   * The packages depended-on by this package.
   *
   * Used to display package dependencies and to create documentation links.
   */
  val dependencies: List<PackageDependency> = listOf(),

  /**
   * The overview documentation for this package.
   *
   * Supports the same Markdown syntax as Pkldoc comments. By default, only the first paragraph is
   * displayed.
   */
  val overview: String?,

  /** Imports used to resolve Pkldoc links in [overview]. */
  val overviewImports: Map<String, URI> = mapOf(),

  /** Annotations for this package, such as `@Deprecated`. */
  val annotations: List<PObject> = listOf(),

  /** Extra attributes to add to the documentation of the package. */
  val extraAttributes: Map<String, String> = mapOf(),
) {
  companion object {
    @Suppress("UNCHECKED_CAST")
    fun fromPkl(module: PModule): DocPackageInfo =
      DocPackageInfo(
        name = module["name"] as String,
        version = module["version"] as String,
        importUri = module["importUri"] as String,
        uri = (module["uri"] as String?)?.toUri(),
        authors = module["authors"] as List<String>,
        sourceCode = (module["sourceCode"] as String?)?.toUri(),
        sourceCodeUrlScheme = module["sourceCodeUrlScheme"] as String?,
        issueTracker = (module["issueTracker"] as String?)?.toUri(),
        dependencies =
          (module["dependencies"] as List<PObject>).map { dependency ->
            PackageDependency(
              name = dependency["name"] as String,
              uri = null, // dependencies declared in a doc-package-info file do not have URIs
              version = dependency["version"] as String,
              sourceCode = (dependency["sourceCode"] as String?)?.toUri(),
              sourceCodeUrlScheme = dependency["sourceCodeUrlScheme"] as String?,
              documentation = (dependency["documentation"] as String?)?.toUri(),
            )
          },
        overview = module["overview"] as String,
        overviewImports =
          (module["overviewImports"] as Map<String, String>).mapValues { it.value.toUri() },
        annotations = module["annotations"] as List<PObject>,
        extraAttributes = module["extraAttributes"] as Map<String, String>
      )
  }

  internal fun getModuleRef(moduleName: String): ModuleRef? {
    if (moduleName.startsWith(moduleNamePrefix)) {
      return ModuleRef(name, uri, version, getModulePath(moduleName, moduleNamePrefix))
    }
    for (dependency in dependencies) {
      if (moduleName.startsWith(dependency.prefix)) {
        return ModuleRef(
          dependency.name,
          dependency.uri,
          dependency.version,
          getModulePath(moduleName, dependency.prefix)
        )
      }
    }
    return null
  }

  internal fun getTypeRef(type: Member /* PClass|TypeAlias */): TypeRef? {
    val moduleName = type.moduleName
    if (moduleName.startsWith(moduleNamePrefix)) {
      return TypeRef(
        name,
        uri,
        version,
        getModulePath(moduleName, moduleNamePrefix),
        type.simpleName,
        isTypeAlias = type is TypeAlias
      )
    }
    for (dependency in dependencies) {
      if (moduleName.startsWith(dependency.prefix)) {
        return TypeRef(
          dependency.name,
          dependency.uri,
          dependency.version,
          getModulePath(moduleName, dependency.prefix),
          type.simpleName,
          isTypeAlias = type is TypeAlias
        )
      }
    }
    return null
  }

  internal fun getModuleImportUri(moduleName: String): URI =
    when (importUri) {
      "pkl:/" -> "pkl:${moduleName.substring(4)}".toUri()
      else -> {
        val path = getModulePath(moduleName, moduleNamePrefix).uriEncoded + ".pkl"
        URI(importUri + path)
      }
    }

  internal fun getModuleSourceCode(moduleName: String): URI? {
    val path = "/" + getModulePath(moduleName, moduleNamePrefix).uriEncoded + ".pkl"
    // assumption: the fragment is only used for line numbers
    return sourceCodeUrlScheme?.replace("%{path}", path)?.substringBefore('#')?.let(URI::create)
  }

  /** Information about a depended-on package. */
  data class PackageDependency(
    /** The name of the depended-on package. */
    val name: String,

    /** The URI of the depended-upon package, if any. */
    val uri: @Contextual URI?,

    /** The version of the depended-on package. */
    val version: String,

    /**
     * The web URL of the source code for the depended-on package *version*. Must end with a slash.
     */
    val sourceCode: @Contextual URI?,

    /** The source URL scheme of the depended-upon package _version_, with placeholders. */
    val sourceCodeUrlScheme: String?,

    /**
     * The web URL of the Pkldoc page for the depended-on package *version*. Must end with a slash.
     * Only needs to be set if the depended-on package belongs to a different Pkldoc website.
     */
    val documentation: @Contextual URI?,
  ) {
    internal val prefix = "$name."

    /** Note: Returns an absolute URI, or an URI relative to the current site. */
    internal fun getModuleDocUrl(moduleName: String): URI? =
      when {
        !moduleName.startsWith(prefix) -> null
        else -> {
          val modulePath = moduleName.substring(prefix.length).replace('.', '/').pathEncoded
          if (documentation == null) {
            "${name.pathEncoded}/$version/$modulePath/index.html".toUri()
          } else {
            documentation.resolve("$modulePath/index.html")
          }
        }
      }

    internal fun getModuleSourceCode(moduleName: String): URI? =
      when {
        !moduleName.startsWith(prefix) -> null
        else -> {
          val modulePath = moduleName.substring(prefix.length).replace('.', '/')
          sourceCode?.resolve("$modulePath.pkl")
        }
      }

    internal fun getModuleSourceCodeWithSourceLocation(
      moduleName: String,
      sourceLocation: SourceLocation
    ): URI? {
      return when {
        !moduleName.startsWith(prefix) -> null
        else -> {
          val modulePath = moduleName.substring(prefix.length).replace('.', '/')
          val path = "/$modulePath.pkl"
          sourceCodeUrlScheme?.replaceSourceCodePlaceholders(path, sourceLocation)?.toUri()
        }
      }
    }
  }
}

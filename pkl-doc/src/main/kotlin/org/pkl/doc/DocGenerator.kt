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

import java.io.IOException
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isSameFileAs
import org.pkl.commons.deleteRecursively
import org.pkl.core.ModuleSchema
import org.pkl.core.PClassInfo
import org.pkl.core.Version

/**
 * Entry point for the low-level Pkldoc API.
 *
 * For the high-level Pkldoc API, see [CliDocGenerator].
 */
class DocGenerator(
  /**
   * The documentation website to generate.
   *
   * API equivalent of `pkl:DocsiteInfo`.
   */
  private val docsiteInfo: DocsiteInfo,

  /** The modules to generate documentation for, grouped by package. */
  modules: Map<DocPackageInfo, Collection<ModuleSchema>>,

  /**
   * A function to resolve imports in [modules], [packageInfos], and [docsiteInfo].
   *
   * Module `pkl.base` is resolved with this function even if not explicitly imported.
   */
  private val importResolver: (URI) -> ModuleSchema,

  /** A comparator for package versions. */
  versionComparator: Comparator<String>,
  /** The directory where generated documentation is placed. */
  private val outputDir: Path,

  /**
   * Internal option used only for testing.
   *
   * Generates source URLs with fixed line numbers `#L123-L456` to avoid churn in expected output
   * files (e.g., when stdlib line numbers change).
   */
  private val isTestMode: Boolean = false
) {
  companion object {
    internal fun List<PackageData>.current(
      versionComparator: Comparator<String>
    ): List<PackageData> {
      val comparator =
        compareBy<PackageData> { it.ref.pkg }.thenBy(versionComparator) { it.ref.version }
      return asSequence()
        // If matching a semver pattern, remove any version that has a prerelease
        // version (e.g. SNAPSHOT in 1.2.3-SNAPSHOT)
        .filter { Version.parseOrNull(it.ref.version)?.preRelease == null }
        .sortedWith(comparator)
        .distinctBy { it.ref.pkg }
        .toList()
    }
  }

  private val descendingVersionComparator: Comparator<String> = versionComparator.reversed()

  private val docPackages: List<DocPackage> = modules.map { DocPackage(it.key, it.value.toList()) }

  /** Runs this documentation generator. */
  fun run() {
    try {
      val htmlGenerator =
        HtmlGenerator(docsiteInfo, docPackages, importResolver, outputDir, isTestMode)
      val searchIndexGenerator = SearchIndexGenerator(outputDir)
      val packageDataGenerator = PackageDataGenerator(outputDir)
      val runtimeDataGenerator = RuntimeDataGenerator(descendingVersionComparator, outputDir)

      for (docPackage in docPackages) {
        if (docPackage.isUnlisted) continue

        docPackage.deletePackageDir()
        htmlGenerator.generate(docPackage)
        searchIndexGenerator.generate(docPackage)
        packageDataGenerator.generate(docPackage)
      }

      val packagesData = packageDataGenerator.readAll()
      val currentPackagesData = packagesData.current(descendingVersionComparator)

      createSymlinks(currentPackagesData)

      htmlGenerator.generateSite(currentPackagesData)
      searchIndexGenerator.generateSiteIndex(currentPackagesData)
      runtimeDataGenerator.deleteDataDir()
      runtimeDataGenerator.generate(packagesData)
    } catch (e: IOException) {
      throw DocGeneratorException("I/O error generating documentation.", e)
    }
  }

  private fun DocPackage.deletePackageDir() {
    outputDir.resolve("$name/$version").deleteRecursively()
  }

  private fun createSymlinks(currentPackagesData: List<PackageData>) {
    for (packageData in currentPackagesData) {
      val basePath = outputDir.resolve(packageData.ref.pkg.pathEncoded)
      val src = basePath.resolve(packageData.ref.version)
      val dest = basePath.resolve("current")
      if (dest.exists() && dest.isSameFileAs(src)) continue
      dest.deleteIfExists()
      dest.createSymbolicLinkPointingTo(basePath.relativize(src))
    }
  }
}

internal class DocPackage(val docPackageInfo: DocPackageInfo, val modules: List<ModuleSchema>) {
  val name: String
    get() = docPackageInfo.name

  val version: String
    get() = docPackageInfo.version

  val uri: URI?
    get() = docPackageInfo.uri

  val overview: String?
    get() = docPackageInfo.overview

  val minPklVersion: Version? by lazy { docModules.mapNotNull { it.minPklVersion }.maxOrNull() }

  val deprecation: String? = docPackageInfo.annotations.deprecation

  val isUnlisted: Boolean = docPackageInfo.annotations.isUnlisted

  val hasListedModule: Boolean by lazy { docModules.any { !it.isUnlisted } }

  private val exampleModulesBySubject: Map<String, List<ModuleSchema>> by lazy {
    val result = mutableMapOf<String, MutableList<ModuleSchema>>()
    for (mod in modules) {
      val ann = mod.annotations.find { it.classInfo == PClassInfo.DocExample } ?: continue

      @Suppress("UNCHECKED_CAST") val subjects = ann["subjects"] as List<String>
      for (subject in subjects) {
        val examples = result[subject]
        if (examples == null) {
          result[subject] = mutableListOf(mod)
        } else {
          examples.add(mod)
        }
      }
    }
    result
  }

  val docModules: List<DocModule> by lazy {
    val regularModules =
      modules.filter { mod -> !mod.annotations.any { it.classInfo == PClassInfo.DocExample } }
    regularModules.map { mod ->
      DocModule(
        this,
        mod,
        docPackageInfo.version,
        docPackageInfo.getModuleImportUri(mod.moduleName),
        docPackageInfo.getModuleSourceCode(mod.moduleName),
        exampleModulesBySubject[mod.moduleName] ?: listOf()
      )
    }
  }
}

internal class DocModule(
  val parent: DocPackage,
  val schema: ModuleSchema,
  val version: String,
  val importUri: URI,
  val sourceUrl: URI?,
  val examples: List<ModuleSchema>
) {
  val name: String
    get() = schema.moduleName

  val path: String by lazy {
    name.substring(parent.docPackageInfo.moduleNamePrefix.length).replace('.', '/')
  }

  val overview: String?
    get() = schema.docComment

  val minPklVersion: Version? by lazy {
    val version =
      schema.annotations.find { it.classInfo == PClassInfo.ModuleInfo }?.get("minPklVersion")
        as String?
    version?.let { Version.parse(it) }
  }

  val deprecation: String? = schema.annotations.deprecation

  val isUnlisted: Boolean = schema.annotations.isUnlisted
}

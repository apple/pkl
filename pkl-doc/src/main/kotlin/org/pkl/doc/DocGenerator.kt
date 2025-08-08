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

import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.io.path.*
import kotlinx.coroutines.*
import org.pkl.commons.copyRecursively
import org.pkl.core.ModuleSchema
import org.pkl.core.PClassInfo
import org.pkl.core.Version
import org.pkl.core.util.IoUtils

/**
 * Entry point for the low-level Pkldoc API.
 *
 * For the high-level Pkldoc API, see [CliDocGenerator].
 */
@OptIn(ExperimentalPathApi::class)
class DocGenerator(
  /**
   * The documentation website to generate.
   *
   * API equivalent of `pkl:DocsiteInfo`.
   */
  private val docsiteInfo: DocsiteInfo,

  /** The packages to generate documentation for. */
  packages: Map<DocPackageInfo, Collection<ModuleSchema>>,

  /**
   * A function to resolve imports in [packages] and [docsiteInfo].
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
  private val isTestMode: Boolean = false,

  /**
   * Disables creation of symbolic links, using copies of files and directories instead of them.
   *
   * In particular, determines how to create the "current" directory which contains documentation
   * for the latest version of the package.
   *
   * `false` will make the current directory into a symlink to the actual version directory. `true`,
   * however, will create a full copy instead.
   */
  private val noSymlinks: Boolean = false,

  /** The output stream to write logs to. */
  consoleOut: OutputStream,

  /**
   * The doc migrator that is used to determine the latest docsite version, as well as update the
   * version file.
   */
  private val docMigrator: DocMigrator = DocMigrator(outputDir, consoleOut, versionComparator),
) : AbstractGenerator(consoleOut) {
  companion object {
    const val CURRENT_DIRECTORY_NAME = "current"

    internal fun determineCurrentPackages(
      packages: List<PackageData>,
      descendingVersionComparator: Comparator<String>,
    ): List<PackageData> {
      val comparator =
        compareBy<PackageData> { it.ref.pkg }.thenBy(descendingVersionComparator) { it.ref.version }
      return packages
        // If matching a semver pattern, remove any version that has a prerelease
        // version (e.g. SNAPSHOT in 1.2.3-SNAPSHOT)
        .filter { Version.parseOrNull(it.ref.version)?.preRelease == null }
        .sortedWith(comparator)
        .distinctBy { it.ref.pkg }
        .toList()
    }

    /**
     * The default exeuctor when running the doc generator.
     *
     * Uses [Executors.newVirtualThreadPerTaskExecutor] if available (JDK 21). Otherwise, uses
     * [Executors.newFixedThreadPool] with 64 threads, or the number of available processors
     * available to the JVM (whichever is higher).
     */
    internal val executor: Executor
      get() {
        try {
          val method = Executors::class.java.getMethod("newVirtualThreadPerTaskExecutor")
          return method.invoke(null) as Executor
        } catch (e: Throwable) {
          when (e) {
            is NoSuchMethodException,
            is IllegalAccessException ->
              return Executors.newFixedThreadPool(
                64.coerceAtLeast(Runtime.getRuntime().availableProcessors())
              )
            else -> throw e
          }
        }
      }
  }

  private val descendingVersionComparator: Comparator<String> = versionComparator.reversed()

  private val docPackages: List<DocPackage> =
    packages.map { DocPackage(it.key, it.value.toList()) }.filter { !it.isUnlisted }

  private fun tryLoadPackageData(entry: SearchIndexGenerator.PackageIndexEntry): PackageData? {
    var packageDataFile =
      outputDir.resolve(entry.packageEntry.url).resolveSibling("package-data.json")
    if (!Files.exists(packageDataFile)) {
      // search-index.js in Pkl 0.29 and below did not encode path.
      // If we get a file does not exist, try again by encoding the path.
      packageDataFile =
        outputDir.resolve(entry.packageEntry.url.pathEncoded).resolveSibling("package-data.json")
      if (!Files.exists((packageDataFile))) {
        writeOutputLine(
          "[Warn] likely corrupted search index; missing $packageDataFile. This entry will be removed in the newly generated index."
        )
        return null
      }
    }
    writeOutput("Loading package data for ${packageDataFile.toUri()}\r")
    return PackageData.read(packageDataFile)
  }

  private suspend fun getCurrentPackages(
    siteSearchIndex: List<SearchIndexGenerator.PackageIndexEntry>
  ): List<PackageData> = coroutineScope {
    siteSearchIndex.map { async { tryLoadPackageData(it) } }.awaitAll().filterNotNull()
  }

  /** Runs this documentation generator. */
  fun run() =
    runBlocking(executor.asCoroutineDispatcher()) {
      try {
        if (!docMigrator.isUpToDate) {
          throw DocGeneratorException(
            "Docsite is not up to date. Expected: ${DocMigrator.CURRENT_VERSION}. Found: ${docMigrator.docsiteVersion}. Use DocMigrator to migrate the site."
          )
        }
        val htmlGenerator =
          HtmlGenerator(docsiteInfo, docPackages, importResolver, outputDir, isTestMode, consoleOut)
        val searchIndexGenerator = SearchIndexGenerator(outputDir, consoleOut)
        val packageDataGenerator = PackageDataGenerator(outputDir, consoleOut)
        val runtimeDataGenerator =
          RuntimeDataGenerator(descendingVersionComparator, outputDir, consoleOut)

        coroutineScope {
          for (docPackage in docPackages) {
            launch {
              docPackage.deletePackageDir()
              coroutineScope {
                launch { htmlGenerator.generate(docPackage) }
                launch { searchIndexGenerator.generate(docPackage) }
                launch { packageDataGenerator.generate(docPackage) }
              }
            }
          }
        }

        writeOutputLine("Generated HTML for packages")

        val newlyGeneratedPackages = docPackages.map(::PackageData).sortedBy { it.ref.pkg }
        val currentSearchIndex = searchIndexGenerator.getCurrentSearchIndex()

        writeOutputLine("Loaded current search index")

        val existingCurrentPackages = getCurrentPackages(currentSearchIndex)

        writeOutputLine("Fetched latest packages")

        val currentPackages =
          determineCurrentPackages(
            newlyGeneratedPackages + existingCurrentPackages,
            descendingVersionComparator,
          )

        createCurrentDirectories(currentPackages, existingCurrentPackages)
        searchIndexGenerator.generateSiteIndex(currentPackages)
        htmlGenerator.generateSite(currentPackages)
        runtimeDataGenerator.generate(newlyGeneratedPackages)

        writeOutputLine("Wrote package runtime data files")

        docMigrator.updateDocsiteVersion()
      } catch (e: IOException) {
        throw DocGeneratorBugException("I/O error generating documentation: $e", e)
      }
    }

  private fun DocPackage.deletePackageDir() {
    outputDir.resolve(IoUtils.encodePath("$name/$version")).deleteRecursively()
  }

  private fun createCurrentDirectories(
    currentPackages: List<PackageData>,
    existingCurrentPackages: List<PackageData>,
  ) {
    val packagesToCreate = currentPackages - existingCurrentPackages
    for (packageData in packagesToCreate) {
      val basePath = outputDir.resolve(packageData.ref.pkg.pathEncoded)
      val src = basePath.resolve(packageData.ref.version)
      val dst = basePath.resolve(CURRENT_DIRECTORY_NAME)

      if (noSymlinks) {
        dst.deleteRecursively()
        src.copyRecursively(dst)
      } else {
        if (!dst.exists() || !dst.isSameFileAs(src)) {
          dst.deleteRecursively()
          dst.createSymbolicLinkPointingTo(IoUtils.relativize(src, basePath))
        }
      }
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

  @Suppress("unused") val deprecation: String? = docPackageInfo.annotations.deprecation

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
        exampleModulesBySubject[mod.moduleName] ?: listOf(),
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
  val examples: List<ModuleSchema>,
) {
  val name: String
    get() = schema.moduleName

  val path: String by lazy {
    name.pathEncoded.substring(parent.docPackageInfo.moduleNamePrefix.length).replace('.', '/')
  }

  val overview: String?
    get() = schema.docComment

  val minPklVersion: Version? by lazy {
    val version =
      schema.annotations.find { it.classInfo == PClassInfo.ModuleInfo }?.get("minPklVersion")
        as String?
    version?.let { Version.parse(it) }
  }

  @Suppress("unused") val deprecation: String? = schema.annotations.deprecation

  val isUnlisted: Boolean = schema.annotations.isUnlisted
}

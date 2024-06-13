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
import java.net.URISyntaxException
import java.nio.file.Path
import kotlin.Pair
import org.pkl.commons.cli.CliBaseOptions.Companion.getProjectFile
import org.pkl.commons.cli.CliCommand
import org.pkl.commons.cli.CliException
import org.pkl.commons.toPath
import org.pkl.core.*
import org.pkl.core.module.ModuleKeyFactories
import org.pkl.core.packages.*

/**
 * Entry point for the high-level Pkldoc API.
 *
 * The high-level API offers the same configuration options as the Pkldoc CLI.
 *
 * For the low-level Pkldoc API, see [DocGenerator].
 */
class CliDocGenerator(private val options: CliDocGeneratorOptions) : CliCommand(options.base) {

  private val packageResolver =
    PackageResolver.getInstance(securityManager, httpClient, moduleCacheDir)

  private val stdlibDependency =
    DocPackageInfo.PackageDependency(
      name = "pkl",
      uri = null,
      version = if (options.isTestMode) "0.24.0" else Release.current().version().toString(),
      sourceCode =
        if (options.isTestMode) URI("https://github.com/apple/pkl/blob/dev/stdlib/")
        else URI(Release.current().sourceCode().homepage()),
      sourceCodeUrlScheme =
        if (options.isTestMode)
          "https://github.com/apple/pkl/blob/0.24.0/stdlib%{path}#L%{line}-L%{endLine}"
        else Release.current().sourceCode().sourceCodeUrlScheme,
      documentation =
        if (options.isTestMode) URI("https://pages.github.com/apple/pkl/stdlib/pkl/0.24.0/")
        else
          URI(
            PklInfo.current()
              .packageIndex
              .getPackagePage("pkl", Release.current().version().toString())
          ),
    )

  private fun DependencyMetadata.getPackageDependencies(): List<DocPackageInfo.PackageDependency> {
    return buildList {
      for ((_, dependency) in dependencies) {
        val metadata =
          try {
            packageResolver.getDependencyMetadata(dependency.packageUri, dependency.checksums)
          } catch (e: Exception) {
            throw CliException(
              "Failed to fetch dependency metadata for ${dependency.packageUri}: ${e.message}"
            )
          }
        val packageDependency =
          DocPackageInfo.PackageDependency(
            name =
              "${dependency.packageUri.uri.authority}${dependency.packageUri.uri.path.substringBeforeLast('@')}",
            uri = dependency.packageUri.uri,
            version = metadata.version.toString(),
            sourceCode = metadata.sourceCode,
            sourceCodeUrlScheme = metadata.sourceCodeUrlScheme,
            documentation = metadata.documentation
          )
        add(packageDependency)
      }
      add(stdlibDependency)
    }
  }

  private fun PackageUri.toDocPackageInfo(): DocPackageInfo {
    val metadataAndChecksum =
      try {
        packageResolver.getDependencyMetadataAndComputeChecksum(this)
      } catch (e: PackageLoadError) {
        throw CliException("Failed to package metadata for $this: ${e.message}")
      }
    val metadata = metadataAndChecksum.first
    val checksum = metadataAndChecksum.second
    return DocPackageInfo(
      name = "${uri.authority}${uri.path.substringBeforeLast('@')}",
      moduleNamePrefix = "${metadata.name}.",
      version = metadata.version.toString(),
      importUri = toPackageAssetUri("/").toString(),
      uri = uri,
      authors = metadata.authors,
      issueTracker = metadata.issueTracker,
      dependencies = metadata.getPackageDependencies(),
      overview = metadata.description,
      extraAttributes = mapOf("Checksum" to checksum.sha256),
      sourceCode = metadata.sourceCode,
      sourceCodeUrlScheme = metadata.sourceCodeUrlScheme
    )
  }

  private fun PackageUri.gatherAllModules(): List<PackageAssetUri> {
    fun PackageAssetUri.gatherModulesRecursively(): List<PackageAssetUri> {
      val self = this
      return buildList {
        for (element in packageResolver.listElements(self, null)) {
          val elementAssetUri = self.resolve(element.name)
          if (element.isDirectory) {
            addAll(elementAssetUri.gatherModulesRecursively())
          } else if (element.name.endsWith(".pkl")) {
            add(elementAssetUri)
          }
        }
      }
    }
    return toPackageAssetUri("/").gatherModulesRecursively()
  }

  override fun doRun() {
    val docsiteInfoModuleUris = mutableListOf<URI>()
    val packageInfoModuleUris = mutableListOf<URI>()
    val regularModuleUris = mutableListOf<URI>()
    val pklProjectPaths = mutableSetOf<Path>()
    val packageUris = mutableListOf<PackageUri>()
    for (moduleUri in options.base.normalizedSourceModules) {
      if (moduleUri.scheme == "file") {
        val dir = moduleUri.toPath().parent
        val projectFile = dir.getProjectFile(options.base.normalizedRootDir)
        if (projectFile != null) {
          pklProjectPaths.add(projectFile)
        }
      }
      when {
        moduleUri.path?.endsWith("/docsite-info.pkl", ignoreCase = true) ?: false ->
          docsiteInfoModuleUris.add(moduleUri)
        moduleUri.path?.endsWith("/doc-package-info.pkl", ignoreCase = true) ?: false ->
          packageInfoModuleUris.add(moduleUri)
        moduleUri.scheme == "package" -> {
          if (moduleUri.fragment != null) {
            throw CliException("Cannot generate documentation for just one module within a package")
          }
          try {
            packageUris.add(PackageUri(moduleUri))
          } catch (e: URISyntaxException) {
            throw CliException(e.message!!)
          }
        }
        else -> regularModuleUris.add(moduleUri)
      }
    }

    if (docsiteInfoModuleUris.size > 1) {
      throw CliException(
        "`sourceModules` contains multiple modules named `docsite-info.pkl`:\n" +
          docsiteInfoModuleUris.joinToString("\n")
      )
    }

    if (packageInfoModuleUris.isEmpty() && packageUris.isEmpty()) {
      throw CliException(
        "`sourceModules` must contain at least one module named `doc-package-info.pkl`, or an argument must be a package URI."
      )
    }

    if (regularModuleUris.isEmpty() && packageUris.isEmpty()) {
      throw CliException(
        "`sourceModules` must contain at least one module to generate documentation for."
      )
    }

    val builder = evaluatorBuilder()
    var docsiteInfo: DocsiteInfo
    val schemasByDocPackageInfoAndPath =
      mutableMapOf<Pair<DocPackageInfo, Path>, MutableSet<ModuleSchema>>()
    val schemasByDocPackageInfo = mutableMapOf<DocPackageInfo, Set<ModuleSchema>>()
    // Evaluate module imports eagerly, which is cheap if docs are also generated for most imported
    // modules.
    // Alternatively, imports could be evaluated lazily,
    // at the expense of interleaving schema/module evaluation and Pkldoc generation.
    val importedModules: MutableMap<URI, ModuleSchema> = mutableMapOf()

    try {
      fun Evaluator.collectImportedModules(imports: Map<String, URI>) {
        for ((_, uri) in imports) {
          importedModules.computeIfAbsent(uri) { evaluateSchema(ModuleSource.uri(uri)) }
        }
      }

      builder.build().use { evaluator ->
        for (packageUri in packageUris) {
          val docPackageInfo = packageUri.toDocPackageInfo()
          val pklModules = packageUri.gatherAllModules()
          val pklModuleSchemas =
            pklModules
              .map { evaluator.evaluateSchema(ModuleSource.uri(it.uri)) }
              .filter { !it.isAmend }
              .onEach { evaluator.collectImportedModules(it.imports) }
              .toSet()

          schemasByDocPackageInfo[docPackageInfo] = pklModuleSchemas
        }

        docsiteInfo =
          when {
            docsiteInfoModuleUris.isEmpty() -> DocsiteInfo(null, null, mapOf())
            else -> {
              val module = evaluator.evaluate(ModuleSource.uri(docsiteInfoModuleUris.single()))
              DocsiteInfo.fromPkl(module).apply {
                evaluator.collectImportedModules(overviewImports)
              }
            }
          }

        for (uri in packageInfoModuleUris) {
          val module = evaluator.evaluate(ModuleSource.uri(uri))
          val docPackageInfo =
            DocPackageInfo.fromPkl(module).apply {
              evaluator.collectImportedModules(overviewImports)
            }
          schemasByDocPackageInfoAndPath[docPackageInfo to uri.toPath().parent] = mutableSetOf()
        }

        for (uri in regularModuleUris) {
          val entry =
            schemasByDocPackageInfoAndPath.keys.find { uri.toPath().startsWith(it.second) }
              ?: throw CliException("Could not find a doc-package-info.pkl for module $uri")
          val schema =
            evaluator.evaluateSchema(ModuleSource.uri(uri)).apply {
              evaluator.collectImportedModules(imports)
            }
          schemasByDocPackageInfoAndPath[entry]!!.add(schema)
        }

        // doc generator resolves `pkl.base` even if not imported explicitly
        val pklBaseUri = URI("pkl:base")
        importedModules[pklBaseUri] = evaluator.evaluateSchema(ModuleSource.uri(pklBaseUri))
      }
    } finally {
      ModuleKeyFactories.closeQuietly(builder.moduleKeyFactories)
    }

    val versions = mutableMapOf<String, Version>()
    val versionComparator =
      Comparator<String> { v1, v2 ->
        versions
          .getOrPut(v1) { Version.parse(v1) }
          .compareTo(versions.getOrPut(v2) { Version.parse(v2) })
      }
    schemasByDocPackageInfo.putAll(schemasByDocPackageInfoAndPath.mapKeys { it.key.first })

    try {
      DocGenerator(
          docsiteInfo,
          schemasByDocPackageInfo,
          importedModules::getValue,
          versionComparator,
          options.normalizedOutputDir,
          options.isTestMode
        )
        .run()
    } catch (e: DocGeneratorException) {
      throw CliException(e.message!!)
    }
  }
}

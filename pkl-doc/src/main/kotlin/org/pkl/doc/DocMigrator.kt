/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.pkl.commons.lazyWithReceiver
import org.pkl.commons.readString
import org.pkl.commons.toUri
import org.pkl.commons.writeString
import org.pkl.core.util.IoUtils
import org.pkl.doc.RuntimeData.Companion.EMPTY

/** Migrates an existing Pkldoc site from version 1 to version 2. */
@OptIn(ExperimentalPathApi::class)
class DocMigrator(
  private val outputDir: Path,
  consoleOut: OutputStream,
  versionComparator: Comparator<String>,
) : AbstractGenerator(consoleOut) {
  companion object {
    const val CURRENT_VERSION = 2

    private val json = Json {
      ignoreUnknownKeys = true
      explicitNulls = false
    }

    private const val LEGACY_KNOWN_VERSIONS_PREFIX =
      "runtimeData.links('${HtmlConstants.KNOWN_VERSIONS}','"
    private const val LEGACY_KNOWN_USAGES_PREFIX =
      "runtimeData.links('${HtmlConstants.KNOWN_USAGES}','"
    private const val LEGACY_KNOWN_SUBTYPES_PREFIX =
      "runtimeData.links('${HtmlConstants.KNOWN_SUBTYPES}','"
    private const val LEGACY_SUFFIX = "');"

    internal fun parseLegacyRuntimeData(path: Path, myVersionHref: String): RuntimeData {
      try {
        var runtimeData = EMPTY
        for (line in Files.lines(path)) {
          runtimeData =
            when {
              line.startsWith(LEGACY_KNOWN_VERSIONS_PREFIX) -> {
                val knownVersions =
                  readLegacyLine(line, LEGACY_KNOWN_VERSIONS_PREFIX, LEGACY_SUFFIX).mapTo(
                    mutableSetOf()
                  ) { link ->
                    // fill in missing href
                    if (link.href != null) link else link.copy(href = myVersionHref)
                  }
                runtimeData.copy(knownVersions = knownVersions)
              }

              line.startsWith(LEGACY_KNOWN_USAGES_PREFIX) -> {
                val knownUsages = readLegacyLine(line, LEGACY_KNOWN_USAGES_PREFIX, LEGACY_SUFFIX)
                runtimeData.copy(knownUsages = knownUsages)
              }

              line.startsWith(LEGACY_KNOWN_SUBTYPES_PREFIX) -> {
                val knownSubtypes =
                  readLegacyLine(line, LEGACY_KNOWN_SUBTYPES_PREFIX, LEGACY_SUFFIX)
                runtimeData.copy(knownSubtypes = knownSubtypes)
              }

              else -> throw RuntimeException("Unexpected runtimeData line: $line")
            }
        }
        return runtimeData
      } catch (e: NoSuchFileException) {
        throw e
      }
    }

    private fun readLegacyLine(line: String, prefix: String, suffix: String): Set<RuntimeDataLink> {
      val jsStr = line.substring(prefix.length, line.length - suffix.length)
      return json.decodeFromString<List<RuntimeDataLink>>(jsStr).toSet()
    }
  }

  val isUpToDate by lazy {
    if (!Files.exists(outputDir.resolve("index.html"))) {
      // must be the first run
      return@lazy true
    }
    docsiteVersion == CURRENT_VERSION
  }

  val docsiteVersion by lazy {
    if (!versionPath.isRegularFile()) 1 else versionPath.readString().trim().toInt()
  }

  private val versionPath
    get() = outputDir.resolve(".pkldoc/VERSION")

  private val descendingVersionComparator = versionComparator.reversed()

  fun updateDocsiteVersion() {
    versionPath.createParentDirectories()
    versionPath.writeString(CURRENT_VERSION.toString())
  }

  private suspend fun migratePackage(pkgData: PackageData, isCurrentVersion: Boolean) {
    coroutineScope {
      if (!isCurrentVersion) {
        launch { migrateRuntimeData(pkgData) }
      }
      launch { migrateHtml(pkgData, isCurrentVersion) }
    }
  }

  fun run() =
    runBlocking(Dispatchers.IO) {
      if (isUpToDate) {
        consoleOut.writeLine(
          "Generated pkldoc is already version $CURRENT_VERSION; there's nothing to do."
        )
        return@runBlocking
      }
      val packageDatas = Files.walk(outputDir).filter { it.name == "package-data.json" }.toList()
      val count = AtomicInteger(1)
      for (path in packageDatas) {
        val pkgData = PackageData.read(path)
        val isCurrentVersion = path.parent.name == "current"
        migratePackage(pkgData, isCurrentVersion)
        if (!isCurrentVersion) {
          deleteLegacyRuntimeData(pkgData)
        }
        consoleOut.write(
          "Migrated ${count.incrementAndGet()} packages (${pkgData.ref.pkg}@${pkgData.ref.version})\r"
        )
        consoleOut.flush()
      }
      launch { copyResource("scripts/pkldoc.js", outputDir) }
      updateDocsiteVersion()
      consoleOut.writeLine("Finished migration, migrated $count packages.")
    }

  private suspend fun migrateHtml(packageData: PackageData, isCurrentVersion: Boolean) =
    coroutineScope {
      for (ref in packageData.refs) {
        launch { doMigrateHtml(ref, outputDir.resolveHtmlPath(ref, isCurrentVersion)) }
      }
    }

  private fun Path.resolveHtmlPath(ref: ElementRef<*>, isCurrentVersion: Boolean): Path {
    val effectiveRef = if (isCurrentVersion) ref.withVersion("current") else ref
    return resolve(effectiveRef.htmlPath)
  }

  private val migratedCurrentPackages = mutableSetOf<String>()

  private fun doMigrateHtml(ref: ElementRef<*>, path: Path) {
    val newHtml = buildString {
      val lines = path.readLines()
      for ((idx, line) in lines.withIndex()) {
        if (line.contains(ref.legacyVersionedRuntimeDataPath)) continue

        appendLine(line)

        if (line.contains("scripts/pkldoc.js")) {
          if (lines.getOrNull(idx + 1)?.contains("<script type=\"module\"") == false) {
            val packageVersionUrl =
              IoUtils.relativize(ref.perPackageRuntimeDataPath.toUri(), ref.htmlPath.toUri())
                .toString()
            val perPackageVersionUrl =
              IoUtils.relativize(ref.perPackageVersionRuntimeDataPath.toUri(), ref.htmlPath.toUri())
                .toString()

            appendLine(
              """    <script type="module">
      import perPackageData from "$packageVersionUrl" with { type: "json" }
      import perPackageVersionData from "$perPackageVersionUrl" with { type: "json" }

      runtimeData.knownVersions(perPackageData.knownVersions, ${json.encodeToString(ref.version)});
      runtimeData.knownUsagesOrSubtypes("known-subtypes", perPackageVersionData.knownSubtypes);
      runtimeData.knownUsagesOrSubtypes("known-usages", perPackageVersionData.knownUsages);
    </script>"""
            )
          }
        }
      }
    }
    path.writeString(newHtml)
  }

  private fun getLatestPackageData(pkg: String): PackageData {
    val currentPackage = outputDir.resolve("${pkg.pathEncoded}/current/package-data.json")
    if (currentPackage.isRegularFile()) {
      return PackageData.read(currentPackage)
    }
    // it's possible that the "current" path doesn't exist if there are only pre-releases.
    val versions = currentPackage.parent.parent.listDirectoryEntries()
    val latestVersion = versions.map { it.name }.sortedWith(descendingVersionComparator).first()
    return PackageData.read(
      outputDir.resolve("${pkg.pathEncoded}/$latestVersion/package-data.json")
    )
  }

  /** Convert legacy style data paths to new style paths */
  private suspend fun migrateRuntimeData(packageData: PackageData) = coroutineScope {
    if (!migratedCurrentPackages.contains(packageData.ref.pkg)) {
      val currentPackageData = getLatestPackageData(packageData.ref.pkg)
      for (ref in currentPackageData.refs) {
        launch { doMigratePerPackageData(ref) }
      }
      migratedCurrentPackages.add(packageData.ref.pkg)
    }
    for (ref in packageData.refs) {
      launch { doMigratePerPackageVersionData(ref) }
    }
  }

  private val PackageData.refs
    get(): List<ElementRef<*>> {
      return buildList {
        add(ref)
        for (module in modules) {
          add(module.ref)
          for (clazz in module.classes) {
            add(clazz.ref)
          }
        }
      }
    }

  private val ElementRef<*>.legacyRuntimeData: RuntimeData? by lazyWithReceiver {
    val legacyPath = outputDir.resolve(legacyVersionedRuntimeDataPath)
    when {
      legacyPath.exists() ->
        parseLegacyRuntimeData(legacyPath, myVersionHref = pageUrlForVersion(version))
      else -> null
    }
  }

  private fun doMigratePerPackageVersionData(ref: ElementRef<*>) {
    val data = ref.legacyRuntimeData ?: return
    data.perPackageVersion().writeTo(outputDir.resolve(ref.perPackageVersionRuntimeDataPath))
  }

  private fun doMigratePerPackageData(ref: ElementRef<*>) {
    val data = ref.legacyRuntimeData ?: return
    data.perPackage().writeTo(outputDir.resolve(ref.perPackageRuntimeDataPath))
  }

  private suspend fun deleteLegacyRuntimeData(packageData: PackageData) = coroutineScope {
    for (ref in packageData.refs) {
      launch { outputDir.resolve(ref.legacyVersionedRuntimeDataPath).deleteIfExists() }
    }
  }
}

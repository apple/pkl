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
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import org.pkl.commons.toUri
import org.pkl.core.util.IoUtils
import org.pkl.doc.RuntimeData.Companion.EMPTY

/** Migrates an existing Pkldoc site from version 1 to version 2. */
@OptIn(ExperimentalPathApi::class)
class DocMigrator(
  private val outputDir: Path,
  private val consoleOut: OutputStream,
  versionComparator: Comparator<String>,
) {
  companion object {
    const val CURRENT_VERSION = 2

    private val json = Json {
      ignoreUnknownKeys = true
      prettyPrint = true
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
              val knownSubtypes = readLegacyLine(line, LEGACY_KNOWN_SUBTYPES_PREFIX, LEGACY_SUFFIX)
              runtimeData.copy(knownSubtypes = knownSubtypes)
            }

            else -> throw RuntimeException("Unexpected runtimeData line: $line")
          }
      }
      return runtimeData
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
    if (!versionPath.isRegularFile()) 1 else versionPath.readText().trim().toInt()
  }

  private val versionPath
    get() = outputDir.resolve(".pkldoc/VERSION")

  private val descendingVersionComparator = versionComparator.reversed()

  fun updateDocsiteVersion() {
    versionPath.createParentDirectories()
    versionPath.writeText(CURRENT_VERSION.toString())
  }

  fun run() {
    if (isUpToDate) {
      consoleOut.writeLine(
        "Generated pkldoc is already version $CURRENT_VERSION; there's nothing to do."
      )
      return
    }
    val packageDatas = Files.walk(outputDir).filter { it.name == "package-data.json" }
    var count = 1
    for (path in packageDatas) {
      val pkgData = PackageData.read(path)
      val isCurrentVersion = path.parent.name == "current"
      if (!isCurrentVersion) {
        migrateRuntimeData(pkgData)
      }
      migrateHtml(pkgData, isCurrentVersion)
      consoleOut.write("Migrated $count packages (${pkgData.ref.pkg}@${pkgData.ref.version})\r")
      consoleOut.flush()
      count++
    }
    copyResource("scripts/pkldoc.js", outputDir)
    updateDocsiteVersion()
    consoleOut.writeLine("Finished migration, migrated $count packages.")
  }

  private fun migrateHtml(packageData: PackageData, isCurrentVersion: Boolean) {
    for (ref in packageData.refs) {
      doMigrateHtml(ref, outputDir.resolveHtmlPath(ref, isCurrentVersion))
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
    path.writeText(newHtml)
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
  private fun migrateRuntimeData(packageData: PackageData) {
    val packageRef = packageData.ref
    if (!migratedCurrentPackages.contains(packageData.ref.pkg)) {
      val currentPackageData = getLatestPackageData(packageData.ref.pkg)
      for (ref in currentPackageData.refs) {
        doMigratePerPackageData(ref)
      }
      migratedCurrentPackages.add(packageData.ref.pkg)
    }
    val legacyPath =
      outputDir.resolve(
        "data/${packageRef.pkg.pathEncoded}/${packageRef.version.pathEncoded}/index.js"
      )
    if (!legacyPath.exists()) {
      return
    }
    for (ref in packageData.refs) {
      doMigratePerPackageVersionData(ref)
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

  private fun getLegacyRuntimeData(ref: ElementRef<*>): RuntimeData? {
    val legacyPath = outputDir.resolve(ref.legacyVersionedRuntimeDataPath)
    if (!legacyPath.exists()) {
      return null
    }
    return parseLegacyRuntimeData(legacyPath, myVersionHref = ref.pageUrlForVersion(ref.version))
  }

  private fun doMigratePerPackageVersionData(ref: ElementRef<*>) {
    val data = getLegacyRuntimeData(ref) ?: return
    data.perPackageVersion().writeTo(outputDir.resolve(ref.perPackageVersionRuntimeDataPath))
    outputDir.resolve(ref.legacyVersionedRuntimeDataPath).deleteIfExists()
  }

  private fun doMigratePerPackageData(ref: ElementRef<*>) {
    val data = getLegacyRuntimeData(ref) ?: return
    data.perPackage().writeTo(outputDir.resolve(ref.perPackageRuntimeDataPath))
  }
}

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
package org.pkl.core.project;

import com.oracle.truffle.api.source.SourceSection;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.graalvm.collections.EconomicMap;
import org.pkl.core.PklBugException;
import org.pkl.core.PklException;
import org.pkl.core.SecurityManager;
import org.pkl.core.SecurityManagerException;
import org.pkl.core.StackFrameTransformer;
import org.pkl.core.ast.builder.ImportsAndReadsParser;
import org.pkl.core.http.HttpClient;
import org.pkl.core.module.ModuleKeyFactories;
import org.pkl.core.module.ModuleKeys;
import org.pkl.core.module.ProjectDependenciesManager;
import org.pkl.core.module.ResolvedModuleKeys;
import org.pkl.core.packages.Checksums;
import org.pkl.core.packages.Dependency.LocalDependency;
import org.pkl.core.packages.Dependency.RemoteDependency;
import org.pkl.core.packages.DependencyMetadata;
import org.pkl.core.packages.PackageLoadError;
import org.pkl.core.packages.PackageResolver;
import org.pkl.core.packages.PackageUri;
import org.pkl.core.runtime.ModuleResolver;
import org.pkl.core.runtime.VmExceptionBuilder;
import org.pkl.core.util.ByteArrayUtils;
import org.pkl.core.util.ErrorMessages;
import org.pkl.core.util.GlobResolver;
import org.pkl.core.util.GlobResolver.InvalidGlobPatternException;
import org.pkl.core.util.IoUtils;
import org.pkl.core.util.Nullable;
import org.pkl.core.util.Pair;

/**
 * Given a list of project directories, prepares artifacts to be published as a package.
 *
 * <p>Validates that relative imports of all included Pkl modules resolve to locations within the
 * package.
 *
 * <p>Given a package URI {@code package://example.com/thepackage@1.0.0}, the following files get
 * created:
 *
 * <ul>
 *   <li><em>thepackage@1.0.0</em> - the metadata JSON file
 *   <li><em>thepackage@1.0.0.sha256</em> - the SHA-256 checksum of the metadata file
 *   <li><em>thepackage@1.0.0.zip</em> - the zip archive containing the contents of the package
 *   <li><em>thepackage@1.0.0.zip.sha256</em> - the SHA-256 checksum of the zip archive
 * </ul>
 */
public final class ProjectPackager {
  /**
   * Modification time value for all zip entries in a package, to ensure that archives are
   * reproducible.
   *
   * <p>Date is 1980 February 1st (value taken from {@code
   * org.gradle.api.internal.file.archive.ZipCopyAction}). Note that this date does not contain time
   * zone information.
   */
  private static final LocalDateTime ZIP_ENTRY_MTIME =
      LocalDateTime.of(1980, Month.FEBRUARY, 1, 0, 0);

  private final EconomicMap<PackageUri, PackageResult> packageResults = EconomicMap.create();

  private final List<Project> projects;
  private final Path workingDir;
  private final String outputPathPattern;
  private final StackFrameTransformer stackFrameTransformer;
  private final SecurityManager securityManager;
  private final PackageResolver packageResolver;
  private final boolean skipPublishCheck;
  private final Writer outputWriter;

  public ProjectPackager(
      List<Project> projects,
      Path workingDir,
      String outputPathPattern,
      StackFrameTransformer stackFrameTransformer,
      SecurityManager securityManager,
      HttpClient httpClient,
      boolean skipPublishCheck,
      Writer outputWriter) {
    this.projects = projects;
    this.workingDir = workingDir;
    this.outputPathPattern = outputPathPattern;
    this.stackFrameTransformer = stackFrameTransformer;
    this.securityManager = securityManager;
    // intentionally use InMemoryPackageResolver
    this.packageResolver = PackageResolver.getInstance(securityManager, httpClient, null);
    this.skipPublishCheck = skipPublishCheck;
    this.outputWriter = outputWriter;
  }

  private void writeLine(String line) throws IOException {
    outputWriter.write(line);
    outputWriter.write(IoUtils.getLineSeparator());
  }

  public void createPackages() throws IOException {
    for (var project : projects) {
      var packageResult = doPackage(project);
      writeLine(IoUtils.relativize(packageResult.getMetadataFile(), workingDir).toString());
      writeLine(IoUtils.relativize(packageResult.getMetadataChecksumFile(), workingDir).toString());
      writeLine(IoUtils.relativize(packageResult.getZipFile(), workingDir).toString());
      writeLine(IoUtils.relativize(packageResult.getZipChecksumFile(), workingDir).toString());
      outputWriter.flush();
    }
  }

  private Path resolveOutputDirectory(Package pkg) {
    var substituted =
        outputPathPattern
            .replace("%{name}", pkg.getName())
            .replace("%{version}", pkg.getVersion().toString());
    return workingDir.resolve(substituted);
  }

  public PackageResult doPackage(Project project) throws IOException {
    var pkg = project.getPackage();
    if (pkg == null) {
      throw new PklException(
          ErrorMessages.create("noPackageDefinedByProject", project.getProjectFileUri()));
    }
    if (packageResults.containsKey(pkg.getUri())) {
      return packageResults.get(pkg.getUri());
    }
    var files = collectPackageElements(project, pkg);
    validatePklImportsAndReads(project, files);
    var outputDir = resolveOutputDirectory(pkg);
    var metadataFileName = IoUtils.takeLastSegment(pkg.getUri().getUri().getPath(), '/');
    var metadataFile = outputDir.resolve(metadataFileName);
    var metadataChecksumFile = outputDir.resolve(metadataFileName + ".sha256");
    var zipFile = outputDir.resolve(metadataFileName + ".zip");
    var zipChecksumFile = outputDir.resolve(metadataFileName + ".zip.sha256");
    var zipFileChecksum = createPackageZipAndComputeChecksum(project, files, zipFile);
    var metadataFileChecksum =
        createDependencyMetadataAndComputeChecksum(project, pkg, metadataFile, zipFileChecksum);
    Files.writeString(zipChecksumFile, zipFileChecksum);
    Files.writeString(metadataChecksumFile, metadataFileChecksum);
    if (!skipPublishCheck) {
      checkAlreadyPublishedPackage(pkg, metadataFileChecksum);
    }
    var result =
        new PackageResult(
            metadataFile, metadataChecksumFile, zipFile, zipChecksumFile, metadataFileChecksum);
    packageResults.put(pkg.getUri(), result);
    return result;
  }

  private void checkAlreadyPublishedPackage(Package pkg, String computedChecksum)
      throws IOException {
    try {
      var metadataAndChecksum =
          packageResolver.getDependencyMetadataAndComputeChecksum(pkg.getUri());
      var receivedChecksum = metadataAndChecksum.second.getSha256();
      if (!receivedChecksum.equals(computedChecksum)) {
        throw new PklException(
            ErrorMessages.create(
                "packageAlreadyPublishedWithDifferentContents",
                pkg.getUri(),
                computedChecksum,
                receivedChecksum));
      }
    } catch (PackageLoadError e) {
      if (e.getMessageName().equals("badHttpStatusCode")) {
        if ((int) e.getArguments()[0] == 404) {
          return;
        } else {
          throw new PklException(
              ErrorMessages.create(
                  "unableToAccessPublishedPackage",
                  pkg.getName(),
                  pkg.getPackageZipUrl(),
                  e.getArguments()[0]));
        }
      }
      throw e;
    } catch (SecurityManagerException e) {
      throw new PklException(e.getMessage());
    }
  }

  private String createDependencyMetadataAndComputeChecksum(
      Project project, Package pkg, Path metadataFile, String zipFileChecksum) throws IOException {
    var dependencyMetadata = createDependencyMetadata(project, pkg, zipFileChecksum);
    try (var fos = newDigestOutputStream(Files.newOutputStream((metadataFile)))) {
      dependencyMetadata.writeTo(fos);
      return ByteArrayUtils.toHex(fos.getMessageDigest().digest());
    }
  }

  /** If the project has a local dependency, package it as well, so we can record its checksum. */
  private Map<String, RemoteDependency> buildDependencies(Project project) throws IOException {
    try {
      var ret =
          new HashMap<String, RemoteDependency>(
              project.getDependencies().getLocalDependencies().size()
                  + project.getDependencies().getRemoteDependencies().size());
      // module resolver is only used for reading PklProject.deps.json, so provide one that reads
      // files.
      var moduleResolver = new ModuleResolver(List.of(ModuleKeyFactories.file));
      var projectDependenciesManager =
          new ProjectDependenciesManager(
              project.getDependencies(), moduleResolver, this.securityManager);
      for (var entry : project.getDependencies().getRemoteDependencies().entrySet()) {
        var resolved =
            (RemoteDependency)
                projectDependenciesManager.getResolvedDependency(entry.getValue().getPackageUri());
        ret.put(
            entry.getKey(),
            new RemoteDependency(
                resolved.getPackageUri().toExternalPackageUri(), resolved.getChecksums()));
      }
      for (var entry : project.getLocalProjectDependencies().entrySet()) {
        var localProject = entry.getValue();
        assert localProject.getPackage() != null;
        var packageUri = localProject.getPackage().getUri();
        var resolved = projectDependenciesManager.getResolvedDependency(packageUri);
        if (resolved instanceof LocalDependency) {
          var packageResult = doPackage(localProject);
          ret.put(
              entry.getKey(),
              new RemoteDependency(
                  packageUri.toExternalPackageUri(),
                  new Checksums(packageResult.getMetadataChecksum())));
        } else {
          var remoteDep = (RemoteDependency) resolved;
          ret.put(
              entry.getKey(),
              new RemoteDependency(
                  remoteDep.getPackageUri().toExternalPackageUri(), remoteDep.getChecksums()));
        }
      }
      return ret;
    } catch (PackageLoadError e) {
      throw new PklException(
          ErrorMessages.create(
              "unexpectedPackageLoadError", project.getProjectFileUri(), e.getMessage()),
          e);
    }
  }

  private DependencyMetadata createDependencyMetadata(
      Project project, Package pkg, String packageZipChecksum) throws IOException {
    return new DependencyMetadata(
        pkg.getName(),
        pkg.getUri(),
        pkg.getVersion(),
        pkg.getPackageZipUrl(),
        new Checksums(packageZipChecksum),
        buildDependencies(project),
        pkg.getSourceCodeUrlScheme(),
        pkg.getSourceCode(),
        pkg.getDocumentation(),
        pkg.getLicense(),
        pkg.getLicenseText(),
        pkg.getAuthors(),
        pkg.getIssueTracker(),
        pkg.getDescription());
  }

  private DigestOutputStream newDigestOutputStream(OutputStream outputStream) {
    try {
      var md = MessageDigest.getInstance("SHA-256");
      return new DigestOutputStream(outputStream, md);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is available in all JVM implementations
      throw PklBugException.unreachableCode();
    }
  }

  /**
   * Sets mtime to 0 so package creation is idempotent. Running the packager multiple times produces
   * the same output.
   */
  private String createPackageZipAndComputeChecksum(
      Project project, List<Path> files, Path outputZipFile) {
    DigestOutputStream digestOutputStream;
    try {
      Files.createDirectories(outputZipFile.getParent());
      digestOutputStream = newDigestOutputStream(Files.newOutputStream(outputZipFile));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    try (var zos = new ZipOutputStream(digestOutputStream)) {
      for (var file : files) {
        var relativePath = IoUtils.relativize(file, project.getProjectDir());
        var zipEntry = new ZipEntry(IoUtils.toNormalizedPathString(relativePath));
        zipEntry.setTimeLocal(ZIP_ENTRY_MTIME);
        zos.putNextEntry(zipEntry);
        Files.copy(file, zos);
        zos.closeEntry();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return ByteArrayUtils.toHex(digestOutputStream.getMessageDigest().digest());
  }

  private void validatePklImportsAndReads(Project project, List<Path> files) {
    for (var file : files) {
      if (file.toString().endsWith(".pkl")) {
        validateImportsAndReads(project, file);
      }
    }
  }

  private List<Pattern> getExcludePatterns(Package pkg) {
    var excludePatterns = new ArrayList<Pattern>();
    for (String s : pkg.getExclude()) {
      try {
        excludePatterns.add(GlobResolver.toRegexPattern(s));
      } catch (InvalidGlobPatternException e) {
        throw new PklException(e.getMessage(), e);
      }
    }
    return excludePatterns;
  }

  private List<Path> collectPackageElements(Project project, Package pkg) {
    var excludePatterns = getExcludePatterns(pkg);
    try (var stream = Files.walk(project.getProjectDir())) {
      return stream
          .filter(Files::isRegularFile)
          .filter(
              (it) -> {
                var relativePath = IoUtils.relativize(it, project.getProjectDir());
                var fileNameRelativeToProjectRoot = IoUtils.toNormalizedPathString(relativePath);
                for (var pattern : excludePatterns) {
                  if (pattern.matcher(it.getFileName().toString()).matches()) {
                    return false;
                  }
                  if (pattern.matcher(fileNameRelativeToProjectRoot).matches()) {
                    return false;
                  }
                }
                return true;
              })
          // Have consistent sort order independent of different file systems
          .sorted()
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private boolean isAbsoluteImport(String importStr) {
    return importStr.matches("\\w+:.*") || importStr.startsWith("@");
  }

  /**
   * Parse a Pkl module, and verify that its {@code import}s and {@code read}s resolve to locations
   * within the package directory.
   *
   * <p>Note that these might be glob expressions, so these paths might not actually exist. For
   * example, an import might look like {@code "foo/*.pkl"}, which would resolve as path {@code
   * `/some/dir/foo/*.pkl`}, which is not a real file. This is just a sanity check to ensure that
   * the paths can reasonably resolve to a location within the package directory.
   */
  public void validateImportsAndReads(Project project, Path pklModulePath) {
    var imports = getImportsAndReads(pklModulePath);
    if (imports == null) {
      return;
    }
    for (var importContext : imports) {
      var importStr = importContext.first;
      var sourceSection = importContext.second;
      if (isAbsoluteImport(importStr)) {
        continue;
      }
      URI importUri;
      try {
        importUri = IoUtils.toUri(importStr);
      } catch (URISyntaxException e) {
        throw new VmExceptionBuilder()
            .evalError("invalidModuleUri", importStr)
            .withSourceSection(sourceSection)
            .build()
            .toPklException(stackFrameTransformer);
      }
      if (importStr.startsWith("/") && !project.getProjectDir().toString().equals("/")) {
        throw new VmExceptionBuilder()
            .evalError("invalidRelativeProjectImport", importStr)
            .withSourceSection(sourceSection)
            .build()
            .toPklException(stackFrameTransformer);
      }
      var currentPath = pklModulePath.getParent();
      var importPath = Path.of(importUri.getPath());
      // It's not good enough to just check the normalized path to see whether it exists within the
      // root dir.
      // It's possible that the import path resolves to a path outside the project dir,
      // and then back inside the project dir.
      for (var i = 0; i < importPath.getNameCount(); i++) {
        var segment = importPath.getName(i);
        currentPath = currentPath.resolve(segment);
        var normalized = currentPath.normalize();
        if (!normalized.startsWith(project.getProjectDir())) {
          throw new VmExceptionBuilder()
              .evalError("invalidRelativeProjectImport", importStr)
              .withSourceSection(sourceSection)
              .build()
              .toPklException(stackFrameTransformer);
        }
      }
    }
  }

  private @Nullable List<Pair<String, SourceSection>> getImportsAndReads(Path pklModulePath) {
    try {
      var moduleKey = ModuleKeys.file(pklModulePath.toUri());
      var resolvedModuleKey = ResolvedModuleKeys.file(moduleKey, moduleKey.getUri(), pklModulePath);
      return ImportsAndReadsParser.parse(moduleKey, resolvedModuleKey);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static class PackageResult {
    private final Path zipFile;
    private final Path zipChecksumFile;
    private final Path metadataFile;
    private final Path metadataChecksumFile;
    private final String metadataChecksum;

    public PackageResult(
        Path zipFile,
        Path zipChecksumFile,
        Path metadataFile,
        Path metadataChecksumFile,
        String metadataChecksum) {
      this.zipFile = zipFile;
      this.zipChecksumFile = zipChecksumFile;
      this.metadataFile = metadataFile;
      this.metadataChecksumFile = metadataChecksumFile;
      this.metadataChecksum = metadataChecksum;
    }

    public Path getZipFile() {
      return zipFile;
    }

    public Path getZipChecksumFile() {
      return zipChecksumFile;
    }

    public Path getMetadataFile() {
      return metadataFile;
    }

    public Path getMetadataChecksumFile() {
      return metadataChecksumFile;
    }

    public String getMetadataChecksum() {
      return metadataChecksum;
    }
  }
}

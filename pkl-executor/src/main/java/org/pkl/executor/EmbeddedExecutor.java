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
package org.pkl.executor;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.pkl.executor.spi.v1.ExecutorSpi;
import org.pkl.executor.spi.v1.ExecutorSpiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class EmbeddedExecutor implements Executor {
  private static final Logger logger = LoggerFactory.getLogger(EmbeddedExecutor.class);

  private static final Pattern MODULE_INFO_PATTERN =
      Pattern.compile("@ModuleInfo\\s*\\{.*minPklVersion\\s*=\\s*\"([0-9.]*)\".*}", Pattern.DOTALL);

  private final List<PklDistribution> pklDistributions = new ArrayList<>();

  /**
   * @throws IllegalArgumentException if a Jar file cannot be found or is not a valid Pkl
   *     distribution
   */
  public EmbeddedExecutor(List<Path> pklFatJars) {
    this(pklFatJars, Executor.class.getClassLoader());
  }

  // for testing only
  EmbeddedExecutor(List<Path> pklFatJars, ClassLoader pklExecutorClassLoader) {
    for (var jarFile : pklFatJars) {
      pklDistributions.add(new PklDistribution(jarFile, pklExecutorClassLoader));
    }
  }

  public String evaluatePath(Path modulePath, ExecutorOptions options) {
    logger.info("Started evaluating Pkl module. modulePath={} options={}", modulePath, options);

    long startTime = System.nanoTime();

    Version requestedVersion = null;
    PklDistribution distribution = null;
    String output = null;
    RuntimeException exception = null;

    try {
      if (!Files.isRegularFile(modulePath)) {
        throw new ExecutorException(
            String.format("Cannot find Pkl module `%s`.", toDisplayPath(modulePath, options)));
      }

      // Note that version detection for the given module happens before security checks for its
      // evaluation.
      // This should be acceptable because version detection only involves the module passed
      // directly to the executor
      // (but not any modules imported by it) and only requires parsing (but not evaluating) the
      // module.
      requestedVersion = detectRequestedPklVersion(modulePath, options);
      //noinspection resource
      distribution = findCompatibleDistribution(modulePath, requestedVersion, options);
      output = distribution.evaluatePath(modulePath, options);
    } catch (RuntimeException e) {
      exception = e;
    }

    var endTime = System.nanoTime();

    // Could log exception, but this would violate "don't log and throw",
    // and Pkl stack trace might contain semi-sensitive information.
    logger.info(
        "Finished evaluating Pkl module. modulePath={} outcome={} requestedVersion={} selectedVersion={} elapsedMillis={}",
        modulePath,
        exception == null ? "success" : "failure",
        requestedVersion == null ? "n/a" : requestedVersion.toString(),
        distribution == null ? "n/a" : distribution.getVersion().toString(),
        (endTime - startTime) / 1_000_000);

    if (exception != null) throw exception;

    assert output != null;
    return output;
  }

  private Version detectRequestedPklVersion(Path modulePath, ExecutorOptions options) {
    String sourceText;
    try {
      sourceText = Files.readString(modulePath, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ExecutorException(
          String.format("I/O error loading Pkl module `%s`.", toDisplayPath(modulePath, options)),
          e);
    }

    var version = extractMinPklVersion(sourceText);
    if (version != null) return version;

    var availableVersions =
        pklDistributions.stream()
            .map(it -> it.getVersion().toString())
            .collect(Collectors.joining(", "));

    throw new ExecutorException(
        String.format(
            "Pkl module `%s` does not state which Pkl version it requires. (Available versions: %s)%n"
                + "To fix this problem, annotate the module's `amends`, `extends`, or `module` clause with `@ModuleInfo { minPklVersion = \"x.y.z\" }`.",
            toDisplayPath(modulePath, options), availableVersions));
  }

  /* @Nullable */ static Version extractMinPklVersion(String sourceText) {
    var matcher = MODULE_INFO_PATTERN.matcher(sourceText);
    return matcher.find() ? Version.parse(matcher.group(1)) : null;
  }

  private PklDistribution findCompatibleDistribution(
      Path modulePath, Version requestedVersion, ExecutorOptions options) {
    var result =
        pklDistributions.stream()
            .filter(it -> it.getVersion().compareTo(requestedVersion) >= 0)
            .min(Comparator.comparing(PklDistribution::getVersion));

    if (result.isPresent()) return result.get();

    var availableVersions =
        pklDistributions.stream()
            .map(it -> it.getVersion().toString())
            .collect(Collectors.joining(", "));

    throw new ExecutorException(
        String.format(
            "Pkl version `%s` requested by module `%s` is not supported. Available versions: %s%n"
                + "To fix this problem, edit the module's `@ModuleInfo { minPklVersion = \"%s\" }` annotation.",
            requestedVersion,
            toDisplayPath(modulePath, options),
            availableVersions,
            requestedVersion));
  }

  private static Path toDisplayPath(Path modulePath, ExecutorOptions options) {
    var rootDir = options.getRootDir();
    return rootDir == null ? modulePath : relativize(modulePath, rootDir);
  }

  // On Windows, `Path.relativize` will fail if the two paths have different roots.
  private static Path relativize(Path path, Path base) {
    if (path.isAbsolute() && base.isAbsolute() && !path.getRoot().equals(base.getRoot())) {
      return path;
    }
    return base.relativize(path);
  }

  @Override
  public void close() throws Exception {
    for (var pklDistribution : pklDistributions) {
      pklDistribution.close();
    }
  }

  private static final class PklDistribution implements AutoCloseable {
    final URLClassLoader pklDistributionClassLoader;
    final /* @Nullable */ ExecutorSpi executorSpi;
    final Version version;

    /**
     * @throws IllegalArgumentException if the Jar file does not exist or is not a valid Pkl
     *     distribution
     */
    PklDistribution(Path pklFatJar, ClassLoader pklExecutorClassLoader) {
      if (!Files.isRegularFile(pklFatJar)) {
        throw new IllegalArgumentException(
            String.format("Invalid Pkl distribution: Cannot find Jar file `%s`.", pklFatJar));
      }

      pklDistributionClassLoader =
          new PklDistributionClassLoader(pklFatJar, pklExecutorClassLoader);
      var serviceLoader = ServiceLoader.load(ExecutorSpi.class, pklDistributionClassLoader);

      try {
        executorSpi = serviceLoader.iterator().next();
      } catch (NoSuchElementException e) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid Pkl distribution: Cannot find service of type `%s` in Jar file `%s`.",
                ExecutorSpi.class.getTypeName(), pklFatJar));

      } catch (ServiceConfigurationError e) {
        throw new IllegalArgumentException(
            String.format(
                "Invalid Pkl distribution: Unexpected error loading service of type `%s` from Jar file `%s`.",
                ExecutorSpi.class.getTypeName(), pklFatJar),
            e);
      }

      // convert to normal to allow running with a dev version
      version = Version.parse(executorSpi.getPklVersion()).toNormal();
    }

    Version getVersion() {
      return version;
    }

    String evaluatePath(Path modulePath, ExecutorOptions options) {
      var currentThread = Thread.currentThread();
      var prevContextClassLoader = currentThread.getContextClassLoader();
      // Truffle loads stuff from context class loader, so set it to our class loader
      currentThread.setContextClassLoader(pklDistributionClassLoader);
      try {
        return executorSpi.evaluatePath(modulePath, options.toSpiOptions());
      } catch (ExecutorSpiException e) {
        throw new ExecutorException(e.getMessage(), e.getCause(), executorSpi.getPklVersion());
      } catch (RuntimeException e) {
        // This branch would ideally never be hit, but older Pkl releases (<0.27) erroneously throw
        // PklException in some cases.
        // Can't catch PklException directly because pkl-executor cannot depend on pkl-core.
        if (e.getClass().getName().equals("org.pkl.core.PklException")) {
          throw new ExecutorException(e.getMessage(), e.getCause());
        }
        throw e;
      } finally {
        currentThread.setContextClassLoader(prevContextClassLoader);
      }
    }

    @Override
    public void close() throws IOException {
      pklDistributionClassLoader.close();
    }
  }

  private static final class PklDistributionClassLoader extends URLClassLoader {
    final ClassLoader pklExecutorClassLoader;

    static {
      registerAsParallelCapable();
    }

    PklDistributionClassLoader(Path pklFatJar, ClassLoader pklExecutorClassLoader) {
      // pass `null` to make bootstrap class loader the effective parent
      super(toUrls(pklFatJar), null);
      this.pklExecutorClassLoader = pklExecutorClassLoader;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized (getClassLoadingLock(name)) {
        var clazz = findLoadedClass(name);

        if (clazz == null) {
          if (name.startsWith("org.pkl.executor.spi.")) {
            try {
              // give pkl-executor a chance to load the SPI clasa
              clazz = pklExecutorClassLoader.loadClass(name);
            } catch (ClassNotFoundException ignored) {
              // The SPI class exists in this distribution but not in pkl-executor,
              // so load it from the distribution.
              // This can happen if the pkl-executor version is lower than the distribution version.
              clazz = findClass(name);
            }
          } else if (name.startsWith("java.")
              || name.startsWith("jdk.")
              || name.startsWith("sun.")
              // Don't add all of `javax` because some packages might come from a dependency
              // (e.g. jsr305)
              || name.startsWith("javax.annotation.processing")
              || name.startsWith("javax.lang.")
              || name.startsWith("javax.naming.")
              || name.startsWith("javax.net.")
              || name.startsWith("javax.crypto.")
              || name.startsWith("javax.security.")
              || name.startsWith("com.sun.")) {
            clazz = getPlatformClassLoader().loadClass(name);
          } else {
            clazz = findClass(name);
          }
        }

        if (resolve) {
          resolveClass(clazz);
        }

        return clazz;
      }
    }

    @Override
    public URL getResource(String name) {
      var resource = getPlatformClassLoader().getResource(name);
      return resource != null ? resource : findResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
      return ConcatenatedEnumeration.create(
          getPlatformClassLoader().getResources(name), findResources(name));
    }

    static URL[] toUrls(Path pklFatJar) {
      try {
        return new URL[] {pklFatJar.toUri().toURL()};
      } catch (MalformedURLException e) {
        throw new AssertionError(e);
      }
    }
  }

  private static final class ConcatenatedEnumeration<E> implements Enumeration<E> {
    final Enumeration<E> e1;
    final Enumeration<E> e2;

    static <E> Enumeration<E> create(Enumeration<E> e1, Enumeration<E> e2) {
      return !e1.hasMoreElements()
          ? e2
          : !e2.hasMoreElements() ? e1 : new ConcatenatedEnumeration<>(e1, e2);
    }

    ConcatenatedEnumeration(Enumeration<E> e1, Enumeration<E> e2) {
      this.e1 = e1;
      this.e2 = e2;
    }

    public boolean hasMoreElements() {
      return e1.hasMoreElements() || e2.hasMoreElements();
    }

    public E nextElement() throws NoSuchElementException {
      return e1.hasMoreElements() ? e1.nextElement() : e2.nextElement();
    }
  }
}

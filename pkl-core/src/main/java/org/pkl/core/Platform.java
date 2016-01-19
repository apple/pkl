/**
 * Copyright © 2023 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import java.util.Objects;
import org.graalvm.home.Version;

/**
 * Information about the Pkl release that the current program runs on. This class is the Java
 * equivalent of standard library module {@code pkl.platform}.
 */
public final class Platform {
  private static final Platform CURRENT;

  static {
    var pklVersion = Release.current().version().toString();
    var osName = System.getProperty("os.name");
    if (osName.equals("Mac OS X")) osName = "macOS";
    var osVersion = System.getProperty("os.version");
    var architecture = System.getProperty("os.arch");

    var runtimeName = Truffle.getRuntime().getName();
    var runtimeVersion = Version.getCurrent().toString();
    var vmName = TruffleOptions.AOT ? runtimeName : System.getProperty("java.vm.name");
    var vmVersion = TruffleOptions.AOT ? runtimeVersion : System.getProperty("java.vm.version");

    CURRENT =
        new Platform(
            new Language(pklVersion),
            new Runtime(runtimeName, runtimeVersion),
            new VirtualMachine(vmName, vmVersion),
            new OperatingSystem(osName, osVersion),
            new Processor(architecture));
  }

  private final Language language;
  private final Runtime runtime;
  private final VirtualMachine virtualMachine;
  private final OperatingSystem operatingSystem;
  private final Processor processor;

  /** Constructs a platform. */
  public Platform(
      Language language,
      Runtime runtime,
      VirtualMachine virtualMachine,
      OperatingSystem operatingSystem,
      Processor processor) {
    this.language = language;
    this.runtime = runtime;
    this.virtualMachine = virtualMachine;
    this.operatingSystem = operatingSystem;
    this.processor = processor;
  }

  /** The Pkl release that the current program runs on. */
  public static Platform current() {
    return CURRENT;
  }

  /** The language implementation of this platform. */
  public Language language() {
    return language;
  }

  /** The language runtime of this platform. */
  public Runtime runtime() {
    return runtime;
  }

  /** The virtual machine of this platform. */
  public VirtualMachine virtualMachine() {
    return virtualMachine;
  }

  /** The operating system of this platform. */
  public OperatingSystem operatingSystem() {
    return operatingSystem;
  }

  /** The processor of this platform. */
  public Processor processor() {
    return processor;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Platform)) return false;

    var other = (Platform) obj;
    return language.equals(other.language)
        && runtime.equals(other.runtime)
        && virtualMachine.equals(other.virtualMachine)
        && operatingSystem.equals(other.operatingSystem)
        && processor.equals(other.processor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(language, runtime, virtualMachine, operatingSystem, processor);
  }

  /** The language implementation of a platform. */
  public static final class Language {
    private final String version;

    /** Constructs a {@link Language}. */
    public Language(String version) {
      this.version = version;
    }

    /** The version of this language implementation. */
    public String version() {
      return version;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Language)) return false;

      var other = (Language) obj;
      return version.equals(other.version);
    }

    @Override
    public int hashCode() {
      return version.hashCode();
    }
  }

  /** The language runtime of a platform. */
  public static final class Runtime {
    private final String name;
    private final String version;

    /** Constructs a {@link Runtime}. */
    public Runtime(String name, String version) {
      this.name = name;
      this.version = version;
    }

    /** The name of this language runtime. */
    public String name() {
      return name;
    }

    /** The version of this language runtime. */
    public String version() {
      return version;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Runtime)) return false;

      var other = (Runtime) obj;
      return name.equals(other.name) && version.equals(other.version);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, version);
    }
  }

  /** The virtual machine of a platform. */
  public static final class VirtualMachine {
    private final String name;
    private final String version;

    /** Constructs a {@link VirtualMachine}. */
    public VirtualMachine(String name, String version) {
      this.name = name;
      this.version = version;
    }

    /** The name of this virtual machine. */
    public String name() {
      return name;
    }

    /** The version of this virtual machine. */
    public String version() {
      return version;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof VirtualMachine)) return false;

      var other = (VirtualMachine) obj;
      return name.equals(other.name) && version.equals(other.version);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, version);
    }
  }

  /** The operating system of a platform. */
  public static final class OperatingSystem {
    private final String name;
    private final String version;

    /** Constructs an {@link OperatingSystem}. */
    public OperatingSystem(String name, String version) {
      this.name = name;
      this.version = version;
    }

    /** The name of this operating system. */
    public String name() {
      return name;
    }

    /** The version of this operating system. */
    public String version() {
      return version;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof OperatingSystem)) return false;

      var other = (OperatingSystem) obj;
      return name.equals(other.name) && version.equals(other.version);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, version);
    }
  }

  /** The processor of a platform. */
  public static final class Processor {
    private final String architecture;

    /** Constructs a {@link Processor}. */
    public Processor(String architecture) {
      this.architecture = architecture;
    }

    /** The instruction set architecture of this processor. */
    public String architecture() {
      return architecture;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof Processor)) return false;

      var other = (Processor) obj;
      return architecture.equals(other.architecture);
    }

    @Override
    public int hashCode() {
      return architecture.hashCode();
    }
  }
}

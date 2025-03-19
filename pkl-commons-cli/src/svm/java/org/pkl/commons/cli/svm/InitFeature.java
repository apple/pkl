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
package org.pkl.commons.cli.svm;

import org.graalvm.nativeimage.hosted.Feature;
import org.pkl.core.runtime.BaseModule;

/**
 * This class is registered with native-image via a CLI option (see Gradle task `nativeExecutable`).
 */
@SuppressWarnings({"unused", "ResultOfMethodCallIgnored"})
public final class InitFeature implements Feature {
  /**
   * Enforce that {@link BaseModule#getModule()}'s static initializer completes before depending
   * static initializers are invoked. This is necessary to avoid deadlocks in native-image's
   * multi-threaded execution of static initializers. It's not clear at this point if multi-threaded
   * initialization on the JVM could also deadlock, i.e., if this is a Pkl bug.
   */
  public void duringSetup(DuringSetupAccess access) {
    BaseModule.getModule();
  }
}

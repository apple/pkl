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
package org.pkl.gradle;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.tasks.Nested;
import org.pkl.gradle.spec.EvalSpec;
import org.pkl.gradle.spec.JavaCodeGenSpec;
import org.pkl.gradle.spec.KotlinCodeGenSpec;
import org.pkl.gradle.spec.PkldocSpec;
import org.pkl.gradle.spec.TestSpec;

@SuppressWarnings("unused")
public interface PklExtension {
  NamedDomainObjectContainer<EvalSpec> getEvaluators();

  NamedDomainObjectContainer<JavaCodeGenSpec> getJavaCodeGenerators();

  NamedDomainObjectContainer<KotlinCodeGenSpec> getKotlinCodeGenerators();

  NamedDomainObjectContainer<PkldocSpec> getPkldocGenerators();

  NamedDomainObjectContainer<TestSpec> getTests();

  @Nested
  PklProjectCommands getProject();

  default void evaluators(Action<? super NamedDomainObjectContainer<EvalSpec>> action) {
    action.execute(getEvaluators());
  }

  default void javaCodeGenerators(
      Action<? super NamedDomainObjectContainer<JavaCodeGenSpec>> action) {
    action.execute(getJavaCodeGenerators());
  }

  default void kotlinCodeGenerators(
      Action<? super NamedDomainObjectContainer<KotlinCodeGenSpec>> action) {
    action.execute(getKotlinCodeGenerators());
  }

  default void pkldocGenerators(Action<? super NamedDomainObjectContainer<PkldocSpec>> action) {
    action.execute(getPkldocGenerators());
  }

  default void tests(Action<? super NamedDomainObjectContainer<TestSpec>> action) {
    action.execute(getTests());
  }

  default void project(Action<? super PklProjectCommands> action) {
    action.execute(getProject());
  }
}

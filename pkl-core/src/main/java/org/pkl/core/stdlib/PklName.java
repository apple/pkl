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
package org.pkl.core.stdlib;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Associates a stdlib node class with an Pkl member name or a stdlib package with a Pkl module
 * name. Only required if the Java class/package can't have the same name as its corresponding Pkl
 * member/module.
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
// From
// https://docs.gradle.org/current/userguide/java_plugin.html#aggregating_annotation_processors:
// "Aggregating" processors [...] can only read CLASS or RUNTIME retention annotations.
@Retention(RetentionPolicy.CLASS)
public @interface PklName {
  /** The simple Pkl name of the annotated element. */
  String value();
}

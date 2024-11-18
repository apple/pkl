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
package org.pkl.core;

import java.io.Serializable;

/**
 * Java representation of a Pkl value.
 *
 * <p>The following Pkl values aren't represented as {@code Value} but as instances of Java standard
 * library classes:
 *
 * <ul>
 *   <li>{@code pkl.base#String}: {@link java.lang.String}
 *   <li>{@code pkl.base#Boolean}: {@link java.lang.Boolean}
 *   <li>{@code pkl.base#Int}: {@link java.lang.Long}
 *   <li>{@code pkl.base#Float}: {@link java.lang.Double}
 *   <li>{@code pkl.base#List}: {@link java.util.List}
 *   <li>{@code pkl.base#Set}: {@link java.util.Set}
 *   <li>{@code pkl.base#Map}: {@link java.util.Map}
 *   <li>{@code pkl.base#Listing}: {@link java.util.List}
 *   <li>{@code pkl.base#Mapping}: {@link java.util.Map}
 *   <li>{@code pkl.base#Regex}: {@link java.util.regex.Pattern}
 * </ul>
 */
public interface Value extends Serializable {
  /** Invokes the given visitor's visit method for this {@code Value}. */
  void accept(ValueVisitor visitor);

  /** Invokes the given converters's convert method for this {@code Value}. */
  <T> T accept(ValueConverter<T> converter);

  /** Returns information about the Pkl class associated with this {@code Value}. */
  PClassInfo<?> getClassInfo();
}

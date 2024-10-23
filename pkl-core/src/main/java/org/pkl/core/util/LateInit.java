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
package org.pkl.core.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.Nonnull;
import javax.annotation.meta.TypeQualifierNickname;
import javax.annotation.meta.When;

/**
 * Indicates that the annotated field is initially {@code null} and initialized to a non-null value
 * before or upon first use. Corresponds to {@code lateinit} in Kotlin (but does not result in
 * automatic runtime non-null checks).
 *
 * <p>Note: Fields that are initialized late to a nullable value should only be annotated with
 * {@link Nullable} (same as in Kotlin).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Nonnull(when = When.UNKNOWN)
@TypeQualifierNickname
public @interface LateInit {}

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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.meta.TypeQualifierDefault;

/**
 * Indicates that method return types and method parameters in the annotated package are {@link
 * Nonnull} unless explicitly annotated with {@link Nullable}.
 *
 * <p>This annotation is a generalization of {@link javax.annotation.ParametersAreNonnullByDefault}.
 * All Pkl packages containing Java code should carry this annotation.
 *
 * <p>Ideally, this default would apply to every {@link ElementType#TYPE_USE}, but I haven't been
 * able to make this work reasonably in <a
 * href="https://youtrack.jetbrains.com/issue/IDEA-278618">IntelliJ</a>.
 */
@Documented
@TypeQualifierDefault({ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_PARAMETER})
@javax.annotation.Nonnull
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface NonnullByDefault {}

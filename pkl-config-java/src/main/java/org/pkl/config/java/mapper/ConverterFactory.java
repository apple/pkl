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
package org.pkl.config.java.mapper;

import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Predicate;
import org.pkl.core.PClassInfo;

/**
 * A factory for {@link Converter}s. Used to implement conversions to generic Java classes. In such
 * a case a single {@link Converter} does not suffice. Instead the factory creates a new converter
 * for every parameterization of the target type. Once created, the converter is cached for later
 * use, and the factory is never again invoked for the same parameterized target type.
 *
 * <p>For best performace, all introspection of target types (for example using {@link Reflection})
 * should happen in the factory rather then the returned converters.
 */
@FunctionalInterface
public interface ConverterFactory {
  /**
   * Returns a converter for the given source and target types, or {@code Optional.empty()} if the
   * factory cannot handle the requested types.
   */
  // idea: return Success/Failure providing an explanation of why this factory wasn't applicable
  Optional<Converter<?, ?>> create(PClassInfo<?> sourceType, Type targetType);

  /**
   * Returns a new factory that restricts use of this factory to target types for which the given
   * predicate holds.
   */
  default ConverterFactory when(Predicate<Type> predicate) {
    return (sourceType, targetType) -> {
      if (!predicate.test(targetType)) return Optional.empty();
      return create(sourceType, targetType);
    };
  }
}

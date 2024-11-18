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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.pkl.core.ValueFormatter;
import org.pkl.core.util.Nullable;

@ValueType
public final class VmRegex extends VmValue {
  private final Pattern pattern;

  public VmRegex(Pattern pattern) {
    this.pattern = pattern;
  }

  public Pattern getPattern() {
    return pattern;
  }

  @TruffleBoundary
  public Matcher matcher(String input) {
    return pattern.matcher(input);
  }

  @Override
  public VmClass getVmClass() {
    return BaseModule.getRegexClass();
  }

  @Override
  public void force(boolean allowUndefinedValues) {
    // do nothing
  }

  @Override
  public Pattern export() {
    return pattern;
  }

  @Override
  public void accept(VmValueVisitor visitor) {
    visitor.visitRegex(this);
  }

  @Override
  public <T> T accept(VmValueConverter<T> converter, Iterable<Object> path) {
    return converter.convertRegex(this, path);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof VmRegex other)) return false;
    return pattern.pattern().equals(other.pattern.pattern());
  }

  @Override
  public int hashCode() {
    return pattern.pattern().hashCode();
  }

  @Override
  public String toString() {
    var builder = new StringBuilder();
    builder.append("Regex(");
    ValueFormatter.withCustomStringDelimiters().formatStringValue(pattern.pattern(), "", builder);
    builder.append(')');
    return builder.toString();
  }
}

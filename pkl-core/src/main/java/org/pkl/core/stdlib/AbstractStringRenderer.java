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
package org.pkl.core.stdlib;

import org.pkl.core.runtime.VmClass;
import org.pkl.core.runtime.VmFunction;
import org.pkl.core.runtime.VmTypeAlias;

/** Base class for renderers that are part of the standard library. */
public abstract class AbstractStringRenderer extends AbstractRenderer {
  protected static final char LINE_BREAK = '\n';

  protected final StringBuilder builder;

  /** The indent to be used. */
  protected final String indent;

  /** The current indent. Modified by {@link #increaseIndent()} and {@link #decreaseIndent()}. */
  protected final StringBuilder currIndent = new StringBuilder();

  public AbstractStringRenderer(
      String name,
      StringBuilder builder,
      String indent,
      PklConverter converter,
      boolean skipNullProperties,
      boolean skipNullEntries) {
    super(name, converter, skipNullProperties, skipNullEntries);
    this.builder = builder;
    this.indent = indent;
  }

  protected void increaseIndent() {
    currIndent.append(indent);
  }

  protected void decreaseIndent() {
    currIndent.setLength(currIndent.length() - indent.length());
  }

  // override these to mark them final

  @Override
  public final void visitTypeAlias(VmTypeAlias value) {
    super.visitTypeAlias(value);
  }

  @Override
  public final void visitClass(VmClass value) {
    super.visitClass(value);
  }

  @Override
  public final void visitFunction(VmFunction value) {
    super.visitFunction(value);
  }
}

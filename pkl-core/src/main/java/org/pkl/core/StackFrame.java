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
package org.pkl.core;

import java.util.List;
import java.util.Objects;
import org.pkl.core.util.Nullable;

/** An element of a Pkl stack trace. */
// better name would be `StackTraceElement`
public final class StackFrame {
  private final String moduleUri;

  // TODO: can we make this non-null?
  private final @Nullable String memberName;

  private final List<String> sourceLines;
  private final int startLine;
  private final int startColumn;
  private final int endLine;
  private final int endColumn;

  public StackFrame(
      String moduleUri,
      @Nullable String memberName,
      List<String> sourceLines,
      int startLine,
      int startColumn,
      int endLine,
      int endColumn) {

    assert startLine >= 1;
    assert startColumn >= 1;
    assert endLine >= 1;
    assert endColumn >= 1;
    assert startLine <= endLine;
    assert startLine < endLine || startColumn <= endColumn;

    this.moduleUri = moduleUri;
    this.memberName = memberName;
    this.sourceLines = sourceLines;
    this.startLine = startLine;
    this.startColumn = startColumn;
    this.endLine = endLine;
    this.endColumn = endColumn;
  }

  /** Returns the module URI to display for this frame. May not be a syntactically valid URI. */
  public String getModuleUri() {
    return moduleUri;
  }

  /** Returns a copy of this frame with the given module URI. */
  public StackFrame withModuleUri(String moduleUri) {
    return new StackFrame(
        moduleUri, memberName, sourceLines, startLine, startColumn, endLine, endColumn);
  }

  /** Returns the qualified name of the property or function corresponding to this frame, if any. */
  public @Nullable String getMemberName() {
    return memberName;
  }

  /**
   * Returns the lines of source code corresponding to this frame. The first line has line number
   * {@link #getStartLine}. The last line has line number {@link #getEndLine()}.
   */
  public List<String> getSourceLines() {
    return sourceLines;
  }

  /** Returns the start line number (1-based) corresponding to this frame. */
  public int getStartLine() {
    return startLine;
  }

  /** Returns the start column number (1-based) corresponding to this frame. */
  public int getStartColumn() {
    return startColumn;
  }

  /** Returns the end line number (1-based) corresponding to this frame. */
  public int getEndLine() {
    return endLine;
  }

  /** Returns the end column number (1-based) corresponding to this frame. */
  public int getEndColumn() {
    return endColumn;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof StackFrame other)) return false;
    if (startLine != other.startLine) return false;
    if (startColumn != other.startColumn) return false;
    if (endLine != other.endLine) return false;
    if (endColumn != other.endColumn) return false;
    if (!moduleUri.equals(other.moduleUri)) return false;
    if (!Objects.equals(memberName, other.memberName)) return false;
    return sourceLines.equals(other.sourceLines);
  }

  @Override
  public int hashCode() {
    var result = moduleUri.hashCode();
    result = 31 * result + Objects.hashCode(memberName);
    result = 31 * result + sourceLines.hashCode();
    result = 31 * result + startLine;
    result = 31 * result + startColumn;
    result = 31 * result + endLine;
    result = 31 * result + endColumn;
    return result;
  }
}

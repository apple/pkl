/*
 * Copyright © 2025-2026 Apple Inc. and the Pkl project authors. All rights reserved.
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

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.jspecify.annotations.Nullable;
import org.pkl.core.PklBugException;

/** A per-context thread-local value that can be used to influence execution. */
public class VmLocalContext {
  private boolean shouldEagerTypecheck = false;

  /** Whether we are currently inside a type test ({@code is} check). */
  private boolean inTypeTest = false;

  /**
   * Number of active {@link VmValueTracker} instances. Used to determine if instrumentation is
   * already active.
   */
  private int activeTrackerDepth = 0;

  private boolean instrumentationEverUsed = false;

  private @Nullable VirtualFrame realTypeAliasFrame = null;

  public VmLocalContext() {}

  public void shouldEagerTypecheck(boolean shouldEagerTypecheck) {
    this.shouldEagerTypecheck = shouldEagerTypecheck;
  }

  public boolean shouldEagerTypecheck() {
    return this.shouldEagerTypecheck;
  }

  public void setInTypeTest(boolean inTypeTest) {
    this.inTypeTest = inTypeTest;
  }

  public boolean isInTypeTest() {
    return inTypeTest;
  }

  public void enterTracker() {
    activeTrackerDepth++;
    instrumentationEverUsed = true;
  }

  public void exitTracker() {
    activeTrackerDepth--;
  }

  public boolean hasActiveTracker() {
    return activeTrackerDepth > 0;
  }

  public boolean isInstrumentationEverUsed() {
    return instrumentationEverUsed;
  }

  public boolean setRealTypeAliasFrame(Object receiver, Object owner) {
    if (realTypeAliasFrame != null) return false;

    realTypeAliasFrame = new FakeFrame(receiver, owner);
    return true;
  }

  public void clearRealTypeAliasFrame() {
    realTypeAliasFrame = null;
  }

  public @Nullable VirtualFrame getRealTypeAliasFrame() {
    return realTypeAliasFrame;
  }

  private static final class FakeFrame implements VirtualFrame, MaterializedFrame {
    private final Object[] args;

    public FakeFrame(Object receiver, Object owner) {
      this.args = new Object[] {receiver, owner};
    }

    @Override
    public FrameDescriptor getFrameDescriptor() {
      throw PklBugException.unreachableCode();
    }

    @Override
    public Object[] getArguments() {
      return args;
    }

    @Override
    public MaterializedFrame materialize() {
      return this;
    }
  }
}

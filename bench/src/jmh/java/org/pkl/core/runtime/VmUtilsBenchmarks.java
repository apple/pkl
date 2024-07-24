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
package org.pkl.core.runtime;

import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@SuppressWarnings("unused")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 10, time = 2)
public class VmUtilsBenchmarks {
  private VmObjectLike receiver;
  private Object memberKey;
  private IndirectCallNode callNode;

  @Setup
  public void setup() {
    receiver = VmDynamic.empty();
    memberKey = "testMember";
    callNode = IndirectCallNode.create();
  }

  @Benchmark
  public void readMemberOrNullCacheHit(Blackhole blackhole) {
    Object result = VmUtils.readMemberOrNull(receiver, memberKey, true, callNode);
    blackhole.consume(result);
  }

  @Benchmark
  public void readMemberOrNullCacheMiss(Blackhole blackhole) {
    Object result = VmUtils.readMemberOrNull(receiver, "nonExistentMember", true, callNode);
    blackhole.consume(result);
  }
}

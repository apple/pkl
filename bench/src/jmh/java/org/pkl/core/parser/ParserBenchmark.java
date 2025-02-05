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
package org.pkl.core.parser;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@SuppressWarnings("unused")
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
public class ParserBenchmark {
  // One-time execution of this code took ~10s until moving rule alternative
  // for parenthesized expression after alternative for anonymous function.
  @Benchmark
  public void run() {
    new Parser()
        .parseModule(
            """
            a1 {
              a2 {
                a3 {
                  a4 {
                    a5 {
                      a6 {
                        a7 {
                          a8 {
                            a9 {
                              a10 {
                                a11 {
                                  a12 {
                                    a13 = map(map(map((x) -> 1)))
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }""");
  }
}

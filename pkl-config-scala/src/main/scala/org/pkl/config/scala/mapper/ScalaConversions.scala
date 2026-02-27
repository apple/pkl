/*
 * Copyright © 2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.config.scala.mapper

import org.pkl.config.java.mapper.Conversion
import org.pkl.core.{PClassInfo, Duration => PDuration}

import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.matching.Regex

/** Provides conversions between Java types backing PKL and Scala types,
  * enabling seamless interoperability for configuration values within PKL.
  */
object ScalaConversions {

  val pStringToInstant: Conversion[String, Instant] =
    Conversion.of(
      PClassInfo.String,
      classOf[Instant],
      (v: String, _) => Instant.parse(v)
    )

  val pIntToInstant: Conversion[java.lang.Long, Instant] =
    Conversion.of(
      PClassInfo.Int,
      classOf[Instant],
      (v: java.lang.Long, _) => Instant.ofEpochMilli(v)
    )

  val pDurationToDuration: Conversion[PDuration, Duration] =
    Conversion.of(
      PClassInfo.Duration,
      classOf[Duration],
      (v: PDuration, _) => Duration.fromNanos(v.inNanos()).toCoarsest
    )

  val pDurationToFiniteDuration: Conversion[PDuration, FiniteDuration] =
    Conversion.of(
      PClassInfo.Duration,
      classOf[FiniteDuration],
      (v: PDuration, _) =>
        FiniteDuration(v.inWholeNanos(), TimeUnit.NANOSECONDS).toCoarsest
    )

  val pStringToScalaRegex: Conversion[String, Regex] =
    Conversion.of(PClassInfo.String, classOf[Regex], (v: String, _) => v.r)

  val pRegexToScalaRegex: Conversion[Pattern, Regex] =
    Conversion.of(
      PClassInfo.Regex,
      classOf[Regex],
      (v: Pattern, _) => v.pattern().r
    )

  def all: List[Conversion[_, _]] = List(
    pIntToInstant,
    pStringToInstant,
    pDurationToFiniteDuration,
    pDurationToDuration,
    pStringToScalaRegex,
    pRegexToScalaRegex
  )
}

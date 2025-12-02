/*
 * Copyright © 2015-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.core.util.paguro.oneOf;

import static org.pkl.core.util.paguro.FunctionUtils.stringify;

import java.util.Objects;
import org.pkl.core.util.paguro.function.Fn1;

/**
 * `Or` represents the presence of a successful outcome, or an error. Contrast this with Option
 * which represents the presence or absence of a value. Option.Some and Or.Good are just about
 * identical. Unlike Option.None, Bad contains an error code or value. <a
 * href="https://www.youtube.com/watch?v=bCTZQi2dpl8" target="_blank">Bill Venners, Scalactic,
 * SuperSafe, and Functional Error Handling talk at SF Scala 2015-02-24</a> convinced me that Or is
 * friendlier than <a href="http://www.scala-lang.org/api/rc2/scala/Either.html"
 * target="_blank">Either</a>. This class is based on Bill Venners' Or. I did not make Every, One,
 * and Many subclasses, figuring that you can make an Or&lt;GoodType,ImList&lt;BadType&gt;&gt; if
 * you expect that.
 *
 * <p>Bill makes the point that there are still some reasons to throw exceptions, but he says to
 * "Throw exceptions at developers, not at code" meaning that if there's code in your program that
 * can recover from the issue, use a functional return type (like Or). Throw exceptions for things a
 * program can't handle without developer intervention.
 *
 * <p>Any errors are my own.
 *
 * <p>This implementation is more like a sealed trait (in Kotlin or Scala) than a simple {@link
 * OneOf2} union type. This makes it a little less general, and more meaningful to use.
 */
public interface Or<G, B> {
  /** Construct a new Good from the given object. */
  static <G, B> Or<G, B> good(G good) {
    return new Good<>(good);
  }

  /** Construct a new Bad from the given object. */
  static <G, B> Or<G, B> bad(B bad) {
    return new Bad<>(bad);
  }

  /** Returns true if this Or has a good value. */
  boolean isGood();

  /** Returns true if this Or has a bad value. */
  boolean isBad();

  /** Returns the good value if this is a Good, or throws an exception if this is a Bad. */
  G good();

  /** Returns the bad value if this is a Bad, or throws an exception if this is a Good. */
  B bad();

  /**
   * Exactly one of these functions will be executed - determined by whether this is a Good or a
   * Bad.
   *
   * @param fg the function to be executed if this OneOf stores the first type.
   * @param fb the function to be executed if this OneOf stores the second type.
   * @return the return value of whichever function is executed.
   */
  <R> R match(Fn1<G, R> fg, Fn1<B, R> fb);

  /** Represents the presence of a Good value (and absence of a Bad). */
  final class Good<G, B> implements Or<G, B> {
    private final G g;

    private Good(G good) {
      g = good;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isGood() {
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isBad() {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public G good() {
      return g;
    }

    /** {@inheritDoc} */
    @Override
    public B bad() {
      throw new IllegalStateException("Cant call bad() on a Good.");
    }

    /** {@inheritDoc} */
    @Override
    public <R> R match(Fn1<G, R> fg, Fn1<B, R> fb) {
      return fg.apply(g);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
      return g.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
      //noinspection SimplifiableIfStatement
      if (this == other) {
        return true;
      }
      return other instanceof Good && Objects.equals(this.g, ((Good) other).g);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return "Good(" + stringify(g) + ")";
    }
  }

  /** Represents the presence of a Bad value (and absence of a Good). */
  final class Bad<G, B> implements Or<G, B> {
    private final B b;

    private Bad(B bad) {
      b = bad;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isGood() {
      return false;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isBad() {
      return true;
    }

    /** {@inheritDoc} */
    @Override
    public G good() {
      throw new IllegalStateException("Cant call good() on a Bad.");
    }

    /** {@inheritDoc} */
    @Override
    public B bad() {
      return b;
    }

    /** {@inheritDoc} */
    @Override
    public <R> R match(Fn1<G, R> fg, Fn1<B, R> fb) {
      return fb.apply(b);
    }

    /** {@inheritDoc} */
    // Returns twos compliment of contained item.
    @Override
    public int hashCode() {
      return ~b.hashCode();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
      //noinspection SimplifiableIfStatement
      if (this == other) {
        return true;
      }
      return other instanceof Bad && Objects.equals(this.b, ((Bad) other).b);
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
      return "Bad(" + stringify(b) + ")";
    }
  }
} // end interface Or

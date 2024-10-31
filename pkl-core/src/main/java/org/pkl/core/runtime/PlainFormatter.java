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

/*
   TODO:
     * Consider consolidating all of `PlainFormatter` into `TextFormatter`.
*/
public class PlainFormatter extends TextFormatter<PlainFormatter> {

  @Override
  public PlainFormatter newInstance() {
    return new PlainFormatter();
  }

  @Override
  public PlainFormatter margin(String marginMatter) {
    return a(marginMatter);
  }

  @Override
  public PlainFormatter hint(String hint) {
    return a(hint);
  }

  @Override
  public PlainFormatter newline() {
    return a('\n');
  }

  @Override
  public PlainFormatter newlines(int count) {
    return repeat(count, '\n');
  }

  @Override
  public PlainFormatter stackOverflowLoopCount(int counter) {
    return a(String.valueOf(counter));
  }

  @Override
  public PlainFormatter text(String text) {
    return a(text);
  }

  @Override
  public PlainFormatter errorHeader(String header) {
    return a(header);
  }

  @Override
  public PlainFormatter error(String message) {
    return a(message);
  }

  @Override
  public PlainFormatter lineNumber(String line) {
    return a(line);
  }

  @Override
  public PlainFormatter object(Object obj) {
    return a(obj);
  }

  @Override
  protected PlainFormatter self() {
    return this;
  }
}

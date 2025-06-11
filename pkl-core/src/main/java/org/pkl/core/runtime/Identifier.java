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
package org.pkl.core.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Note: this class has a natural ordering that is inconsistent with equals. */
public final class Identifier implements Comparable<Identifier> {
  /** Pool for non-local members. */
  private static final Map<String, Identifier> pool = new ConcurrentHashMap<>();

  /** Pool for local properties. Also used for frame slots of `for` expression variables. */
  private static final Map<String, Identifier> localPropertyPool = new ConcurrentHashMap<>();

  /** Pool for local methods. */
  private static final Map<String, Identifier> localMethodPool = new ConcurrentHashMap<>();

  // collection literals
  public static final Identifier LIST = get("List");
  public static final Identifier SET = get("Set");
  public static final Identifier MAP = get("Map");

  // members of pkl.base
  public static final Identifier ANY = get("Any");
  public static final Identifier TYPED = get("Typed");
  public static final Identifier MODULE = get("Module");
  public static final Identifier MODULE_INFO = get("ModuleInfo");

  // members of pkl.base#Any
  public static final Identifier TO_STRING = get("toString");

  // members of pkl.base#Annotation and its children
  public static final Identifier MESSAGE = get("message");

  // members of pkl.base#Listing and pkl.base#Mapping
  public static final Identifier DEFAULT = get("default");

  // members of pkl.base#ValueRenderer subclasses
  public static final Identifier MODE = get("mode");
  public static final Identifier INDENT = get("indent");
  public static final Identifier INDENT_WIDTH = get("indentWidth");
  public static final Identifier OMIT_NULL_PROPERTIES = get("omitNullProperties");
  public static final Identifier USE_CUSTOM_STRING_DELIMITERS = get("useCustomStringDelimiters");
  public static final Identifier IS_STREAM = get("isStream");
  public static final Identifier RESTRICT_CHARSET = get("restrictCharset");
  public static final Identifier XML_VERSION = get("xmlVersion");
  public static final Identifier ROOT_ELEMENT_NAME = get("rootElementName");
  public static final Identifier ROOT_ELEMENT_ATTRIBUTES = get("rootElementAttributes");
  public static final Identifier CONVERTERS = get("converters");
  public static final Identifier USE_MAPPING = get("useMapping");

  // members of pkl.base#RegexMatch
  public static final Identifier VALUE = get("value");

  // members of pkl.base#Module
  public static final Identifier OUTPUT = get("output");
  public static final Identifier FILES = get("files");

  // members of pkl.base#{ModuleOutput, Resource, RenderDirective, PcfRenderDirective, XmlComment,
  // XmlCData}
  public static final Identifier TEXT = get("text");

  public static final Identifier BYTES_CONSTRUCTOR = get("Bytes");

  // members of pkl.base#{FileOutput}
  public static final Identifier BYTES = get("bytes");

  // members of pkl.base#ModuleOutput, pkl.base#Resource, pkl.base#String
  public static final Identifier BASE64 = get("base64");

  // members of pkl.base#Resource
  public static final Identifier URI = get("uri");

  // members of pkl.base#ModuleInfo
  public static final Identifier MIN_PKL_VERSION = get("minPklVersion");

  // members of pkl.base#Duration
  public static final Identifier NS = get("ns");
  public static final Identifier US = get("us");
  public static final Identifier MS = get("ms");
  public static final Identifier S = get("s");
  public static final Identifier MIN = get("min");
  public static final Identifier H = get("h");
  public static final Identifier D = get("d");

  // members of pkl.base#DataSize
  public static final Identifier B = get("b");
  public static final Identifier KB = get("kb");
  public static final Identifier KIB = get("kib");
  public static final Identifier MB = get("mb");
  public static final Identifier MIB = get("mib");
  public static final Identifier GB = get("gb");
  public static final Identifier GIB = get("gib");
  public static final Identifier TB = get("tb");
  public static final Identifier TIB = get("tib");
  public static final Identifier PB = get("pb");
  public static final Identifier PIB = get("pib");

  // members of pkl.base#Function(1-5)
  public static final Identifier APPLY = get("apply");

  // members of pkl.base#PcfRenderDirective
  public static final Identifier BEFORE = get("before");
  public static final Identifier AFTER = get("after");

  // members of pkl.base#XmlElement, pkl.jsonnet#ExtVar
  public static final Identifier IS_XML_ELEMENT = get("_isXmlElement");
  public static final Identifier NAME = get("name");
  public static final Identifier ATTRIBUTES = get("attributes");
  public static final Identifier IS_BLOCK_FORMAT = get("isBlockFormat");

  // members of pkl.jsonnet#ImportStr
  public static final Identifier PATH = get("path");

  // members of pkl.test
  public static final Identifier FACTS = get("facts");
  public static final Identifier EXAMPLES = get("examples");

  // members of pkl.benchmark
  public static final Identifier ITERATIONS = get("iterations");
  public static final Identifier ITERATION_TIME = get("iterationTime");
  public static final Identifier IS_VERBOSE = get("isVerbose");
  public static final Identifier EXPRESSION = get("expression");
  public static final Identifier SOURCE_MODULE = get("sourceModule");
  public static final Identifier SOURCE_TEXT = get("sourceText");
  public static final Identifier SOURCE_URI = get("sourceUri");

  // members of pkl.yaml
  public static final Identifier MAX_COLLECTION_ALIASES = get("maxCollectionAliases");

  // common in lambdas etc
  public static final Identifier IT = get("it");

  private final String name;

  private Identifier(String name) {
    this.name = name;
  }

  @TruffleBoundary
  public static Identifier get(String name) {
    return pool.computeIfAbsent(name, Identifier::new);
  }

  @TruffleBoundary
  public static Identifier localProperty(String name) {
    return localPropertyPool.computeIfAbsent(name, Identifier::new);
  }

  @TruffleBoundary
  public static Identifier localMethod(String name) {
    return localMethodPool.computeIfAbsent(name, Identifier::new);
  }

  @TruffleBoundary
  public static Identifier property(String name, boolean isLocal) {
    return isLocal ? localProperty(name) : get(name);
  }

  @TruffleBoundary
  public static Identifier method(String name, boolean isLocal) {
    return isLocal ? localMethod(name) : get(name);
  }

  public Identifier toLocalProperty() {
    return localProperty(name);
  }

  public Identifier toRegular() {
    return get(name);
  }

  public Identifier toLocalMethod() {
    return localMethod(name);
  }

  public boolean isRegular() {
    return get(name) == this;
  }

  // not named isLocalProperty() to work around https://bugs.openjdk.java.net/browse/JDK-8185424
  // (which is apparently related to `Xdoclint:none` option)
  public boolean isLocalProp() {
    return localProperty(name) == this;
  }

  public boolean isLocalMethod() {
    return localMethod(name) == this;
  }

  @Override
  @TruffleBoundary
  public int compareTo(Identifier other) {
    return name.compareTo(other.name);
  }

  // equals and hashCode intentionally inherited from Object

  @Override
  @TruffleBoundary
  public String toString() {
    return name;
  }
}

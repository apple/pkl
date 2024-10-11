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

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Java representation of {@code pkl.analyze#ImportGraph}.
 *
 * @param imports The graph of imports declared within the program.
 *     <p>Each key is a module inside the program, and each value is the module URIs decalred as
 *     imports inside that module. The set of all modules initialized within the program is the set
 *     of keys in this map.
 * @param resolvedImports A mapping of a module's in-language URI, and the URI that it resolves to.
 *     <p>For example, a local package dependency is represented with scheme {@code
 *     projectpackage:}, and (typically) resolves to a {@code file:} scheme.
 */
public record ImportGraph(Map<URI, Set<URI>> imports, Map<URI, URI> resolvedImports) {}

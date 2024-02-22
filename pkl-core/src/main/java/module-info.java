/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>https://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

/** Pkl: Core. */
@SuppressWarnings("module")
module pkl.core {
  requires java.base;
  requires java.naming;
  requires org.graalvm.nativeimage;
  requires org.graalvm.truffle;
  requires org.snakeyaml.engine.v2;
  requires pkl.executor;

  exports org.pkl.core.module to
      pkl.cli,
      pkl.commons.cli,
      pkl.config.java,
      pkl.doc,
      pkl.server;
  exports org.pkl.core.packages to
      pkl.cli,
      pkl.commons.cli,
      pkl.config.java,
      pkl.doc,
      pkl.server;
  exports org.pkl.core.project to
      pkl.cli,
      pkl.commons.cli,
      pkl.config.java,
      pkl.server;
  exports org.pkl.core.repl to
      pkl.cli,
      pkl.commons.cli,
      pkl.config.java;
  exports org.pkl.core.resource to
      pkl.cli,
      pkl.commons.cli,
      pkl.config.java,
      pkl.server;
  exports org.pkl.core.runtime to
      pkl.cli,
      pkl.commons.cli,
      pkl.config.java,
      pkl.server;
  exports org.pkl.core.stdlib.test.report to
      pkl.cli,
      pkl.commons.cli,
      pkl.config.java;
  exports org.pkl.core.util to
      pkl.cli,
      pkl.commons.cli,
      pkl.config.java,
      pkl.doc;
  exports org.pkl.core.settings to
      pkl.cli,
      pkl.commons.cli;
  exports org.pkl.core.parser to
      pkl.doc;
  exports org.pkl.core.util.json to
      pkl.doc;
  exports org.pkl.core.ast.member to
      pkl.server;
  exports org.pkl.core.plugin;
  exports org.pkl.core;

  uses org.pkl.core.StackFrameTransformer;
  uses org.pkl.core.module.ModuleKeyFactory;
  uses org.pkl.core.plugin.PklPlugin;
  uses org.pkl.executor.Executor;

  provides com.oracle.truffle.api.provider.TruffleLanguageProvider with
      org.pkl.core.runtime.VmLanguageProvider;
}

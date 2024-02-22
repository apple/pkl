/**
 * Pkl: CLI.
 */
open module pkl.cli {
  requires java.base;
  requires kotlin.stdlib;

  requires org.jline.reader;

  requires org.graalvm.nativeimage;
  requires org.graalvm.truffle;
  requires org.graalvm.truffle.runtime;
  requires org.graalvm.truffle.runtime.svm;

  requires pkl.core;
  requires pkl.commons;
  requires pkl.server;
}

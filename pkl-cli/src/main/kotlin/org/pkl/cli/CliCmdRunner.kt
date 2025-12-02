package org.pkl.cli

import org.pkl.commons.cli.CliBaseOptions
import org.pkl.commons.cli.CliCommand
import org.pkl.core.Closeables
import org.pkl.core.EvaluatorBuilder
import org.pkl.core.ModuleSource.uri
import java.io.Writer

class CliCmdRunner
  @JvmOverloads
  constructor(
    private val options: CliBaseOptions,
    private val args: List<String>,
    private val consoleWriter: Writer = System.out.writer(),
    private val errWriter: Writer = System.err.writer(),
  ) : CliCommand(options) {
    
  override fun doRun() {
    val builder = evaluatorBuilder()
    try {
      evalCmd(builder)
    } finally {
      Closeables.closeQuietly(builder.moduleKeyFactories)
      Closeables.closeQuietly(builder.resourceReaders)
    }
  }

  private fun evalCmd(builder: EvaluatorBuilder) {
    assert(options.normalizedSourceModules.size == 1)
    val source = options.normalizedSourceModules.first()

    val evaluator = builder.build()
    evaluator.use {
      val schema = evaluator.evaluateSchema(uri(source))
      val subcommands
    
  }
}

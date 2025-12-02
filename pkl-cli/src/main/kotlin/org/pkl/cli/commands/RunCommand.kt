package org.pkl.cli.commands

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import org.pkl.cli.CliCmdRunner
import org.pkl.commons.cli.commands.BaseCommand
import org.pkl.commons.cli.commands.BaseOptions
import org.pkl.commons.cli.commands.ProjectOptions
import java.net.URI

class CmdCommand: BaseCommand(name = "cmd", helpLink = helpLink) {
  override val helpString = "Run a Pkl CLI tool"
  override val treatUnknownOptionsAsArgs = true
  init { context { allowInterspersedArgs = false }}
  
  val module: URI by argument(name = "module", completionCandidates = CompletionCandidates.Path)
    .convert { BaseOptions.parseModuleName(it) }
  
  val args: List<String> by argument(name = "args").multiple()

  private val projectOptions by ProjectOptions()

  override fun run() {
    CliCmdRunner(baseOptions.baseOptions(listOf(module), projectOptions), args).run()
  }
}

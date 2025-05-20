package org.pkl.formatter

import org.pkl.formatter.ast.ForceLine
import org.pkl.formatter.ast.Nodes
import org.pkl.parser.GenericParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class Formatter {
    fun format(path: Path): String {
        try {
            return format(Files.readString(path))
        } catch (e: IOException) {
            throw RuntimeException("Could not format $path:", e)
        }
    }

    fun format(text: String): String {
        val parser = GenericParser()
        val builder = Builder(text)
        val gen = Generator()
        val mod = parser.parseModule(text)
        val ast = builder.format(mod)
        // force a line at the end of the file
        gen.generate(Nodes(listOf(ast, ForceLine)))
        return gen.toString()
    }
}

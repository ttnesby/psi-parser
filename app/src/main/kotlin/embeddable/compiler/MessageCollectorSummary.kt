package embeddable.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

class MessageCollectorSummary : MessageCollector {
    private var errorCount = 0
    private var warningCount = 0
    private var infoCount = 0

    override fun clear() {
        errorCount = 0
        warningCount = 0
        infoCount = 0
    }

    override fun hasErrors(): Boolean = errorCount > 0

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?
    ) {
        when (severity) {
            CompilerMessageSeverity.ERROR, CompilerMessageSeverity.EXCEPTION -> errorCount++
            CompilerMessageSeverity.WARNING -> warningCount++
            CompilerMessageSeverity.INFO -> infoCount++
            else -> {} // ignore other severities
        }
    }
}
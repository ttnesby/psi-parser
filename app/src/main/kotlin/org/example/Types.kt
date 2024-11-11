package org.example

import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.psi.*

data class RuleServiceDoc(
        val navn: String,
        val beskrivelse: String,
        val inndata: List<PropertyDoc>,
        val utdata: List<PropertyDoc>,
) {
    override fun toString(): String {
        return """
            |RuleServiceDoc(
            |   navn = $navn,
            |   beskrivelse = $beskrivelse,
            |   inndata = $inndata,
            |   utdata = $utdata
            |)
        """.trimMargin()
    }
}

data class PropertyDoc(
        val navn: String,
        val type: String,
        val beskrivelse: String,
) {
    override fun toString(): String {
        return """
                |PropertyDoc(
                |   navn = $navn,
                |   type = $type,
                |   beskrivelse = $beskrivelse
                |)
            """.trimMargin()
    }
}

data class RuleFlowDoc(
        val navn: String,
)

package org.example

import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.psi.*

data class RuleServiceDoc(
        val navn: String,
        val beskrivelse: String,
        val inndata: List<PropertyDoc>,
        val utdata: List<PropertyDoc>,
        val tjeneste: RuleServiceMethodDoc? = null
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

// TODO - transform to Mural data modell when ready
// Data classes to hold the extracted information
data class RuleServiceMethodDoc(val kdoc: List<String>, val flows: List<FlowCall>)

data class FlowCall(val flowClass: String, val parameters: List<String>)

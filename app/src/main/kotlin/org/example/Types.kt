package org.example

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import java.io.File
import java.net.URI

// data class for rule service documentation, see AbstractPensjonRuleService
data class RuleServiceDoc(
    val navn: String,
    val beskrivelse: String,
    val inndata: List<PropertyDoc>,
    val utdata: List<PropertyDoc>,
    val flyt: FlowElement.Flow,
    val gitHubUri: URI,
) {
    companion object {
        fun new(
            navn: String,
            beskrivelse: String,
            inndata: List<PropertyDoc>,
            utdata: List<PropertyDoc>,
            flyt: FlowElement.Flow,
            gitHubUri: URI
        ): RuleServiceDoc = RuleServiceDoc(navn, beskrivelse, inndata, utdata, flyt, gitHubUri)
    }

    override fun toString(): String {
        return """
            |RuleServiceDoc(
            |   navn = $navn
            |   beskrivelse = $beskrivelse
            |   inndata = $inndata
            |   utdata = $utdata
            |   flyt = $flyt
            |   gitHubUri = $gitHubUri
            |)
        """.trimMargin()
    }
}

// data class for rule flow documentation, see AbstractPensjonRuleFlow
data class RuleFlowDoc(
    val navn: String,
    val beskrivelse: String,
    val inndata: List<PropertyDoc>,
    val flyt: FlowElement.Flow,
    val gitHubUri: URI,
) {
    companion object {
        fun new(
            navn: String,
            beskrivelse: String,
            inndata: List<PropertyDoc>,
            flyt: FlowElement.Flow,
            gitHubUri: URI
        ): RuleFlowDoc = RuleFlowDoc(navn, beskrivelse, inndata, flyt, gitHubUri)
    }

    override fun toString(): String {
        return """
            |RuleFlowDoc(
            |   navn = $navn
            |   beskrivelse = $beskrivelse
            |   inndata = $inndata
            |   flyt = $flyt
            |   gitHubUri = $gitHubUri
            |)
        """.trimMargin()
    }
}

// data class for rule set documentation, see AbstractPensjonRuleSet
data class RuleSetDoc(
    val navn: String,
    val beskrivelse: String,
    val inndata: List<PropertyDoc>,
    val flyt: FlowElement.Flow,
    val gitHubUri: URI,
) {
    companion object {
        fun new(
            navn: String,
            beskrivelse: String,
            inndata: List<PropertyDoc>,
            flyt: FlowElement.Flow,
            gitHubUri: URI
        ): RuleSetDoc = RuleSetDoc(navn, beskrivelse, inndata, flyt, gitHubUri)
    }

    override fun toString(): String {
        return """
            |RuleSetDoc(
            |   navn = $navn
            |   beskrivelse = $beskrivelse
            |   inndata = $inndata
            |   flyt = $flyt
            |   gitHubUri = ${gitHubUri.toString()}
            |)
        """.trimMargin()
    }
}

data class PropertyDoc(
    val navn: String,
    val type: String,
    val beskrivelse: String,
) {

    companion object {
        fun new(navn: String, type: String, beskrivelse: String): PropertyDoc =
            PropertyDoc(navn, type, beskrivelse)

        fun fromParameter(parameter: KtParameter, ktClass: KtClass): PropertyDoc =
            PropertyDoc(
                navn = parameter.name ?: "",
                type = parameter.typeReference?.text ?: "Unknown",
                beskrivelse = "Parameter of ${ktClass.name}"
            )

        fun fromPrimaryConstructor(constructor: KtPrimaryConstructor): List<PropertyDoc> =
            constructor.valueParameters.map { param ->
                PropertyDoc(
                    navn = param.name ?: "",
                    type = param.typeReference?.text ?: "Unknown",
                    beskrivelse = param.getKDocOrEmpty()
                )
            }
    }

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

// TODO
// - Simplify by integratin FlowReference into FlowElement
// - Implement this
//

// Sealed class for flow elements
//
// The starting point for a rule flow is a rule service. The rule service will invoke a rule flow,
// which in turn will invoke other rule flows or rule sets. A rule set may invoke other rule sets
// and
// even rule flows (see BestemGunstigsteTTAvdødRS, line 74)
//
// Within a rule flow, there may be branching where the flow is split into multiple branches,
// each with a condition. Each branch is called a "gren" (branch), and the branching element is
// called "forgrening"
sealed class FlowElement {

    data class Flow(val elementer: List<FlowElement>) : FlowElement()
    data class Forgrening(val beskrivelse: String, val navn: String, val gren: List<Gren>) :
        FlowElement()

    data class Gren(val beskrivelse: String, val betingelse: String, val flyt: Flow) :
        FlowElement()

    data class Documentation(val beskrivelse: String) : FlowElement()

    // reference to flow element in other files
    data class RuleFlow(val navn: String, val fil: File) : FlowElement()
    data class RuleSet(val navn: String, val fil: File) : FlowElement()
    data class Function(val navn: String, val fil: File) : FlowElement()
}

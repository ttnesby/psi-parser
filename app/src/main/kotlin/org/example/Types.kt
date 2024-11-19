package org.example

import java.io.File
import java.net.URI
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter

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

/**
 * Sealed class for flow elements RuleEntityDoc: Rule flow or rule set flow, see
 * AbstractPensjonRuleFlow/AbstractPensjonRuleSet
 *
 * Rule flow is invoked from a rule service, but also from other rule flows, and even rule sets For
 * the latter case, see BestemGunstigsteTTAvdødRS, line 74
 *
 * Rule set flow is invoked from a rule flow, but also from other rule sets
 *
 * Forgrening: branching a flow into multiple branches, each with condition Gren: a branch in a
 * branching flow
 *
 * Reference: reference to another flow element, avoiding containment
 */
sealed class FlowElement {
    // Rule flow or rule set flow documentation
    data class RuleEntityDoc(
            val navn: String,
            val beskrivelse: String,
            val inndata: List<PropertyDoc>,
            val flyt: Flow,
            val gitHubUrl: URI,
    ) : FlowElement()
    data class Flow(val elementer: List<FlowElement>) : FlowElement()
    data class Forgrening(val beskrivelse: String, val navn: String, val gren: List<Gren>) :
            FlowElement()
    data class Gren(val beskrivelse: String, val betingelse: String, val flyt: Flow) :
            FlowElement()
    data class Reference(val reference: FlowReference) : FlowElement()
}

sealed class FlowReference {
    data class Documentation(val beskrivelse: String) : FlowReference()
    data class RuleFlow(val navn: String, val fil: File) : FlowReference()
    data class RuleSet(val navn: String, val fil: File) : FlowReference()
    data class Function(val navn: String, val fil: File) : FlowReference()
}

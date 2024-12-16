package pensjon.regler

import org.example.formatOrEmpty
import org.example.getKDocOrEmpty
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import java.io.File
import java.net.URI

// data class for rule service documentation, see AbstractPensjonRuleService
data class RuleServiceInfo(
    val navn: String,
    val beskrivelse: String,
    val inndata: List<PropertyInfo>,
    val utdata: List<PropertyInfo>,
    val flyt: FlowElement.Flow,
    val gitHubUri: URI,
) {
    companion object {
        fun new(
            navn: String,
            beskrivelse: String,
            inndata: List<PropertyInfo>,
            utdata: List<PropertyInfo>,
            flyt: FlowElement.Flow,
            gitHubUri: URI
        ): RuleServiceInfo = RuleServiceInfo(navn, beskrivelse, inndata, utdata, flyt, gitHubUri)
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
data class RuleFlowInfo(
    val navn: String,
    val beskrivelse: String,
    val inndata: List<PropertyInfo>,
    val flyt: FlowElement.Flow,
    val gitHubUri: URI,
) {
    companion object {
        fun new(
            navn: String,
            beskrivelse: String,
            inndata: List<PropertyInfo>,
            flyt: FlowElement.Flow,
            gitHubUri: URI
        ): RuleFlowInfo = RuleFlowInfo(navn, beskrivelse, inndata, flyt, gitHubUri)
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
data class RuleSetInfo(
    val navn: String,
    val beskrivelse: String,
    val inndata: List<PropertyInfo>,
    val flyt: FlowElement.Flow,
    val gitHubUri: URI,
) {
    companion object {
        fun new(
            navn: String,
            beskrivelse: String,
            inndata: List<PropertyInfo>,
            flyt: FlowElement.Flow,
            gitHubUri: URI
        ): RuleSetInfo = RuleSetInfo(navn, beskrivelse, inndata, flyt, gitHubUri)
    }

    override fun toString(): String {
        return """
            |RuleSetDoc(
            |   navn = $navn
            |   beskrivelse = $beskrivelse
            |   inndata = $inndata
            |   flyt = $flyt
            |   gitHubUri = $gitHubUri
            |)
        """.trimMargin()
    }
}

data class PropertyInfo(
    val navn: String,
    val type: String,
    val beskrivelse: String,
) {

    companion object {
        fun new(navn: String, type: String, beskrivelse: String): PropertyInfo =
            PropertyInfo(navn, type, beskrivelse)

        fun fromParameter(parameter: KtParameter): PropertyInfo =
            PropertyInfo(
                navn = parameter.name ?: "",
                type = parameter.typeReference?.text ?: "Unknown",
                beskrivelse = "Parameter in primary constructor of ${parameter.containingClass()?.name}"
            )

        fun fromProperties(properties: List<KtProperty>): List<PropertyInfo> =
            properties.map { prop ->
                PropertyInfo(
                    navn = prop.name!!,
                    type = prop.typeReference?.text ?: "Unknown",
                    beskrivelse = prop.children.filterIsInstance<KDoc>().firstOrNull()?.formatOrEmpty() ?: ""
                )
            }

        fun fromPrimaryConstructor(constructor: KtPrimaryConstructor): List<PropertyInfo> =
            constructor.valueParameters.map { param ->
                PropertyInfo(
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

    // reference to flow element in other files
    data class RuleFlow(val navn: String, val beskrivelse: String, val fil: File) : FlowElement()
    data class RuleSet(val navn: String, val beskrivelse: String, val fil: File) : FlowElement()
    data class Function(val navn: String, val beskrivelse: String, val fil: File) : FlowElement()
}

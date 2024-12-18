package pensjon.regler

import java.io.File
import java.net.URI

sealed interface RuleInfo {
    val navn: String
    val beskrivelse: String
    val inndata: List<PropertyInfo>
    val flyt: FlowElement.Flow
    val gitHubUri: URI
}

data class RuleServiceInfo(
    override val navn: String,
    override val beskrivelse: String,
    override val inndata: List<PropertyInfo>,
    val utdata: List<PropertyInfo>,
    override val flyt: FlowElement.Flow,
    override val gitHubUri: URI,
) : RuleInfo

data class RuleFlowInfo(
    override val navn: String,
    override val beskrivelse: String,
    override val inndata: List<PropertyInfo>,
    override val flyt: FlowElement.Flow,
    override val gitHubUri: URI,
) : RuleInfo

data class RuleSetInfo(
    override val navn: String,
    override val beskrivelse: String,
    override val inndata: List<PropertyInfo>,
    override val flyt: FlowElement.Flow,
    override val gitHubUri: URI,
) : RuleInfo

data class PropertyInfo(
    val navn: String,
    val type: String,
    val beskrivelse: String,
)

data class Condition(
    val navn: String,
    val uttrykk: String,
)

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

    data class Gren(val beskrivelse: String, val betingelse: Condition, val flyt: Flow) :
        FlowElement()

    // reference to flow element in other files
    data class RuleFlow(val navn: String, val beskrivelse: String, val fil: File) : FlowElement()
    data class RuleSet(val navn: String, val beskrivelse: String, val fil: File) : FlowElement()
    data class Function(val navn: String, val beskrivelse: String, val fil: File) : FlowElement()
}

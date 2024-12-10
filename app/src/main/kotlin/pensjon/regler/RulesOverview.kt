package pensjon.regler

import org.example.RuleFlowDoc
import org.example.RuleServiceDoc
import org.example.RuleSetDoc

class RulesOverview (
    val services: List<RuleServiceDoc>,
    val flows: List<RuleFlowDoc>,
    val sets: List<RuleSetDoc>
) {
    companion object {
        fun empty(): RulesOverview = RulesOverview(emptyList(), emptyList(), emptyList())
        fun newService(service: RuleServiceDoc): RulesOverview = RulesOverview(listOf(service), emptyList(), emptyList())
        fun newFlow(flow: RuleFlowDoc): RulesOverview = RulesOverview(emptyList(), listOf(flow), emptyList())
        fun newSet(set: RuleSetDoc): RulesOverview = RulesOverview(emptyList(), emptyList(), listOf(set))
    }
    fun addBatch(
        services: List<RuleServiceDoc>,
        flows: List<RuleFlowDoc>,
        sets: List<RuleSetDoc>
    ): RulesOverview = RulesOverview(
        this.services + services,
        this.flows + flows,
        this.sets + sets
    )
}
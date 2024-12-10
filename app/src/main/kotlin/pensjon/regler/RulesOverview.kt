package pensjon.regler

class RulesOverview (
    val services: List<RuleServiceInfo>,
    val flows: List<RuleFlowInfo>,
    val sets: List<RuleSetInfo>
) {
    companion object {
        fun empty(): RulesOverview = RulesOverview(emptyList(), emptyList(), emptyList())
        fun newService(service: RuleServiceInfo): RulesOverview = RulesOverview(listOf(service), emptyList(), emptyList())
        fun newFlow(flow: RuleFlowInfo): RulesOverview = RulesOverview(emptyList(), listOf(flow), emptyList())
        fun newSet(set: RuleSetInfo): RulesOverview = RulesOverview(emptyList(), emptyList(), listOf(set))
    }
    fun addBatch(
        services: List<RuleServiceInfo>,
        flows: List<RuleFlowInfo>,
        sets: List<RuleSetInfo>
    ): RulesOverview = RulesOverview(
        this.services + services,
        this.flows + flows,
        this.sets + sets
    )
}
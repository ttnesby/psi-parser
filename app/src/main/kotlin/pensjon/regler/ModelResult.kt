package pensjon.regler



class ModelResult (
    val services: List<RuleServiceInfo>,
    val flows: List<RuleFlowInfo>,
    val sets: List<RuleSetInfo>
) {
    companion object {
        fun empty(): ModelResult = ModelResult(emptyList(), emptyList(), emptyList())
        fun newService(service: RuleServiceInfo): ModelResult = ModelResult(listOf(service), emptyList(), emptyList())
        fun newFlow(flow: RuleFlowInfo): ModelResult = ModelResult(emptyList(), listOf(flow), emptyList())
        fun newSet(set: RuleSetInfo): ModelResult = ModelResult(emptyList(), emptyList(), listOf(set))
    }
    fun addBatch(
        services: List<RuleServiceInfo>,
        flows: List<RuleFlowInfo>,
        sets: List<RuleSetInfo>
    ): ModelResult = ModelResult(
        this.services + services,
        this.flows + flows,
        this.sets + sets
    )
}
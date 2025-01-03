package rule.dsl


enum class DSLTypeBranch(val typeName: String) {
    FORGRENING("forgrening");
//    GREN("gren"),
//    FLYT("flyt");

    override fun toString(): String = typeName

    companion object {
        fun fromString(typeName: String): DSLTypeBranch? = entries.find { it.typeName == typeName }
    }
}

// TODO - need to define a clean cut for how the extract flow is working - manage with just one extractFlow
// - for DSLTypeFlow, the extraction is based on the block expression for each item
// - for DSLTypeBranch, shouldn't be enough with FORGRENING, with list of GREN, where each GREN is BETINGELSE and FLYT
// where the latter should call extract flow for the block expression of flyt, not GREN - resolving missing betingelse

// The key point, the data structure for FORGRENING is recursive where flyt { extract local flow } should be enough

enum class DSLTypeFlow(val typeName: String) {
    SERVICE("ruleService"),
    FLOW("ruleflow");

    override fun toString(): String = typeName
}

sealed interface DSLTypeSuperClass {
    val typeName: String
}

enum class DSLTypeService(override val typeName: String) : DSLTypeSuperClass {
    REQUEST("ServiceRequest"),
    RESPONSE("ServiceResponse");

    override fun toString(): String = typeName
}

enum class DSLTypeAbstract(override val typeName: String) : DSLTypeSuperClass{
    RULE_SERVICE("AbstractPensjonRuleService"),
    RULE_FLOW("AbstractPensjonRuleflow"),
    RULE_SET("AbstractPensjonRuleset");

    override fun toString(): String = typeName
}
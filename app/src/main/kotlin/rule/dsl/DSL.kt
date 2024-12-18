package rule.dsl

enum class DSLType(val typeName: String) {
    FORGRENING("forgrening"),
    GREN("gren"),
    FLYT("flyt");

    override fun toString(): String = typeName
}

enum class DSLTypeFlow(val typeName: String) {
    SERVICE("ruleService"),
    FLOW("ruleflow");

    override fun toString(): String = typeName
}

enum class DSLTypeService(val typeName: String) {
    REQUEST("ServiceRequest"),
    RESPONSE("ServiceResponse");

    override fun toString(): String = typeName
}

enum class DSLTypeAbstract(val typeName: String) {
    RULE_SERVICE("AbstractPensjonRuleService"),
    RULE_FLOW("AbstractPensjonRuleflow"),
    RULE_SET("AbstractPensjonRuleset");

    override fun toString(): String = typeName
}
package rule.dsl


enum class DSLTypeBranch(val typeName: String) {
    FORGRENING("forgrening"),
    GREN("gren"),
    FLYT("flyt");

    override fun toString(): String = typeName

    companion object {
        fun fromString(typeName: String): DSLTypeBranch? = entries.find { it.typeName == typeName }
    }
}

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
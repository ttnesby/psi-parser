package org.example

import org.example.FlowElement
import org.example.FlowElement.Flow
import org.example.FlowElement.RuleFlow
import org.example.FlowElement.RuleSet
import java.io.File

fun generateAsciiDoc(ruleDocs: List<RuleServiceDoc>, outputPath: String) {

    for (rule in ruleDocs) {
        val docBuilder = StringBuilder()
        docBuilder.appendLine("== ${rule.navn}")
        docBuilder.appendLine()
        docBuilder.appendLine("*Beskrivelse:*")
        docBuilder.appendLine(rule.beskrivelse)
        docBuilder.appendLine()

        docBuilder.appendLine("*Inndata:* ${rule.inndata[0].type}")
        docBuilder.appendLine("[cols=\"1,2,2\", options=\"header\"]")
        docBuilder.appendLine("|===")
        docBuilder.appendLine("|Navn |Type |Beskrivelse")
        for (input in rule.inndata.drop(1)) {
            docBuilder.appendLine("|${input.navn} |${input.type} |${input.beskrivelse}")
        }
        docBuilder.appendLine("|===")
        docBuilder.appendLine()

        docBuilder.appendLine("*Utdata:* ${rule.utdata[0].type}")
        docBuilder.appendLine("[cols=\"1,2,2\", options=\"header\"]")
        docBuilder.appendLine("|===")
        docBuilder.appendLine("|Navn |Type |Beskrivelse")
        for (output in rule.utdata.drop(1)) {
            docBuilder.appendLine("|${output.navn} |${output.type} |${output.beskrivelse}")
        }
        docBuilder.appendLine("|===")
        docBuilder.appendLine()

        docBuilder.appendLine("*Flow:*")
        docBuilder.appendFlowElement(rule.flyt.elementer)

        docBuilder.appendLine()

        docBuilder.appendLine("*GitHub Lenke:*")
        docBuilder.appendLine("link:${rule.gitHubUri}[${rule.navn}]")
        docBuilder.appendLine()
        File("$outputPath\\${rule.navn}.adoc").writeText(docBuilder.toString())
    }
}

fun StringBuilder.appendFlowElement(elementer: List<FlowElement>) {
    elementer.forEach { element ->
        when (element) {
            // TODO burde kanskje ha pakkereferanse om vi skal referere til .adoc filer. Dette er nå kun sti for filen.
            is FlowElement.Function -> {
                this.appendLine("link:${element.fil.canonicalPath.replace(".kt", ".adoc")}[${element.navn}]")
            }

            is RuleFlow -> {
                this.appendLine("link:${element.fil.canonicalPath.replace(".kt", ".adoc")}[${element.navn}]")
            }

            is RuleSet -> {
                this.appendLine("link:${element.fil.canonicalPath.replace(".kt", ".adoc")}[${element.navn}]")
            }

            is FlowElement.Flow -> {
                this.appendFlowElement(element.elementer)
            }
            is FlowElement.Forgrening -> {
                this.appendLine("Navn: ${element.navn} - Beskrivelse: ${element.beskrivelse}")
                this.appendFlowElement(element.gren)
            }
            is FlowElement.Gren -> {
                this.appendLine("Beskrivelse: ${element.beskrivelse}")
                this.appendFlowElement(element.flyt.elementer)
            }
        }
    }
}

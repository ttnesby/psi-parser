import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import pensjon.regler.*
import result.addons.flatMap
import java.net.URI
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.Path

@Tag("integration")
class AppIntegrationTest {

    private val logger = LoggerFactory.getLogger(AppIntegrationTest::class.java)

    private lateinit var disposable: Disposable

    @BeforeEach
    fun setUpEach() {
        disposable = Disposer.newDisposable()
    }

    @AfterEach
    fun tearDownEach() {
        disposable.dispose()
    }

    @Test
    fun `test parsing for FastsettTrygdetid`() {

        val pensjonRegler = "/Users/torsteinnesby/gitHub/navikt/pensjon-regler"
        val output = "/Users/torsteinnesby/tmp/AsciiDocs"

        val args = arrayOf(
            "--repo=${pensjonRegler}",
            "--output=${output}",
            "--allow_empty_flow=true",
            "--log=WARN"
        )

        val fastsettTrygdetidPathPrefix = Path("repository")/
                "nav-repository-pensjon" /
                "src" /
                "main" /
                "kotlin" /
                "no" /
                "nav" /
                "domain" /
                "pensjon" /
                "regler" /
                "repository"

        val fastsettTrygdeTidTjeneste = fastsettTrygdetidPathPrefix /
                "tjeneste" /
                "fastsetttrygdetid"

        val fastsettTrygdeTidKomponent = fastsettTrygdetidPathPrefix/
                "komponent"/
                "trygdetid"

        val stotteKomponent = fastsettTrygdetidPathPrefix/
                "komponent"/
                "stottefunksjoner"

        val kontrollerinformasjonsgrunnlagKomponent = fastsettTrygdetidPathPrefix/
                "komponent"/
                "kontrollerinformasjonsgrunnlag"

        val poengrekkeKomponent = fastsettTrygdetidPathPrefix/
                "komponent"/
                "poengrekke"

        //"system/nav-system-pensjon-domain/src/main/kotlin/no/nav/pensjon/regler/internal/to/TrygdetidRequest.kt"

        val initCustomSourceRoots: (Path) -> ((Path) -> Boolean) = { localRoot ->
            { path ->
                path.startsWith(localRoot / fastsettTrygdeTidTjeneste) ||
                path.startsWith(localRoot / fastsettTrygdeTidKomponent) ||
                path.startsWith(localRoot / stotteKomponent) ||
                path.startsWith(localRoot / kontrollerinformasjonsgrunnlagKomponent) ||
                path.startsWith(localRoot / poengrekkeKomponent) || (
                    path.startsWith(localRoot / "system") &&
                    path.name == "kotlin" &&
                    path.parent?.name == "main" &&
                    path.parent?.parent?.name == "src"
                )
            }
        }

        validateConfig(args).flatMap { config ->
            codeToModel(config, disposable, initCustomSourceRoots)
        }.map { result ->
            val services = result.filterIsInstance<RuleServiceInfo>()
            val flows = result.filterIsInstance<RuleFlowInfo>()
            val sets = result.filterIsInstance<RuleSetInfo>()

            assertEquals(1, services.size)
            assertEquals(45, flows.size)
            assertEquals(263, sets.size)

            `verify rule service FastsettTrygdetidService`(
                services.find { it.navn == "FastsettTrygdetidService" }!!,
                Path(pensjonRegler))

            `verify rule flow StartTrygdetidFlyt`(flows.find { it.navn == "StartTrygdetidFlyt" }!!)

            `verify rule flow FastsettTrygdetidFlyt`(flows.find { it.navn == "FastsettTrygdetidFlyt" }!!)

        }
    }

    private fun `verify rule service FastsettTrygdetidService`(ruleService: RuleServiceInfo, localRoot: Path) {

        assertEquals("FastsettTrygdetidService", ruleService.navn)
        assertEquals("", ruleService.beskrivelse)

        assertEquals(11, ruleService.inndata.size)
        assertEquals(
            PropertyInfo(
                navn = "beregningsvilkarPeriodeListe",
                beskrivelse = "Liste av beregningsvilkarPerioder, p�krevd ved uf�retrygd.",
                type = "MutableList<BeregningsvilkarPeriode>"

            ), ruleService.inndata.last()
        )

        assertEquals(5, ruleService.utdata.size)
        assertEquals(
            PropertyInfo(
                navn = "pakkseddel",
                beskrivelse = "",
                type = "Pakkseddel"

            ), ruleService.utdata.last()
        )

        assertEquals(5, ruleService.flyt.elementer.size)

        val expectedUri =
            "https://github.com/navikt/pensjon-regler/blob/master/repository/nav-repository-pensjon/src/main/kotlin/no/nav/domain/pensjon/regler/repository/tjeneste/fastsetttrygdetid/function/FastsettTrygdetidService.kt"

        assertEquals(URI(expectedUri), ruleService.gitHubUri)
    }

    private fun `verify rule flow StartTrygdetidFlyt`(ruleFlow: RuleFlowInfo) {

        assertEquals("StartTrygdetidFlyt", ruleFlow.navn)
        assertEquals("", ruleFlow.beskrivelse)
        assertEquals(4, ruleFlow.inndata.size)

        assertEquals(
            PropertyInfo(
                navn = "variable",
                type = "TrygdetidVariable?",
                beskrivelse = ""
            ), ruleFlow.inndata.last()
        )

        assertEquals(2, ruleFlow.flyt.elementer.size)
    }

    private fun `verify rule flow FastsettTrygdetidFlyt`(ruleFlow: RuleFlowInfo) {

        assertEquals("FastsettTrygdetidFlyt", ruleFlow.navn)

        val forgrening = ruleFlow
            .flyt.elementer
            .filterIsInstance<FlowElement.Forgrening>().first()

        assertEquals("Uføretrygd?", forgrening.navn)
        assertEquals("Task: Uføretrygd?", forgrening.beskrivelse)
        assertEquals(2, forgrening.gren.size)

        assertEquals("Ja", forgrening.gren.first().betingelse.navn)
        assertEquals("Nei", forgrening.gren.last().betingelse.navn)
    }
}
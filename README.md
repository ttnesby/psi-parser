# psi-parser

## Kjøre programmet

Programmet tar 2 argumenter:
1) Filsti til pensjon-regler repo root
2) Filsti til output mappe for AsciiDoc filer
3) Type feilhåndtering
4) Log level

Et eksempel:
```zsh
./gradlew run --args="--repo=/Users/torsteinnesby/gitHub/navikt/pensjon-regler --output=/Users/torsteinnesby/tmp/AsciiDocs --allow_empty_flow=true --log=WARN"
```

## Introduksjon
Det overordnede formålet er å automatisere kode-til-dokumentasjon for `pensjon-regler` repo:
1) Bruk av `embeddable compiler` for å oversette relevante kotlin filer til internformatet `Program Structure Interface (PSI)`
2) Ekstrahere relevante PSI elementer, regeltjenester, regelflyt og regelsett, inkludert deres flyt og relasjoner, til tilsvarende modell komponenter
3) Generere dokumentasjon (flytskjema (SVG) og gjenbruk av KDOC) på formatet [AsciiDoc](https://asciidoc.org/) for relevante PSI elementer
4) Bidrag til [PO Pensjon-systemdokumentasjon](https://pensjon-dokumentasjon.intern.dev.nav.no/pen/index.html)

## Teknisk

I proof-of-concept fasen er programmet en enkel cli applikasjon. Veien videre er ukjent.

PSI tilbyr ulike mekanismer for finne relevante elementer med PSUtil og `visitor pattern` metoder.
Se
- [Program Structure Interface (PSI)](https://plugins.jetbrains.com/docs/intellij/psi.html).
- [PSI og kotlin typer](https://github.com/JetBrains/kotlin/tree/master/compiler/psi/src/org/jetbrains/kotlin/psi)

Det er også greit å ta en titt på [intellij Platform API](https://plugins.jetbrains.com/docs/intellij/explore-api.html).

## Struktur

`embeddable.compiler`
- `Compiler.kt` - opprette et `compiler miljø` for å analysere kotlin filer.
- `MessageCollerSummary` - for meldinger når binding context opprettes
- `PsiKtFunctions.kt` - hjelpefunksjoner for å hente ut relevante PSI elementer.

`org.example`
- `GenerateAsciiDoc.kt` - opprette AsciiDoc

`repository`
- `Repository.kt` - Funksjoner for repo `pensjon-regler` 

`result.addons`
- `Functions.kt` - Et par tillegg til `Result`

`rule.dsl` forenklet DSL fra det DSL som `pensjon-regler` bruker for å definere regler og regelflyt.
- `Model` Modellkomponenter utledet fra DSL som skal konverteres til AsciiDoc
  - `RuleInfo.kt` - interface for Service, Flow og Set
  - `RuleServiceInfo.kt` - informasjon om Regeltjeneste
  - `RuleFlowInfo.kt` - informasjon om Regelflyt
  - `RuleSetInfo.kt` - informasjon om RegelSett
  - `PropertyInfo.kt` - informasjon om parametere
  - `FlowElement.kt` - informasjon om flyt elementer, eks. referanse til flyt og regel sett
- `DSLTypeAbstractOrService.kt` - interface for DSL Regeltjeneste, -flyt og -sett, samt Request/Response
- `DSLTypeFlow.kt` - DSL for flyt
- `DSLTypeBranch.kt` - DSL for `forgrening` i flyt

`pensjon-regler`
- `Repo.kt` - en enkel modell av `pensjon-regler` repo, relevante source roots og kotlin filer.
- `Model.kt` - hvilke informasjonselementer som skal ekstraheres fra PSI
- `Extractor.kt` - bruker repo, compiler miljø og dsl for å ekstrahere relevante informasjonselementer fra PSI til modell.

`asciidoc` - tbd

## `Eksterne avhengigheter behøves ikke per nå`

Repo `pensjon-regler` har mange avhengigheter. En enkel måte å laste ned alle avhengigheter er å bruke `maven` og `dependency:copy-dependencies` målet.
```zsh
mvn dependency:copy-dependencies -DoutputDirectory=/Users/torsteinnesby/tmp/Libs
```
Da er det ganske lett å laste opp samtlige avhengigheter som `JVMClasspathRoots` som gir en mer fullstendig `BindingContext`.

Alternativt kan man bruke lokal maven repository i `.m2`.



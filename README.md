# psi-parser

## Kjøre programmet

Programmet tar 2 argumenter:
1) Filsti til pensjon-regler repo root
2) Filsti til output mappe for AsciiDoc filer

```zsh
./gradlew run --args="/Users/torsteinnesby/gitHub/navikt/pensjon-regler /Users/torsteinnesby/tmp/AsciiDocs"
```

## Introduksjon
Det overordnede formålet er å automatisere kode-til-dokumentasjon for `pensjon-regler` repo:
1) Bruk av `embeddable compiler` for å oversette relevante kotlin filer til internformatet `Program Structure Interface (PSI)`
2) Ekstrahere relevante PSI elementer, regeltjenester, regelflyt og regelsett, inkludert deres flyt og relasjoner
3) Generere dokumentasjon på formatet [AsciiDoc](https://asciidoc.org/) for relevante PSI elementer
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
- `CompilerContext.kt` - opprette et `compiler miljø` for å analysere kotlin filer.
- `PsiKtFunctions.kt` - hjelpefunksjoner for å hente ut relevante PSI elementer.

`rule.dsl`
- `DSL.kt` - forenklet DSL fra det DSL som `pensjon-regler` bruker for å definere regler og regelflyt.

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

Alternativt kan man bruke maven `m2`.



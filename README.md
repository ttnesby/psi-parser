# psi-parser

## Kjøre programmet

Programmet tar 3 argumenter:
1) Filsti til pensjon-regler repo root
2) Filsti til output mappe for AsciiDoc filer

```zsh
./gradlew run --args="/Users/torsteinnesby/gitHub/navikt/pensjon-regler /Users/torsteinnesby/tmp/AsciiDocs"
```

## Introduksjon
Det overordnede formålet er å automatisere kode-til-dokumentasjon for `pensjon-regler` repo:
1) Bruk av `embeddable compiler` for å oversette kotlin kode til internformatet `Program Structure Interface (PSI)`
2) Ekstrahere relevante PSI elementer, regeltjenester, regelflyt og regelsett og deres flyt og relasjoner
3) Generere dokumentasjon på formatet [AsciiDoc](https://asciidoc.org/) for relevante PSI elementer
4) Bidrag til [PO Pensjon-systemdokumentasjon](https://pensjon-dokumentasjon.intern.dev.nav.no/pen/index.html)

## Teknisk

I proof-of-concept fasen er programmet en enkel cli applikasjon. Veien videre er ukjent.

### Embeddable Compiler

Programmatisk bruk av embeddable compiler krever et visst konfigurasjon og opprettelse avhengig av behovet.
Med et `enkelt` behov kan generering av compiler mijøet være `createForTests` som gir raskere initialisering og mindre minnebruk.
En `configFiles` parameter til nevnte metode bestemmer hvor mye PSI funkasjonalitet som blir tilgjengelig.
`METADATA_CONFIG_FILES` gir enkel PSI mulighet per fil, men ingen navigering på tvers av filer i ulike moduler.

Ekstrahering av relevante PSI elementer og deres relasjon (user type reference med resolve) krever `createForProduction` og `JVM_CONFIG_FILES`.

Oppsett av embeddable compiler:
1) Konfigurasjon
2) Opprettelse av compiler miljøet
3) Legge til eventuelle relevante avhengigheter som `JVMClasspathRoots`
3) Analyse av kotlin filer
4) Klar for ekstrahering av relevante elementer gjennom PSI parsing

### Konfigurasjon og Analyse

Forutsetningen for en enkel navigering mellom PSI filer basert på avhengigheter, er full analyse og tilhørende `BindingContext`.
En `god nok` bindingContext forutsetter færrest mulig feil. Per nå er antall feil og advarsler så godt som det kan bli.

#### `Eksterne avhengigheter behøves ikke per nå`

Repo `pensjon-regler` har mange avhengigheter. En enkel måte å laste ned alle avhengigheter er å bruke `maven` og `dependency:copy-dependencies` målet.
```zsh
mvn dependency:copy-dependencies -DoutputDirectory=/Users/torsteinnesby/tmp/Libs
```
Da er det ganske lett å laste opp samtlige avhengigheter som `JVMClasspathRoots` som gir en mer fullstendig `BindingContext`.

Alternativt kan man bruke maven `m2`.




### Program Structure Interface (PSI)

PSI tilbyr ulike mekanismer for finne relevante elementer med PSUtil og `visitor pattern` metoder.
Se
- [Program Structure Interface (PSI)](https://plugins.jetbrains.com/docs/intellij/psi.html).
- [PSI og kotlin typer](https://github.com/JetBrains/kotlin/tree/master/compiler/psi/src/org/jetbrains/kotlin/psi)

Det er også greit å ta en titt på [intellij Platform API](https://plugins.jetbrains.com/docs/intellij/explore-api.html).

## Relevante elementer

Eksempler på `relevante elementer`;
- Tjenestenivå, tjenester basert på `AbstractRuleService<T>`, f.eks. `FastsettTrygdetidService`
- Flytnivå, flyter basert på 'AbstractRuleflow<T : Any>', f.eks. `StartTrygdetidFlyt`
- Diverse subflyter, forgreining, gren
- Regelsett
- ...

## Tjenestenivå

Konkret regeltjeneste: ønsker å finne alle `children` av `AbstractRuleService<T>` med vilkårlig T.

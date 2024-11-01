# psi-parser

Det overordnede formålet er å automatisere kode-til-dokumentasjon for `pensjon-regler` repo:
1) Bruk av `embeddable compiler` for å oversette kotlin kode til internformatet `Program Structure Interface (PSI)`
2) Ekstrahere relevante PSI elementer (regeltjenester, regelflyt, regelsett, regel og javaDoc) og deres relasjon
3) Generere dokumentasjon på formatet [AsciiDoc](https://asciidoc.org/) med flyttegninger
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
3) Analyse av kotlin moduler og filer
4) Klar for ekstrahering av relevante elementer gjennom PSI parsing

### Program Structure Interface (PSI)

PSI tilbyr ulike mekanismer for finne relevante elementer gjennom PSUtil mange `visitor pattern` metoder.
Se [Program Structure Interface (PSI)](https://plugins.jetbrains.com/docs/intellij/psi.html).

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

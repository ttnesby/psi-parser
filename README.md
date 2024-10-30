# psi-parser

Rammen er konseptuell enkel, kotlin cli program som har en embedded kotlin compiler.
Håper embedded compiler har nødvendig PSI funksjonalitet for å parse og isolere relevante elementer fra `pensjon-regler`.

## Kort om PSI

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

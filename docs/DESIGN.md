# EXTREME PROGRESSION — Ontwerpdocument

> Werktitel. De definitieve naam is nog niet gekozen.
> Status: **Interview ronde 14 (fase 5: skill 6/10, Farming). Er is nog geen gameplaycode; het ontwerp is nog niet goedgekeurd.**

Legenda per mechaniek:
- ✅ **Bevestigd**: door de eigenaar goedgekeurd
- 💡 **Voorstel**: idee van de ontwerper, nog niet goedgekeurd
- ❓ **Open**: vraag die nog beantwoord moet worden

---

## 0. Interviewlog

| Ronde | Onderwerp | Status |
|------:|-----------|--------|
| 1 | Platform & architectuur + opzet skillsysteem | ✅ beantwoord |
| 2 | Samenwerking met Extreme Dimensions, skilllijst, synergieën, trade-offs | ✅ beantwoord |
| 3 | Skill-sjabloon: tegenwerking, actieve abilities, specialisaties, ontdekken | ✅ beantwoord |
| 4 | Fase 3: schaal, totaalbudget, tempo, specialisatiemomenten, tegenpolen | ✅ beantwoord |
| 5 | Fase 3 afronden: getallen voor tempo, plafond, Mastery, respec, tegenpolen | ✅ alles akkoord |
| 6 | Fase 4 deel 1: wat een dood kost, welke skills, herstel, vangnet, Extreme Dimensions | ✅ beantwoord |
| 7 | Fase 4 deel 2: herhaalde doden, dimensies, vermoeidheid, Mastery, PvP | ✅ beantwoord |
| 8 | Fase 5: opbouw van de trees, punten, ranks, cooldowns, afleren | ✅ beantwoord |
| 9 | Skill 1/10: Melee | ✅ alles akkoord |
| 10 | Skill 2/10: Ranged (+ drie specialisaties per skill) | ✅ alles akkoord |
| 11 | Skill 3/10: Defense | ✅ akkoord |
| 12 | Skill 4/10: Mining | ✅ akkoord |
| 13 | Skill 5/10: Foraging | ✅ akkoord |
| 14 | Skill 6/10: Farming | ❓ wacht op antwoorden |

---

## 1. Projectarchitectuur

### Bevestigd
- ✅ **Optie A**: een Fabric-mod die op de server **en** op elke client verplicht is. De client toont een volledig eigen GUI (geen kist-menu's).
- ✅ Het menu opent met een **sneltoets** en is **ook bereikbaar vanuit het inventory-scherm**.
- ✅ Nieuwe items en blokken **alleen als het echt noodzakelijk is**. Uitgangspunt: vanilla-mechanieken versterken.
- ✅ **Minecraft 26.3, Fabric** (Java 25), dezelfde stack als Extreme Dimensions.
- ✅ **Zelfstandig**: de mod werkt zonder Extreme Dimensions. **Optionele koppeling** met ED is gewenst.
  - 💡 Technisch: ED komt in `fabric.mod.json` alleen onder `suggests`, niet onder `depends`. Alle koppelcode staat apart (`compat/extremedimensions`) en draait alleen als `FabricLoader.isModLoaded("extremedimensions")` waar is. Zonder ED wordt die code nooit geladen.
  - ❓ Wat de koppeling precies doet, bespreken we later (XP in de Seam/Nether/End, samenspel van de doodsstraffen).

### Huidige stand van de repo (onderzocht op 2026-10-30)
- `Peertjeh/In-Game-MMO`: 1 commit, alleen `README.md` ("MMO With GUI"). Greenfield: geen code, build, GUI of opslag.

### Referentie 1: Extreme Dimensions (`Peertjeh/extremedimensions`, v3.7.0)
Dit is de stijl- en conventiereferentie, van dezelfde auteur.
- **Stack:** Minecraft **26.3**, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18 met `splitEnvironmentSourceSets` (`src/main` en `src/client`), **Java 25**, Gradle.
- **Tests & CI:** server-gametests en een client-gametest met screenshots in GitHub Actions; compatibiliteitsrun met Sodium/Iris/Mod Menu; automatische release per versie met `CHANGELOG.md`.
- **Talen:** `en_us.json` + `nl_nl.json`. `checkTranslations` dwingt af dat `nl_nl` compleet is en dezelfde placeholders gebruikt.
- **GUI:** eigen client-`Screen`s (Journal, Curses, Status, Trials, ...), allemaal getekend via `client/ui/Ui.java`. Die huisstijl is die van vanilla zelf: panelen als de tooltip-box (bijna zwart violet, verlopend violet kader), de 16 chatkleuren, platte meters als een bossbar, een kleurenblind-optie en een optie voor minder flitsen.
- **Inventory:** knoppen rechts van het receptenboek. Journal op `boek + 2`, Curses op `boek + 24` (20×18 px); ze schuiven mee als het boek opent.
- **Sneltoetsen:** in een eigen categorie, standaard **niet** toegewezen (`InputConstants.UNKNOWN`).
- **Netwerk:** de server is leidend. De client vraagt met een packet, de server stuurt de staat, de client opent het scherm.
- **Opslag:** per wereld één JSON-bestand (`<wereld>/extremedimensions.json`, Gson). Wijzigingen worden als "dirty" gemarkeerd en samen met de wereld opgeslagen. Oude saves worden aangevuld (`filled`), en `MigrationTests` laadt saves van oudere versies.
- **Bestaand doodssysteem:** Collapse (Tether leeg) → 1 van 3 curses kiezen; 92 curses + 24 Abyssal; atonement, scars, Mending Cloth; "elke dood maakt de helft van de atonement ongedaan". **Relevant voor fase 4 (stapeling van straffen).**

### Referentie 2: mcMMO (alleen ontwerpreferentie, geen dependency)
Bron: `mcMMO-Dev/mcmmo-wiki-repo` (documentatie, geen broncode).
- Spigot/Paper/Folia-plugin, MC 1.20.5+. **Niet bruikbaar als codebasis.**
- 19 skills: combat (Swords, Axes, Archery, Crossbows, Maces, Spears, Tridents, Unarmed, Taming), gathering (Mining, Woodcutting, Excavation, Herbalism, Fishing), passief (Acrobatics), utility (Alchemy, Repair) en child skills (Salvage, Smelting).
- Levels 0–100 of 0–1000 (RetroMode). XP-curve: lineair `1020 + 20 × level`, of exponentieel `0.1 × level^1.8 + 2000`.
- Sub-skills met vaste ranks, plus één actieve "Super Ability" per skill (rechtsklik met tool).
- Child skills: level = gemiddelde van twee parents.
- Hardcore: X% van de levels boven een drempel kwijt (standaard 75%), geen herstel.
- Vampirism: bij PvP steelt de doder levels.
- Anti-exploit: geplaatste blokken geven geen XP, spawner/egg/portal-mobs geven 0 XP, diminishing returns, fishing-bewegingscheck, XP-plafond voor combat.
- Interface: chatcommando's en een XP-bossbar.

Zwaktes die we bewust oplossen:
1. Wapenskills overlappen. → ✅ opgelost met **skills per speelstijl**.
2. Geen keuzes. → specialisaties (fase 5).
3. Hardcore is bot en ondoorzichtig. → fase 4.
4. Geen echte interface. → ✅ eigen client-GUI.

### Voorstellen (op basis van Extreme Dimensions)
- 💡 **Zelfde stack:** MC 26.3, Fabric, Java 25, dezelfde Fabric API-versie. Zo draaien beide mods op dezelfde server.
- 💡 **Zelfde conventies:** split source sets, gametests in CI, Gson-JSON per wereld met migratietests, `en_us` + `nl_nl` met vertaalcheck, CHANGELOG-releases.
- 💡 **Zelfde visuele taal:** een eigen `Ui`-klasse in dezelfde vanilla-stijl, zodat beide mods als één spel aanvoelen. Een eigen accentkleur per skillcategorie.
- 💡 **Inventory-knop:** rechts naast de knoppen van Extreme Dimensions (`boek + 46`), of, als ED ontbreekt, direct naast het boek. Nooit overlappen.
- 💡 **Sneltoets:** standaard `K`, aan te passen in Controls.

---

## 2. Skills

### Bevestigd
- ✅ **Compacte set.** Elke skill heeft een eigen tree, een eigen GUI-pagina en een eigen XP-tabel.
- ✅ **Skills per speelstijl**, niet per wapen of tool.
- ✅ **Samenhang via synergieën die de speler zelf ontdekt.** Hier en daar een **harde vereiste** (optie D).
- ✅ **De 10 skills hieronder zijn goedgekeurd.** De specialisaties in de tabel zijn nog **voorbeelden** (💡); die worden per skill uitgewerkt.
- ✅ **Onontdekte synergieën** zijn zichtbaar als **runen met een vage hint** (optie B), zoals de onbekende curses in ED.
- ✅ **Ontdekken is server-breed én persoonlijk** (eigenaar: "server-breed is lache, maar ik wil ook iets persoonlijks").
  - ✅ Uitwerking (goedgekeurd in ronde 4):
    - **Persoonlijk:** elke speler moet een synergie zelf ontdekken voordat hij voor hem werkt. Je eigen codex toont wanneer en waar je hem vond.
    - **Server-breed:** de eerste ontdekker komt in de **kroniek** van de server (een GUI-pagina), eventueel met een aankondiging in de chat (instelbaar). Na die eerste ontdekking wordt de rune-hint voor de anderen duidelijker (een deel wordt leesbaar).
    - **Eerste ontdekker:** krijgt een blijvende, puur cosmetische vermelding ("Eerste ontdekker") bij die synergie. Geen extra kracht.
- ✅ **Specialisaties hebben altijd trade-offs**: elke keuze heeft een prijs.
- ✅ **Skills mogen elkaar tegenwerken**, via:
  - ✅ **a) Uitrustingsconflicten** (wat je draagt of vasthoudt bepaalt welke bonussen werken)
  - ✅ **b) Specialisatieconflicten** (sommige specialisaties sluiten elkaar uit over skills heen)
  - ✅ **c) Tegenpolen**, en die moeten **harder straffen** dan het eerste voorstel (−20%, pas vanaf level 50). Dat voorstel is afgewezen.
    - ✅ Uitwerking (goedgekeurd in ronde 5):
      - Paren: **Melee ↔ Ranged**, **Smithing ↔ Arcana**, **Defense ↔ Exploration**.
      - De straf begint zodra de tegenpool **boven level 25** zit én hoger is dan deze skill.
      - Hij groeit met het verschil in levels, tot **−60% XP** bij 50 levels verschil.
      - Daarnaast werken de **passieve bonussen** van de zwakkere skill maar voor **75%**.
      - Altijd zichtbaar in de GUI.
  - ✅ **d) Totaalbudget: verplicht, in de hybride vorm** (zie §4).

### De 10 skills (✅ lijst goedgekeurd; specialisaties nog 💡)

| # | Skill | Categorie | Speelstijl | XP vooral uit | Mogelijke specialisaties (voorbeeld) |
|--:|-------|-----------|------------|---------------|--------------------------------------|
| 1 | **Melee** | Combat | Alles op korte afstand: zwaard, bijl, mace, speer, trident, vuisten | Schade aan vijandige mobs/spelers, gewogen naar gevaar | Blade (parry, bleed) · Crusher (armor breken, stagger) · Brawler (snelheid, combo's) |
| 2 | **Ranged** | Combat | Boog, kruisboog, gegooide trident | Raak schieten; meer voor afstand en bewegende doelen | Sniper (afstand, zwakke plekken) · Skirmisher (bewegen en schieten) |
| 3 | **Defense** | Combat | Klappen opvangen en overleven | Schade geblokt met schild of opgevangen door armor (alleen vijandige bronnen) | Bulwark (schild) · Juggernaut (zwaar armor, trager) · Resilience (herstel, effecten weerstaan) |
| 4 | **Mining** | Gathering | Ondergronds: houweel en schep | Ertsen (zeldzaam = meer), steen, diepte (géén dimensiebonus, zie §7) | Prospector (ertsen) · Excavator (schep, archaeology) · Tunneler (snelheid) |
| 5 | **Foraging** | Gathering | De wilde natuur: bomen en wilde planten | Natuurlijke logs, wilde bessen, paddenstoelen, bloemen | Lumberjack (bomen vellen) · Gatherer (wilde opbrengst) |
| 6 | **Farming** | Gathering | Verbouwen en verzorgen: gewassen en dieren | Volgroeide oogst, fokken, scheren, temmen | Grower · Herder · Beastmaster (getemde dieren sterker) |
| 7 | **Fishing** | Gathering | Op en aan het water | Vangsten (zeldzamer = meer), schatten | Angler · Treasure Hunter |
| 8 | **Exploration** | Survival | Reizen en beweging | Nieuwe chunks, biomen en structuren ontdekken, overleefde vallen, zwemafstand | Wanderer (beweging) · Survivalist (honger, omgeving) |
| 9 | **Smithing** | Crafting | Metaal en uitrusting: oven, aambeeld, smithing table | Ertsen smelten, repareren, upgraden | Forgemaster · Tinkerer |
| 10 | **Arcana** | Crafting | Brouwen en enchanten | Complexere drankjes, hogere enchants | Alchemist · Enchanter |

Keuzes in dit voorstel:
- Woodcutting en Herbalism zijn verdeeld in **wild** (Foraging) en **verbouwd** (Farming). Dat is een duidelijker verschil in speelstijl.
- Taming valt onder Farming (Beastmaster).
- Acrobatics en Exploration zijn samengevoegd tot Exploration.
- Brewing en Enchanting zijn samengevoegd tot Arcana.
- Excavation valt onder Mining (Excavator).
- Geen aparte Cooking-skill (in vanilla is koken alleen smelten). Voedselefficiëntie kan onder Farming of Exploration vallen.

### Skill 1/10: Melee (✅ goedgekeurd in ronde 9)
**Identiteit:** vechten van dichtbij. Melee beloont timing en goed gekozen wapens, niet klikspam.

**XP:**
- XP = toegebrachte schade × gevarenfactor van het doelwit.
  - De schade telt maximaal tot de resterende levens van het doelwit (geen XP voor overkill).
  - Voorbeelden van gevarenfactoren: zombie 1,0 · skelet 1,2 · creeper 1,5 · enderman 1,5 · wither-skelet 2,0 · warden en bazen 3,0.
- **Alleen volledig opgeladen klappen** (aanvalsbalk ≥ 90%) geven XP. Klikspam en macro's leveren dus niets op.
- Passieve dieren geven geen XP. Mobs uit spawners of spawn-eggs geven geen XP (details in fase 7).
- ✅ **PvP-schade geeft ook XP.** Maatregelen tegen farmen met een tweede account volgen in fase 7.

**Stam** (gratis, vanzelf vrij):
| Level | Bonus |
|---|---|
| 5 | Gevechtsritme: de aanvalsbalk laadt 3% sneller |
| 10 | Volle zwaai: volledig opgeladen klappen doen +5% schade |
| 15 | Vaste voet: 10% minder knockback terwijl je aanvalt |
| 20 | Genadeslag: kritieke klappen (in de val) doen +10% schade |

**Specialisaties** (eerste op 25, tweede op 75):
| | **Blade** | **Crusher** | **Brawler** |
|---|---|---|---|
| Wapens | Zwaard, speer, trident (van dichtbij) | Bijl, mace | Vuisten (lege hand) |
| Speelstijl | Precisie, bloeden, pareren | Zware klappen, armor breken, wankelen | Snelheid, combo's, ontwapenen |
| Actieve ability | **Riposte**: 3 seconden lang wordt de eerstvolgende klap die jou raakt volledig gepareerd, en je slaat terug met een zekere crit plus bloeden. Cooldown 4 min. | **Shatter**: je volgende volle klap binnen 5 seconden geeft een schokgolf. Die raakt alle vijanden binnen 3 blokken, slaat ze terug, en zet hun armor 8 seconden op 0. Cooldown 5 min. | **Flurry**: 6 seconden lang negeer je de aanvalsbalk, en elke klap bouwt je combo op. Cooldown 4 min. |
| Trade-off | −15% schade tegen doelwitten met zware armor | −10% aanvalssnelheid met **alle** wapens | Werkt alleen met een lege hand **en** zonder borstplaat van ijzer of zwaarder |
| Voorbeeld keuzeknoop | Diepe snede (langer bloeden) **of** Snelle snede (+aanvalssnelheid) | Aardbeving (mace-smash met een groter bereik) **of** Kloof (bijl houdt schilden langer uitgeschakeld) | IJzeren vuist (+schade) **of** Ontwapenen (kans dat een speler zijn wapen laat vallen) |

**Mastery-perks** (voorbeeld, 1 van 2 per rank): +1% schade tegen bazen **of** +1% schade tegen spelers.

**Tegenwerking:**
- Tegenpool: **Ranged** (zie §2).
- Uitrustingsconflict: Brawler (zie de trade-off hierboven).
- Conflicten tussen specialisaties van verschillende skills leggen we vast als alle skills ontworpen zijn.

**Geen nieuwe items nodig.**

### Skill 2/10: Ranged (✅ goedgekeurd in ronde 10)
**Identiteit:** vechten op afstand. Ranged beloont geduld, positie en goed mikken.

**XP:**
- XP = toegebrachte schade × gevarenfactor × **afstandsbonus**.
  - De afstandsbonus is +2% per blok boven de 10 blokken, tot maximaal +60% (op 40 blokken).
  - De schade telt maximaal tot de resterende levens van het doelwit.
  - De gevarenfactoren zijn dezelfde als bij Melee.
- **Alleen een volledig gespannen boog** (≥ 90%) geeft XP. Een kruisboog is altijd volledig geladen.
- PvP-schade telt, net als bij Melee.
- Geen XP van passieve dieren of mobs uit spawners.

**Stam** (gratis, vanzelf vrij):
| Level | Bonus |
|---|---|
| 5 | Vaste hand: je spant de boog 5% sneller |
| 10 | Pijlen terug: 10% kans om je pijlen terug te vinden bij een gedode mob |
| 15 | Scherp oog: volledig gespannen schoten doen +5% schade |
| 20 | Windlezer: pijlen vliegen 5% sneller (minder valhoek) |

**Specialisaties:**
| | **Sniper** | **Skirmisher** | **Harpooner** |
|---|---|---|---|
| Wapens | Boog, kruisboog | Boog, kruisboog | Gegooide trident |
| Speelstijl | Afstand, kopschoten, doorboren | Bewegen en schieten, snel herladen | Werpen, binnentrekken, water en regen |
| Actieve ability | **Deadeye**: inzoomen zoals een verrekijker. Je volgende volle schot binnen 8 seconden is een zekere crit en doorboort 1 doelwit; een kopschot doet +100%. Cooldown 4 min. | **Volley**: 5 seconden lang schiet elk schot 3 pijlen in een waaier, voor de prijs van 1 pijl. Cooldown 4 min. | **Harpoon**: je volgende geworpen trident trekt het doelwit naar je toe. Cooldown 4 min. |
| Trade-off | −20% schade op doelwitten dichterbij dan 8 blokken | −20% schade op doelwitten verder dan 20 blokken | Boog en kruisboog doen −15% schade |
| Voorbeeld keuzeknoop | Doorboren (+1 doelwit) **of** Kopschot (+schade op het hoofd) | Snel herladen (kruisboog −25% laadtijd) **of** Rennend schieten (niet vertragen tijdens het spannen) | Terugkeer (de trident komt ook zonder Loyalty terug, maar langzaam) **of** Getijdenkracht (+schade in water of regen) |

**Mastery-perks** (voorbeeld, 1 van 2 per rank): +1% schade tegen vliegende mobs **of** +1% schade tegen spelers.

**Tegenwerking:**
- Tegenpool: **Melee**.
- Harpooner: een trident van dichtbij valt onder Melee (Blade); een geworpen trident onder Ranged.

**Geen nieuwe items nodig.**

### Skill 3/10: Defense (✅ goedgekeurd in ronde 11)
**Identiteit:** klappen opvangen en overleven. Defense is de skill van wie vooraan staat.

**XP:**
- XP = schade die je **schild blokt** of die je **armor opvangt**, × de gevarenfactor van de aanvaller.
  - Bij armor telt het verschil tussen de ruwe schade en de schade die je echt krijgt.
- Alleen schade van **vijandige mobs en spelers** telt.
  - Omgevingsschade (vallen, vuur, lava, verdrinken, cactus) geeft geen Defense-XP.
  - Valschade hoort bij Exploration (zie skill 8).
- Tegen AFK-farmen (stilstaan voor een spawner in volle armor):
  - Je krijgt alleen XP als je de afgelopen seconden zelf hebt aangevallen of bewogen.
  - Eén aanvaller geeft maximaal 6 keer XP, en daarna weer na 60 seconden zonder klappen van die aanvaller. Dat werkt zoals de dodge-regel van mcMMO; details in fase 7.

**Stam** (gratis, vanzelf vrij):
| Level | Bonus |
|---|---|
| 5 | Schildreflex: je schild staat 1 tick sneller klaar (vanilla wacht 5 ticks) |
| 10 | Gehard: 3% minder schade van vijandige mobs |
| 15 | Op adem komen: natuurlijke regeneratie begint 2 seconden eerder na een gevecht |
| 20 | Standvastig: 5% knockback-weerstand |

**Specialisaties:**
| | **Bulwark** | **Juggernaut** | **Resilience** |
|---|---|---|---|
| Uitrusting | Schild | Zware armor (ijzer en zwaarder) | Alles: herstel in plaats van pantser |
| Speelstijl | Blokken, terugkaatsen, anderen beschermen | Klappen incasseren, niet wijken | Effecten afschudden, snel herstellen |
| Actieve ability | **Bastion**: 6 seconden lang blokt je schild uit **alle** richtingen, en bondgenoten tot 3 blokken achter je krijgen 30% minder schade. Cooldown 4 min. | **Unbreakable**: 8 seconden lang geen knockback en 40% minder schade, maar je kunt niet sprinten. Cooldown 5 min. | **Second Wind**: in één keer alle negatieve effecten weg, plus 6 harten herstel over 5 seconden. Cooldown 5 min. |
| Trade-off | **Zonder schild** in je hand krijg je +10% schade | −10% loopsnelheid met 3 of meer stukken armor van ijzer of zwaarder | Je armor beschermt 10% minder |
| Voorbeeld keuzeknoop | Spiegelschild (25% van een geblokt projectiel kaatst terug) **of** Schildslag (blok je op het moment van de klap, dan staat de aanvaller 1 seconde stil) | Stekels (10% van de melee-schade gaat terug naar de aanvaller) **of** Anker (volledig knockback-vrij terwijl je sluipt) | Tegengif (immuun voor vergiftiging) **of** Koel bloed (je brandt 50% korter) |

**Mastery-perks** (voorbeeld, 1 van 2 per rank): +1% schadereductie tegen bazen **of** tegen spelers.

**Tegenwerking:**
- Tegenpool: **Exploration** (zwaar tegenover licht).
- Uitrustingsconflict: in zware armor (Juggernaut-stijl) werken de bewegingsbonussen van Exploration niet.
- Brawler (Melee) mag geen borstplaat van ijzer of zwaarder dragen en botst dus vanzelf met Juggernaut.

**Geen nieuwe items nodig.**

- ✅ **Groepsabilities mogen.** Abilities die ook bondgenoten helpen, zoals Bastion, zijn toegestaan.
- ✅ **XP-bron:** schade die je opvangt (schild of armor). Er komt geen aparte bonus voor overleven met weinig levens.

### Skill 4/10: Mining (✅ goedgekeurd in ronde 12)
**Identiteit:** ondergronds werk met houweel en schep: ertsen vinden, tunnels graven en de grond doorzoeken.

**XP:**
- XP per blok, uit een instelbare tabel.
  - Zeldzame ertsen geven veel, steen weinig.
  - Diepte zit in de tabel: deepslate en deepslate-ertsen geven meer dan hun gewone versie. Er is geen aparte dieptebonus en geen dimensiebonus.
- Met de schep: aarde, zand, grind, klei, modder en soul sand (weinig). **Suspicious sand en gravel borstelen** (archaeology) geeft veel.
- **Geplaatste blokken geven nooit XP.** Ook blokken uit een cobblestone-generator (lava + water) niet. Details in fase 7.

**Stam** (gratis, vanzelf vrij):
| Level | Bonus |
|---|---|
| 5 | Mijnwerkersritme: +3% breeksnelheid met houweel en schep |
| 10 | Stevig gereedschap: 5% kans dat een blok geen duurzaamheid kost |
| 15 | Grotkennis: 10% minder schade van druipsteen, magmablokken en vallend grind of zand |
| 20 | Dubbele vondst: 5% kans op een dubbele ertsdrop (Ancient Debris is uitgezonderd, instelbaar) |

**Specialisaties:**
| | **Prospector** | **Excavator** | **Tunneler** |
|---|---|---|---|
| Gereedschap | Houweel | Schep en borstel | Houweel |
| Speelstijl | Ertsen vinden en meer opbrengst | Graven, schatten en archaeology | Snelheid en grote tunnels |
| Actieve ability | **Ore Sense**: 10 seconden lang zie je ertsen binnen 12 blokken door de muren heen oplichten. De server stuurt alleen die posities, dus het is geen x-ray voor de hele wereld. Cooldown 5 min. | **Earthmover**: 8 seconden lang breekt je schep 3×3 blokken tegelijk. Cooldown 4 min. | **Tunnel Bore**: 8 seconden lang breek je 1×3 blokken (rank V: 3×3). Cooldown 4 min. |
| Trade-off | −10% breeksnelheid op gewone steen en deepslate | −15% breeksnelheid met een houweel | **Geen** kans op dubbele drops (ook niet die van de stam), en ertsen geven −25% XP |
| Voorbeeld keuzeknoop | Edelsteenoog (alleen diamant en smaragd, maar een groter bereik) **of** Breed spectrum (alle ertsen, kleiner bereik) | Schatgraver (meer vondsten in aarde, zand en grind) **of** Archeoloog (betere vondsten bij het borstelen) | Diepgang (deepslate breekt even snel als steen) **of** Duurzaam (50% minder slijtage tijdens Tunnel Bore) |

**Vondsten van Excavator** zijn alleen vanilla-items: vuursteen, kleiballen, botten, goudklompjes, pottery sherds en dergelijke.

**Mastery-perks** (voorbeeld, 1 van 2 per rank): +1% kans op dubbele erts **of** +1% breeksnelheid.

**Tegenwerking:** geen tegenpool. Verder gaat het via specialisatieconflicten, die volgen als alle skills ontworpen zijn.

**Geen nieuwe items nodig.**

- ✅ **Ore Sense** blijft zoals voorgesteld: ertsen door muren zien, binnen 12 blokken, met een cooldown van 5 min.
- ✅ **Dubbele drops** blijven, met Ancient Debris uitgezonderd.

### Skill 5/10: Foraging (✅ goedgekeurd in ronde 13)
**Identiteit:** de wilde natuur: bomen kappen en verzamelen wat vanzelf groeit.

**XP:**
- **Logs uit natuurlijke bomen**, per houtsoort uit een tabel. Zeldzame bomen geven meer, zoals jungle, cherry en pale oak.
- **Bomen die een speler zelf heeft geplant** geven **25% XP**. Zo blijft een boomfarm nuttig, maar niet de snelste weg. Instelbaar.
- **Wilde planten:** zoete bessen van wilde struiken, glow berries, natuurlijke paddenstoelen, bloemen, cacao uit de jungle, wilde meloenen en pompoenen, en honing en honingraat uit wilde bijenkorven.
- Geplaatste blokken en planten die een speler met bonemeal heeft laten groeien, geven niets.

**Stam** (gratis, vanzelf vrij):
| Level | Bonus |
|---|---|
| 5 | Houthakkersritme: +3% kapsnelheid op logs |
| 10 | Bladeren schudden: 10% meer kans op saplings, appels en stokken uit bladeren |
| 15 | Bosvoet: bessenstruiken prikken je niet meer |
| 20 | Dubbel hout: 5% kans op een dubbele log |

**Specialisaties:**
| | **Lumberjack** | **Gatherer** | **Herbalist** |
|---|---|---|---|
| Speelstijl | Bomen kappen | Wilde opbrengst verzamelen | Leven van wild voedsel |
| Actieve ability | **Tree Feller**: de eerstvolgende boom die je binnen 6 seconden raakt, valt in één keer om. Elke log kost duurzaamheid. Rank I: max 16 logs, rank V: max 64. Cooldown 4 min. | **Bountiful Harvest**: 10 seconden lang geven alle wilde planten 3× opbrengst. Cooldown 5 min. | **Wild Remedy**: 30 seconden lang geeft al het voedsel dat je eet dubbele verzadiging, plus 5 seconden Regeneration I. Cooldown 5 min. |
| Trade-off | Wilde planten geven −50% XP, en je krijgt er geen bonusopbrengst van | −15% kapsnelheid op logs | Verbouwd voedsel (brood, gewassen, vlees van gefokte dieren) vult 20% minder |
| Voorbeeld keuzeknoop | Breed kappen (Tree Feller neemt grotere bomen) **of** Spaarzaam (Tree Feller kost de helft aan duurzaamheid) | Zeldzame zaden (kans op torchflower seeds of pitcher pods uit gras) **of** Honingoogst (dubbele honing uit wilde korven, zonder dat de bijen boos worden) | Paddenstoelkenner (paddenstoelensoep geeft Regeneration) **of** Bessenkenner (bessen vullen +2 honger) |

**Mastery-perks** (voorbeeld, 1 van 2 per rank): +1% kans op een dubbele log **of** +1% kans op dubbele wilde opbrengst.

**Tegenwerking:** geen tegenpool. Herbalist botst inhoudelijk met Farming (verbouwd voedsel vult minder); een mogelijk specialisatieconflict volgt later.

**Geen nieuwe items nodig.** Torchflower seeds en pitcher pods zijn vanilla-items, die je normaal alleen via een sniffer krijgt.

- ✅ Herbalist is de derde specialisatie. Zelf geplante bomen geven 25% XP. Tree Feller mag hele bomen kappen.

### Skill 6/10: Farming (💡 voorstel, ronde 14)
**Identiteit:** verbouwen en verzorgen: gewassen, vee en getemde dieren.

**XP:**
- **Volgroeide gewassen** oogsten: tarwe, wortels, aardappels, bieten, nether wart, meloenen en pompoenen (van stengels), cacao, en geplante bessenstruiken.
  - Zeldzame gewassen (torchflower, pitcher plant) geven meer.
  - Suikerriet: alleen de bovenste blokken, niet de onderste.
  - Een onrijp gewas geeft niets.
- Gewassen die met **bonemeal** zijn gegroeid, geven **50% XP**. Instelbaar.
- **Automatisch geoogst** (zuigers, water, villagers) geeft niets. Alleen wat een speler zelf oogst telt.
- **Dieren:**
  - **fokken**: XP per jong;
  - **scheren**;
  - **temmen**: eenmalig per dier. Zeldzame dieren geven meer.
- Melken en eieren rapen geven geen XP, want die kun je eindeloos herhalen.
- **Schade die je getemde dieren doen**, geeft Farming-XP en geen Melee-XP. Daarvoor gelden dezelfde regels tegen farmen als bij Melee.

**Stam** (gratis, vanzelf vrij):
| Level | Bonus |
|---|---|
| 5 | Lichte tred: je vertrapt geen akkerland meer |
| 10 | Rijke oogst: 5% kans op een extra gewas |
| 15 | Opgroeien: jonge dieren die jij hebt gefokt, groeien 10% sneller |
| 20 | Boerenkost: voedsel van gewassen vult +1 honger |

**Specialisaties:**
| | **Grower** | **Herder** | **Beastmaster** |
|---|---|---|---|
| Speelstijl | Gewassen | Vee: fokken, wol, vlees en leer | Getemde dieren: wolven, katten, paarden, kamelen en dergelijke |
| Actieve ability | **Harvest Festival**: 10 seconden lang geven gewassen dubbele opbrengst en planten ze zichzelf opnieuw. Cooldown 4 min. | **Herding Call**: 10 seconden lang volgen dieren binnen 16 blokken je zonder voer, en je volgende kweek geeft een tweeling. Cooldown 4 min. | **Rally the Pack**: 15 seconden lang krijgen je getemde wolven binnen 24 blokken Strength en Resistance, en vallen ze jouw doelwit aan. Cooldown 5 min. |
| Trade-off | Fokken geeft −50% XP en geen extra bonussen | Gewassen geven geen extra opbrengst, ook niet die van de stam | Zelf doe je −10% melee-schade zolang een getemde wolf van jou binnen 16 blokken is |
| Voorbeeld keuzeknoop | Rijpe aura (gewassen binnen 4 blokken van jou groeien 15% sneller) **of** Zaadspaarder (je plant altijd vanzelf opnieuw) | Tweeling (grotere kans op een tweeling) **of** Wolhandel (dubbele wol en leer) | Roedelleider (de bonus geldt tot 3 wolven) **of** Ruiter (paarden en kamelen die jij berijdt, zijn 15% sneller en springen hoger) |

**Mastery-perks** (voorbeeld, 1 van 2 per rank): +1% gewasopbrengst **of** +1% schade van je getemde dieren.

**Tegenwerking:**
- Geen tegenpool.
- Herbalist (Foraging) botst inhoudelijk met Farming.
- Beastmaster ruilt eigen melee-kracht in voor die van zijn roedel.

**Geen nieuwe items nodig.**

### Voorbeelden van harde vereisten (D) (💡)
- Smithing: de netherite-upgrade-specialisatie vereist Mining 30.
- Arcana: enchants van het hoogste niveau vereisen ... (nog te bepalen)

### Tegenwerkende skills: de vier mechanismen (a, b, c licht en d gekozen, zie boven)
- **a) Uitrustingsconflicten:** wat je draagt of vasthoudt bepaalt welke bonussen werken. Voorbeeld: in zwaar armor werken de bewegingsbonussen van Exploration niet.
- **b) Specialisatieconflicten:** sommige specialisaties sluiten elkaar uit over skills heen. Voorbeeld: Juggernaut (Defense) en Wanderer (Exploration) kun je niet allebei hebben.
- **c) Tegenpolen:** XP in de ene skill maakt XP in de tegenpool trager. Voorbeelden: Melee ↔ Ranged, Smithing ↔ Arcana.
- **d) Totaalbudget:** hoe meer levels je in totaal hebt, hoe duurder elk volgend level wordt.

## 3. XP-generatie
_Nog niet besproken._

## 4. Levelprogressie & formules
### Bevestigd
- ✅ **Schaal: 0–100 per skill**, met een **Mastery-laag** na het voltooien (na level 100).
- ✅ **Totaalbudget: hybride.** Elk level wordt zacht duurder naarmate je totaal stijgt, en aan het eind ligt een hard plafond.
- ✅ **Tempo: VEEL langzamer** dan het eerste voorbeeld (100 uur tot level 100). Dat voorbeeld is afgewezen.
- ✅ **Respec: optie B.** Wisselen kan, maar het kost een flink deel van je voortgang in die skill plus een wachttijd.

### Getallen (✅ goedgekeurd in ronde 5)
**Tempo (de eerste skill, zonder budgetfactor):** `uren(L) = 400 × (L/100)^2.66`. De eerste levels gaan snel, zodat je het systeem meteen voelt. Daarna wordt de curve steil.

| Level | 1 | 5 | 10 | 25 | 50 | 75 | 100 |
|---|---|---|---|---|---|---|---|
| Cumulatieve speeltijd | seconden | ~8 min | ~1 uur | ~10 uur | ~63 uur | ~186 uur | ~400 uur |

De uren worden later omgerekend naar XP per actie (fase 7), op basis van hoe snel een gemiddelde speler die actie uitvoert.

**Hybride budget:** XP nodig = `basis(level) × (1 + (totaal/300)²)`, met een **hard plafond van 400 totale levels**. Mastery telt niet mee voor het plafond.

| Meesters na elkaar (elk 0 → 100) | 1e | 2e | 3e | 4e (plafond) |
|---|---|---|---|---|
| Uren voor die skill | ~425 | ~534 | ~731 | ~1017 |
| Cumulatief | ~425 | ~959 | ~1690 | ~2707 |

**Mastery:** 10 Mastery-ranks per skill, elk ≈ 40 uur. Bij elke rank kies je 1 van 2 kleine perks. Mastery is het eerste dat je verliest bij een dood (fase 4).

**Specialisaties:** de eerste kies je op **level 25** (≈ 10 uur), de tweede op **level 75** (≈ 186 uur).

**Respec (B):** wisselen kost **20% van de totale XP in die skill** (op level 100 zak je naar ~92, op level 75 naar ~69). Daarna moet je **7 echte dagen** wachten voordat je weer kunt wisselen. De nieuwe specialisatie begint op rank 1.

Ter referentie, de speeltijd van één level (zonder budgetfactor):

| Van level | 10 | 25 | 50 | 75 | 90 | 99 |
|---|---|---|---|---|---|---|
| Naar het volgende level | ~15 min | ~1,1 uur | ~3,4 uur | ~6,7 uur | ~9 uur | ~10,5 uur |

- ✅ **Geen afleren.** Wie het plafond van 400 haalt, zit vast aan zijn keuzes (ronde 8).
  - Alleen een dood kan levels laten zakken. Dat is geen bruikbare truc om ruimte vrij te maken: het verlies valt over alle skills (de hoogste het meest) en je kunt niet kiezen waar.

## 5. Skill-unlocks
- ✅ Specialisaties komen vrij op **level 25** en **level 75**. Mastery begint na **level 100** (zie §4 en §6).
- 💡 De verdere structuur (vaste unlocks of punten) volgt in ronde 8 (§6).

## 6. Abilities & skill trees
### Bevestigd
- ✅ Elke skill heeft een eigen tree (zie §2).
- ✅ Specialisaties hebben altijd trade-offs.
- ✅ **Eén actieve ability per specialisatie** (niet per skill).
- ✅ **Volledig passieve skills mogen**, maar ze mogen niet te sterk worden. Bij zo'n skill krijgt een specialisatie een sterkere passieve bonus met trade-off, in plaats van een actieve ability.
- ✅ **Specialisaties: optie B.** De eerste op **level 25**, de tweede op **level 75**.
  - ✅ Respec kost **20% van de totale XP in die skill**, plus **7 echte dagen** wachttijd. De nieuwe specialisatie begint op rank 1.
- ✅ **Mastery:** 10 ranks per skill na level 100, elk ≈ 40 uur. Bij elke rank kies je 1 van 2 kleine perks.
- ✅ Actieve abilities activeer je met een **ability-toets** (standaard `R`, aan te passen) terwijl je het juiste gereedschap vasthoudt. Niet met rechtsklik.

### Opbouw van elke tree (✅ ronde 8)
- ✅ **Stam en takken:**
  - Elke skill heeft een **stam** met gedeelde passieve bonussen. Die komen vanzelf vrij op levels 5, 10, 15 en 20.
  - Op level 25 kies je een tak (je specialisatie).
- ✅ **Punten:**
  - Vanaf level 25 krijg je **1 skillpunt per level**. Op level 100 heb je er dus 75.
  - Een knoop kost 1–3 punten. Een volledige tak kost meer dan 75 punten, dus je kunt nooit alles nemen.
  - Vanaf level 75 deelt je tweede specialisatie dezelfde puntenpot.
- ✅ **Soms keuzeknopen:** op enkele plekken in een tak kies je A **of** B.
- ✅ **Ability-ranks I–V** voor de actieve ability van een specialisatie, te kopen met punten.
- ✅ **Punten herverdelen** binnen dezelfde specialisatie kost **evenveel als een volledige respec**: 20% XP en 7 dagen.
- ✅ **Lange cooldowns:** 3–5 minuten, met een sterk en beslissend effect.
- ✅ **Twee actieve abilities** in één skill (na level 75): `R` voor de eerste specialisatie, `Shift + R` voor de tweede.
- ✅ **Punten bij levelverlies:** zak je onder een level, dan valt het laatst gekochte punt eerst weg. Wordt het level teruggewonnen, dan komt dat punt automatisch terug op dezelfde plek.
- ✅ **Drie specialisaties per skill.** Met maar twee is de tweede keuze op level 75 geen keuze meer. Met drie kies je op 75 echt (ronde 10).

## 7. Doodsstraffen
### Vaste principes (uit de opdracht, geen keuze)
- Doodsstraffen zijn **altijd zichtbaar**. De GUI toont per skill wat er "op het spel" staat.
- Na een dood toont een overzicht **precies** wat je verloren hebt, en dat het **permanent** is.
- Voortgang wordt **nooit stilletjes** weggehaald.

### Ontwerpgevolg van het trage tempo
- Level 100 kost ~400 uur, en één level rond level 75–99 kost 7–10 uur. Hele levels verliezen is dus zwaar.

### Bevestigd (ronde 6 en 7)
- ✅ **Verlieslagen (optie B)**, in deze volgorde:
  - (1) voortgang naar de volgende Mastery-rank
  - (2) XP binnen je huidige level (je zakt niet onder het begin van je level)
  - (3) hele Mastery-ranks of hele levels, alleen bij herhaalde doden
- ✅ **Alle skills** worden geraakt, **je hoogste skill het meest**:
  - Elke skill verliest `basis × (level van die skill / hoogste level)`.
  - Met een basis van **50%** van de XP binnen het huidige level verliest je hoogste skill 50%, een skill op de helft van dat level 25%, en een skill op een kwart 12,5%.
  - Voorbeeld: op level 90 (één level ≈ 9 uur) kost een dood je hoogste skill maximaal ~4,5 uur, en gemiddeld ~2,25 uur.
- ✅ **Doodsdruk:**
  - Elke dood verhoogt de druk met 1.
  - Per uur actief spelen zonder te sterven zakt de druk met 1.

  | Doodsdruk bij het sterven | Straf |
  |---|---|
  | 0 | Laag 1 + 2: basis **50%** |
  | 1 | Laag 1 + 2: basis **100%** |
  | 2 of meer | Zoals bij 1, plus laag 3 in je hoogste skill |

- ✅ **Mastery-ranks kun je verliezen**, inclusief de perk die je bij die rank koos.
  - 💡 Laag 3 neemt bij een skill met Mastery eerst **1 Mastery-rank**. Pas als er geen ranks meer zijn, gaat er **1 heel level** af.
- ✅ **Dimensies** vermenigvuldigen de straf: Overworld ×1, Nether ×1,5, End ×2, en met Extreme Dimensions de Seam ×2,5.
- ✅ **Gevaarlijke dimensies geven géén extra XP.** Het risico is hoger, de beloning niet.
- ✅ **Doodsvermoeidheid:** na een dood werken je passieve bonussen 10 minuten op 50%.
  - ✅ **Geen timer in beeld (HUD).** De resterende tijd staat **wel in de GUI** (en in het overzicht na de dood).
- ✅ **Geen vampirism.** Bij een PvP-dood verdwijnt het verlies gewoon; de doder krijgt niets.
- ✅ **Geen schim** of terughaalplek bij de plek van je dood.
- ✅ **Verlies is altijd permanent.** Er zijn geen herstelmechanieken. Je verdient het alleen terug door opnieuw XP te verdienen.
- ✅ **Geen mijlpaalvloeren.** Je kunt onder level 25 of 75 zakken. Je specialisatie wordt dan **onbruikbaar** tot je dat level terugwint.
  - 💡 De keuze blijft wel bewaard: wie het level terugwint, heeft de specialisatie meteen weer, zonder respec-kosten.
- ✅ **Extreme Dimensions: volledig stapelen (optie A).** Als beide mods draaien, gelden beide straffen volledig.

### Voorstellen (💡)
- **Beheer** (fase 9): alle straffen zijn instelbaar, met **presets** zoals de `Preset`-klasse van Extreme Dimensions. Jouw keuzes zijn de standaard.

### Open vragen
- ❓ Telt een **Collapse** in Extreme Dimensions (de Tether loopt leeg en je wordt thuis wakker) ook als dood voor deze mod? (koppeling met ED)

## 8. Herstelmechanieken
- ✅ **Geen herstelmechanieken.** Er is geen schim, geen uitdaging en geen item om verlies terug te halen. Verlies is permanent (ronde 7).

## 9. GUI-navigatie
- ✅ Openen met een sneltoets **en** vanuit het inventory-scherm.
- ✅ Een eigen client-scherm, geen kist-menu.
- 💡 De inventory-knop staat naast de ED-knoppen, de sneltoets is standaard `K` (zie §1).

## 10. Spelerdata
- 💡 Gson-JSON per wereld, net als Extreme Dimensions, met migratie en tests (wordt uitgewerkt in fase 8).

## 11. Configuratie
_Nog niet besproken._

## 12. Anti-exploit
- 💡 Alle XP wordt op de server berekend; de client toont alleen (volgt uit optie A).

## 13. Implementatieroadmap
_Pas na goedkeuring van het volledige ontwerp._

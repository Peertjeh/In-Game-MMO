# EXTREME PROGRESSION — Ontwerpdocument

> Werktitel. De definitieve naam is nog niet gekozen.
> Status: **Interview ronde 2. Er is nog geen gameplaycode; het ontwerp is nog niet goedgekeurd.**

Legenda per mechaniek:
- ✅ **Bevestigd**: door de eigenaar goedgekeurd
- 💡 **Voorstel**: idee van de ontwerper, nog niet goedgekeurd
- ❓ **Open**: vraag die nog beantwoord moet worden

---

## 0. Interviewlog

| Ronde | Onderwerp | Status |
|------:|-----------|--------|
| 1 | Platform & architectuur + opzet skillsysteem | ✅ beantwoord |
| 2 | Samenwerking met Extreme Dimensions, skilllijst, synergieën, trade-offs | ❓ wacht op antwoorden |

---

## 1. Projectarchitectuur

### Bevestigd
- ✅ **Optie A**: een Fabric-mod die op de server **en** op elke client verplicht is. De client toont een volledig eigen GUI (geen kist-menu's).
- ✅ Het menu opent met een **sneltoets** en is **ook bereikbaar vanuit het inventory-scherm**.
- ✅ Nieuwe items en blokken **alleen als het echt noodzakelijk is**. Uitgangspunt: vanilla-mechanieken versterken.

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

### Open vragen
- ❓ Draaien beide mods samen, en hoe sterk mogen ze gekoppeld zijn? (zelfstandig met optionele koppeling / harde afhankelijkheid / volledig los)
- ❓ Bevestiging: MC 26.3 + Fabric + Java 25?

---

## 2. Skills

### Bevestigd
- ✅ **Compacte set.** Elke skill heeft een eigen tree, een eigen GUI-pagina en een eigen XP-tabel.
- ✅ **Skills per speelstijl**, niet per wapen of tool.
- ✅ **Samenhang via synergieën die de speler zelf ontdekt.** Hier en daar een **harde vereiste** (optie D).

### Voorstel: 10 skills (💡, nog niet goedgekeurd)

| # | Skill | Categorie | Speelstijl | XP vooral uit | Mogelijke specialisaties (voorbeeld) |
|--:|-------|-----------|------------|---------------|--------------------------------------|
| 1 | **Melee** | Combat | Alles op korte afstand: zwaard, bijl, mace, speer, trident, vuisten | Schade aan vijandige mobs/spelers, gewogen naar gevaar | Blade (parry, bleed) · Crusher (armor breken, stagger) · Brawler (snelheid, combo's) |
| 2 | **Ranged** | Combat | Boog, kruisboog, gegooide trident | Raak schieten; meer voor afstand en bewegende doelen | Sniper (afstand, zwakke plekken) · Skirmisher (bewegen en schieten) |
| 3 | **Defense** | Combat | Klappen opvangen en overleven | Schade geblokt met schild of opgevangen door armor (alleen vijandige bronnen) | Bulwark (schild) · Juggernaut (zwaar armor, trager) · Resilience (herstel, effecten weerstaan) |
| 4 | **Mining** | Gathering | Ondergronds: houweel en schep | Ertsen (zeldzaam = meer), steen, diepte, gevaarlijke dimensies | Prospector (ertsen) · Excavator (schep, archaeology) · Tunneler (snelheid) |
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

### Voorbeelden van harde vereisten (D) (💡)
- Smithing: de netherite-upgrade-specialisatie vereist Mining 30.
- Arcana: enchants van het hoogste niveau vereisen ... (nog te bepalen)

### Open vragen
- ❓ Akkoord met deze 10, of schrappen, samenvoegen, toevoegen of hernoemen?
- ❓ Hoe verborgen zijn de synergieën?
- ❓ Hebben specialisaties echte nadelen (trade-offs)?

## 3. XP-generatie
_Nog niet besproken._

## 4. Levelprogressie & formules
_Nog niet besproken._

## 5. Skill-unlocks
_Nog niet besproken._

## 6. Abilities & skill trees
- ✅ Elke skill heeft een eigen tree (zie §2).
- _Rest nog niet besproken._

## 7. Doodsstraffen
- ❓ Hoe verhoudt dit zich tot de curses van Extreme Dimensions als beide mods draaien? (fase 4)

## 8. Herstelmechanieken
_Nog niet besproken._

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

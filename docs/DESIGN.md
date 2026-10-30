# EXTREME PROGRESSION — Ontwerpdocument

> Werktitel. De definitieve naam is nog niet gekozen.
> Status: **Interview fase 1/2. Er is nog niets goedgekeurd en er is nog geen gameplaycode.**

Legenda per mechaniek:
- ✅ **Bevestigd**: door de eigenaar goedgekeurd
- 💡 **Voorstel**: idee van de ontwerper, nog niet goedgekeurd
- ❓ **Open**: vraag die nog beantwoord moet worden

---

## 0. Interviewlog

| Ronde | Onderwerp | Status |
|------:|-----------|--------|
| 1 | Platform & architectuur + opzet skillsysteem | ❓ wacht op antwoorden |

---

## 1. Projectarchitectuur

### Huidige stand (fase 1, onderzocht op 2026-10-30)
- Repository `Peertjeh/In-Game-MMO`: 1 commit ("Initial commit"), alleen `README.md` ("MMO With GUI").
- Geen Minecraft-versie, modloader, build systeem, dependencies, GUI of opslag aanwezig: **greenfield**.
- Doel volgens de eigenaar: een **mod op Modrinth, client- én server-side**.

### Referentie: mcMMO (alleen als ontwerpreferentie, geen dependency)
Bron: `mcMMO-Dev/mcmmo-wiki-repo` (documentatie, geen broncode).
- Platform: Spigot/Paper/Folia-plugin, MC 1.20.5+. **Niet bruikbaar als codebasis voor een mod.**
- 19 skills, 5 groepen: combat (Swords, Axes, Archery, Crossbows, Maces, Spears, Tridents, Unarmed, Taming), gathering (Mining, Woodcutting, Excavation, Herbalism, Fishing), passief (Acrobatics), utility (Alchemy, Repair) en child skills (Salvage, Smelting).
- Levels 0–100 (Standard) of 0–1000 (RetroMode). XP-curve: lineair `1020 + 20 × level`, of exponentieel `0.1 × level^1.8 + 2000`.
- Sub-skills met vaste ranks (`skillranks.yml`), plus één actieve "Super Ability" per skill (rechtsklik met tool, cooldown).
- Child skills: level = gemiddelde van twee parent skills.
- Hardcore: verlies van X% van de levels boven een drempel (standaard 75%), per skill aan of uit. Geen herstel.
- Vampirism: bij PvP-kills steelt de doder een percentage van de levels van het slachtoffer.
- Anti-exploit: geplaatste blokken geven geen XP, spawner/egg/portal-mobs geven 0 XP, diminishing returns per tijdvenster, fishing-bewegingscheck, XP-plafond voor combat.
- Interface: vooral chatcommando's en een XP-bossbar. Geen echte GUI.

**Zwaktes van mcMMO die we bewust willen oplossen (💡):**
1. Wapenskills overlappen sterk (damage + crit + bleed in een ander jasje).
2. Geen keuzes: twee spelers met hetzelfde level zijn identiek.
3. Hardcore is bot: veel levels kwijt, geen herstel, weinig transparantie.
4. Geen echte voortgangsinterface.

### Open vragen
- ❓ Minecraft-versie?
- ❓ Modloader: Fabric, NeoForge, of beide (Architectury)? Of een Paper-plugin plus een optionele client-mod?
- ❓ Draait er al een server met plugins of mods (bijv. "Extreme Dimension")? Hoe ziet die GUI eruit?
- ❓ Is de client-mod verplicht voor elke speler, of moet een vanilla client ook kunnen spelen (met een inventory-GUI als fallback)?

---

## 2. Skills
- ❓ Aantal skills / omvang
- ❓ Categorieën
- ❓ Granulariteit van combat (per wapen of gegroepeerd)
- ❓ Alleen bestaande vanilla-mechanieken aanpassen, of ook nieuwe content (items, blokken)?

## 3. XP-generatie
_Nog niet besproken._

## 4. Levelprogressie & formules
_Nog niet besproken._

## 5. Skill-unlocks
_Nog niet besproken._

## 6. Abilities & skill trees
_Nog niet besproken._

## 7. Doodsstraffen
_Nog niet besproken._

## 8. Herstelmechanieken
_Nog niet besproken._

## 9. GUI-navigatie
- ❓ Hoe opent de speler het menu: keybind, knop/tab in het inventory-scherm, commando, of een combinatie?

## 10. Spelerdata
_Nog niet besproken._

## 11. Configuratie
_Nog niet besproken._

## 12. Anti-exploit
_Nog niet besproken._

## 13. Implementatieroadmap
_Pas na goedkeuring van het volledige ontwerp._

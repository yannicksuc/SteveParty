# Steve Party Maker modpack

Files for the friends beta modpack (Minecraft **1.21.1**, Fabric). For now it holds the
**FTB Quests** quest book; the packwiz files come later.

## Contents

| Path | What |
|---|---|
| `config/ftbquests/quests/` | The quest book: `data.snbt`, `chapter_groups.snbt`, `chapters/*.snbt` (15 chapters, 211 quests), `reward_tables/*.snbt` (Party Favors, Star Chest), `lang/en_us.snbt` and `lang/fr_fr.snbt` (every title and description, in English and French). |
| `tools/build_quests.py` | Generates the whole book (content, layout, rewards, both languages). Ids are stable from one run to the next. |
| `tools/validate_quests.py` | Offline checks: SNBT parses, unique ids, dependencies and reward tables resolve, no dependency loop, every item exists in the mod or in vanilla 1.21.1, both languages complete, no overlapping quests. |
| `tools/vanilla_*_1.21.1.txt` | Vanilla 1.21.1 item and entity ids (from the game's registries), used by the validator. |

The book is in **flexible** progression mode: an item counts as soon as it is in the inventory, even
out of order, but dependencies still lock the rewards. Quests are shared per FTB Teams team; rewards are per player.

## Required mods (Fabric 1.21.1)

| Mod | Version | Source |
|---|---|---|
| FTB Quests (Fabric) | 2101.1.36 | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-fabric) |
| FTB Library (Fabric) | 2101.1.37 (2101.1.36 or newer) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-library-fabric) |
| FTB Teams (Fabric) | 2101.1.11 (2101.1.9 or newer) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-teams-fabric) |
| Architectury API | 13.0.11+fabric (13.0.8 or newer) | [Modrinth](https://modrinth.com/mod/architectury-api) / CurseForge |
| Fabric API | 0.102.1+1.21.1 or newer | [Modrinth](https://modrinth.com/mod/fabric-api) |

The FTB mods are published on CurseForge only. The quest files use the FTB Quests 2101 format
(file `version: 13`, texts in `lang/*.snbt`).

## Regenerate and check

```
python modpack/tools/build_quests.py      # rewrites modpack/config/ftbquests/quests/
python modpack/tools/validate_quests.py   # exit code 1 on any error
```

Item names in quest titles come from `src/main/resources/assets/steveparty/lang/`, so the item ids of
the 1.21.1 port must stay the same.

## Edit the quests in game

1. Copy `config/ftbquests/` into the instance (or the server's `config/`) and start the world.
2. Be an operator (single player: "Allow Commands" on), open the quest book and click the pencil icon at the
   top right (or run `/ftbquests editing_mode`). Right-click a quest or chapter to edit it; drag to move.
3. Texts are saved in the lang file of your current game language: switch Minecraft to Français to edit
   the French texts.
4. Changes are saved automatically to `config/ftbquests/quests/` (Ctrl+S forces it). On a dedicated
   server, Shift+Ctrl+S downloads the book to the client. After editing the files by hand, run `/ftbquests reload`.

## Export back

Copy the instance's (or server's) `config/ftbquests/quests/` over `modpack/config/ftbquests/quests/`,
run `python modpack/tools/validate_quests.py`, and commit. Once the book has been edited in game, the
exported files are the reference: port the edits into `build_quests.py` before running it again, or it
overwrites them.

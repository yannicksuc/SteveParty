# Advancements

SteveParty's advancements cover the whole mod. Each feature has a progression path (craft it, place it, use it), plus goals and challenges for the rarer feats, and a tab of hidden, funny ones. They target **Minecraft 1.21.1** (data format checked against the vanilla 1.21.1 advancements). They also load on 1.21.3, because the format is the same for everything used here.

- **129 advancements** in 4 tabs:
  - **80 are live**, in `src/main/resources/data/steveparty/advancement/<tab>/`. They only use vanilla triggers.
  - **49 are pending**, in `docs/advancements-pending/data/steveparty/advancement/<tab>/`. They need one of the **23 custom triggers** specified below. An unknown trigger id makes an advancement fail to load and logs an error, so these files stay out of `data/` until their triggers exist.
- Other advancement folders: the recipe-unlock advancements (`advancement/recipes/`) are unchanged, and the guide-book unlocks live in `advancement/guide/` (another branch).
- Rewards stay small, because the modpack's FTB Quests gives the main rewards:
  - tasks: nothing, or 3 Coins for the first party milestones;
  - goals: 25 xp + 10 Coins (stars tab: 25 xp + 1 star fragment);
  - challenges: 100 xp + 25 Coins (stars tab: 100 xp + 8 star fragments);
  - hidden fun ones: 10 to 50 xp.
  - The loot tables are in `data/steveparty/loot_table/advancement_reward/`: `coins_3`, `coins_10`, `coins_25`, `star_fragment` and `star_fragments`. The fragment tables give one random colour among blue, red, green, yellow and purple, the 5 Mula colours other than black.
- **Lang keys** look like `advancements.steveparty.<tab>.<path>.title` / `.description`, for example `advancements.steveparty.stars.tame_black.title`. They are in `en_us.json` and `fr_fr.json` as one group at the end of each file, pending advancements included, so moving those in needs no lang change.
- **Validation:** `python scripts/validate_advancements.py` checks:
  - JSON syntax, parents, criteria and requirements;
  - triggers: vanilla 1.21.1 ones, or a custom one listed below; a live file may use a custom trigger only once it is registered in Java;
  - every item, block, entity, potion and tag id, against vanilla 1.21.1 and the mod;
  - tab backgrounds, reward loot tables, and lang keys in both languages.

  It downloads the vanilla 1.21.1 registries from misode/mcmeta, or reads them from a local `data.min.json` passed as argument.

## Tabs

| Tab | Root | Background | Content |
|---|---|---|---|
| `main` | Steve Party Maker | polished yellow terracotta | board, pawns, dice, the party, Goal Pole, podiums, mini-games |
| `workshop` | Party Workshop | light blue plastic | plastic, pipes, stencils and signs, shops and the Boxed Trader |
| `stars` | Wish Upon a Star | polished black concrete | Mulas, star fragments, Power Star, Gravity Core, Dice Forge, shooting-star nights, Telescope |
| `wild_cards` | Wild Cards | magenta plastic | secrets, stunts and silly things, almost all hidden |

A root unlocks as soon as the player holds one of the tab's typical items. The `stars` root also unlocks when the player right-clicks a Mula. Vanilla shows a child once its parent is done, so each tab opens up gradually.

## Notes on the live (data-only) advancements

- **Taming Mulas** uses vanilla `minecraft:tame_animal` (`stars/tame`, `stars/tame_black`, `stars/tame_all`). `MulaEntity#tameAttempt` calls `TameableEntity#setOwner(PlayerEntity)`. In Yarn 1.21.1 and 1.21.3 that is intermediary `method_6170`, Mojang's `tame(Player)`, which fires `Criteria.TAME_ANIMAL`. The colour is matched with the entity NBT `{Variant:N}` (0 blue, 1 red, 2 green, 3 yellow, 4 purple, 5 black). If the port replaces that call, keep a call that fires `TAME_ANIMAL`.
- **Feeding Mulas** (`stars/feed`, `stars/potion`, `wild_cards/golden_treat`) uses `player_interacted_with_entity`: one criterion per colour, with that colour's foods (`MulaFood.FOODS` and `SEEDS`) and `{Variant:N}`. `interactMob` also returns SUCCESS when the Mula refuses a meal during its 1 s cooldown, so a click in that second still counts. That is harmless.
- **`stars/sit`** checks the Mula's NBT `{Sitting:1b}` after the click. Only the owner can make a Mula sit, so it also proves the taming.
- **`main/pawns/pocket_pawn`** checks a click with a Token on an entity whose NBT has `{Tokenized:1b}`. `TokenPawnInteractionMixin` passes the click to `TokenItem#useOnEntity`, which returns SUCCESS.
- **`main/pawns/plunger`** and **`wild_cards/library_rules`** check the target's `{Silent:1b}` after the Plunger click. Mobs whose own `interactMob` takes the click first (a villager opens its trades) never reach the Plunger, so they don't count.
- **`main/pole/top`** uses `minecraft:location` with `stepping_on` a `goal_pole`. Vanilla checks location once per second, and players stay on top of the pole (upside down) long enough.
- **`wild_cards/leap_of_faith`** uses `fall_from_height` (≥ 30 blocks) with `stepping_on` a `goal_pole`. `onLanding` still fires even though the pole cancels fall damage.
- **`workshop/plastic/hop_switch`** uses `enter_block`, because the switch is lower than a full block and the location check (once per second) could miss the short press.
- **`workshop/pipes/travel`** uses `started_riding` with `vehicle.type = steveparty:pipe_carrier`, because `PipeTravel` mounts the traveller on a `PipeCarrierEntity`.
- **`workshop/shops/buy`** uses vanilla `villager_trade`. `BoxedTraderEntity` extends `MerchantEntity` and calls `super.trade(offer)`, which fires the criterion.
- **`workshop/shops/shopkeeper_key`** uses `player_interacted_with_entity`. `BoxedTraderEntity#interactMob` passes on a key, and `ShopkeeperKeyItem#useOnEntity` handles it.
- **`main/pawns/game_master`** uses an item sub-predicate `minecraft:enchantments` with `steveparty:game_master`.
- **`stars/luck_potion`** uses `brewed_potion` with `minecraft:luck`, the mod's star-fragment brewing recipe.

## The advancements, tab by tab

In the criteria column, "all" means every criterion is needed (they add up over time) and "any" means one is enough. Long id lists are shortened to `[first, second, … (count)]`; the JSON files hold the full lists.

### Tab `main`: Steve Party Maker / Steve Party Maker

Background: `steveparty:textures/block/polished_yellow_terracotta.png`. 59 advancements (36 data, 23 pending).


- `main/root` Steve Party Maker (task)
    - `main/board/first_tile` Square One (task)
        - `main/board/cartridge` Plug and Play (task)
            - `main/board/start_space` On Your Marks (task)
            - `main/board/every_cartridge` Full Deck (goal)
        - `main/board/check_point` Free Ride (task)
        - `main/board/wrench` Righty-Tighty (task)
            - `main/board/router` Remote Control (task)
    - `main/pawns/crystal_ball` Crystal Clear (task)
        - `main/pawns/tokenizer_wand` Abracadabra (task)
            - `main/pawns/first_pawn` Pionificus! (task, pending)
                - `main/pawns/tiny_pawn` Honey, I Shrunk the Mob (task, pending)
                - `main/pawns/giant_pawn` Supersized (task, pending)
            - `main/pawns/pocket_pawn` Pocket Pawn (task)
            - `main/pawns/game_master` Game Master (goal)
    - `main/pawns/plunger` Shh! (task)
    - `main/dice/default_dice` Roll With It (task)
        - `main/dice/double_dice` Seeing Double (task)
            - `main/dice/triple_dice` Third Time Lucky (task)
                - `main/dice/three_of_a_kind` Three of a Kind (goal, pending)
                    - `main/dice/thirty` Thirty Steps to Glory (challenge, hidden, pending)
            - `main/dice/snake_eyes` Snake Eyes (task, hidden, pending)
        - `main/dice/roll_ten` Perfect Ten (task, pending)
        - `main/dice/blank_face` Blank Slate (task)
            - `main/dice/special_faces` Face Value (goal)
                - `main/dice/swap` Switcheroo (task, pending)
            - `main/dice/module` Tinkerer (task)
                - `main/dice/every_module` Swiss Army Die (goal)
    - `main/party/party_controller` Party Planner (task)
        - `main/party/party_cards` Program Director (goal)
        - `main/party/party_bell` Ring My Bell (task)
        - `main/party/step_controller` One Step Ahead (task)
        - `main/party/piggy_bank` Oink Oink (task)
        - `main/party/coins` A Pocketful of Coins (task)
        - `main/party/start` Let's Party! (task, pending)
            - `main/party/finish` That's a Wrap (task, pending)
                - `main/party/win` Superstar (goal, pending)
                - `main/party/marathon` Marathon (challenge, pending)
            - `main/party/seeing_red` Seeing Red (task, pending)
            - `main/party/replay` Encore! (task, pending)
            - `main/party/teleport` Beam Me Up (task, pending)
        - `main/minigames/catalogue` Game Library (task)
            - `main/minigames/page` House Rules (task)
                - `main/minigames/controller` Referee (task)
                - `main/minigames/minigame_pipe` Warp Zone (task)
                    - `main/minigames/all_minigame_pipes` Copper, Iron, Gold (goal)
                - `main/minigames/play` Game On! (task, pending)
                    - `main/minigames/test` Beta Tester (task, pending)
                    - `main/minigames/ready` Ready Player One (task, pending)
                    - `main/minigames/win` Champion (goal, pending)
                        - `main/minigames/team_win` Teamwork Makes the Dream Work (task, pending)
                        - `main/minigames/one_vs_all` Against All Odds (challenge, pending)
    - `main/pole/goal_pole_base` Pole Position (task)
        - `main/pole/top` 1-Up! (task)
            - `main/pole/goal` Goal! (goal, pending)
        - `main/pole/flag` Flying Colours (task)
    - `main/podium/podium` Hall of Fame (task)
        - `main/podium/all_podiums` Gold, Silver, Bronze (task)
        - `main/podium/first_place` Number One (task, pending)

| id | EN | FR | icon | frame | hidden | parent | criteria (all / any) | reward | status |
|---|---|---|---|---|---|---|---|---|---|
| `main/root` | **Steve Party Maker**<br>Build your own party board game | **Steve Party Maker**<br>Construisez votre propre jeu de plateau | `steveparty:party_controller` | task | no | — | any:<br>has_party_item: `minecraft:inventory_changed` {items: [{items: [steveparty:tile, steveparty:advanced_tile, … (13)]}]} | — | data |
| `main/board/first_tile` | **Square One**<br>Place a board tile | **Case départ**<br>Poser une tuile de plateau | `steveparty:tile` | task | no | `main/root` | any:<br>placed_tile: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: [steveparty:tile, steveparty:advanced_tile]}}}]} | — | data |
| `main/board/cartridge` | **Plug and Play**<br>Craft a Cartridge, the brain of a board space | **Brancher et jouer**<br>Fabriquer une Cartouche, le cerveau d'une case | `steveparty:board_space_behavior` | task | no | `main/board/first_tile` | any:<br>has_cartridge: `minecraft:inventory_changed` {items: [{items: steveparty:board_space_behavior}]} | — | data |
| `main/board/start_space` | **On Your Marks**<br>Get a Start cartridge to enrol your pawns | **À vos marques**<br>Obtenir une Cartouche de départ pour inscrire vos pions | `steveparty:tile_behavior_start` | task | no | `main/board/cartridge` | any:<br>has_start: `minecraft:inventory_changed` {items: [{items: steveparty:tile_behavior_start}]} | — | data |
| `main/board/every_cartridge` | **Full Deck**<br>Collect every kind of cartridge: Start, Stop, Shop, Inventory, Advance/Back, Replay and Teleport | **Jeu complet**<br>Réunir toutes les cartouches : Départ, Stop, Boutique, Inventaire, Avancer/Reculer, Rejouer et Téléportation | `steveparty:teleport_cartridge` | goal | no | `main/board/cartridge` | all:<br>start: `minecraft:inventory_changed` {items: [{items: steveparty:tile_behavior_start}]}<br>stop: `minecraft:inventory_changed` {items: [{items: steveparty:board_space_behavior_stop}]}<br>shop: `minecraft:inventory_changed` {items: [{items: steveparty:shop_cartridge}]}<br>inventory: `minecraft:inventory_changed` {items: [{items: steveparty:inventory_cartridge}]}<br>advance_back: `minecraft:inventory_changed` {items: [{items: steveparty:advance_back_cartridge}]}<br>replay: `minecraft:inventory_changed` {items: [{items: steveparty:replay_cartridge}]}<br>teleport: `minecraft:inventory_changed` {items: [{items: steveparty:teleport_cartridge}]} | 25 xp, coins_10 | data |
| `main/board/check_point` | **Free Ride**<br>Place a Check Point: pawns float through it without spending a step | **Passe-droit**<br>Poser un Point de passage : les pions le traversent sans dépenser de pas | `steveparty:check_point` | task | no | `main/board/first_tile` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:check_point}}}]} | — | data |
| `main/board/wrench` | **Righty-Tighty**<br>Get a Wrench to link the spaces of your board | **Serrer à droite**<br>Obtenir une Clé pour relier les cases de votre plateau | `steveparty:wrench` | task | no | `main/board/first_tile` | any:<br>has_wrench: `minecraft:inventory_changed` {items: [{items: steveparty:wrench}]} | — | data |
| `main/board/router` | **Remote Control**<br>Place a Board Space Redstone Router | **Télécommande**<br>Poser un Routeur redstone de cases | `steveparty:board_space_redstone_router` | task | no | `main/board/wrench` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:board_space_redstone_router}}}]} | — | data |
| `main/pawns/crystal_ball` | **Crystal Clear**<br>Get a Garnet Crystal Ball | **Limpide**<br>Obtenir une Boule de cristal en grenat | `steveparty:garnet_crystal_ball` | task | no | `main/root` | any:<br>has_ball: `minecraft:inventory_changed` {items: [{items: steveparty:garnet_crystal_ball}]} | — | data |
| `main/pawns/tokenizer_wand` | **Abracadabra**<br>Craft a Tokenizer Wand | **Abracadabra**<br>Fabriquer une Baguette de pions | `steveparty:tokenizer_wand` | task | no | `main/pawns/crystal_ball` | any:<br>has_wand: `minecraft:inventory_changed` {items: [{items: steveparty:tokenizer_wand}]} | — | data |
| `main/pawns/first_pawn` | **Pionificus!**<br>Turn a mob into a pawn with the Tokenizer Wand | **Pionificus !**<br>Transformer un mob en pion avec la Baguette de pions | `steveparty:token` | task | no | `main/pawns/tokenizer_wand` | any:<br>tokenized: `steveparty:pawn_spell` {result: tokenized} | — | **pending** (custom trigger) |
| `main/pawns/pocket_pawn` | **Pocket Pawn**<br>Store a pawn in a Token | **Pion de poche**<br>Ranger un pion dans un Jeton | `steveparty:token` | task | no | `main/pawns/tokenizer_wand` | any:<br>stored: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {nbt: {Tokenized:1b}}}], item: {items: steveparty:token}} | — | data |
| `main/pawns/game_master` | **Game Master**<br>Get a Tokenizer Wand enchanted with Game Master | **Maître du jeu**<br>Obtenir une Baguette de pions enchantée Maître du jeu | `steveparty:tokenizer_wand` | goal | no | `main/pawns/tokenizer_wand` | any:<br>has_enchanted_wand: `minecraft:inventory_changed` {items: [{items: steveparty:tokenizer_wand, predicates: {minecraft:enchantments: [{enchantments: steveparty:game_master}]}}]} | 25 xp, coins_10 | data |
| `main/pawns/tiny_pawn` | **Honey, I Shrunk the Mob**<br>Make a pawn of the smallest size | **Chérie, j'ai rétréci le mob**<br>Créer un pion de la plus petite taille | `steveparty:token` | task | no | `main/pawns/first_pawn` | any:<br>tiny: `steveparty:pawn_spell` {size: {max: 0.3}} | — | **pending** (custom trigger) |
| `main/pawns/giant_pawn` | **Supersized**<br>Make a pawn of the largest size (2 blocks) | **Format géant**<br>Créer un pion de la plus grande taille (2 blocs) | `steveparty:token` | task | no | `main/pawns/first_pawn` | any:<br>giant: `steveparty:pawn_spell` {size: {min: 1.95}} | — | **pending** (custom trigger) |
| `main/pawns/plunger` | **Shh!**<br>Silence a mob with the Plunger | **Chut !**<br>Réduire un mob au silence avec la Ventouse | `steveparty:plunger` | task | no | `main/root` | any:<br>silenced: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {nbt: {Silent:1b}}}], item: {items: steveparty:plunger}} | — | data |
| `main/dice/default_dice` | **Roll With It**<br>Craft a Default Dice | **Les dés sont jetés**<br>Fabriquer un Dé par défaut | `steveparty:default_dice` | task | no | `main/root` | any:<br>has_dice: `minecraft:inventory_changed` {items: [{items: steveparty:default_dice}]} | — | data |
| `main/dice/double_dice` | **Seeing Double**<br>Get a Double Dice | **Double vue**<br>Obtenir un Dé double | `steveparty:double_dice` | task | no | `main/dice/default_dice` | any:<br>has_dice: `minecraft:inventory_changed` {items: [{items: steveparty:double_dice}]} | — | data |
| `main/dice/triple_dice` | **Third Time Lucky**<br>Get a Triple Dice | **Jamais deux sans trois**<br>Obtenir un Dé triple | `steveparty:triple_dice` | task | no | `main/dice/double_dice` | any:<br>has_dice: `minecraft:inventory_changed` {items: [{items: steveparty:triple_dice}]} | — | data |
| `main/dice/roll_ten` | **Perfect Ten**<br>Roll a 10 on a Default Dice | **Dix sur dix**<br>Faire 10 avec un Dé par défaut | `steveparty:dice_face_10` | task | no | `main/dice/default_dice` | any:<br>ten: `steveparty:dice_rolled` {dice_count: 1, forged: false, total: 10} | — | **pending** (custom trigger) |
| `main/dice/three_of_a_kind` | **Three of a Kind**<br>Roll three identical numbers with a Triple Dice | **Brelan**<br>Obtenir trois nombres identiques avec un Dé triple | `steveparty:triple_dice` | goal | no | `main/dice/triple_dice` | any:<br>triple: `steveparty:dice_rolled` {dice_count: 3, all_equal: true} | 25 xp, coins_10 | **pending** (custom trigger) |
| `main/dice/thirty` | **Thirty Steps to Glory**<br>Roll 30 with a Triple Dice | **Trente pas vers la gloire**<br>Faire 30 avec un Dé triple | `steveparty:premium_dice_face_10` | challenge | yes | `main/dice/three_of_a_kind` | any:<br>thirty: `steveparty:dice_rolled` {dice_count: 3, forged: false, total: 30} | 100 xp, coins_25 | **pending** (custom trigger) |
| `main/dice/snake_eyes` | **Snake Eyes**<br>Roll a total of 2 with a Double Dice | **Double as**<br>Faire 2 au total avec un Dé double | `steveparty:dice_face_1` | task | yes | `main/dice/double_dice` | any:<br>two: `steveparty:dice_rolled` {dice_count: 2, forged: false, total: 2} | — | **pending** (custom trigger) |
| `main/dice/blank_face` | **Blank Slate**<br>Make Blank Dice Faces | **Page blanche**<br>Fabriquer des Faces de dé vierges | `steveparty:blank_dice_face` | task | no | `main/dice/default_dice` | any:<br>has_face: `minecraft:inventory_changed` {items: [{items: steveparty:blank_dice_face}]} | — | data |
| `main/dice/special_faces` | **Face Value**<br>Collect a Coin face, a Debt face, a Swap face and a Cursed face | **Faces cachées**<br>Réunir une face Pièces, une face Dette, une face Échange et une face maudite | `steveparty:swap_dice_face` | goal | no | `main/dice/blank_face` | all:<br>coin: `minecraft:inventory_changed` {items: [{items: [steveparty:coin_dice_face_1, steveparty:coin_dice_face_2, … (10)]}]}<br>debt: `minecraft:inventory_changed` {items: [{items: [steveparty:debt_dice_face_1, steveparty:debt_dice_face_2, … (10)]}]}<br>swap: `minecraft:inventory_changed` {items: [{items: steveparty:swap_dice_face}]}<br>cursed: `minecraft:inventory_changed` {items: [{items: [steveparty:cursed_dice_face_1, steveparty:cursed_dice_face_2, steveparty:cursed_dice_face_3]}]} | 25 xp, coins_10 | data |
| `main/dice/swap` | **Switcheroo**<br>Roll a Swap face | **Chassé-croisé**<br>Tomber sur une face Échange | `steveparty:swap_dice_face` | task | no | `main/dice/special_faces` | any:<br>swap: `steveparty:dice_rolled` {has_face: swap} | — | **pending** (custom trigger) |
| `main/dice/module` | **Tinkerer**<br>Craft a Dice Module | **Bricoleur**<br>Fabriquer un Module de dé | `steveparty:dice_module_lucky` | task | no | `main/dice/blank_face` | any:<br>has_module: `minecraft:inventory_changed` {items: [{items: [steveparty:dice_module_slow, steveparty:dice_module_choice, … (8)]}]} | — | data |
| `main/dice/every_module` | **Swiss Army Die**<br>Collect all 8 Dice Modules | **Dé suisse**<br>Réunir les 8 Modules de dé | `steveparty:dice_module_skeleton_key` | goal | no | `main/dice/module` | all:<br>slow: `minecraft:inventory_changed` {items: [{items: steveparty:dice_module_slow}]}<br>choice: `minecraft:inventory_changed` {items: [{items: steveparty:dice_module_choice}]}<br>infinity: `minecraft:inventory_changed` {items: [{items: steveparty:dice_module_infinity}]}<br>lucky: `minecraft:inventory_changed` {items: [{items: steveparty:dice_module_lucky}]}<br>reroll: `minecraft:inventory_changed` {items: [{items: steveparty:dice_module_reroll}]}<br>reversed: `minecraft:inventory_changed` {items: [{items: steveparty:dice_module_reversed}]}<br>skeleton_key: `minecraft:inventory_changed` {items: [{items: steveparty:dice_module_skeleton_key}]}<br>homing: `minecraft:inventory_changed` {items: [{items: steveparty:dice_module_homing}]} | 25 xp, coins_10 | data |
| `main/party/party_controller` | **Party Planner**<br>Place a Party Controller | **Organisateur de fête**<br>Poser un Contrôleur de fête | `steveparty:party_controller` | task | no | `main/root` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:party_controller}}}]} | — | data |
| `main/party/party_cards` | **Program Director**<br>Collect every kind of Party Card | **Directeur des programmes**<br>Réunir toutes les sortes de Cartes de partie | `steveparty:party_card_repeat` | goal | no | `main/party/party_controller` | all:<br>turns: `minecraft:inventory_changed` {items: [{items: steveparty:party_card_turns}]}<br>minigame: `minecraft:inventory_changed` {items: [{items: steveparty:party_card_minigame}]}<br>event: `minecraft:inventory_changed` {items: [{items: steveparty:party_card_event}]}<br>repeat: `minecraft:inventory_changed` {items: [{items: steveparty:party_card_repeat}]}<br>sequence_start: `minecraft:inventory_changed` {items: [{items: steveparty:party_card_sequence_start}]} | 25 xp, coins_10 | data |
| `main/party/party_bell` | **Ring My Bell**<br>Place a Party Bell | **Sonnez les cloches**<br>Poser une Cloche de partie | `steveparty:party_bell` | task | no | `main/party/party_controller` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:party_bell}}}]} | — | data |
| `main/party/step_controller` | **One Step Ahead**<br>Place a Step Controller | **Un pas d'avance**<br>Poser un Contrôleur de pas | `steveparty:step_controller` | task | no | `main/party/party_controller` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:step_controller}}}]} | — | data |
| `main/party/piggy_bank` | **Oink Oink**<br>Place a Piggy Bank | **Groin groin**<br>Poser une Tirelire | `steveparty:piggy_bank` | task | no | `main/party/party_controller` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:piggy_bank}}}]} | — | data |
| `main/party/coins` | **A Pocketful of Coins**<br>Hold a full stack of 64 Coins | **Plein les poches**<br>Avoir une pile de 64 Pièces | `steveparty:coin` | task | no | `main/party/party_controller` | any:<br>stack: `minecraft:inventory_changed` {items: [{items: steveparty:coin, count: {min: 64}}]} | — | data |
| `main/party/start` | **Let's Party!**<br>Take part in a party | **Que la fête commence !**<br>Participer à une partie | `steveparty:party_controller` | task | no | `main/party/party_controller` | any:<br>started: `steveparty:party_started` | coins_3 | **pending** (custom trigger) |
| `main/party/finish` | **That's a Wrap**<br>Play a party to the end | **C'est dans la boîte**<br>Jouer une partie jusqu'au bout | `steveparty:flag` | task | no | `main/party/start` | any:<br>ended: `steveparty:party_ended` | coins_3 | **pending** (custom trigger) |
| `main/party/win` | **Superstar**<br>Finish a party in 1st place | **Superstar**<br>Terminer une partie à la 1re place | `steveparty:power_star` | goal | no | `main/party/finish` | any:<br>won: `steveparty:party_ended` {rank: 1, pawns: {min: 2}} | 25 xp, coins_10 | **pending** (custom trigger) |
| `main/party/marathon` | **Marathon**<br>Play a party of 20 rounds or more to the end | **Marathon**<br>Jouer jusqu'au bout une partie de 20 manches ou plus | `steveparty:step_controller` | challenge | no | `main/party/finish` | any:<br>long: `steveparty:party_ended` {rounds: {min: 20}} | 100 xp, coins_25 | **pending** (custom trigger) |
| `main/party/seeing_red` | **Seeing Red**<br>Land on a bad space 3 times in the same party | **Rouge de colère**<br>Tomber 3 fois sur une case négative dans la même partie | `steveparty:red_plastic_block` | task | no | `main/party/start` | any:<br>red: `steveparty:pawn_landed` {status: bad, in_party: true, times_this_party: {min: 3}} | — | **pending** (custom trigger) |
| `main/party/replay` | **Encore!**<br>Land on a Replay space | **Bis !**<br>Tomber sur une case Rejouer | `steveparty:replay_cartridge` | task | no | `main/party/start` | any:<br>replay: `steveparty:pawn_landed` {space: replay} | — | **pending** (custom trigger) |
| `main/party/teleport` | **Beam Me Up**<br>Land on a Teleport space | **Téléportez-moi**<br>Tomber sur une case Téléportation | `steveparty:teleport_cartridge` | task | no | `main/party/start` | any:<br>teleport: `steveparty:pawn_landed` {space: teleport} | — | **pending** (custom trigger) |
| `main/pole/goal_pole_base` | **Pole Position**<br>Place a Goal Pole Base | **Pole position**<br>Poser un Socle de mât d'arrivée | `steveparty:goal_pole_base` | task | no | `main/root` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:goal_pole_base}}}]} | — | data |
| `main/pole/top` | **1-Up!**<br>Land on top of a Goal Pole | **1-Up !**<br>Atterrir au sommet d'un Mât d'arrivée | `steveparty:goal_pole` | task | no | `main/pole/goal_pole_base` | any:<br>on_top: `minecraft:location` {player: [{condition: minecraft:entity_properties, entity: this, predicate: {stepping_on: {block: {blocks: steveparty:goal_pole}}}}]} | — | data |
| `main/pole/flag` | **Flying Colours**<br>Hang a Flag on a Goal Pole | **Haut les couleurs**<br>Accrocher un Drapeau à un Mât d'arrivée | `steveparty:flag` | task | no | `main/pole/goal_pole_base` | any:<br>flag: `minecraft:item_used_on_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:goal_pole}}}, {condition: minecraft:match_tool, predicate: {items: steveparty:flag}}]} | — | data |
| `main/pole/goal` | **Goal!**<br>Reach your own goal on a Goal Pole | **But !**<br>Atteindre votre but sur un Mât d'arrivée | `steveparty:goal_pole` | goal | no | `main/pole/top` | any:<br>goal: `steveparty:pole_goal_reached` | 25 xp, coins_10 | **pending** (custom trigger) |
| `main/podium/podium` | **Hall of Fame**<br>Place a Podium | **Panthéon**<br>Poser un Podium | `steveparty:podium` | task | no | `main/root` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: [steveparty:podium, steveparty:gold_podium, steveparty:silver_podium, steveparty:bronze_podium]}}}]} | — | data |
| `main/podium/all_podiums` | **Gold, Silver, Bronze**<br>Place a Gold, a Silver and a Bronze Podium | **Or, argent, bronze**<br>Poser un Podium d'or, d'argent et de bronze | `steveparty:gold_podium` | task | no | `main/podium/podium` | all:<br>gold: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:gold_podium}}}]}<br>silver: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:silver_podium}}}]}<br>bronze: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:bronze_podium}}}]} | — | data |
| `main/podium/first_place` | **Number One**<br>Take the 1st place on a podium | **Numéro un**<br>Prendre la 1re place d'un podium | `steveparty:gold_podium` | task | no | `main/podium/podium` | any:<br>first: `steveparty:podium_claimed` {place: 1} | — | **pending** (custom trigger) |
| `main/minigames/catalogue` | **Game Library**<br>Craft a Mini-Games Catalogue | **Ludothèque**<br>Fabriquer un Catalogue de mini-jeux | `steveparty:mini_games_catalogue` | task | no | `main/party/party_controller` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:mini_games_catalogue}]} | — | data |
| `main/minigames/page` | **House Rules**<br>Make a Mini-Game Page | **Règles de la maison**<br>Fabriquer une Page de mini-jeu | `steveparty:mini_game_page` | task | no | `main/minigames/catalogue` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:mini_game_page}]} | — | data |
| `main/minigames/controller` | **Referee**<br>Place a Mini-Game Controller | **Arbitre**<br>Poser un Contrôleur de mini-jeu | `steveparty:mini_game_controller` | task | no | `main/minigames/page` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:mini_game_controller}}}]} | — | data |
| `main/minigames/minigame_pipe` | **Warp Zone**<br>Craft a Mini-Game Pipe | **Zone de distorsion**<br>Fabriquer un Tuyau de mini-jeu | `steveparty:copper_minigame_pipe` | task | no | `main/minigames/page` | any:<br>has: `minecraft:inventory_changed` {items: [{items: [steveparty:copper_minigame_pipe, steveparty:iron_minigame_pipe, steveparty:golden_minigame_pipe]}]} | — | data |
| `main/minigames/all_minigame_pipes` | **Copper, Iron, Gold**<br>Collect the copper, iron and golden Mini-Game Pipes | **Cuivre, fer, or**<br>Réunir les Tuyaux de mini-jeu en cuivre, en fer et en or | `steveparty:golden_minigame_pipe` | goal | no | `main/minigames/minigame_pipe` | all:<br>copper: `minecraft:inventory_changed` {items: [{items: steveparty:copper_minigame_pipe}]}<br>iron: `minecraft:inventory_changed` {items: [{items: steveparty:iron_minigame_pipe}]}<br>golden: `minecraft:inventory_changed` {items: [{items: steveparty:golden_minigame_pipe}]} | 25 xp, coins_10 | data |
| `main/minigames/play` | **Game On!**<br>Play a mini-game round | **C'est parti !**<br>Jouer une manche de mini-jeu | `steveparty:mini_game_page` | task | no | `main/minigames/page` | any:<br>played: `steveparty:minigame_ended` {practice: false} | — | **pending** (custom trigger) |
| `main/minigames/test` | **Beta Tester**<br>Play a mini-game outside a party | **Bêta-testeur**<br>Jouer un mini-jeu hors partie | `steveparty:mini_game_controller` | task | no | `main/minigames/play` | any:<br>tested: `steveparty:minigame_ended` {in_party: false} | — | **pending** (custom trigger) |
| `main/minigames/ready` | **Ready Player One**<br>Vote ready after a practice round | **Prêt à jouer**<br>Se déclarer prêt après une manche d'essai | `steveparty:mini_game_controller` | task | no | `main/minigames/play` | any:<br>ready: `steveparty:minigame_ready` | — | **pending** (custom trigger) |
| `main/minigames/win` | **Champion**<br>Win a mini-game in a party | **Champion**<br>Gagner un mini-jeu pendant une partie | `steveparty:gold_podium` | goal | no | `main/minigames/play` | any:<br>won: `steveparty:minigame_ended` {place: 1, practice: false, in_party: true} | 25 xp, coins_10 | **pending** (custom trigger) |
| `main/minigames/team_win` | **Teamwork Makes the Dream Work**<br>Win a team mini-game in a party | **L'union fait la force**<br>Gagner un mini-jeu en équipe pendant une partie | `steveparty:blue_plastic_block` | task | no | `main/minigames/win` | any:<br>won: `steveparty:minigame_ended` {place: 1, practice: false, in_party: true, format: teams, team_size: {min: 2}} | — | **pending** (custom trigger) |
| `main/minigames/one_vs_all` | **Against All Odds**<br>Win a 1 vs 3 mini-game on your own | **Seul contre tous**<br>Gagner seul un mini-jeu en 1 contre 3 | `steveparty:red_plastic_block` | challenge | no | `main/minigames/win` | any:<br>won: `steveparty:minigame_ended` {place: 1, practice: false, format: teams, team_size: 1, opponents: {min: 3}} | 100 xp, coins_25 | **pending** (custom trigger) |

### Tab `workshop`: Party Workshop / Atelier de fête

Background: `steveparty:textures/block/plastic_block/light_blue_plastic_block.png`. 22 advancements (19 data, 3 pending).


- `workshop/root` Party Workshop (task)
    - `workshop/plastic/pellets` Fantastic Plastic (task)
        - `workshop/plastic/block` Brick by Brick (task)
            - `workshop/plastic/all_colours` Taste the Rainbow (goal)
            - `workshop/plastic/hop_switch` Boing! (task)
                - `workshop/plastic/triple_jump_shoes` Wahoo! (task)
                - `workshop/plastic/looting_box` Bonk! (task, pending)
    - `workshop/pipes/pipe` Plumber's Apprentice (task)
        - `workshop/pipes/travel` Down the Tubes (task)
    - `workshop/signs/stencil` Stencil Me In (task)
        - `workshop/signs/stencil_maker` Pattern Recognition (task)
        - `workshop/signs/stencil_hammer` Hammer Time (task)
        - `workshop/signs/all_signs` Sign Language (goal)
    - `workshop/shops/villager_block` Thinking Inside the Box (task)
        - `workshop/shops/boxed_trader` Special Delivery (goal, pending)
        - `workshop/shops/buy` Valued Customer (task)
        - `workshop/shops/trading_stall` Open for Business (task)
            - `workshop/shops/shopkeeper_key` Key to Success (task)
                - `workshop/shops/first_sale` First Sale (goal, pending)
            - `workshop/shops/cash_register` Ka-ching! (task)
        - `workshop/shops/bandana` Bandana Bandit (task)
            - `workshop/shops/box_costume` Cardboard Couture (task)

| id | EN | FR | icon | frame | hidden | parent | criteria (all / any) | reward | status |
|---|---|---|---|---|---|---|---|---|---|
| `workshop/root` | **Party Workshop**<br>Make the board look like a party | **Atelier de fête**<br>Donner un air de fête au plateau | `steveparty:light_blue_plastic_block` | task | no | — | any:<br>has_workshop_item: `minecraft:inventory_changed` {items: [{items: [steveparty:plastic_pellets, steveparty:stencil, … (58)]}]} | — | data |
| `workshop/plastic/pellets` | **Fantastic Plastic**<br>Smelt sugar cane into Plastic Pellets | **Fantastique plastique**<br>Cuire de la canne à sucre en Granulés de plastique | `steveparty:plastic_pellets` | task | no | `workshop/root` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:plastic_pellets}]} | — | data |
| `workshop/plastic/block` | **Brick by Brick**<br>Craft a Plastic Block | **Brique par brique**<br>Fabriquer un Bloc de plastique | `steveparty:red_plastic_block` | task | no | `workshop/plastic/pellets` | any:<br>has: `minecraft:inventory_changed` {items: [{items: #steveparty:plastic_blocks}]} | — | data |
| `workshop/plastic/all_colours` | **Taste the Rainbow**<br>Collect all 16 colours of Plastic Block | **Arc-en-ciel**<br>Réunir les 16 couleurs de Bloc de plastique | `steveparty:lime_plastic_block` | goal | no | `workshop/plastic/block` | all:<br>white: `minecraft:inventory_changed` {items: [{items: steveparty:white_plastic_block}]}<br>orange: `minecraft:inventory_changed` {items: [{items: steveparty:orange_plastic_block}]}<br>magenta: `minecraft:inventory_changed` {items: [{items: steveparty:magenta_plastic_block}]}<br>light_blue: `minecraft:inventory_changed` {items: [{items: steveparty:light_blue_plastic_block}]}<br>yellow: `minecraft:inventory_changed` {items: [{items: steveparty:yellow_plastic_block}]}<br>lime: `minecraft:inventory_changed` {items: [{items: steveparty:lime_plastic_block}]}<br>pink: `minecraft:inventory_changed` {items: [{items: steveparty:pink_plastic_block}]}<br>gray: `minecraft:inventory_changed` {items: [{items: steveparty:gray_plastic_block}]}<br>light_gray: `minecraft:inventory_changed` {items: [{items: steveparty:light_gray_plastic_block}]}<br>cyan: `minecraft:inventory_changed` {items: [{items: steveparty:cyan_plastic_block}]}<br>purple: `minecraft:inventory_changed` {items: [{items: steveparty:purple_plastic_block}]}<br>blue: `minecraft:inventory_changed` {items: [{items: steveparty:blue_plastic_block}]}<br>brown: `minecraft:inventory_changed` {items: [{items: steveparty:brown_plastic_block}]}<br>green: `minecraft:inventory_changed` {items: [{items: steveparty:green_plastic_block}]}<br>red: `minecraft:inventory_changed` {items: [{items: steveparty:red_plastic_block}]}<br>black: `minecraft:inventory_changed` {items: [{items: steveparty:black_plastic_block}]} | 25 xp, coins_10 | data |
| `workshop/plastic/hop_switch` | **Boing!**<br>Step on a Hop Switch | **Boing !**<br>Monter sur un Interrupteur sauteur | `steveparty:hop_switch` | task | no | `workshop/plastic/block` | any:<br>on_switch: `minecraft:enter_block` {block: steveparty:hop_switch} | — | data |
| `workshop/plastic/triple_jump_shoes` | **Wahoo!**<br>Wear the Triple Jump Shoes | **Wahou !**<br>Porter les Chaussures de triple saut | `steveparty:triple_jump_shoes` | task | no | `workshop/plastic/hop_switch` | any:<br>wearing: `minecraft:location` {player: [{condition: minecraft:entity_properties, entity: this, predicate: {equipment: {feet: {items: steveparty:triple_jump_shoes}}}}]} | — | data |
| `workshop/plastic/looting_box` | **Bonk!**<br>Hit a Looting Box with your head from below | **Bonk !**<br>Frapper une Boîte à butin de la tête, par en dessous | `steveparty:looting_box` | task | no | `workshop/plastic/hop_switch` | any:<br>bonk: `steveparty:looting_box_hit` | — | **pending** (custom trigger) |
| `workshop/pipes/pipe` | **Plumber's Apprentice**<br>Craft a Pipe | **Apprenti plombier**<br>Fabriquer un Tuyau | `steveparty:green_pipe` | task | no | `workshop/root` | any:<br>has: `minecraft:inventory_changed` {items: [{items: [steveparty:white_pipe, steveparty:white_windowed_pipe, … (49)]}]} | — | data |
| `workshop/pipes/travel` | **Down the Tubes**<br>Travel through a pipe | **Dans les tuyaux**<br>Voyager dans un tuyau | `steveparty:green_windowed_pipe` | task | no | `workshop/pipes/pipe` | any:<br>riding: `minecraft:started_riding` {player: [{condition: minecraft:entity_properties, entity: this, predicate: {vehicle: {type: steveparty:pipe_carrier}}}]} | — | data |
| `workshop/signs/stencil` | **Stencil Me In**<br>Get a Stencil | **Au pochoir**<br>Obtenir un Pochoir | `steveparty:stencil` | task | no | `workshop/root` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:stencil}]} | — | data |
| `workshop/signs/stencil_maker` | **Pattern Recognition**<br>Place a Stencil Maker | **Motif reconnu**<br>Poser une Fabrique de pochoirs | `steveparty:stencil_maker` | task | no | `workshop/signs/stencil` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:stencil_maker}}}]} | — | data |
| `workshop/signs/stencil_hammer` | **Hammer Time**<br>Stamp a board tile with the Stencil Hammer | **Coup de marteau**<br>Tamponner une tuile de plateau au Marteau à pochoirs | `steveparty:stencil_gun` | task | no | `workshop/signs/stencil` | any:<br>stamped: `minecraft:item_used_on_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: [steveparty:tile, steveparty:advanced_tile]}}}, {condition: minecraft:match_tool, predicate: {items: steveparty:stencil_gun}}]} | — | data |
| `workshop/signs/all_signs` | **Sign Language**<br>Place an Easel Sign, a Wooden Panel, a Wooden Cutout Panel, a Rock Sign and a Plastic Road Sign | **Langage des signes**<br>Poser un Chevalet, un Grand panneau en bois, un Panneau en bois découpé, une Pierre gravée et un Panneau routier en plastique | `steveparty:oak_easel_sign` | goal | no | `workshop/signs/stencil` | all:<br>easel: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: [steveparty:oak_easel_sign, steveparty:spruce_easel_sign, … (11)]}}}]}<br>wooden_panel: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:wooden_panel}}}]}<br>cutout_panel: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:wooden_cutout_panel}}}]}<br>rock_sign: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:rock_sign}}}]}<br>road_sign: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:plastic_road_sign}}}]} | 25 xp, coins_10 | data |
| `workshop/shops/villager_block` | **Thinking Inside the Box**<br>Get a Villager Block (a piston may help...) | **Mis en boîte**<br>Obtenir un Bloc de villageois (un piston peut aider...) | `steveparty:villager_block` | task | no | `workshop/root` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:villager_block}]} | — | data |
| `workshop/shops/boxed_trader` | **Special Delivery**<br>Fall onto a Villager Block to turn it into a Boxed Trader | **Livraison spéciale**<br>Tomber sur un Bloc de villageois pour en faire un Marchand en carton | `steveparty:boxed_trader_spawn_egg` | goal | no | `workshop/shops/villager_block` | any:<br>created: `steveparty:boxed_trader_created` | 25 xp, coins_10 | **pending** (custom trigger) |
| `workshop/shops/buy` | **Valued Customer**<br>Buy something from a Boxed Trader | **Client fidèle**<br>Acheter quelque chose à un Marchand en carton | `steveparty:coin` | task | no | `workshop/shops/villager_block` | any:<br>bought: `minecraft:villager_trade` {villager: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:boxed_trader}}]} | — | data |
| `workshop/shops/trading_stall` | **Open for Business**<br>Place a Trading Stall | **Ouvert au public**<br>Poser un Étal du marchand | `steveparty:trading_stall` | task | no | `workshop/shops/villager_block` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:trading_stall}}}]} | — | data |
| `workshop/shops/shopkeeper_key` | **Key to Success**<br>Link a Shopkeeper Key to a Boxed Trader | **La clé du succès**<br>Lier une Clé du Marchand à un Marchand en carton | `steveparty:shopkeeper_key` | task | no | `workshop/shops/trading_stall` | any:<br>linked: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:boxed_trader}}], item: {items: steveparty:shopkeeper_key}} | — | data |
| `workshop/shops/cash_register` | **Ka-ching!**<br>Place a Cash Register | **Ka-tching !**<br>Poser une Caisse enregistreuse | `steveparty:cash_register` | task | no | `workshop/shops/trading_stall` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:cash_register}}}]} | — | data |
| `workshop/shops/first_sale` | **First Sale**<br>Have a customer buy from your Boxed Trader | **Première vente**<br>Qu'un client achète à votre Marchand en carton | `steveparty:cash_register` | goal | no | `workshop/shops/shopkeeper_key` | any:<br>sold: `steveparty:shop_sale` | 25 xp, coins_10 | **pending** (custom trigger) |
| `workshop/shops/bandana` | **Bandana Bandit**<br>Steal a Boxed Trader's bandana with shears | **Voleur de bandana**<br>Voler le bandana d'un Marchand en carton aux cisailles | `steveparty:bandana` | task | no | `workshop/shops/villager_block` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:bandana}]} | — | data |
| `workshop/shops/box_costume` | **Cardboard Couture**<br>Shear the box off a bald Boxed Trader | **Haute couture en carton**<br>Tondre la boîte d'un Marchand en carton déjà chauve | `steveparty:box_costume` | task | no | `workshop/shops/bandana` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:box_costume}]} | — | data |

### Tab `stars`: Wish Upon a Star / La Tête dans les étoiles

Background: `steveparty:textures/block/polished_black_concrete.png`. 31 advancements (18 data, 13 pending).


- `stars/root` Wish Upon a Star (task)
    - `stars/feed` Snack Time (task)
        - `stars/potion` Liquid Lunch (task)
        - `stars/burst` Pop Star (goal, pending)
            - `stars/burst_black` Supernova (challenge, hidden, pending)
    - `stars/tame` Star Buddy (task)
        - `stars/sit` Sit. Stay. Sparkle. (task)
        - `stars/tame_black` Dark Star (challenge)
        - `stars/tame_all` Constellation (challenge)
    - `stars/lift` Up, Up and Away (goal, pending)
    - `stars/fragment_stack` Stardust Hoarder (task)
        - `stars/star_block` Starry Night (task)
    - `stars/white_fragment` Prism (task)
        - `stars/all_fragments` Every Colour of the Sky (goal)
    - `stars/luck_potion` Lucky Brew (task)
    - `stars/power_star` Power Star (task)
        - `stars/gravity_core` Centre of Gravity (task)
            - `stars/pocket_planet` Pocket Planet (task)
        - `stars/dice_forge` Star Forge (task)
            - `stars/ignition` Ignition (task)
                - `stars/forge_die` Custom Made (goal, pending)
                    - `stars/loaded_die` Loaded Dice (task, hidden, pending)
                - `stars/forge_max` Sky High (goal, pending)
                    - `stars/forge_black` Black Hole Sun (challenge, pending)
                - `stars/dance` Dance Floor (task, pending)
                    - `stars/full_house` Full House (challenge, pending)
                - `stars/big_bang` Big Bang (task, hidden, pending)
    - `stars/ephemeride` Shooting Stars (task, pending)
    - `stars/telescope` Stargazer (task)
        - `stars/telescope_found` Follow That Star (task, pending)
            - `stars/landing_site` Where Stars Fall (goal, pending)

| id | EN | FR | icon | frame | hidden | parent | criteria (all / any) | reward | status |
|---|---|---|---|---|---|---|---|---|---|
| `stars/root` | **Wish Upon a Star**<br>Find a star fragment, or meet a Mula | **La Tête dans les étoiles**<br>Trouver un fragment d'étoile ou rencontrer une Mula | `steveparty:yellow_star_fragment` | task | no | — | any:<br>fragment: `minecraft:inventory_changed` {items: [{items: [steveparty:white_star_fragment, steveparty:orange_star_fragment, … (16)]}]}<br>met_mula: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula}}]} | — | data |
| `stars/feed` | **Snack Time**<br>Feed a Mula something of its own colour | **L'heure du goûter**<br>Nourrir une Mula avec quelque chose de sa couleur | `minecraft:cookie` | task | no | `stars/root` | any:<br>blue: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:0}}}], item: {items: [minecraft:cod, minecraft:cooked_cod, minecraft:tropical_fish]}}<br>red: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:1}}}], item: {items: [minecraft:apple, minecraft:sweet_berries, … (8)]}}<br>green: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:2}}}], item: {items: [minecraft:melon_slice, minecraft:poisonous_potato, minecraft:wheat_seeds, minecraft:torchflower_seeds]}}<br>yellow: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:3}}}], item: {items: [minecraft:golden_apple, minecraft:enchanted_golden_apple, … (8)]}}<br>purple: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:4}}}], item: {items: [minecraft:chorus_fruit, minecraft:beetroot, minecraft:beetroot_soup]}}<br>black: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:5}}}], item: {items: [minecraft:dried_kelp, minecraft:cookie, … (7)]}} | — | data |
| `stars/potion` | **Liquid Lunch**<br>Give a potion to a Mula | **Déjeuner liquide**<br>Donner une potion à une Mula | `minecraft:potion` | task | no | `stars/feed` | any:<br>potion: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula}}], item: {items: minecraft:potion}} | — | data |
| `stars/burst` | **Pop Star**<br>Feed a Mula until it bursts into a shooting star | **Étoile filante**<br>Nourrir une Mula jusqu'à ce qu'elle éclate en étoile filante | `steveparty:blue_star_fragment` | goal | no | `stars/feed` | any:<br>burst: `steveparty:mula_burst` | 25 xp, star_fragment | **pending** (custom trigger) |
| `stars/burst_black` | **Supernova**<br>Feed a black Mula until it bursts | **Supernova**<br>Nourrir une Mula noire jusqu'à ce qu'elle éclate | `steveparty:black_star_fragment` | challenge | yes | `stars/burst` | any:<br>burst: `steveparty:mula_burst` {mula: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:5}}}]} | 100 xp, star_fragments | **pending** (custom trigger) |
| `stars/tame` | **Star Buddy**<br>Tame a Mula with a star fragment of its colour | **Copain des étoiles**<br>Apprivoiser une Mula avec un fragment d'étoile de sa couleur | `steveparty:red_star_fragment` | task | no | `stars/root` | any:<br>tamed: `minecraft:tame_animal` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula}}]} | — | data |
| `stars/sit` | **Sit. Stay. Sparkle.**<br>Make your Mula sit | **Assis. Pas bouger. Brille.**<br>Faire asseoir votre Mula | `minecraft:lead` | task | no | `stars/tame` | any:<br>sitting: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Sitting:1b}}}]} | — | data |
| `stars/tame_black` | **Dark Star**<br>Tame a black Mula | **Étoile noire**<br>Apprivoiser une Mula noire | `steveparty:black_star_fragment` | challenge | no | `stars/tame` | any:<br>tamed: `minecraft:tame_animal` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:5}}}]} | 100 xp, star_fragments | data |
| `stars/tame_all` | **Constellation**<br>Tame a Mula of every colour | **Constellation**<br>Apprivoiser une Mula de chaque couleur | `steveparty:purple_star_fragment` | challenge | no | `stars/tame` | all:<br>blue: `minecraft:tame_animal` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:0}}}]}<br>red: `minecraft:tame_animal` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:1}}}]}<br>green: `minecraft:tame_animal` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:2}}}]}<br>yellow: `minecraft:tame_animal` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:3}}}]}<br>purple: `minecraft:tame_animal` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:4}}}]}<br>black: `minecraft:tame_animal` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:5}}}]} | 100 xp, star_fragments | data |
| `stars/lift` | **Up, Up and Away**<br>Get lifted into the night sky by Mulas on leads | **Là-haut**<br>Vous faire emporter dans le ciel nocturne par des Mulas en laisse | `minecraft:lead` | goal | no | `stars/root` | any:<br>lifted: `steveparty:mula_lifted` | 25 xp, star_fragment | **pending** (custom trigger) |
| `stars/fragment_stack` | **Stardust Hoarder**<br>Hold 64 star fragments of the same colour | **Collectionneur de poussière d'étoile**<br>Avoir 64 fragments d'étoile d'une même couleur | `steveparty:green_star_fragment` | task | no | `stars/root` | any:<br>stack: `minecraft:inventory_changed` {items: [{items: [steveparty:white_star_fragment, steveparty:orange_star_fragment, … (16)], count: {min: 64}}]} | — | data |
| `stars/white_fragment` | **Prism**<br>Mix red, blue and yellow fragments into white ones | **Prisme**<br>Mélanger des fragments rouge, bleu et jaune en fragments blancs | `steveparty:white_star_fragment` | task | no | `stars/root` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:white_star_fragment}]} | — | data |
| `stars/all_fragments` | **Every Colour of the Sky**<br>Collect star fragments of all 16 colours | **Toutes les couleurs du ciel**<br>Réunir des fragments d'étoile des 16 couleurs | `steveparty:magenta_star_fragment` | goal | no | `stars/white_fragment` | all:<br>white: `minecraft:inventory_changed` {items: [{items: steveparty:white_star_fragment}]}<br>orange: `minecraft:inventory_changed` {items: [{items: steveparty:orange_star_fragment}]}<br>magenta: `minecraft:inventory_changed` {items: [{items: steveparty:magenta_star_fragment}]}<br>light_blue: `minecraft:inventory_changed` {items: [{items: steveparty:light_blue_star_fragment}]}<br>yellow: `minecraft:inventory_changed` {items: [{items: steveparty:yellow_star_fragment}]}<br>lime: `minecraft:inventory_changed` {items: [{items: steveparty:lime_star_fragment}]}<br>pink: `minecraft:inventory_changed` {items: [{items: steveparty:pink_star_fragment}]}<br>gray: `minecraft:inventory_changed` {items: [{items: steveparty:gray_star_fragment}]}<br>light_gray: `minecraft:inventory_changed` {items: [{items: steveparty:light_gray_star_fragment}]}<br>cyan: `minecraft:inventory_changed` {items: [{items: steveparty:cyan_star_fragment}]}<br>purple: `minecraft:inventory_changed` {items: [{items: steveparty:purple_star_fragment}]}<br>blue: `minecraft:inventory_changed` {items: [{items: steveparty:blue_star_fragment}]}<br>brown: `minecraft:inventory_changed` {items: [{items: steveparty:brown_star_fragment}]}<br>green: `minecraft:inventory_changed` {items: [{items: steveparty:green_star_fragment}]}<br>red: `minecraft:inventory_changed` {items: [{items: steveparty:red_star_fragment}]}<br>black: `minecraft:inventory_changed` {items: [{items: steveparty:black_star_fragment}]} | 25 xp, star_fragment | data |
| `stars/star_block` | **Starry Night**<br>Place a Star Fragments Block | **Nuit étoilée**<br>Poser un Bloc de fragments d'étoile | `steveparty:cyan_star_fragments_block` | task | no | `stars/fragment_stack` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: [steveparty:white_star_fragments_block, steveparty:orange_star_fragments_block, … (16)]}}}]} | — | data |
| `stars/luck_potion` | **Lucky Brew**<br>Brew a Potion of Luck with a star fragment | **Breuvage chanceux**<br>Brasser une Potion de chance avec un fragment d'étoile | `minecraft:potion` | task | no | `stars/root` | any:<br>brewed: `minecraft:brewed_potion` {potion: minecraft:luck} | — | data |
| `stars/power_star` | **Power Star**<br>Assemble a Power Star | **Super étoile**<br>Assembler une Super étoile | `steveparty:power_star` | task | no | `stars/root` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:power_star}]} | — | data |
| `stars/gravity_core` | **Centre of Gravity**<br>Craft a Gravity Core | **Centre de gravité**<br>Fabriquer un Noyau de gravité | `steveparty:gravity_core` | task | no | `stars/power_star` | any:<br>has: `minecraft:inventory_changed` {items: [{items: steveparty:gravity_core}]} | — | data |
| `stars/pocket_planet` | **Pocket Planet**<br>Place a Gravity Core and watch things orbit it | **Planète de poche**<br>Poser un Noyau de gravité et regarder tout tourner autour | `steveparty:gravity_core` | task | no | `stars/gravity_core` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:gravity_core}}}]} | — | data |
| `stars/dice_forge` | **Star Forge**<br>Place a Dice Forge | **Forge stellaire**<br>Poser une Forge à dés | `steveparty:dice_forge` | task | no | `stars/power_star` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:dice_forge}}}]} | — | data |
| `stars/ignition` | **Ignition**<br>Activate a Dice Forge with a Gravity Core | **Mise à feu**<br>Activer une Forge à dés avec un Noyau de gravité | `steveparty:gravity_core` | task | no | `stars/dice_forge` | any:<br>core: `minecraft:item_used_on_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:dice_forge}}}, {condition: minecraft:match_tool, predicate: {items: steveparty:gravity_core}}]} | — | data |
| `stars/forge_die` | **Custom Made**<br>Forge your own die | **Sur mesure**<br>Forger votre propre dé | `steveparty:default_dice` | goal | no | `stars/ignition` | any:<br>forged: `steveparty:dice_forged` | 25 xp, star_fragment | **pending** (custom trigger) |
| `stars/loaded_die` | **Loaded Dice**<br>Forge a die with a single face | **Dé pipé**<br>Forger un dé à une seule face | `steveparty:dice_face_6` | task | yes | `stars/forge_die` | any:<br>forged: `steveparty:dice_forged` {faces: 1} | — | **pending** (custom trigger) |
| `stars/forge_max` | **Sky High**<br>Raise a Dice Forge's core to its maximum height | **Au septième ciel**<br>Faire monter le noyau d'une Forge à dés à sa hauteur maximale | `steveparty:dice_forge` | goal | no | `stars/ignition` | any:<br>max: `steveparty:dice_forge_level` {max_level: true} | 25 xp, star_fragment | **pending** (custom trigger) |
| `stars/forge_black` | **Black Hole Sun**<br>Max out a Dice Forge with 5 black star fragments | **Trou noir**<br>Monter une Forge à dés au maximum avec 5 fragments d'étoile noirs | `steveparty:black_star_fragment` | challenge | no | `stars/forge_max` | any:<br>black: `steveparty:dice_forge_level` {max_level: true, black_fragments: {min: 4}} | 100 xp, star_fragments | **pending** (custom trigger) |
| `stars/dance` | **Dance Floor**<br>Watch Mulas dance around a Dice Forge | **Piste de danse**<br>Regarder des Mulas danser autour d'une Forge à dés | `minecraft:note_block` | task | no | `stars/ignition` | any:<br>dance: `steveparty:mula_dance` | — | **pending** (custom trigger) |
| `stars/full_house` | **Full House**<br>Watch 12 Mulas dance at once around a Dice Forge | **Salle comble**<br>Regarder 12 Mulas danser en même temps autour d'une Forge à dés | `minecraft:jukebox` | challenge | no | `stars/dance` | any:<br>dance: `steveparty:mula_dance` {dancers: {min: 12}} | 100 xp, star_fragments | **pending** (custom trigger) |
| `stars/big_bang` | **Big Bang**<br>Blow up the floating core of a Dice Forge | **Big bang**<br>Faire exploser le noyau flottant d'une Forge à dés | `minecraft:tnt` | task | yes | `stars/ignition` | any:<br>boom: `steveparty:forge_core_exploded` | — | **pending** (custom trigger) |
| `stars/ephemeride` | **Shooting Stars**<br>Witness a night of shooting stars | **Pluie d'étoiles**<br>Assister à une nuit d'étoiles filantes | `minecraft:firework_rocket` | task | no | `stars/root` | any:<br>seen: `steveparty:ephemeride_seen` | — | **pending** (custom trigger) |
| `stars/telescope` | **Stargazer**<br>Place a Telescope | **Astronome**<br>Poser un Télescope | `steveparty:telescope` | task | no | `stars/root` | any:<br>placed: `minecraft:placed_block` {location: [{condition: minecraft:location_check, predicate: {block: {blocks: steveparty:telescope}}}]} | — | data |
| `stars/telescope_found` | **Follow That Star**<br>Light a guide star by tracking a past shooting star through a Telescope | **Suivez cette étoile**<br>Allumer une étoile guide en suivant une ancienne étoile filante au Télescope | `minecraft:spyglass` | task | no | `stars/telescope` | any:<br>found: `steveparty:telescope_star_found` | — | **pending** (custom trigger) |
| `stars/landing_site` | **Where Stars Fall**<br>Reach the place where a flock of Mulas came down | **Là où tombent les étoiles**<br>Atteindre l'endroit où une nuée de Mulas est descendue | `minecraft:compass` | goal | no | `stars/telescope_found` | any:<br>reached: `steveparty:mula_site_reached` | 25 xp, star_fragment | **pending** (custom trigger) |

### Tab `wild_cards`: Wild Cards / Jokers

Background: `steveparty:textures/block/plastic_block/magenta_plastic_block.png`. 17 advancements (7 data, 10 pending).


- `wild_cards/root` Wild Cards (task)
    - `wild_cards/obsidian_trader` Rock-Bottom Prices (challenge, hidden, pending)
    - `wild_cards/spin_doctor` Spin Doctor (goal, hidden, pending)
    - `wild_cards/warden_pawn` Tiny Terror (challenge, hidden, pending)
    - `wild_cards/nice_try` Nice Try (task, hidden, pending)
    - `wild_cards/mula_pawn` Collector's Figurine (task, hidden, pending)
    - `wild_cards/trader_pawn` Cardboard Cut-Out (task, hidden, pending)
    - `wild_cards/library_rules` Library Rules (goal, hidden)
    - `wild_cards/leap_of_faith` Leap of Faith (goal, hidden)
    - `wild_cards/undercover` Undercover Boss (task, hidden)
    - `wild_cards/bandana` Dressed to Steal (task, hidden)
    - `wild_cards/closing_down` Closing-Down Sale (task, hidden)
    - `wild_cards/golden_treat` Fit for a Queen (task, hidden)
    - `wild_cards/roll_the_bones` Roll the Bones (task, hidden, pending)
    - `wild_cards/broke` Broke (task, hidden, pending)
    - `wild_cards/last_place` Better Luck Next Time (task, hidden, pending)
    - `wild_cards/taking_part` It's the Taking Part That Counts (task, hidden, pending)

| id | EN | FR | icon | frame | hidden | parent | criteria (all / any) | reward | status |
|---|---|---|---|---|---|---|---|---|---|
| `wild_cards/root` | **Wild Cards**<br>Secrets, stunts and silly things | **Jokers**<br>Secrets, exploits et bêtises | `steveparty:box_costume` | task | no | — | any:<br>has_party_item: `minecraft:inventory_changed` {items: [{items: [steveparty:tile, steveparty:advanced_tile, … (87)]}]} | — | data |
| `wild_cards/obsidian_trader` | **Rock-Bottom Prices**<br>Make a Boxed Trader on obsidian... it's a long way down | **Prix cassés**<br>Faire un Marchand en carton sur de l'obsidienne... la chute est longue | `minecraft:obsidian` | challenge | yes | `wild_cards/root` | any:<br>created: `steveparty:boxed_trader_created` {block: {blocks: minecraft:obsidian}} | 50 xp, coins_10 | **pending** (custom trigger) |
| `wild_cards/spin_doctor` | **Spin Doctor**<br>Get a trading villager to orbit a Dice Forge | **Tourner en rond**<br>Faire tourner un villageois marchand en orbite autour d'une Forge à dés | `steveparty:dice_forge` | goal | yes | `wild_cards/root` | any:<br>orbit: `steveparty:entity_orbiting` {source: dice_forge, entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: minecraft:villager}}, {condition: minecraft:inverted, term: {condition: minecraft:entity_properties, entity: this, predicate: {nbt: {VillagerData:{profession:'minecraft:none'}}}}}, {condition: minecraft:inverted, term: {condition: minecraft:entity_properties, entity: this, predicate: {nbt: {VillagerData:{profession:'minecraft:nitwit'}}}}}]} | 50 xp, coins_10 | **pending** (custom trigger) |
| `wild_cards/warden_pawn` | **Tiny Terror**<br>Turn a Warden into a pawn | **Petite terreur**<br>Transformer un Warden en pion | `minecraft:sculk_shrieker` | challenge | yes | `wild_cards/root` | any:<br>warden: `steveparty:pawn_spell` {result: tokenized, entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: minecraft:warden}}]} | 50 xp, coins_10 | **pending** (custom trigger) |
| `wild_cards/nice_try` | **Nice Try**<br>Try to turn the Ender Dragon or the Wither into a pawn | **Bien essayé**<br>Tenter de transformer le Dragon de l'Ender ou le Wither en pion | `minecraft:dragon_head` | task | yes | `wild_cards/root` | any:<br>refused: `steveparty:pawn_spell` {result: refused} | 10 xp | **pending** (custom trigger) |
| `wild_cards/mula_pawn` | **Collector's Figurine**<br>Turn a Mula into a pawn | **Figurine de collection**<br>Transformer une Mula en pion | `steveparty:mula_spawn_egg` | task | yes | `wild_cards/root` | any:<br>mula: `steveparty:pawn_spell` {result: tokenized, entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula}}]} | 10 xp | **pending** (custom trigger) |
| `wild_cards/trader_pawn` | **Cardboard Cut-Out**<br>Turn a Boxed Trader into a pawn | **Silhouette en carton**<br>Transformer un Marchand en carton en pion | `steveparty:boxed_trader_spawn_egg` | task | yes | `wild_cards/root` | any:<br>trader: `steveparty:pawn_spell` {result: tokenized, entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:boxed_trader}}]} | 10 xp | **pending** (custom trigger) |
| `wild_cards/library_rules` | **Library Rules**<br>Silence a Warden with the Plunger | **Silence, on lit**<br>Réduire un Warden au silence avec la Ventouse | `minecraft:sculk_sensor` | goal | yes | `wild_cards/root` | any:<br>silenced: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: minecraft:warden, nbt: {Silent:1b}}}], item: {items: steveparty:plunger}} | 50 xp, coins_10 | data |
| `wild_cards/leap_of_faith` | **Leap of Faith**<br>Fall at least 30 blocks and land right on top of a Goal Pole | **Saut de l'ange**<br>Tomber d'au moins 30 blocs et atterrir pile au sommet d'un Mât d'arrivée | `steveparty:goal_pole` | goal | yes | `wild_cards/root` | any:<br>leap: `minecraft:fall_from_height` {distance: {y: {min: 30.0}}, player: [{condition: minecraft:entity_properties, entity: this, predicate: {stepping_on: {block: {blocks: steveparty:goal_pole}}}}]} | 50 xp, coins_10 | data |
| `wild_cards/undercover` | **Undercover Boss**<br>Wear a Box Costume | **Patron infiltré**<br>Porter un Costume de carton | `steveparty:box_costume` | task | yes | `wild_cards/root` | any:<br>wearing: `minecraft:location` {player: [{condition: minecraft:entity_properties, entity: this, predicate: {equipment: {chest: {items: steveparty:box_costume}}}}]} | 10 xp | data |
| `wild_cards/bandana` | **Dressed to Steal**<br>Wear a stolen bandana | **Tenue de voleur**<br>Porter un bandana volé | `steveparty:bandana` | task | yes | `wild_cards/root` | any:<br>wearing: `minecraft:location` {player: [{condition: minecraft:entity_properties, entity: this, predicate: {equipment: {head: {items: steveparty:bandana}}}}]} | 10 xp | data |
| `wild_cards/closing_down` | **Closing-Down Sale**<br>Defeat a Boxed Trader... you monster | **Liquidation totale**<br>Vaincre un Marchand en carton... quel monstre | `minecraft:iron_sword` | task | yes | `wild_cards/root` | any:<br>killed: `minecraft:player_killed_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:boxed_trader}}]} | — | data |
| `wild_cards/golden_treat` | **Fit for a Queen**<br>Feed an Enchanted Golden Apple to a yellow Mula | **Digne d'une reine**<br>Donner une Pomme dorée enchantée à une Mula jaune | `minecraft:enchanted_golden_apple` | task | yes | `wild_cards/root` | any:<br>fed: `minecraft:player_interacted_with_entity` {entity: [{condition: minecraft:entity_properties, entity: this, predicate: {type: steveparty:mula, nbt: {Variant:3}}}], item: {items: minecraft:enchanted_golden_apple}} | 10 xp | data |
| `wild_cards/roll_the_bones` | **Roll the Bones**<br>Roll a die over a skeleton's head | **Jeu d'osselets**<br>Lancer un dé au-dessus de la tête d'un squelette | `minecraft:bone` | task | yes | `wild_cards/root` | any:<br>rolled: `steveparty:dice_rolled` {target: [{condition: minecraft:entity_properties, entity: this, predicate: {type: #minecraft:skeletons}}]} | 10 xp | **pending** (custom trigger) |
| `wild_cards/broke` | **Broke**<br>Lose your last coin to a Debt face | **Fauché**<br>Perdre votre dernière pièce sur une face Dette | `steveparty:debt_dice_face_10` | task | yes | `wild_cards/root` | any:<br>broke: `steveparty:dice_coins` {coins: {max: -1}, balance: 0} | 10 xp | **pending** (custom trigger) |
| `wild_cards/last_place` | **Better Luck Next Time**<br>Finish a party in last place | **Ce sera pour la prochaine fois**<br>Terminer une partie à la dernière place | `steveparty:bronze_podium` | task | yes | `wild_cards/root` | any:<br>last: `steveparty:party_ended` {last: true, pawns: {min: 2}} | coins_3 | **pending** (custom trigger) |
| `wild_cards/taking_part` | **It's the Taking Part That Counts**<br>Finish a mini-game without a place | **L'important, c'est de participer**<br>Finir un mini-jeu sans obtenir de place | `steveparty:podium` | task | yes | `wild_cards/root` | any:<br>participant: `steveparty:minigame_ended` {place: 0, practice: false} | 10 xp | **pending** (custom trigger) |

## Custom triggers: implementation spec (after the 1.21.1 port)

The pending JSONs already use these ids and conditions.

**Paths:** class paths are relative to `src/main/java/fr/lordfinn/steveparty/`. Method names are from the 1.21.3 code; check them again on the ported branch.

### Common infrastructure

- **Package and registration:** new package `fr.lordfinn.steveparty.advancement`.
  - `ModCriteria` registers every trigger, e.g. `public static final PawnSpellCriterion PAWN_SPELL = Criteria.register("steveparty:pawn_spell", new PawnSpellCriterion());`.
  - Call `ModCriteria.initialize()` from `Steveparty#onInitialize`, next to `ModScoreboardCriteria.initialize()`.
- **Criterion classes:** each one is `XCriterion extends AbstractCriterion<XCriterion.Conditions>`.
  - `getConditionsCodec()` returns `Conditions.CODEC`.
  - `Conditions` is a record implementing `AbstractCriterion.Conditions`. Its first field is `Optional<LootContextPredicate> player`: `EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player")`.
  - Every other field is optional: absent means "any".
- **Field types:**

  | Field | Codec | Matching / validation |
  |---|---|---|
  | Entity | `EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC` | match with `predicate.test(EntityPredicate.createAdvancementEntityLootContext(player, entity))`; validate in `validate(LootContextPredicateValidator)` with `validator.validateEntityPredicate(entity, ".entity")` |
  | Int range | `NumberRange.IntRange.CODEC` | |
  | Double range | `NumberRange.DoubleRange.CODEC` | |
  | Item | `ItemPredicate.CODEC` | |
  | Enum-like string | `Codec.STRING` | compare lower-case |
  | Boolean | `Codec.BOOL` | |
- **Firing:** a public `trigger(ServerPlayerEntity player, …)` calls `this.trigger(player, c -> c.matches(…))`.
  - A trigger costs almost nothing when no player has an unfinished advancement listening to it.
  - Still, never compute anything expensive (entity NBT, scans) before calling `trigger`. Do that work lazily inside `matches`.
- **Player only:** fire on the server, only for a `ServerPlayerEntity`. Owners are often stored as a UUID: resolve them with `server.getPlayerManager().getPlayer(uuid)` and skip them when offline.
- **Shared class:** five triggers carry nothing but the player. They can share one `PlayerOnlyCriterion` class registered five times: `ephemeride_seen`, `telescope_star_found`, `mula_site_reached`, `minigame_ready`, `pole_goal_reached`. `looting_box_hit` can be a sixth.

### The 23 triggers

| Trigger id | Conditions (besides `player`) | Fire at | Used by |
|---|---|---|---|
| `steveparty:pawn_spell` | `result`: `tokenized` \| `resized` \| `refused`; `entity` (the mob); `size` (double range, largest dimension of the pawn in blocks after the spell) | `items/custom/TokenizerWandItem#castSpell(ServerPlayerEntity, entityId, size, color)`: SpellResult TOKENIZED → `tokenized`, RESIZED → `resized`. For `refused`, fire where the server refuses a boss (`isBoss()`: Ender Dragon or Wither) for that player. If the refusal only happens on the client, when the spell is opened, add the trigger to the server-side wand click (`PlayerWandInteractMixin`, and `TokenizerFlare` when it hits a boss) | main/pawns/first_pawn, tiny_pawn, giant_pawn; wild_cards/warden_pawn, nice_try, mula_pawn, trader_pawn |
| `steveparty:dice_rolled` | `dice_count` (int range: 1 single or forged, 2 double, 3 triple); `total` (int range: sum of the rolled faces' numbers, before modules such as Reversed); `all_equal` (bool: ≥ 2 dice and all rolled faces identical); `forged` (bool: a die of the throw has its own faces); `has_face` (`normal` \| `premium` \| `cursed` \| `blank` \| `coin` \| `debt` \| `swap`, a `DiceFacesComponent.Kind` among the rolled faces); `target` (entity the die floated over); `in_party` (bool) | `entities/custom/DiceEntity#onRollFinished(List<DiceFace>)`, once per throw (on the lead die: `lead() == this`), player = `getOnlineOwner()`. With Lucky / Reroll / Choice, fire on the kept result (`dice/DiceRollSequence#finish`), not on discarded rolls | main/dice/roll_ten, three_of_a_kind, thirty, snake_eyes, swap; wild_cards/roll_the_bones |
| `steveparty:dice_coins` | `coins` (int range: coins actually given, negative = lost); `balance` (int range: coins held afterwards, counted with the party's currency item) | `service/DiceRollEffects#applyCoins(world, token, rollerUUID, coins)`, after the transfer | wild_cards/broke |
| `steveparty:dice_forged` | `die` (item predicate); `faces` (int range: face slots used); `modules` (int range: modules on the die) | `screen_handlers/custom/DiceForgeScreenHandler`: `onTakeItem(PlayerEntity, ItemStack)` of the output slot (taken by hand or shift-click; hoppers don't count) | stars/forge_die, loaded_die |
| `steveparty:dice_forge_level` | `fragments` (int range: altitude count 0-320, see `countAltitudeFragments`); `black_fragments` (int range: black fragments in the 5 fragment slots); `max_level` (bool: `isMaxLevel()`) | `blocks/custom/DiceForgeBlockEntity`: when the altitude count or the activation changes (fragment slots 13-16 in `setStack`, `activate()`, `updateCoreAltitude()`), compared with a cached last value, never every tick. Fire for every player within 16 blocks of the forge | stars/forge_max, forge_black |
| `steveparty:entity_orbiting` | `entity`; `source`: `dice_forge` \| `gravity_core` | `utils/GravityPull#pull(...)` on the server, when a non-player entity reaches its orbit (within `PULL_ORBIT` ± 0.5 of the core). Throttle: evaluate only when `world.getTime() % 20 == 0`, and at most once per entity per 100 ticks. Fire for every player within 32 blocks of the core | wild_cards/spin_doctor |
| `steveparty:forge_core_exploded` | `mulas` (int range: Mulas burst by `burstMulas`) | `entities/custom/ForgeCoreEntity#damage` → `DiceForgeBlockEntity#explodeCore(attacker)`, when `source.getAttacker()` is a player (arrows resolve to their shooter) | stars/big_bang |
| `steveparty:mula_burst` | `mula` (entity predicate; the colour is NBT `{Variant:N}`) | `entities/custom/MulaEntity#interactMob`, in the `getHunger() >= MAX_HUNGER` branch, before `setHunger(0)`; player = the feeder | stars/burst, burst_black |
| `steveparty:mula_lifted` | `mulas` (int range); `lift` (double range, total lift) | `entities/custom/MulaLift#update(ServerWorld, ServerPlayerEntity)`, where `state.carried` becomes true | stars/lift |
| `steveparty:ephemeride_seen` | — | `entities/custom/MulaEphemeride#wave(ServerWorld, ServerPlayerEntity, Event)`, on the player's first wave | stars/ephemeride |
| `steveparty:telescope_star_found` | — | `telescope/TelescopeService#found(ServerPlayerEntity, int)`, when it returns true | stars/telescope_found |
| `steveparty:mula_site_reached` | — | `telescope/TelescopeService#visits(ServerWorld)`, when `sites.visitAround(player.getUuid(), …)` returns true | stars/landing_site |
| `steveparty:mula_dance` | `dancers` (int range: Mulas dancing this dance at that forge, all rings); `dance` (string, e.g. `carousel_of_stars`) | `blocks/custom/DiceForgeBlockEntity#conductMulas()`, when a new dance starts (every 520 ticks), for players within 16 blocks horizontally | stars/dance, full_house |
| `steveparty:boxed_trader_created` | `block` (block predicate; only its `blocks` and `state` parts are matched, against the crushed block's state `trader.getBlockState()`, because the block is gone); `fall_distance` (double range) | `mixin/PlayerFallMixin#onTick`, right after `world.spawnEntity(trader)`; player = the falling `ServerPlayerEntity` | workshop/shops/boxed_trader; wild_cards/obsidian_trader |
| `steveparty:shop_sale` | `customer` (entity predicate, the buyer); `item` (item predicate, the item sold) | `entities/custom/BoxedTraderEntity#afterUsing(TradeOffer)`, plus `screen_handlers/custom/ShopStopScreenHandler#onPurchase()` if shop-stop purchases skip `afterUsing`. Player = the shop owner (NBT `ShopOwner`), if online. Not when the customer is the owner | workshop/shops/first_sale |
| `steveparty:party_started` | `pawns` (int range) | `blocks/custom/PartyController/PartyControllerEntity#boot()`, for each online owner in `getPartyData().getOwners(world)` | main/party/start |
| `steveparty:party_ended` | `rank` (int range, 1 = best, ties share a rank); `last` (bool: worst rank and not also 1st); `pawns` (int range); `rounds` (int range: rounds played) | `blocks/custom/PartyController/steps/EndPartyStep#start(controller)`, only at a normal end. Compute the standings like `PartyLiveData.ranks(...)` (stars, then coins, with the party's currency items). Fire for each online owner; an owner of several pawns uses their best rank | main/party/finish, win, marathon; wild_cards/last_place |
| `steveparty:pawn_landed` | `space`: `default` \| `start` \| `stop` \| `inventory` \| `shop` \| `advance_back` \| `replay` \| `teleport` (from `BoardSpaceType`); `status`: `good` \| `bad` \| `neutral` (the behaviour's `getStatus`); `in_party` (bool); `times_this_party` (int range: landings with this status for this owner in the current party, counted in the party data, reset at `boot()`; 0 outside a party) | `blocks/custom/boardspaces/BoardSpaceBlockEntity#onDestinationReached(token, partyController)`: end of a move only, not spaces passed over. Player = the pawn's owner (`TokenizedEntityInterface#steveparty$getTokenOwner`) | main/party/seeing_red, replay, teleport |
| `steveparty:minigame_ended` | `place` (int range, 1 = first, 0 = participant without a place); `format`: `teams` \| `free_for_all` \| `all_together`; `team_size` (int range: the player's side); `opponents` (int range: players on the other sides); `practice` (bool); `in_party` (bool) | `blocks/custom/PartyController/steps/MiniGamePartyStep#conclude(controller, finalPlaces)` (real round), `#practiceResults` (practice = true), `minigame/MiniGameTest#finish()` (in_party = false). For every participant; not when a round is stopped | main/minigames/play, test, win, team_win, one_vs_all; wild_cards/taking_part |
| `steveparty:minigame_ready` | — | `blocks/custom/PartyController/steps/MiniGamePartyStep#toggleReady(ServerPlayerEntity)`, when the player becomes ready (not when un-voting) | main/minigames/ready |
| `steveparty:podium_claimed` | `place` (int range, 1 = highest column) | `podium/Podiums#register(group, column, ServerPlayerEntity)`, with `group.placeOf(column)` | main/podium/first_place |
| `steveparty:pole_goal_reached` | — | `blocks/custom/GoalPoleBlockEntity#acceptPlayerPoints(holder, points)`, when it returns true. The holder is a scoreboard name: `getPlayerManager().getPlayer(holder)` | main/pole/goal |
| `steveparty:looting_box_hit` | — | `blocks/custom/LootingBoxBlockEntity#onPlayerInteract(ServerPlayerEntity)`, when it gives items | workshop/plastic/looting_box |

### Activating the pending advancements

1. Implement and register the triggers above.
2. Move the files: `git mv` everything under `docs/advancements-pending/data/steveparty/advancement/` to the same paths under `src/main/resources/data/steveparty/advancement/`. No file exists in both places, and the lang keys are already there.
3. Run `python scripts/validate_advancements.py`. It refuses a live advancement whose custom trigger isn't registered in Java (it looks for the id next to `Criteria.register`).
4. In game:
   - `/advancement grant @s everything` should show the 4 tabs without errors in the log.
   - Then play each feature once.
   - Practical checks: an Ender Dragon pawn attempt, a die rolled over a skeleton, a mini-game won 1 vs 3, a villager carried around a Dice Forge.

The trigger ids and condition names may be adjusted while implementing. If so, update the pending JSONs, `CUSTOM_TRIGGERS` in the validator and this document together.

Ideas left out on purpose, because the mod has no such mechanic:
- stealing a star;
- being eliminated from a mini-game;
- a triple-jump advancement: the combo is computed on the client only (`LivingEntityMixin#onJump`), so it would need a C2S packet. "Wear the Triple Jump Shoes" is used instead.

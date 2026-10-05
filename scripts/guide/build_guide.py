#!/usr/bin/env python3
"""Builds the Modonomicon guide book (EN/FR) and its milestone advancements.

The book only loads when Modonomicon is installed (the data under data/steveparty/modonomicon is ignored
otherwise). The advancements are plain vanilla advancements, without rewards.

Source of truth for the guide: edit the content below, then run
    python scripts/guide/build_guide.py
    python scripts/guide/check_guide.py

Output:
    src/main/resources/data/steveparty/modonomicon/books/guide/...   book, categories, entries
    src/main/resources/data/steveparty/advancement/guide/*.json        milestone advancements
    src/main/resources/assets/steveparty_guide/lang/{en_us,fr_fr}.json  all book + advancement texts
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src" / "main" / "resources"
BOOK_ID = "guide"
BOOK_DIR = RES / "data" / "steveparty" / "modonomicon" / "books" / BOOK_ID
ADV_DIR = RES / "data" / "steveparty" / "advancement" / "guide"
LANG_DIR = RES / "assets" / "steveparty_guide" / "lang"
P = "book.steveparty.guide"

# --------------------------------------------------------------------------------------------------
# Milestones: vanilla advancements under steveparty:guide/..., used as coarse unlock conditions.
# criteria: list of (name, kind, value); kind "has" = inventory_changed with item ids or a #tag,
# kind "placed" = placed_block with a block id. Any criterion completes the advancement (OR).
# --------------------------------------------------------------------------------------------------
MILESTONES = {
    "root": dict(
        parent=None, icon="steveparty:tile", frame="task", toast=True,
        criteria=[("has_board_item", "has", ["steveparty:plastic_pellets", "steveparty:tile",
                                             "steveparty:advanced_tile", "steveparty:check_point",
                                             "steveparty:board_space_behavior"])],
        en=("Steve Party Maker", "Get Plastic Pellets or a first board item"),
        fr=("Steve Party Maker", "Obtenir des granulés de plastique ou un premier objet de plateau"),
        lock=None),
    "lay_the_board": dict(
        parent="root", icon="steveparty:advanced_tile", frame="task", toast=True,
        criteria=[("placed_tile", "placed", "steveparty:tile"),
                  ("placed_advanced_tile", "placed", "steveparty:advanced_tile"),
                  ("placed_check_point", "placed", "steveparty:check_point")],
        en=("Lay the Board", "Place a Tile, an Advanced Tile or a Checkpoint"),
        fr=("Poser le plateau", "Poser une tuile, une tuile avancée ou un point de déclenchement"),
        lock=("Place a Tile to unlock this entry.", "Pose une tuile pour débloquer cette entrée.")),
    "make_a_pawn": dict(
        parent="root", icon="steveparty:tokenizer_wand", frame="task", toast=False,
        criteria=[("has_wand_or_token", "has", ["steveparty:tokenizer_wand", "steveparty:token"])],
        en=("Pionificus!", "Get a Tokenizer Wand or a Token"),
        fr=("Pionificus !", "Obtenir une baguette de pions ou un jeton"),
        lock=("Get a Tokenizer Wand or a Token to unlock this entry.",
              "Obtiens une baguette de pions ou un jeton pour débloquer cette entrée.")),
    "roll_the_dice": dict(
        parent="root", icon="steveparty:default_dice", frame="task", toast=False,
        criteria=[("has_dice", "has", ["steveparty:default_dice", "steveparty:double_dice",
                                       "steveparty:triple_dice", "steveparty:forged_dice"])],
        en=("Roll the Dice", "Get a die"),
        fr=("Lancer les dés", "Obtenir un dé"),
        lock=("Get a die to unlock this entry.", "Obtiens un dé pour débloquer cette entrée.")),
    "party_time": dict(
        parent="lay_the_board", icon="steveparty:party_controller", frame="goal", toast=True,
        criteria=[("placed_party_controller", "placed", "steveparty:party_controller")],
        en=("Party Time", "Place a Party Controller"),
        fr=("C'est la fête", "Poser un contrôleur de fête"),
        lock=("Place a Party Controller to unlock this entry.",
              "Pose un contrôleur de fête pour débloquer cette entrée.")),
    "mini_games": dict(
        parent="party_time", icon="steveparty:mini_game_page", frame="task", toast=True,
        criteria=[("has_page", "has", ["steveparty:mini_game_page", "steveparty:mini_games_catalogue"]),
                  ("placed_mini_game_controller", "placed", "steveparty:mini_game_controller")],
        en=("Let the Games Begin", "Get a Mini-Game Page or place a Mini-game Controller"),
        fr=("Que les jeux commencent", "Obtenir une page de mini-jeu ou poser un contrôleur de mini-jeu"),
        lock=("Get a Mini-Game Page or place a Mini-game Controller to unlock this entry.",
              "Obtiens une page de mini-jeu ou pose un contrôleur de mini-jeu pour débloquer cette entrée.")),
    "open_shop": dict(
        parent="root", icon="steveparty:trading_stall", frame="task", toast=True,
        criteria=[("has_shopkeeper_key", "has", ["steveparty:shopkeeper_key"]),
                  ("placed_trading_stall", "placed", "steveparty:trading_stall")],
        en=("Open for Business", "Get a Shopkeeper Key or place a Trading Stall"),
        fr=("Ouvert au public", "Obtenir une clé du marchand ou poser un étal du marchand"),
        lock=("Get a Shopkeeper Key or place a Trading Stall to unlock this entry.",
              "Obtiens une clé du marchand ou pose un étal du marchand pour débloquer cette entrée.")),
    "stardust": dict(
        parent="root", icon="steveparty:blue_star_fragment", frame="task", toast=False,
        criteria=[("has_star_fragment", "has", "#steveparty:star_fragments")],
        en=("Stardust", "Get a star fragment"),
        fr=("Poussière d'étoile", "Obtenir un fragment d'étoile"),
        lock=("Get a star fragment to unlock this entry.",
              "Obtiens un fragment d'étoile pour débloquer cette entrée.")),
    "dice_forge": dict(
        parent="stardust", icon="steveparty:dice_forge", frame="goal", toast=True,
        criteria=[("placed_dice_forge", "placed", "steveparty:dice_forge")],
        en=("Master Forger", "Place a Dice Forge"),
        fr=("Maître forgeron", "Poser une forge à dés"),
        lock=("Place a Dice Forge to unlock this entry.", "Pose une forge à dés pour débloquer cette entrée.")),
}

# --------------------------------------------------------------------------------------------------
# Pages. Text uses Modonomicon markdown: **bold**, *italic*, "- " lists, blank line = new paragraph.
# --------------------------------------------------------------------------------------------------


def T(en, fr, title_en=None, title_fr=None):
    return dict(kind="text", en=en, fr=fr, title_en=title_en, title_fr=title_fr)


def C(r1, r2=None, en="", fr=""):
    return dict(kind="crafting", r1=r1, r2=r2, en=en, fr=fr)


def SM(r1, en="", fr=""):
    return dict(kind="smelting", r1=r1, r2=None, en=en, fr=fr)


def SC(r1, en="", fr=""):
    return dict(kind="stonecutting", r1=r1, r2=None, en=en, fr=fr)


def SP(item, en, fr):
    return dict(kind="spotlight", item=item, en=en, fr=fr)


def E(eid, icon, name, desc, pages, parents=(), gate=None):
    return dict(id=eid, icon=icon, name=name, desc=desc, pages=pages, parents=list(parents), gate=gate)


CATEGORIES = []


def category(cid, icon, name, entries, parallax="flow"):
    CATEGORIES.append(dict(id=cid, icon=icon, name=name, entries=entries, parallax=parallax))


# ---------------------------------------------------------------- Getting started
category("getting_started", "steveparty:power_star", ("Getting Started", "Bien débuter"), [
    E("welcome", "steveparty:power_star", ("Welcome!", "Bienvenue !"),
      ("What Steve Party Maker is about", "Ce qu'est Steve Party Maker"), [
        T("Steve Party Maker turns Minecraft into a **party board game**: build a board block by block, "
          "turn any mob into a pawn, roll the dice, play mini-games and collect stars, all in survival.\n\n"
          "Almost every part of it can be driven with **redstone**.",
          "Steve Party Maker transforme Minecraft en **jeu de plateau festif** : construis un plateau bloc par "
          "bloc, change n'importe quel mob en pion, lance les dés, joue à des mini-jeux et gagne des étoiles, "
          "le tout en survie.\n\nPresque tout se pilote à la **redstone**."),
        T("Entries open as you progress, like a star map. A locked entry tells you which milestone opens it: "
          "placing a Tile, placing a Party Controller, finding a star fragment…\n\n"
          "Every item is in the mod's creative tab. With REI installed, you can also browse all the recipes.",
          "Les entrées s'ouvrent au fil de ta progression, comme une carte des étoiles. Une entrée verrouillée "
          "indique l'étape qui l'ouvre : poser une tuile, poser un contrôleur de fête, trouver un fragment "
          "d'étoile…\n\nTous les objets sont dans l'onglet créatif du mod. Avec REI, tu peux aussi parcourir "
          "toutes les recettes.",
          "How This Book Works", "Comment marche ce livre"),
    ]),
    E("plastic", "steveparty:plastic_pellets", ("Plastic Pellets", "Granulés de plastique"),
      ("The first ingredient of almost everything", "Le premier ingrédient de presque tout"), [
        T("Almost everything starts with **Plastic Pellets**: smelt **sugar cane** in a furnace.\n\n"
          "Pellets go into Tiles, Cartridges, Tokens, plastic blocks and road signs. Start a sugar cane farm early!",
          "Presque tout commence par des **granulés de plastique** : fais cuire de la **canne à sucre** au four.\n\n"
          "Les granulés servent aux tuiles, cartouches, jetons, blocs de plastique et panneaux routiers. "
          "Lance vite une ferme à canne à sucre !"),
        SM("steveparty:plastic_pellets_from_smelting_sugar_cane"),
    ], parents=["welcome"]),
    E("first_party", "steveparty:default_dice", ("Your First Party", "Ta première partie"),
      ("Six steps to a working board", "Six étapes pour un plateau qui marche"), [
        T("1. Craft **Tiles** and lay out a path.\n"
          "2. Link them with the **Wrench**.\n"
          "3. Put a **Start Cartridge** in the first Tile.\n"
          "4. Turn a mob into a pawn with the **Tokenizer Wand** and set it on the start.\n"
          "5. Place a **Party Controller** and press *Start party*.\n"
          "6. Roll a die!",
          "1. Fabrique des **tuiles** et trace un chemin.\n"
          "2. Relie-les avec la **clé**.\n"
          "3. Mets une **cartouche de départ** dans la première tuile.\n"
          "4. Change un mob en pion avec la **baguette de pions** et pose-le sur le départ.\n"
          "5. Pose un **contrôleur de fête** et clique sur *Lancer la partie*.\n"
          "6. Lance un dé !"),
        T("Each step has its own tab in this book: *The Board*, *Cartridges*, *Pawns & Dice* and "
          "*Running a Party*.\n\nShops, the Mula and decoration can come whenever you like.",
          "Chaque étape a son onglet dans ce livre : *Le plateau*, *Cartouches*, *Pions et dés* et "
          "*Mener une partie*.\n\nBoutiques, Mula et décoration viendront quand tu voudras.",
          "Where to Next", "Et ensuite"),
    ], parents=["welcome"]),
])

# ---------------------------------------------------------------- The board
category("board", "steveparty:tile", ("The Board", "Le plateau"), [
    E("spaces", "steveparty:tile", ("Tiles & Checkpoints", "Tuiles et points"),
      ("The spaces of a board", "Les cases d'un plateau"), [
        T("A board is made of spaces. Each space holds **cartridges** that give it a role and its next "
          "destinations.\n\n"
          "- **Tile** (grey rim): 1 cartridge, counts as a step.\n"
          "- **Advanced Tile** (gold rim): 16 cartridges, redstone picks the active one.\n"
          "- **Checkpoint**: 16 cartridges, does **not** count as a step.",
          "Un plateau est fait de cases. Chaque case contient des **cartouches** qui lui donnent un rôle et ses "
          "destinations.\n\n"
          "- **Tuile** (bord gris) : 1 cartouche, compte comme un pas.\n"
          "- **Tuile avancée** (bord doré) : 16 cartouches, la redstone choisit l'active.\n"
          "- **Point de déclenchement** : 16 cartouches, ne compte **pas** comme un pas."),
        C("steveparty:tile", "steveparty:advanced_tile",
          "Any carpet colour works for the Tile.", "N'importe quelle couleur de tapis convient pour la tuile."),
        C("steveparty:checkpoint", None,
          "A floating glass cube without collision: pawns cross it for free, handy for turns and forks.",
          "Un cube de verre flottant, sans collision : les pions le traversent gratuitement, pratique pour "
          "les virages et les bifurcations."),
    ]),
    E("wrench", "steveparty:wrench", ("Wrench", "Clé"), ("The board builder's tool", "L'outil du bâtisseur"), [
        T("The **Wrench** links spaces, shows the board, opens cartridge blocks (**sneak** + right-click on a space "
          "or a router) and breaks any plastic block in one hit.",
          "La **clé** relie les cases, montre le plateau, ouvre les blocs à cartouches (**accroupi** + clic droit "
          "sur une case ou un routeur) et casse tout bloc de plastique en un coup."),
        C("steveparty:wrench"),
    ], parents=["spaces"]),
    E("placing", "steveparty:tile", ("Placing Tiles", "Poser une tuile"), ("Slopes and directions", "Pentes et directions"), [
        T("A Tile faces one of **8 directions** (45° steps), following your gaze.\n\n"
          "- On stairs it follows the slope: tiles on a staircase form a ramp.\n"
          "- On stair corners it tilts diagonally.\n"
          "- On bottom slabs, snow or carpet it sits on their surface.\n\n"
          "Pawns stand on the surface and walk slopes like stairs.",
          "Une tuile regarde l'une des **8 directions** (pas de 45°), selon ton regard.\n\n"
          "- Sur un escalier, elle suit la pente : les tuiles d'un escalier forment une rampe.\n"
          "- Sur un coin d'escalier, elle penche en diagonale.\n"
          "- Sur une dalle du bas, de la neige ou un tapis, elle se pose sur leur surface.\n\n"
          "Les pions se tiennent sur la surface et montent les pentes comme un escalier."),
    ], parents=["spaces"], gate="lay_the_board"),
    E("sizes", "steveparty:tile", ("Tile Sizes", "Tailles de tuile"), ("Small, standard and large", "Petite, standard et grande"), [
        T("- **Standard**: a 2x2 picture centred on its block.\n"
          "- **Small**: exactly one block.\n"
          "- **Large**: exactly 2x2 blocks.\n\n"
          "Stonecutter: 1 tile gives 2 small. Crafting: 2 small make 1 standard, 4 standard in a square make "
          "1 large, and a large one splits back into 4.",
          "- **Standard** : image 2x2 centrée sur son bloc.\n"
          "- **Petite** : exactement un bloc.\n"
          "- **Grande** : exactement 2x2 blocs.\n\n"
          "Tailleur de pierre : 1 tuile donne 2 petites. Établi : 2 petites font 1 standard, 4 standard en carré "
          "font 1 grande, et une grande redonne ses 4."),
        SC("steveparty:small_tile_from_stonecutting"),
        T("Break a Tile with **Silk Touch**: it drops as a single item that keeps all its cartridges, their "
          "settings, its look and its size. Without Silk Touch, the cartridges drop on the ground.\n\n"
          "A Tile holding cartridges cannot change size at the crafting table.",
          "Casse une tuile avec **Toucher de soie** : elle donne un seul objet qui garde toutes ses cartouches, "
          "leurs réglages, son look et sa taille. Sans Toucher de soie, les cartouches tombent au sol.\n\n"
          "Une tuile qui contient des cartouches ne change pas de taille à l'établi.",
          "Keeping Cartridges", "Garder ses cartouches"),
    ], parents=["placing"], gate="lay_the_board"),
    E("stamping", "steveparty:stencil", ("Stamping Tiles", "Tamponner une tuile"), ("Give a tile its own look", "Donner un look à une tuile"), [
        T("Hold a **Stencil** and a **dye** (one in each hand), or use the **Stencil Hammer**, and right-click "
          "a Tile: it takes the dye colour with the pattern drawn darker. Costs 1 dye.\n\n"
          "With a cartridge inside, the active cartridge is stamped and keeps its look once removed. "
          "A **wet sponge** wipes the stamp.",
          "Tiens un **pochoir** et une **teinture** (un dans chaque main), ou le **marteau à pochoirs**, et fais "
          "clic droit sur une tuile : elle prend la couleur de la teinture, motif dessiné plus foncé. "
          "Coût : 1 teinture.\n\nAvec une cartouche, c'est la cartouche active qui est tamponnée ; elle garde son "
          "look une fois retirée. Une **éponge mouillée** efface le tampon."),
    ], parents=["placing"], gate="lay_the_board"),
    E("linking", "steveparty:wrench", ("Linking Spaces", "Relier les cases"), ("Trace the path with the Wrench", "Tracer le chemin à la clé"), [
        T("The Wrench has 3 modes (**R** or sneak + scroll):\n"
          "- **Trace**: click spaces one after another to link them; click the first one again to close the loop.\n"
          "- **Edit**: pick an origin, then add or remove links from it.\n"
          "- **Cut**: removes every outgoing link.\n\n"
          "Hold right-click and sweep to link a whole path (up to 32 blocks).",
          "La clé a 3 modes (**R** ou accroupi + molette) :\n"
          "- **Tracé** : clique les cases l'une après l'autre pour les relier ; reclique la première pour "
          "fermer la boucle.\n"
          "- **Éditer** : choisis une origine, puis ajoute ou retire des liens depuis elle.\n"
          "- **Couper** : retire tous les liens sortants.\n\n"
          "Maintiens le clic droit en balayant pour relier tout un chemin (jusqu'à 32 blocs)."),
        T("Missing cartridges are taken from your inventory (free in creative); a cartridge in your off hand "
          "sets the type of new spaces.\n\n"
          "Left-click in the air to **undo** (sneak: redo), 32 steps. Tiles turn to face their destination. "
          "With the Wrench in your off hand, each Tile you place is linked to the previous one.",
          "Les cartouches manquantes sont prises dans ton inventaire (gratuites en créatif) ; une cartouche en "
          "main secondaire choisit le type des nouvelles cases.\n\n"
          "Clic gauche dans le vide pour **annuler** (accroupi : rétablir), 32 actions. Les tuiles se tournent "
          "vers leur destination. Clé en main secondaire, chaque tuile posée est reliée à la précédente.",
          "Handy Tricks", "Astuces"),
    ], parents=["wrench"], gate="lay_the_board"),
    E("board_view", "steveparty:wrench", ("Board View & Check", "Vue et diagnostic"), ("Spot dead ends and mistakes", "Repérer impasses et erreurs"), [
        T("With the Wrench in hand (48 blocks), each link shows as moving chevrons. Each space wears a plate: "
          "steps from the start, **Start**, a gold **?** at forks, a red **!** at dead ends, an orange **!** "
          "when unreachable. Only you can see it.",
          "Clé en main (48 blocs), chaque lien devient un chemin de chevrons qui défilent. Chaque case porte une "
          "plaque : nombre de pas depuis le départ, **Départ**, **?** doré aux bifurcations, **!** rouge aux "
          "impasses, **!** orange aux cases injoignables. Toi seul la vois."),
        T("Click the **Party Controller** with the Wrench, or run `/steveparty board check`, for a full "
          "diagnosis: no start, dead ends, shops without a trader, lonely teleports…\n\n"
          "Operators can also use `/steveparty board copy`, `paste` and `template loop|line`.",
          "Clique le **contrôleur de fête** avec la clé, ou lance `/steveparty board check`, pour un diagnostic "
          "complet : pas de départ, impasses, boutiques sans marchand, téléportations isolées…\n\n"
          "Les opérateurs ont aussi `/steveparty board copy`, `paste` et `template loop|line`.",
          "Diagnosis", "Diagnostic"),
    ], parents=["linking"], gate="lay_the_board"),
    E("active_slot", "steveparty:advanced_tile", ("The Active Cartridge", "La cartouche active"), ("Redstone picks the role", "La redstone choisit le rôle"), [
        T("The redstone power a space receives (0-15) picks its active slot: power 0 = slot 1, power 1 = slot 2…\n\n"
          "Role, colour and destinations follow the active cartridge: one space can be blue, then red, then a "
          "Stop. A comparator or a redstone counter makes spaces that change every turn.",
          "La puissance redstone reçue (0 à 15) choisit l'emplacement actif : puissance 0 = emplacement 1, "
          "puissance 1 = emplacement 2…\n\n"
          "Rôle, couleur et destinations suivent la cartouche active : une même case peut être bleue, puis "
          "rouge, puis Stop. Un comparateur ou un compteur redstone fait des cases qui changent à chaque tour."),
    ], parents=["spaces"], gate="lay_the_board"),
    E("router", "steveparty:board_space_redstone_router", ("Redstone Router", "Routeur redstone"), ("Drive many spaces at once", "Piloter plusieurs cases"), [
        T("Put a cartridge in the **Board Space Redstone router**: its destinations are the spaces to drive. "
          "The power the router receives **replaces** theirs, so one lever can switch a whole board.\n\n"
          "A comparator on the router catches events: a 10-tick pulse when a pawn stops on a linked space, "
          "at the level of its role, and level 1 when one passes.",
          "Mets une cartouche dans le **routeur redstone de cases** : ses destinations sont les cases à piloter. "
          "La puissance reçue par le routeur **remplace** la leur : un seul levier change tout un plateau.\n\n"
          "Un comparateur sur le routeur capte les événements : impulsion de 10 ticks au niveau du rôle quand un "
          "pion s'arrête sur une case reliée, niveau 1 quand il passe."),
        T("- 15: plain space\n- 14: bonus\n- 13: malus\n- 12: empty item space\n- 11: start\n- 10: Stop\n"
          "- 9: Roll Again\n- 8: Teleport\n- 7: forward\n- 6: back\n- 5: shop stop\n- 1: a pawn passes",
          "- 15 : case simple\n- 14 : bonus\n- 13 : malus\n- 12 : case objet vide\n- 11 : départ\n- 10 : Stop\n"
          "- 9 : Rejouer\n- 8 : Téléportation\n- 7 : Avancer\n- 6 : Reculer\n- 5 : arrêt boutique\n"
          "- 1 : un pion passe",
          "Comparator Levels", "Niveaux du comparateur"),
        C("steveparty:board_space_redstone_router"),
    ], parents=["active_slot"], gate="lay_the_board"),
    E("moving", "steveparty:check_point", ("Moving on the Board", "Se déplacer"), ("Steps, forks and arrivals", "Pas, bifurcations et arrivées"), [
        T("Each Tile reached = 1 step; a Checkpoint = 0. At a fork, arrows appear for the pawn's owner: "
          "right-click one with an **empty hand** to choose.\n\n"
          "When the steps run out, the space's role fires: a jingle, particles in its colour and a message for "
          "everyone in the party.",
          "Chaque tuile atteinte = 1 pas ; un point de déclenchement = 0. À une bifurcation, des flèches "
          "apparaissent pour le propriétaire du pion : clic droit **main vide** sur l'une pour choisir.\n\n"
          "Quand les pas sont épuisés, le rôle de la case s'active : jingle, particules à sa couleur et message "
          "pour toute la partie."),
    ], parents=["spaces"], gate="lay_the_board"),
])

# ---------------------------------------------------------------- Cartridges
category("cartridges", "steveparty:board_space_behavior", ("Cartridges", "Cartouches"), [
    E("cartridge", "steveparty:board_space_behavior", ("Cartridges", "Cartouches"), ("Destinations and roles", "Destinations et rôles"), [
        T("A **Cartridge** gives a space its destinations. Holding it, right-click a block to add it as a "
          "destination, again to remove it.\n\n"
          "- one destination: a set path;\n- several: a fork, the player chooses;\n- none: a dead end, the pawn stops.",
          "Une **cartouche** donne à une case ses destinations. En main, clic droit sur un bloc pour l'ajouter "
          "comme destination, encore pour la retirer.\n\n"
          "- une destination : chemin tout tracé ;\n- plusieurs : bifurcation, le joueur choisit ;\n"
          "- aucune : cul-de-sac, le pion s'arrête."),
        C("steveparty:cartridge", None, "2 redstone, 2 paper and 2 Plastic Pellets.",
          "2 redstone, 2 papiers et 2 granulés de plastique."),
        T("Every cartridge has its own menu: right-click in the air while holding it, or open its space. "
          "A dye on the space recolours the active cartridge.\n\n"
          "Each role below is a plain Cartridge plus one ingredient, shapeless.",
          "Chaque cartouche a son menu : clic droit dans le vide en la tenant, ou ouvre sa case. Une teinture "
          "sur la case recolore la cartouche active.\n\n"
          "Chaque rôle ci-après est une cartouche simple + un ingrédient, sans forme.",
          "Programming", "Programmer"),
    ]),
    E("start", "steveparty:tile_behavior_start", ("Start Cartridge", "Cartouche de départ"), ("Where a pawn starts", "Là où part un pion"), [
        T("A pawn set on a start space **binds** to it: it floats, faces the nearest player, and the space takes "
          "the pawn's colour. Right-click with an empty hand to own the space and its pawn.\n\n"
          "The Party Controller only takes pawns bound to a start space. Hit the pawn to unbind it.",
          "Un pion posé sur une case de départ s'y **lie** : il flotte, regarde le joueur le plus proche et la "
          "case prend sa couleur. Clic droit main vide pour devenir propriétaire de la case et du pion.\n\n"
          "Le contrôleur de fête ne prend que les pions liés à une case de départ. Frappe le pion pour le délier."),
        C("steveparty:tile_behavior_start", None, "Cartridge + lime dye.", "Cartouche + teinture vert clair."),
    ], parents=["cartridge"]),
    E("stop", "steveparty:board_space_behavior_stop", ("Stop Cartridge", "Cartouche Stop"), ("A forced stop", "Un arrêt forcé"), [
        T("Turns a space into a charcoal **Stop** space with a no-entry sign. A pawn that reaches it **ends its "
          "move** there, whatever steps remain.\n\nIt works on a Checkpoint too.",
          "Fait de la case une case **Stop** anthracite, marquée d'un sens interdit. Un pion qui l'atteint "
          "**termine son déplacement**, quels que soient les pas restants.\n\nMarche aussi sur un point de "
          "déclenchement."),
        C("steveparty:board_space_behavior_stop", None, "Cartridge + red dye.", "Cartouche + teinture rouge."),
    ], parents=["cartridge"], gate="lay_the_board"),
    E("shop", "steveparty:shop_cartridge", ("Shop Cartridge", "Cartouche Boutique"), ("A shop stop, party style", "Un arrêt boutique"), [
        T("On a **Tile**, the shop opens when a pawn **ends** its move there; in a **Checkpoint**, every passing "
          "pawn stops.\n\nThe player gets the trade screen of the nearest **Boxed Trader** that has stalls "
          "(32 blocks). Sneak + scroll sets how many items can be bought (1-9). Without a choice, the shop "
          "closes after 60 s.",
          "Sur une **tuile**, la boutique s'ouvre quand un pion y **termine** son déplacement ; dans un **point "
          "de déclenchement**, tout pion qui passe s'arrête.\n\nLe joueur reçoit l'écran d'échange du "
          "**marchand en carton** le plus proche qui a des étals (32 blocs). Accroupi + molette : nombre "
          "d'achats (1 à 9). Sans choix, elle ferme au bout de 60 s."),
        C("steveparty:shop_cartridge", None, "Cartridge + emerald + gold nugget.",
          "Cartouche + émeraude + pépite d'or."),
    ], parents=["cartridge"], gate="lay_the_board"),
    E("advance_back", "steveparty:advance_back_cartridge", ("Move Forward / Back", "Avancer / Reculer"), ("Bonus steps, or a setback", "Des pas en plus, ou en moins"), [
        T("A pawn ending its move here goes **1 to 6** spaces further: forward (green) or back (magenta). "
          "Sneak + scroll, cartridge in your main hand, sets the count and the direction.\n\n"
          "Going back, it retraces its path. No chaining: landing on another such space does nothing more. "
          "Only during a party.",
          "Un pion qui termine ici repart de **1 à 6** cases : en avant (vert) ou en arrière (magenta). "
          "Accroupi + molette, cartouche en main principale : nombre de cases et sens.\n\n"
          "En arrière, il reprend son chemin. Pas d'enchaînement : arriver sur une autre case de ce type ne fait "
          "rien de plus. Seulement en partie."),
        C("steveparty:advance_back_cartridge", None, "Cartridge + piston.", "Cartouche + piston."),
    ], parents=["cartridge"], gate="lay_the_board"),
    E("replay", "steveparty:replay_cartridge", ("Roll Again Cartridge", "Cartouche Rejouer"), ("One more roll", "Un lancer de plus"), [
        T("When a pawn ends its move here on its player's turn, that player **rolls again** at once, and the "
          "spent die comes back.\n\nThe extra turn never gives another one.",
          "Quand un pion termine ici pendant le tour de son joueur, ce joueur **rejoue** aussitôt, et le dé "
          "dépensé revient.\n\nCe tour en plus n'en donne jamais un autre."),
        C("steveparty:replay_cartridge", None, "Cartridge + repeater.", "Cartouche + répéteur."),
    ], parents=["cartridge"], gate="lay_the_board"),
    E("teleport", "steveparty:teleport_cartridge", ("Teleport Cartridge", "Cartouche Téléportation"), ("Portals between spaces", "Des portails entre cases"), [
        T("A pawn ending its move here jumps to **another Teleport space of the same colour** on the board, at "
          "random or in turn. Networks: purple (default), green, orange or blue; a dye on the space changes it.\n\n"
          "In its menu: stay on arrival or step one space further, and whether that space fires.",
          "Un pion qui termine ici saute sur **une autre case Téléportation de la même couleur** du plateau, au "
          "hasard ou à tour de rôle. Réseaux : violet (défaut), vert, orange ou bleu ; une teinture sur la case "
          "le change.\n\nDans son menu : rester à l'arrivée ou avancer d'une case, et si cette case s'active."),
        C("steveparty:teleport_cartridge", None, "Cartridge + ender pearl.", "Cartouche + perle de l'Ender."),
    ], parents=["cartridge"], gate="lay_the_board"),
    E("inventory", "steveparty:inventory_cartridge", ("Inventory Cartridge", "Cartouche d'inventaire"), ("Bonus and malus spaces", "Cases bonus et malus"), [
        T("Gives or takes items. Right-click up to **8 chests** with it to link them; right-click in the air for "
          "its menu: 9 ghost slots, the **scroll wheel** sets the amount, and going below 1 makes it "
          "**negative**.\n\nSelection: random, all, or cycle.",
          "Donne ou prend des objets. Clic droit sur jusqu'à **8 coffres** pour les lier ; clic droit dans le "
          "vide pour son menu : 9 emplacements fantômes, la **molette** règle la quantité, sous 1 elle passe en "
          "**négatif**.\n\nSélection : au hasard, tous, ou cycle."),
        T("When a pawn ends its move on it, its owner receives the positive items, taken from the chests, and "
          "loses the negative ones, stored in the chests.\n\n"
          "**Blue** = bonus, **red** = malus, **orange** = nothing to give. Blue and red also make the mini-game "
          "teams. The Party Controller bank, Looting Box and Piggy Bank use this cartridge too.",
          "Quand un pion termine dessus, son propriétaire reçoit les objets positifs, pris dans les coffres, et "
          "perd les négatifs, rangés dans les coffres.\n\n"
          "**Bleu** = bonus, **rouge** = malus, **orange** = rien à donner. Bleu et rouge forment aussi les "
          "équipes des mini-jeux. La banque du contrôleur de fête, la boîte à butin et la tirelire s'en servent aussi.",
          "Bonus & Malus", "Bonus et malus"),
        C("steveparty:inventory_cartridge", None, "Cartridge + chest.", "Cartouche + coffre."),
    ], parents=["cartridge"], gate="lay_the_board"),
])

# ---------------------------------------------------------------- Pawns & dice
category("pawns_dice", "steveparty:tokenizer_wand", ("Pawns & Dice", "Pions et dés"), [
    E("wand", "steveparty:tokenizer_wand", ("Tokenizer Wand", "Baguette de pions"), ("Any mob can be a pawn", "Tout mob peut être un pion"), [
        T("Right-click a mob with the **Tokenizer Wand** (or hold right-click in the air to shoot a spark up to "
          "32 blocks): the *Pionificus!* spell opens.\n\n"
          "Draw a circle around the mob: on the guide = a 1-block pawn, twice as big = 2 blocks (max), smaller = "
          "smaller, down to 0.25. Esc cancels. Not on the Ender Dragon or the Wither.",
          "Clic droit sur un mob avec la **baguette de pions** (ou maintiens le clic droit dans le vide pour "
          "lancer une flammèche jusqu'à 32 blocs) : le sort *Pionificus !* s'ouvre.\n\n"
          "Trace un cercle autour du mob : sur le guide = pion d'1 bloc, deux fois plus grand = 2 blocs (max), "
          "plus petit = plus petit, jusqu'à 0,25. Échap annule. Impossible sur le Dragon et le Wither."),
        C("steveparty:tokenizer_wand", "steveparty:garnet_crystal_ball",
          "Wand: Garnet Crystal Ball, gold block, blaze rod. Ball: redstone blocks around a nether star.",
          "Baguette : boule de grenat, bloc d'or, bâton de Blaze. Boule : blocs de redstone autour d'une étoile du Nether."),
    ]),
    E("pawns", "minecraft:armor_stand", ("Pawns", "Pions"), ("A mob turned figurine", "Un mob devenu figurine"), [
        T("A pawn is a figurine: no AI, silent, invulnerable, it can't be pushed or leashed, and feeding or "
          "trading does nothing.\n\n"
          "Its name is the mob's custom name, or yours. Its colour comes from the mob's texture and tints its "
          "octagonal base; right-click it with a dye to change it. Cast the spell on your own pawn again to resize it.",
          "Un pion est une figurine : pas d'IA, muet, invulnérable, il ne se pousse pas, ne se tient pas en "
          "laisse, et le nourrir ou l'échanger ne fait rien.\n\n"
          "Son nom est celui du mob, ou le tien. Sa couleur vient de la texture du mob et teinte son socle "
          "octogonal ; une teinture la change. Relance le sort sur ton pion pour le redimensionner."),
    ], parents=["wand"], gate="make_a_pawn"),
    E("token", "steveparty:token", ("Token", "Jeton"), ("Carry a pawn around", "Transporter un pion"), [
        T("The **Token** is how you move a pawn by hand. Right-click your pawn with an empty Token to store it; "
          "right-click a block with the full Token to set it down, size, owner and colour kept.\n\n"
          "Set it on a start space to enter it in the party.",
          "Le **jeton** sert à déplacer un pion à la main. Clic droit sur ton pion avec un jeton vide pour le "
          "ranger ; clic droit sur un bloc avec le jeton plein pour le poser, taille, propriétaire et couleur "
          "conservés.\n\nPose-le sur une case de départ pour l'inscrire à la partie."),
        C("steveparty:token", None, "Plastic Pellets and a shulker box.", "Granulés de plastique et une boîte de Shulker."),
    ], parents=["pawns"], gate="make_a_pawn"),
    E("game_master", "minecraft:enchanted_book", ("Game Master", "Maître du jeu"), ("A referee's enchantment", "L'enchantement de l'arbitre"), [
        T("A treasure enchantment for the Tokenizer Wand only, found in woodland mansion chests (25%), or dropped "
          "by evokers (5%) and illusioners (25%) killed by a player.\n\n"
          "With it, the wand resizes anyone's pawn and can `/steveparty exclude` any pawn of a party within "
          "100 blocks. Operators already have these rights.",
          "Enchantement trésor, uniquement pour la baguette de pions : coffres de manoir (25 %), évocateurs "
          "(5 %) et illusionnistes (25 %) tués par un joueur.\n\n"
          "Avec lui, la baguette redimensionne le pion de n'importe qui et peut `/steveparty exclude` tout pion "
          "d'une partie à 100 blocs. Les opérateurs ont déjà ces droits."),
    ], parents=["pawns"], gate="make_a_pawn"),
    E("dice", "steveparty:default_dice", ("Dice", "Dés"), ("Throw, stop, move", "Lancer, arrêter, avancer"), [
        T("Right-click: the die flies to the **nearest mob** within 20 blocks (sneak: the nearest **player**), "
          "floats above its head and spins. **Hit** it to stop it.\n\n"
          "The result moves one of the thrower's pawns within 25 blocks, 1.5 s later. During a party, only the "
          "pawn whose turn it is moves.",
          "Clic droit : le dé part vers le **mob le plus proche** à 20 blocs (accroupi : le **joueur** le plus "
          "proche), flotte au-dessus de sa tête et tourne. **Frappe-le** pour l'arrêter.\n\n"
          "Le résultat fait avancer un pion du lanceur à 25 blocs, 1,5 s après. En partie, seul le pion dont "
          "c'est le tour bouge."),
        T("- **Default Dice**: 1 to 10.\n- **Double Dice**: two linked dice, 2 to 20.\n"
          "- **Triple Dice**: three, 3 to 30.\n- **Forged Dice**: its own faces, from the Dice Forge.\n\n"
          "A thrown die vanishes 2 s after its result.",
          "- **Dé par défaut** : 1 à 10.\n- **Dé double** : deux dés liés, 2 à 20.\n"
          "- **Dé triple** : trois, 3 à 30.\n- **Dé forgé** : ses propres faces, sorti de la forge à dés.\n\n"
          "Un dé lancé disparaît 2 s après son résultat.",
          "The Dice", "Les dés"),
        C("steveparty:default_dice", "steveparty:double_dice",
          "8 iron ingots around a wind charge. Combine dice for bigger ones; they split back.",
          "8 lingots de fer autour d'une charge de vent. Combine des dés pour en faire de plus gros ; ils se défont."),
        C("steveparty:triple_dice", "steveparty:triple_dice2"),
    ]),
    E("faces", "steveparty:blank_dice_face", ("Dice Faces", "Faces de dé"), ("Material for custom dice", "De quoi graver des dés"), [
        T("Faces are only used in the **Dice Forge**. 2 iron ingots + 2 nether quartz give 4 **Blank Dice "
          "Faces**; the **stonecutter** turns a blank face into any number (0 to 10, premium or cursed) and "
          "back.\n\nOn a forged die, a blank face is a 0.",
          "Les faces ne servent que dans la **forge à dés**. 2 lingots de fer + 2 quartz du Nether donnent 4 "
          "**faces de dé vierges** ; le **tailleur de pierre** change une face vierge en n'importe quel nombre "
          "(0 à 10, premium ou maudit) et inversement.\n\nSur un dé forgé, une face vierge vaut 0."),
        C("steveparty:blank_dice_face_from_crafting"),
        T("- **Face 0**: stay put and replay the current space.\n"
          "- **Coin +1 to +10**: the thrower gains coins.\n"
          "- **Debt −1 to −10**: the thrower loses coins.\n"
          "- **Swap**: swap places with another player's pawn.\n\n"
          "With these faces, the pawn does not move.",
          "- **Face 0** : rester sur place et rejouer la case.\n"
          "- **Pièces +1 à +10** : le lanceur gagne des pièces.\n"
          "- **Dette −1 à −10** : le lanceur perd des pièces.\n"
          "- **Échange** : permuter avec le pion d'un autre joueur.\n\n"
          "Avec ces faces, le pion ne bouge pas.",
          "Special Faces", "Faces spéciales"),
        C("steveparty:swap_dice_face_from_crafting", "steveparty:coin_dice_face_1_from_crafting",
          "Blank face + ender pearl, or + a Coin. Debt: blank face + spider eye.",
          "Face vierge + perle de l'Ender, ou + une pièce. Dette : face vierge + œil d'araignée."),
    ], parents=["dice"], gate="roll_the_dice"),
    E("modules", "steveparty:dice_module_lucky", ("Dice Modules", "Modules de dé"), ("Properties of a whole die", "Les propriétés d'un dé"), [
        T("A module changes the whole die (blank face + ingredient):\n"
          "- **Slow** (clock): stop it on the shown face;\n"
          "- **Choice** (compass): pick the face;\n"
          "- **Infinity** (echo shard): it comes back;\n"
          "- **Lucky** (rabbit's foot, up to 5): extra rolls, keep the best;\n"
          "- **Reroll** (wind charge, up to 5): keep or roll again.",
          "Un module change tout le dé (face vierge + ingrédient) :\n"
          "- **Lent** (horloge) : l'arrêter sur la face affichée ;\n"
          "- **Au choix** (boussole) : choisir la face ;\n"
          "- **Infini** (éclat d'écho) : il revient ;\n"
          "- **Chanceux** (patte de lapin, jusqu'à 5) : lancers en plus, garder le meilleur ;\n"
          "- **Relance** (charge de vent, jusqu'à 5) : garder ou relancer."),
        T("- **Reversed** (fermented spider eye): the pawn goes backwards;\n"
          "- **Skeleton Key** (tripwire hook): ignores Stop and shop spaces;\n"
          "- **Homing** (eye of ender): picks forks at random.\n\n"
          "Add modules to a die at the crafting table (the modules stay in the grid) or in the Dice Forge.",
          "- **Inversé** (œil d'araignée fermenté) : le pion recule ;\n"
          "- **Passe-partout** (crochet) : ignore les cases Stop et boutique ;\n"
          "- **Tête chercheuse** (œil de l'Ender) : choisit les bifurcations au hasard.\n\n"
          "Ajoute des modules à un dé à l'établi (les modules restent dans la grille) ou dans la forge à dés.",
          "More Modules", "Autres modules"),
        C("steveparty:dice_module_lucky", "steveparty:dice_module_choice"),
    ], parents=["dice"], gate="roll_the_dice"),
])

# ---------------------------------------------------------------- Running a party
category("party", "steveparty:party_controller", ("Running a Party", "Mener une partie"), [
    E("controller", "steveparty:party_controller", ("Party Controller", "Contrôleur de fête"), ("The heart of a party", "Le cœur d'une partie"), [
        T("The **Party Controller** runs the game. Right-click for its dashboard:\n"
          "- **Status**: what is missing, then the live timeline;\n"
          "- **Players**: pawns, stars and coins;\n"
          "- **Program**: the catalogue and 24 party card slots;\n"
          "- **Rewards**: what each place earns;\n"
          "- **Settings**: turns (1-50) and practice round.",
          "Le **contrôleur de fête** mène la partie. Clic droit pour son tableau de bord :\n"
          "- **État** : ce qui manque, puis la frise en direct ;\n"
          "- **Joueurs** : pions, étoiles et pièces ;\n"
          "- **Programme** : le catalogue et 24 emplacements de cartes ;\n"
          "- **Gains** : ce que rapporte chaque place ;\n"
          "- **Réglages** : tours (1 à 50) et manche d'essai."),
        T("It takes the pawns bound to a start space within 100 blocks. Press *Start party*, or send it a rising "
          "redstone edge.\n\nA comparator reads the phase: 0 idle, 1 setup, 2 turn, 3 mini-game, 4 waiting for "
          "a bell, 5 end.",
          "Il prend les pions liés à une case de départ à 100 blocs. Clique *Lancer la partie*, ou envoie-lui "
          "un front montant de redstone.\n\nUn comparateur lit la phase : 0 repos, 1 préparation, 2 tour, "
          "3 mini-jeu, 4 attente d'une cloche, 5 fin.",
          "Starting", "Lancer"),
        C("steveparty:party_controller"),
    ]),
    E("flow", "minecraft:clock", ("How a Party Plays", "Déroulement"), ("From the first roll to the podium", "Du premier lancer au podium"), [
        T("1. Bound pawns join.\n"
          "2. Each owner rolls once: the highest goes first.\n"
          "3. The program is built: by default, one turn per pawn then a mini-game, every round.\n"
          "4. On its turn, a pawn's owner rolls and moves.\n"
          "5. Mini-games pick teams and a page, then send the players through pipes.\n"
          "6. The end: pawns are freed.",
          "1. Les pions liés entrent en jeu.\n"
          "2. Chacun lance un dé : le plus haut commence.\n"
          "3. Le programme se crée : par défaut, un tour par pion puis un mini-jeu, à chaque manche.\n"
          "4. À son tour, le propriétaire du pion lance et avance.\n"
          "5. Les mini-jeux tirent équipes et page, puis envoient les joueurs par les tuyaux.\n"
          "6. Fin : les pions sont libérés."),
        T("If a pawn is missing (offline or unloaded), its turn waits 60 s and the chat offers **[Skip turn]** "
          "and **[Exclude]**. A restarting server resumes the current step.\n\n"
          "Press **M** to arrange the party HUD, **H** to hide it.",
          "Si un pion manque (déconnecté ou non chargé), son tour attend 60 s et le chat propose **[Passer le "
          "tour]** et **[Exclure]**. Un serveur qui redémarre reprend l'étape en cours.\n\n"
          "Touche **M** pour disposer l'interface de partie, **H** pour la masquer.",
          "Hiccups & HUD", "Imprévus et interface"),
    ], parents=["controller"], gate="party_time"),
    E("rewards", "steveparty:coin", ("Coins, Stars & Rewards", "Pièces, étoiles et gains"), ("How players are paid", "Comment les joueurs sont payés"), [
        T("A player's stars and coins are those items in their inventory; ranking goes by stars, then coins. "
          "Defaults: the **Coin** and the **Power Star** (change them in the Rewards tab).\n\n"
          "Rewards are paid from chests: put an Inventory Cartridge linked to chests in the bank slot. The "
          "controller never creates coins or stars.",
          "Les étoiles et pièces d'un joueur sont ces objets dans son inventaire ; classement aux étoiles, puis "
          "aux pièces. Par défaut : la **pièce** et la **Super étoile** (à changer dans l'onglet Gains).\n\n"
          "Les gains sont pris dans des coffres : mets une cartouche d'inventaire liée à des coffres dans la case "
          "banque. Le contrôleur ne crée jamais ni pièce ni étoile."),
        C("steveparty:coin", None, "One gold nugget makes a Coin.", "Une pépite d'or donne une pièce."),
    ], parents=["controller"], gate="party_time"),
    E("step_controller", "steveparty:step_controller", ("Step controller", "Contrôleur de pas"), ("Next, restart or back", "Suivant, recommencer, retour"), [
        T("Right-click to switch mode: **up** = next step, **side** = restart the step, **down** = previous step. "
          "A rising redstone edge applies it to the nearest Party Controller (64 blocks).\n\n"
          "Linked to a Mini-Game Page, its pulse ends that mini-game with the podium places as they stand.",
          "Clic droit pour changer de mode : **haut** = étape suivante, **côté** = recommencer, **bas** = étape "
          "précédente. Un front montant l'applique au contrôleur de fête le plus proche (64 blocs).\n\n"
          "Lié à une page de mini-jeu, son impulsion termine ce mini-jeu avec les places des podiums."),
        C("steveparty:step_controller"),
    ], parents=["controller"], gate="party_time"),
    E("cards", "steveparty:party_card_turns", ("Party Cards", "Cartes de partie"), ("Write your own program", "Écrire son programme"), [
        T("Write your own program in the Program tab, read left to right:\n"
          "- **Turns**: every pawn plays once;\n"
          "- **Mini-game**: one from the catalogue;\n"
          "- **Event**: rings the Event bells;\n"
          "- **Repeat**: replays the cards since the last Sequence start, once per card in its stack;\n"
          "- **Sequence start**: where the next Repeat starts.",
          "Écris ton programme dans l'onglet Programme, de gauche à droite :\n"
          "- **Tour de jeu** : chaque pion joue une fois ;\n"
          "- **Mini-jeu** : un mini-jeu du catalogue ;\n"
          "- **Événement** : fait sonner les cloches Événement ;\n"
          "- **Répéter** : rejoue les cartes depuis le dernier Début de séquence, autant de fois que sa pile ;\n"
          "- **Début de séquence** : d'où repart le Répéter suivant."),
        C("steveparty:party_card_turns", "steveparty:party_card_minigame",
          "A plastic stud of the card's colour + paper + redstone gives 4 cards.",
          "Un plot en plastique de la couleur de la carte + papier + redstone donnent 4 cartes."),
        C("steveparty:party_card_event", "steveparty:party_card_repeat"),
        C("steveparty:party_card_sequence_start"),
    ], parents=["controller"], gate="party_time"),
    E("bell", "steveparty:party_bell", ("Party Bell", "Cloche de partie"), ("Redstone at the right moment", "La redstone au bon moment"), [
        T("Right-click to choose when it pulses: party start, round start, turn start, die thrown, turn end, "
          "mini-game drawn, mini-game end, Event card, party end. Its comparator gives a matching number "
          "(die value, rank…).\n\n"
          "Set to **wait** with the Wrench, it freezes the party until it receives a redstone signal.",
          "Clic droit pour choisir quand elle sonne : début de partie, de manche, de tour, dé lancé, fin de tour, "
          "mini-jeu tiré, fin de mini-jeu, carte Événement, fin de partie. Son comparateur donne un nombre lié "
          "(valeur du dé, rang…).\n\n"
          "En mode **attente** (clé), elle fige la partie jusqu'à recevoir un signal redstone."),
        C("steveparty:party_bell"),
    ], parents=["cards"], gate="party_time"),
    E("piggy_bank", "steveparty:piggy_bank", ("Piggy Bank", "Tirelire"), ("Give and take on a pulse", "Donner et prendre à l'impulsion"), [
        T("A ceramic pig with a coin slot. On each redstone pulse, it applies its Inventory Cartridge: it gives "
          "items from the linked chest and takes the negative ones. Right-click to pick the target: current "
          "player, last winners, all players, or the nearest.\n\n"
          "In *all* mode it's all or nothing: sell a star for coins!",
          "Un cochon en céramique avec une fente. À chaque impulsion, il applique sa cartouche d'inventaire : "
          "donne les objets du coffre lié, prend les objets négatifs. Clic droit pour la cible : joueur du tour, "
          "derniers gagnants, tous, ou le plus proche.\n\n"
          "En mode *tous*, c'est tout ou rien : de quoi vendre une étoile contre des pièces !"),
        C("steveparty:piggy_bank"),
    ], parents=["controller"], gate="party_time"),
    E("goal_pole", "steveparty:goal_pole", ("Goal Pole", "Mât d'arrivée"), ("Jump to the top and score", "Sauter au sommet et marquer"), [
        T("Place a **Goal Pole Base**, stack **Goal Pole** segments on it and hang **Flags**. Land on top of a "
          "segment: **1up**, a golden heart, no fall damage, and a point for the base.\n\n"
          "The base counts landings (or any scoreboard criterion) for the party players, everyone, nearby players "
          "or a selector. Set it up with the Wrench.",
          "Pose un **socle de mât d'arrivée**, empile des segments de **mât d'arrivée** et accroche des "
          "**drapeaux**. Atterris au sommet : **1up**, un cœur doré, pas de dégâts de chute, et un point pour "
          "le socle.\n\nLe socle compte les arrivées (ou un critère de scoreboard) pour les joueurs de la partie, "
          "tous, ceux à proximité ou un sélecteur. Réglage à la clé."),
        T("Set a goal on a segment with the Wrench (at least 1 by default). Reached: the flag slides down and the "
          "gold ball lights up. A comparator on a segment gives 15 once reached; on the base, a pulse per point "
          "or the progress 0-15.\n\nWith *Goal: per player*, each player who reaches it takes the best free podium place.",
          "Règle un but sur un segment à la clé (au moins 1 par défaut). Atteint : le drapeau descend et la boule "
          "s'allume. Un comparateur sur un segment donne 15 une fois le but atteint ; sur le socle, une impulsion "
          "par point ou la progression 0-15.\n\nAvec *But : par joueur*, chacun prend la meilleure place libre des podiums.",
          "Goals & Redstone", "Buts et redstone"),
        C("steveparty:goal_pole_base", "steveparty:goal_pole"),
        SP("steveparty:flag",
           "Three wool in a corner shape; the wool sets the colour, 1-8 dyes retint it. Shears take it back.",
           "Trois laines en coin ; la laine fait la couleur, 1 à 8 teintures la changent. Les cisailles le reprennent."),
    ], parents=["controller"], gate="party_time"),
])

# ---------------------------------------------------------------- Mini-games
category("mini_games", "steveparty:mini_game_page", ("Mini-games", "Mini-jeux"), [
    E("pages", "steveparty:mini_game_page", ("Mini-Game Pages", "Pages de mini-jeu"), ("One page, one mini-game", "Une page, un mini-jeu"), [
        T("A **Mini-Game Page** describes one mini-game. Right-click in the air to open its editor: title, "
          "description, image, then the **Formats**, **Pipes** and **Results** tabs.\n\n"
          "Keep pages in a **Mini Games Catalogue** (91 slots) and put it in the Party Controller. A page + paper "
          "makes a linked copy.",
          "Une **page de mini-jeu** décrit un mini-jeu. Clic droit dans le vide pour son éditeur : titre, "
          "description, image, puis les onglets **Formats**, **Tuyaux** et **Résultats**.\n\n"
          "Range les pages dans un **catalogue de mini-jeux** (91 emplacements) à mettre dans le contrôleur de "
          "fête. Page + papier = une copie liée."),
        C("steveparty:mini_game_page", "steveparty:mini_games_catalogue"),
    ]),
    E("formats", "steveparty:blue_pipe", ("Formats & Teams", "Formats et équipes"), ("Who plays against whom", "Qui joue contre qui"), [
        T("A page lists its formats: **Teams** (2 to 4 sides, sizes from-to), **Free for all** or **All "
          "together**. Names build themselves: Duel, 2 vs 2, 1 vs all…\n\n"
          "In a party, teams come from the board: **Team A** = pawns on a positive (blue) space, **Team B** = on "
          "a negative (red) one; neutral ones fill in. Only pages with a fitting format can be drawn.",
          "Une page liste ses formats : **Équipes** (2 à 4 camps, de N à M joueurs), **Chacun pour soi** ou "
          "**Tous ensemble**. Les noms se composent seuls : Duel, 2 contre 2, 1 contre tous…\n\n"
          "En partie, les équipes viennent du plateau : **équipe A** = pions sur une case positive (bleue), "
          "**équipe B** = sur une case négative (rouge) ; les neutres complètent. Seules les pages au format "
          "compatible peuvent être tirées."),
    ], parents=["pages"], gate="mini_games"),
    E("pipe_roles", "steveparty:green_pipe", ("Mini-game Pipes", "Tuyaux de mini-jeu"), ("Pipe colours are roles", "La couleur du tuyau est un rôle"), [
        T("With the page in hand, click a pipe mouth to link it. Its colour is its role:\n"
          "- green: Players;\n- blue, red, purple, orange: Teams A to D;\n- white or glass: Spectators;\n"
          "- black: Entry;\n- yellow: Exit.\n\n"
          "At the start, each player comes out of a pipe of their role; at the end, everyone goes back where they were.",
          "Page en main, clique l'embouchure d'un tuyau pour le lier. Sa couleur est son rôle :\n"
          "- vert : Joueurs ;\n- bleu, rouge, violet, orange : équipes A à D ;\n- blanc ou verre : Spectateurs ;\n"
          "- noir : Entrée ;\n- jaune : Sortie.\n\n"
          "Au départ, chacun sort d'un tuyau de son rôle ; à la fin, tout le monde revient où il était."),
    ], parents=["formats"], gate="mini_games"),
    E("entrance_pipes", "steveparty:copper_minigame_pipe", ("Mini-game Entrances", "Entrées de mini-jeu"), ("Play outside a party", "Jouer hors partie"), [
        T("A **Mini-game Pipe** is the way into a mini-game outside a party. Click it with the page to program "
          "it, then connect coloured pipes: enter by a green mouth to come out as a player, by a white one as a "
          "spectator…\n\nRange: **copper** 100 blocks, **iron** the whole dimension, **golden** every dimension.",
          "Un **tuyau de mini-jeu** est l'entrée d'un mini-jeu hors partie. Clique-le avec la page pour le "
          "programmer, puis relie-lui des tuyaux de couleur : entre par une embouchure verte pour ressortir en "
          "joueur, blanche en spectateur…\n\nPortée : **cuivre** 100 blocs, **fer** toute la dimension, **or** "
          "toutes les dimensions."),
        C("steveparty:copper_minigame_pipe", "steveparty:iron_minigame_pipe"),
        C("steveparty:golden_minigame_pipe"),
    ], parents=["pipe_roles"], gate="mini_games"),
    E("podiums", "steveparty:podium", ("Podiums", "Podiums"), ("Who won", "Qui a gagné"), [
        T("Podiums record **who won**: the higher the column, the better the place. Sneak on one or right-click "
          "it to take it. Link podiums to the page (click with the page): the mini-game ends when every place is "
          "taken.\n\nPlaced like slabs, they stack into columns. During a linked mini-game, columns of the same "
          "height share one place.",
          "Les podiums retiennent **qui a gagné** : plus la colonne est haute, meilleure est la place. "
          "Accroupis-toi dessus ou clic droit pour le prendre. Lie-les à la page (clic avec la page) : le "
          "mini-jeu finit quand toutes les places sont prises.\n\nPosés comme des dalles, ils s'empilent en "
          "colonnes. Pendant un mini-jeu lié, les colonnes de même hauteur partagent une place."),
        T("A redstone pulse in a column does what the Wrench set: register the nearest player (default), give the "
          "best free place, clear it, or reset the group. Comparator: 15 while taken.\n\n"
          "A Goal Pole Base touching a podium becomes the group's counter.",
          "Une impulsion redstone dans une colonne fait ce que la clé a réglé : inscrire le joueur le plus proche "
          "(défaut), donner la meilleure place libre, vider, ou réinitialiser le groupe. Comparateur : 15 tant "
          "qu'il est occupé.\n\nUn socle de mât d'arrivée qui touche un podium devient le compteur du groupe.",
          "Redstone", "Redstone"),
        C("steveparty:podium", "steveparty:gold_podium",
          "Gold, silver and bronze podiums are only looks.", "Les podiums d'or, d'argent et de bronze ne sont que des apparences."),
    ], parents=["pages"], gate="mini_games"),
    E("mg_controller", "steveparty:mini_game_controller", ("Mini-game Controller", "Contrôleur de mini-jeu"), ("The arena's control panel", "Le pupitre de l'arène"), [
        T("Place it in the arena and click it with the page. Its lamp shows the state: **red** idle, **orange** "
          "practice round, **green** real round.\n\n"
          "Outside a party, **Play** starts the mini-game with the players within 5 blocks of its pipes; no "
          "rewards are paid. A redstone pulse plays, the next one stops.",
          "Pose-le dans l'arène et clique-le avec la page. Sa lampe donne l'état : **rouge** au repos, **orange** "
          "manche d'essai, **verte** vraie manche.\n\n"
          "Hors partie, **Jouer** lance le mini-jeu avec les joueurs à 5 blocs de ses tuyaux ; aucun gain n'est "
          "versé. Une impulsion redstone lance, la suivante arrête."),
        T("In a party, a mini-game with a controller starts with a **practice round**: same teams, no rewards. "
          "It restarts until everyone is ready: press **Y** or the *Ready* button. Then the real round begins.\n\n"
          "Turn practice off in the Party Controller settings.",
          "En partie, un mini-jeu avec un contrôleur commence par une **manche d'essai** : mêmes équipes, sans "
          "gains. Elle recommence tant que tout le monde n'est pas prêt : touche **Y** ou bouton *Prêt*. Puis la "
          "vraie manche commence.\n\nL'essai se désactive dans les réglages du contrôleur de fête.",
          "Practice Round", "Manche d'essai"),
        C("steveparty:mini_game_controller"),
    ], parents=["pages"], gate="mini_games"),
    E("zone", "minecraft:barrier", ("Mini-game Zone", "Zone de mini-jeu"), ("A sealed, restored arena", "Une arène scellée et remise en état"), [
        T("In the page editor, *Draw zone* and right-click two corners (128 blocks per side at most). With the "
          "**Restore** option, each round is played apart in that zone: players leave their inventory at the "
          "door and play with an empty one, nothing crosses the border, and the arena is rebuilt afterwards.\n\n"
          "*Adventure* stops players from breaking or placing blocks.",
          "Dans l'éditeur de la page, *Tracer la zone* puis clic droit sur deux coins (128 blocs par côté au "
          "plus). Avec l'option **Restaurer**, chaque manche se joue à part dans cette zone : les joueurs laissent "
          "leur inventaire à l'entrée et jouent avec un inventaire vide, rien ne traverse la limite, et l'arène est "
          "remise en état après.\n\n*Aventure* empêche de casser ou poser des blocs."),
    ], parents=["mg_controller"], gate="mini_games"),
])

# ---------------------------------------------------------------- Shops
category("shops", "steveparty:trading_stall", ("Shops", "Boutiques"), [
    E("overview", "steveparty:trading_stall", ("Opening a Shop", "Ouvrir une boutique"), ("Four parts, one key", "Quatre pièces, une clé"), [
        T("A shop has 4 parts, all linked to the same trader with the **Shopkeeper Key**:\n"
          "- a **Boxed Trader**: the seller;\n- a **Trading Stall**: the offers;\n"
          "- chests or barrels: the stock;\n- a **Cash register**: the money.\n\n"
          "Right-click the trader with the key, then each block. Fill the stall and the chest, and customers "
          "right-click the trader to buy.",
          "Une boutique a 4 pièces, toutes liées au même marchand avec la **clé du marchand** :\n"
          "- un **marchand en carton** : le vendeur ;\n- un **étal du marchand** : les offres ;\n"
          "- coffres ou tonneaux : le stock ;\n- une **caisse enregistreuse** : l'argent.\n\n"
          "Clic droit sur le marchand avec la clé, puis sur chaque bloc. Remplis l'étal et le coffre, et les "
          "clients achètent d'un clic droit sur le marchand."),
        C("steveparty:shopkeeper_key", None,
          "A star fragment of any colour, a gold ingot and a gold nugget, in a column.",
          "Un fragment d'étoile de n'importe quelle couleur, un lingot d'or et une pépite d'or, en colonne."),
    ]),
    E("villager_block", "steveparty:villager_block", ("Villager Block", "Bloc de villageois"), ("Squash a villager", "Écraser un villageois"), [
        T("A **piston facing down** that extends onto an adult villager's head squashes it into a **Villager "
          "Block**: alive, keeping its trades and job, with about 70 reactions. A sticky piston pulls the "
          "villager back out.",
          "Un **piston tourné vers le bas** qui s'étend sur la tête d'un villageois adulte l'écrase en **bloc de "
          "villageois** : vivant, il garde métier et échanges, avec environ 70 réactions. Un piston collant l'en "
          "fait ressortir."),
        T("Put the Villager Block **on the block to imitate**, then **fall on it** from high enough: 10 + 4 × "
          "that block's hardness (hay 12, stone 16, planks 18). A **Boxed Trader** pops out, disguised as that "
          "block.\n\nThe squashed villager's own trades don't carry over.",
          "Pose le bloc de villageois **sur le bloc à imiter**, puis **tombe dessus** d'assez haut : 10 + 4 × "
          "la dureté de ce bloc (foin 12, pierre 16, planches 18). Un **marchand en carton** en sort, déguisé en "
          "ce bloc.\n\nLes échanges du villageois écrasé ne passent pas au marchand.",
          "Making a Trader", "Obtenir un marchand"),
    ], parents=["overview"]),
    E("trader", "steveparty:boxed_trader_spawn_egg", ("Boxed Trader", "Marchand en carton"), ("A villager hidden in a box", "Un villageois caché dans un carton"), [
        T("A villager hidden in a box disguised as a block. It peeks out when a player comes within 15 blocks "
          "(sneaking players don't wake it); a leash keeps it closed.\n\n"
          "Without a shop it wanders up to 16 blocks around; once a key is linked, it stays put. Killed, it "
          "forgets its owner and links.",
          "Un villageois caché dans un carton déguisé en bloc. Il sort la tête quand un joueur est à 15 blocs "
          "(un joueur accroupi ne le réveille pas) ; une laisse le garde fermé.\n\n"
          "Sans boutique, il se promène à 16 blocs ; dès qu'une clé lui est liée, il reste en place. Tué, il "
          "oublie son propriétaire et ses liens."),
    ], parents=["villager_block"]),
    E("stall", "steveparty:trading_stall", ("Trading Stall", "Étal du marchand"), ("The offers", "Les offres"), [
        T("Each column is an offer (9 at most): the item sold on top, **Price 1** and **Price 2** below. Prices "
          "must match exactly.\n\n"
          "The top item is only a model: each sale takes the same item **from the linked chests**. Without stock, "
          "the offer is greyed out.",
          "Chaque colonne est une offre (9 au plus) : l'objet vendu en haut, **Prix 1** et **Prix 2** dessous. "
          "Les prix doivent correspondre exactement.\n\n"
          "L'objet du haut n'est qu'un modèle : chaque vente prend le même objet **dans les coffres liés**. Sans "
          "stock, l'offre est grisée."),
        C("steveparty:trading_stall", None, "The two carpets give the awning its colours.",
          "Les deux tapis donnent ses couleurs à l'auvent."),
    ], parents=["overview"], gate="open_shop"),
    E("register", "steveparty:cash_register", ("Cash register", "Caisse enregistreuse"), ("Where the money goes", "Où va l'argent"), [
        T("27 slots. After each sale, the payment goes into the linked registers, in order; if they're full, it "
          "drops near the trader, never destroyed. Each sale sends a 4-tick redstone pulse from every linked "
          "register.\n\nMine it with an iron pickaxe or better.",
          "27 emplacements. Après chaque vente, le paiement va dans les caisses liées, dans l'ordre ; si elles "
          "sont pleines, il tombe près du marchand, jamais détruit. Chaque vente envoie une impulsion de 4 ticks "
          "depuis chaque caisse liée.\n\nSe récupère à la pioche en fer ou mieux."),
        C("steveparty:cash_register"),
    ], parents=["stall"], gate="open_shop"),
    E("owners", "steveparty:shopkeeper_key", ("Owners & Protection", "Propriétaire et protection"), ("Your shop, your rules", "Ta boutique, tes règles"), [
        T("The first player to link a key to a trader **owns** it. Only the owner can link blocks, open the stall "
          "and register, open the stock chests or break the shop's blocks; explosions spare them.\n\n"
          "One customer at a time. Hoppers can fill the stock chests and empty the registers.",
          "Le premier joueur qui lie une clé au marchand en devient **propriétaire**. Lui seul lie les blocs, "
          "ouvre l'étal et la caisse, ouvre les coffres de stock ou casse les blocs de la boutique ; les "
          "explosions les épargnent.\n\nUn client à la fois. Les entonnoirs peuvent remplir le stock et vider "
          "les caisses."),
    ], parents=["overview"], gate="open_shop"),
    E("bandanas", "steveparty:bandana", ("Bandanas & Box Costume", "Bandanas et costume"), ("Mischief with shears", "Des bêtises aux cisailles"), [
        T("Each trader wears one of 5 bandanas. **Shears** on an open trader steal it, and it hides for 20 s. "
          "Wearing a bandana, traders ignore you. Give one back to change a trader's colour.\n\n"
          "Shear a bald trader again to steal its box look: a **Box Costume**. Wear it like a chestplate, and "
          "sneak to hide inside as a real-looking block.",
          "Chaque marchand porte l'un de 5 bandanas. Des **cisailles** sur un marchand ouvert le lui volent, et "
          "il se cache 20 s. Avec un bandana, les marchands t'ignorent. Rends-en un pour changer sa couleur.\n\n"
          "Tonds encore un marchand chauve pour voler l'apparence de sa boîte : un **costume de carton**. "
          "Porte-le comme un plastron, et accroupis-toi pour te cacher dedans comme un vrai bloc."),
    ], parents=["trader"], gate="open_shop"),
])

# ---------------------------------------------------------------- Stars & the Mula
category("stars_mula", "steveparty:power_star", ("Stars & the Mula", "Étoiles et Mula"), [
    E("mula", "steveparty:mula_spawn_egg", ("The Mula", "La Mula"), ("A little flying star", "Une petite étoile volante"), [
        T("A small flying star creature. Mulas come in **blue, red, green, yellow, purple** and, rarely, "
          "**black**. They fall from the sky on an **Ephemeride** night, or hatch from a spawn egg.\n\n"
          "A Mula only eats food of **its own colour**: the item tooltip says *Mula food: <colour>*. Right-click "
          "it with the food.",
          "Une petite créature d'étoile volante. Les Mulas sont **bleues, rouges, vertes, jaunes, violettes** et, "
          "rarement, **noires**. Elles tombent du ciel lors d'une nuit d'**Éphéméride**, ou sortent d'un œuf.\n\n"
          "Une Mula ne mange que la nourriture de **sa couleur** : l'infobulle indique *Nourriture de Mula : "
          "<couleur>*. Clic droit avec l'aliment."),
        T("Satiety goes up to **40** and the Mula grows up to ×3. Full, it **bursts**: it drops **64 star "
          "fragments** of its colour (a black one: 1) and flies off as a shooting star, to be reborn 100-400 "
          "blocks away, same Mula, empty belly.\n\nThe best source of star fragments!",
          "La satiété monte jusqu'à **40** et la Mula grossit jusqu'à ×3. Pleine, elle **éclate** : elle lâche "
          "**64 fragments d'étoile** de sa couleur (une noire : 1) et part en étoile filante, pour renaître à "
          "100-400 blocs, même Mula, ventre vide.\n\nLa meilleure source de fragments d'étoile !",
          "Grow and Burst", "Grossir et éclater"),
    ]),
    E("fragments", "steveparty:blue_star_fragment", ("Star Fragments", "Fragments d'étoile"), ("16 colours of stardust", "16 couleurs de poussière d'étoile"), [
        T("16 colours. Mulas drop blue, purple, red, yellow, green and black; mix the others at the crafting "
          "table like dyes, getting back as many as you put in:\n"
          "- red + blue + yellow: 3 white;\n- red + yellow: orange;\n- blue + yellow: green;\n- red + blue: purple…\n\n"
          "Brew an awkward potion with any fragment for a **Potion of Luck**.",
          "16 couleurs. Les Mulas lâchent bleu, violet, rouge, jaune, vert et noir ; les autres se mélangent à "
          "l'établi comme des colorants, en récupérant autant qu'on en met :\n"
          "- rouge + bleu + jaune : 3 blancs ;\n- rouge + jaune : orange ;\n- bleu + jaune : vert ;\n"
          "- rouge + bleu : violet…\n\nUne potion étrange + un fragment à l'alambic donne une **potion de chance**."),
        C("steveparty:blue_star_fragments_block", None,
          "9 fragments make a glowing, glass-like block that breaks back into 9.",
          "9 fragments font un bloc lumineux et translucide, qui se défait en 9."),
    ], parents=["mula"]),
    E("taming", "steveparty:red_star_fragment", ("Taming", "Apprivoiser"), ("A Mula of your own", "Ta propre Mula"), [
        T("Right-click a wild Mula with a fragment of **its own colour**: 1 chance in 3. Tamed, it follows you "
          "like a balloon, orbits your head when you stand still, and sits when you right-click it empty-handed.\n\n"
          "At night, Mulas on a **leash** can lift you into the sky: one big Mula, two half-full ones, or three of "
          "any size.",
          "Clic droit sur une Mula sauvage avec un fragment de **sa couleur** : 1 chance sur 3. Apprivoisée, elle "
          "te suit comme un ballon, tourne autour de ta tête quand tu restes immobile, et s'assoit d'un clic droit "
          "main vide.\n\nLa nuit, des Mulas en **laisse** peuvent t'emporter dans le ciel : une grosse Mula, deux "
          "à moitié pleines, ou trois de n'importe quelle taille."),
    ], parents=["mula"], gate="stardust"),
    E("ephemeride", "minecraft:clock", ("Ephemeride", "Éphéméride"), ("A night of shooting stars", "Une nuit d'étoiles filantes"), [
        T("A night of shooting stars, rolled once per night at dusk: 25% on a full moon, less as the moon wanes, "
          "none on a new moon. Guaranteed on a full moon near a max-level Dice Forge.\n\n"
          "6 waves of stars cross the sky above each player. After the first one, a group of **3-5 Mulas** lands "
          "**150-600 blocks** away, in the direction the stars went.",
          "Une nuit d'étoiles filantes, tirée une fois par nuit au crépuscule : 25 % à la pleine lune, moins quand "
          "elle décroît, jamais à la nouvelle lune. Garantie à la pleine lune près d'une forge à dés au niveau "
          "maximal.\n\n6 vagues d'étoiles traversent le ciel au-dessus de chaque joueur. Après la première, un "
          "groupe de **3 à 5 Mulas** tombe à **150-600 blocs**, dans la direction des étoiles."),
    ], parents=["mula"], gate="stardust"),
    E("telescope", "steveparty:telescope", ("Telescope", "Télescope"), ("Find where the Mulas landed", "Retrouver les Mulas"), [
        T("Replays past Ephemeride nights. At night, under a clear sky, right-click it: scroll to pick a night, "
          "then keep a shooting star in the crosshair until the gauge fills. A **guide star** then lights up "
          "above the spot where the Mulas landed.",
          "Rejoue les nuits d'Éphéméride passées. La nuit, par ciel dégagé, clic droit dessus : la molette choisit "
          "une nuit, puis garde une étoile filante dans le viseur jusqu'à remplir la jauge. Une **étoile guide** "
          "s'allume alors au-dessus de l'endroit où les Mulas sont descendues."),
        C("steveparty:telescope"),
    ], parents=["ephemeride"], gate="stardust"),
    E("power_star", "steveparty:power_star", ("Power Star", "Super étoile"), ("Five colours, one star", "Cinq couleurs, une étoile"), [
        T("A rare, always shining item: one fragment each of **blue, green, yellow, red and purple**, or "
          "**5 black** fragments.\n\nIt crowns the Dice Forge recipe and is the default **Star** of a party.",
          "Un objet rare, toujours brillant : un fragment de chaque couleur **bleu, vert, jaune, rouge et "
          "violet**, ou **5 fragments noirs**.\n\nIl couronne la recette de la forge à dés et c'est l'**Étoile** "
          "par défaut d'une partie."),
        C("steveparty:power_star_from_fragments", "steveparty:power_star_from_black_fragments"),
    ], parents=["fragments"], gate="stardust"),
    E("gravity_core", "steveparty:gravity_core", ("Gravity Core", "Noyau de gravité"), ("A tiny planet", "Une petite planète"), [
        T("Placed, it glows and **pulls** everything within 8 blocks into an orbit, without fall damage; full "
          "netherite armour resists. Mulas and pawns are never pulled.\n\n"
          "Its real job: waking up the **Dice Forge**.",
          "Posé, il brille et **attire** tout ce qui est à 8 blocs en orbite, sans dégâts de chute ; une armure "
          "complète en netherite résiste. Mulas et pions ne sont jamais attirés.\n\n"
          "Son vrai rôle : réveiller la **forge à dés**."),
        C("steveparty:gravity_core", None, "Crying obsidian, 4 black star fragments and a heavy core.",
          "Obsidienne pleureuse, 4 fragments d'étoile noirs et un noyau lourd."),
    ], parents=["power_star"], gate="stardust"),
    E("dice_forge", "steveparty:dice_forge", ("Dice Forge", "Forge à dés"), ("Carve your own dice", "Graver ses propres dés"), [
        T("Right-click it with a **Gravity Core** to wake it up. In its screen:\n"
          "- a ring of **12 face** slots: a stack's size is the face's weight;\n"
          "- **4 star fragment** slots, all different colours (black can repeat and is never used up);\n"
          "- **blank faces**: one per face slot used;\n- **4 module** slots.",
          "Clic droit avec un **noyau de gravité** pour la réveiller. Dans son interface :\n"
          "- un anneau de **12 faces** : la taille d'une pile est le poids de la face ;\n"
          "- **4 fragments d'étoile**, de couleurs toutes différentes (le noir peut se répéter et n'est jamais "
          "consommé) ;\n- **faces vierges** : une par face utilisée ;\n- **4 modules**."),
        C("steveparty:dice_forge", None, "Power Star, obsidian, a blast furnace and a netherite ingot.",
          "Super étoile, obsidienne, un haut fourneau et un lingot de netherite."),
    ], parents=["gravity_core"], gate="stardust"),
    E("forging", "steveparty:forged_dice", ("Forging Dice", "Forger des dés"), ("A die every five seconds", "Un dé toutes les cinq secondes"), [
        T("Click the core (**FORGE**): one die every 5 s, in a loop, while ingredients last. Each die uses its "
          "blank faces and one fragment per non-black slot.\n\n"
          "Hoppers fill it (top and sides) and take dice out from below. Powered = production on. A comparator "
          "reads the output.",
          "Clique le noyau (**FORGER**) : un dé toutes les 5 s, en boucle, tant qu'il y a de quoi. Chaque dé "
          "consomme ses faces vierges et un fragment par emplacement non noir.\n\n"
          "Les entonnoirs la remplissent (dessus, côtés) et sortent les dés par dessous. Alimentée = production "
          "activée. Un comparateur lit la sortie."),
        T("A **Forged Dice** rolls one of its own faces, weighted: a face carved 10 times comes up 10 times as "
          "often. A single face makes a loaded die.\n\n"
          "Forged dice with the same faces stack and can make a Double or Triple Dice.",
          "Un **dé forgé** tire une de ses faces, selon son poids : une face gravée 10 fois sort 10 fois plus "
          "souvent. Une seule face = un dé truqué.\n\n"
          "Des dés forgés aux mêmes faces s'empilent et peuvent faire un dé double ou triple.",
          "Forged Dice", "Dé forgé"),
    ], parents=["dice_forge"], gate="dice_forge"),
    E("rising_core", "steveparty:gravity_core", ("The Rising Core", "Le noyau monte"), ("Orbits and explosions", "Orbites et explosions"), [
        T("The core rises with the fragments inside: 16 blocks × fragments / 256 (a black one counts as 64). At "
          "the top, the forge is at **max level** and guarantees an Ephemeride on full moon nights.\n\n"
          "The raised core pulls things into orbit (up to 32 blocks). Hit it and it **explodes**: no block "
          "broken, but the core is lost. Sneak with an empty hand to take it back safely.",
          "Le noyau monte avec les fragments présents : 16 blocs × fragments / 256 (un noir compte 64). Au sommet, "
          "la forge est au **niveau maximal** et garantit une Éphéméride les nuits de pleine lune.\n\n"
          "Levé, il attire tout en orbite (jusqu'à 32 blocs). Frappé, il **explose** : aucun bloc cassé, mais le "
          "noyau est perdu. Accroupi main vide, on le reprend sans risque."),
    ], parents=["dice_forge"], gate="dice_forge"),
    E("mula_home", "steveparty:mula_spawn_egg", ("Mula Dances", "La danse des Mulas"), ("The forge becomes a home", "La forge devient une maison"), [
        T("A forge with its core becomes the **home** of every Mula within 16 blocks. They stay around it and "
          "**dance** together around the core: ten figures of 26 s each, from the Carousel of Stars to the "
          "Heartbeat Nova.\n\n"
          "A Mula bursting at home loops back and lands 2-4 blocks from the forge. Explode the core and they all "
          "scatter, without fragments.",
          "Une forge avec son noyau devient la **maison** des Mulas à 16 blocs. Elles restent autour et "
          "**dansent** ensemble autour du noyau : dix figures de 26 s, du Carrousel d'étoiles à la Nova "
          "battement de cœur.\n\nUne Mula qui éclate chez elle fait une boucle et retombe à 2-4 blocs de la "
          "forge. Fais exploser le noyau et elles s'envolent toutes, sans fragments."),
    ], parents=["dice_forge"], gate="dice_forge"),
], parallax="nightsky")

# ---------------------------------------------------------------- Decoration
category("decoration", "steveparty:white_plastic_block", ("Decoration", "Décoration"), [
    E("plastic_blocks", "steveparty:white_plastic_block", ("Plastic Blocks", "Blocs de plastique"), ("Toy blocks in 16 colours", "Des blocs jouets en 16 couleurs"), [
        T("4 Plastic Pellets + 1 dye + 1 amethyst shard give 4 blocks. Neighbouring blocks of the same colour "
          "merge into a single piece (up to 4×2×4).\n\n"
          "The Wrench breaks plastic in one hit; shears are fast too.",
          "4 granulés de plastique + 1 colorant + 1 éclat d'améthyste donnent 4 blocs. Les blocs voisins de même "
          "couleur fusionnent en une seule pièce (jusqu'à 4×2×4).\n\n"
          "La clé casse le plastique en un coup ; les cisailles vont vite aussi."),
        C("steveparty:white_plastic_block", "steveparty:white_plastic_block_from_dyeing"),
    ]),
    E("floating", "steveparty:light_blue_plastic_block", ("Plastic Floats", "Le plastique flotte"), ("Water elevators", "Ascenseurs aquatiques"), [
        T("Plastic blocks rise through still water and ride **bubble columns** (up on soul sand, down on magma), "
          "carrying whatever stands on them, without ever crushing anyone.\n\n"
          "A block over soul sand makes a fast water elevator. A **chain** holds a piece in place. Slabs, stairs "
          "and walls never float.",
          "Les blocs de plastique remontent l'eau calme et suivent les **colonnes de bulles** (montée sur sable "
          "des âmes, descente sur magma), en emportant ce qui est dessus, sans jamais écraser personne.\n\n"
          "Un bloc au-dessus du sable des âmes = un ascenseur aquatique rapide. Une **chaîne** retient une pièce. "
          "Dalles, escaliers et murets ne flottent pas."),
    ], parents=["plastic_blocks"]),
    E("plastic_shapes", "steveparty:white_plastic_stairs", ("Studs, Slabs & Fences", "Plots, dalles et barrières"), ("Every plastic shape", "Toutes les formes de plastique"), [
        T("- **Plastic Stud**: 1 block gives 4; sticks to floor, wall or ceiling, needs no support.\n"
          "- **Slabs, stairs, walls**: usual recipes or the stonecutter.\n"
          "- **Plastic Stick**: 2 blocks give 4.\n"
          "- **Fence**: blocks and sticks; it carries road signs.",
          "- **Plot en plastique** : 1 bloc donne 4 ; se pose au sol, au mur ou au plafond, sans support.\n"
          "- **Dalles, escaliers, murets** : recettes habituelles ou tailleur de pierre.\n"
          "- **Bâton en plastique** : 2 blocs donnent 4.\n"
          "- **Barrière** : blocs et bâtons ; elle porte les panneaux routiers."),
        C("steveparty:white_plastic_stud", "steveparty:white_plastic_stairs"),
        C("steveparty:plastic_stick", "steveparty:white_plastic_fence"),
    ], parents=["plastic_blocks"]),
    E("hop_switch", "steveparty:hop_switch", ("Hop Switch", "Interrupteur sauteur"), ("Blocks that vanish and return", "Des blocs qui disparaissent"), [
        T("Makes target blocks **disappear or appear** for a while. Right-click each target with a Cartridge, "
          "then put it in the switch. Jump on it or power it: it stays down for the duration, then pops back up "
          "and **launches** whoever stands on it.\n\n"
          "Modes: toggle, appear, disappear. A **clock** changes the duration (sneak: shorter). All plastic is "
          "switchable.",
          "Fait **disparaître ou apparaître** des blocs ciblés pendant un temps. Clic droit sur chaque cible avec "
          "une cartouche, puis mets-la dans l'interrupteur. Saute dessus ou alimente-le : il reste enfoncé, puis "
          "remonte et **projette** ce qui est dessus.\n\n"
          "Modes : basculer, apparaître, disparaître. Une **horloge** change la durée (accroupi : plus court). "
          "Tout le plastique est basculable."),
        C("steveparty:hop_switch"),
    ], parents=["plastic_blocks"]),
    E("pipes", "steveparty:white_pipe", ("Pipes", "Tuyaux"), ("Enter here, come out there", "Entrer ici, ressortir là"), [
        T("Mario-style pipes. Placed against a pipe or its mouth, they connect. Enter a mouth (right-click, "
          "sneak in front of it, or fall in): you shoot through and come out of another end at random.\n\n"
          "A pipe going into a solid block teleports to the nearest mouth of that colour within 100 blocks.",
          "Des tuyaux façon Mario. Posés contre un tuyau ou son embouchure, ils se raccordent. Entre par une "
          "embouchure (clic droit, accroupi devant, ou en tombant dedans) : tu files et ressors par un autre bout "
          "au hasard.\n\nUn tuyau qui rentre dans un bloc plein téléporte vers l'embouchure la plus proche de "
          "cette couleur, à 100 blocs."),
        C("steveparty:white_pipe", "steveparty:white_windowed_pipe",
          "6 plastic blocks, or 4 + 2 glass for a windowed pipe. Glass pipes: 6 glass.",
          "6 blocs de plastique, ou 4 + 2 verres pour un tuyau à hublot. Tuyau en verre : 6 verres."),
    ], parents=["plastic_blocks"]),
    E("stencils", "steveparty:stencil", ("Stencils", "Pochoirs"), ("A 16x16 pattern", "Un motif 16x16"), [
        T("A **Stencil** holds a 16x16 pattern. Stencil in one hand, dye in the other: right-click a sign or any "
          "full block face to paint it (1 dye). A stencil alone **engraves** a sign. The stencil is never used up.\n\n"
          "Copy one with blank stencils. 50 patterns can be found in structure chests.",
          "Un **pochoir** porte un motif 16x16. Pochoir dans une main, colorant dans l'autre : clic droit sur un "
          "panneau ou une face de bloc pour peindre (1 colorant). Un pochoir seul **grave** un panneau. Le pochoir "
          "n'est jamais consommé.\n\nCopie-le avec des pochoirs vierges. 50 motifs se trouvent dans les coffres "
          "de structures."),
        C("steveparty:stencil"),
    ]),
    E("stencil_maker", "steveparty:stencil_maker", ("Stencil Maker", "Fabrique de pochoirs"), ("Draw your patterns", "Dessiner ses motifs"), [
        T("Right-click it with a stencil, then again to open the editor: left-click cuts a pixel, right-click "
          "fills it, drag to draw lines. Flip, rotate, invert, undo.\n\n"
          "Your **library** (128 patterns, kept on death) learns every patterned stencil you carry. Click a "
          "pattern to copy it for free.",
          "Clic droit dessus avec un pochoir, puis encore pour l'éditeur : clic gauche découpe un pixel, clic "
          "droit le bouche, glisser trace une ligne. Retourner, pivoter, inverser, annuler.\n\n"
          "Ta **bibliothèque** (128 motifs, gardée à la mort) apprend tout pochoir à motif porté. Clique un motif "
          "pour le recopier gratuitement."),
        C("steveparty:stencil_maker"),
    ], parents=["stencils"]),
    E("painting", "minecraft:glow_ink_sac", ("Painting & Touch-ups", "Peindre et retoucher"), ("Dye, ink, sponge, brush", "Colorant, encre, éponge, pinceau"), [
        T("- Dye alone: repaints the symbol.\n- Glow ink sac: the paint glows at night.\n- Sponge: stops the glow.\n"
          "- Wet sponge: erases everything.\n- Brush (held): fades the symbol, gone after 5 strokes.\n\n"
          "Paint on walls, floors and ceilings is a thin layer that hitting can't break.",
          "- Colorant seul : repeint le symbole.\n- Poche d'encre luisante : la peinture brille la nuit.\n"
          "- Éponge : elle ne brille plus.\n- Éponge mouillée : efface tout.\n"
          "- Pinceau (maintenu) : pâlit le symbole, disparu au 5e coup.\n\n"
          "La peinture sur murs, sols et plafonds est une fine couche que les coups ne cassent pas."),
    ], parents=["stencils"]),
    E("hammer", "steveparty:stencil_gun", ("Stencil Hammer", "Marteau à pochoirs"), ("Stamp in one hit", "Tamponner d'un coup"), [
        T("A wooden drum hammer loaded with **9 stencils and 9 dyes** (sneak + right-click). Sneak + scroll "
          "changes the stencil or the colour (**G** switches which). Right-click to **stamp**: a drum thud, a "
          "paint splash and the pattern printed, 1 dye per hit.\n\nThe last colour, *Engrave*, uses no paint.",
          "Un marteau-tambour en bois chargé de **9 pochoirs et 9 colorants** (accroupi + clic droit). Accroupi + "
          "molette change de pochoir ou de couleur (**G** choisit lequel). Clic droit pour **tamponner** : bruit "
          "de tambour, éclaboussure et motif imprimé, 1 colorant par coup.\n\nLa dernière couleur, *Graver*, ne "
          "consomme rien."),
        C("steveparty:stencil_gun"),
    ], parents=["stencils"]),
    E("signs", "steveparty:oak_easel_sign", ("Signs", "Panneaux"), ("Five signs for your symbols", "Cinq panneaux pour tes symboles"), [
        T("Five signs take a symbol, keep it when broken and turn in 16 directions:\n"
          "- **Easel Sign**: any planks, stands on the ground;\n"
          "- **Wooden Panel**: 9 planks;\n"
          "- **Cut-Out Wooden Panel**: stencil + axe carves its outline;\n"
          "- **Rock Sign**: really engraved; neighbours merge into a lintel;\n"
          "- **Plastic Road Sign**: the Wrench changes its plate.",
          "Cinq panneaux reçoivent un symbole, le gardent une fois cassés et tournent dans 16 directions :\n"
          "- **Chevalet** : toutes planches, se pose au sol ;\n"
          "- **Grand panneau en bois** : 9 planches ;\n"
          "- **Panneau en bois découpé** : pochoir + hache découpent sa silhouette ;\n"
          "- **Pierre gravée** : vraie gravure, les voisines forment un linteau ;\n"
          "- **Panneau routier en plastique** : la clé change sa plaque."),
        T("Panels and road signs: sneak-click the top of a fence or wall to stand on it, click its side to hang "
          "it, any other face to lay it flat.\n\n"
          "Material recipes need the **same** block everywhere: no half-oak, half-birch sign.",
          "Panneaux et panneaux routiers : accroupi + clic sur le dessus d'une barrière ou d'un muret pour le "
          "poser dessus, sur son côté pour l'accrocher, sur toute autre face pour le poser à plat.\n\n"
          "Les recettes à matériau demandent **le même** bloc partout : pas de panneau moitié chêne, moitié bouleau.",
          "Placing Signs", "Poser un panneau"),
        SP("steveparty:plastic_road_sign", "A plastic block over pellets gives 2. Off a post, it floats like plastic.",
           "Un bloc de plastique sur des granulés en donne 2. Hors d'un poteau, il flotte comme le plastique."),
    ], parents=["stencils"]),
    E("building", "steveparty:polished_white_terracotta", ("Building Blocks", "Blocs de construction"), ("Polished terracotta and concrete", "Terre cuite et béton polis"), [
        T("Two families, 8 shapes per colour: **Polished Terracotta** (17 colours) and **Polished Concrete** (16): "
          "block, bevelled bricks, stairs, slab, wall, and the brick versions.\n\n"
          "2×2 terracotta or concrete gives 4 polished blocks; 2×2 polished gives 4 bricks. The stonecutter skips "
          "steps.",
          "Deux familles, 8 formes par couleur : **terre cuite polie** (17 couleurs) et **béton poli** (16) : "
          "bloc, briques biseautées, escalier, dalle, muret, et leurs versions en briques.\n\n"
          "2×2 terre cuite ou béton donnent 4 blocs polis ; 2×2 blocs polis donnent 4 briques. Le tailleur de "
          "pierre saute les étapes."),
        C("steveparty:polished_white_terracotta", "steveparty:polished_white_concrete"),
        T("Two polished blocks of different colours in a 2×2 checkerboard give 4 **Polished Tiles** (concrete or "
          "terracotta). Swap the colours for the shifted pattern; the stonecutter makes single-colour tiles.",
          "Deux blocs polis de couleurs différentes en damier 2×2 donnent 4 **carrelages** (béton ou terre "
          "cuite). Inverse les couleurs pour le damier décalé ; le tailleur de pierre fait un carrelage d'une "
          "seule couleur.",
          "Checkered Tiles", "Carrelage"),
    ]),
])

# ---------------------------------------------------------------- Gadgets
category("gadgets", "steveparty:looting_box", ("Gadgets", "Gadgets"), [
    E("looting_box", "steveparty:looting_box", ("Looting Box", "Boîte à butin"), ("Headbutt it for loot", "Un coup de tête, du butin"), [
        T("Open it with the Wrench and insert an **Inventory Cartridge** linked to a chest. Jump and **headbutt** "
          "it from below: it drops items from the chest (all, next or random, per the cartridge mode). Two hits, "
          "then 3 s of rest.\n\nTip: leave 2-3 blocks of air below it.",
          "Ouvre-la avec la clé et mets-y une **cartouche d'inventaire** liée à un coffre. Saute et frappe-la "
          "**de la tête** par en dessous : elle lâche des objets du coffre (tout, le suivant ou au hasard, selon "
          "la cartouche). Deux coups, puis 3 s de repos.\n\nAstuce : laisse 2-3 blocs d'air dessous."),
        C("steveparty:looting_box"),
    ]),
    E("shoes", "steveparty:triple_jump_shoes", ("Triple Jump Shoes", "Chaussures de triple saut"), ("Hop, hop, HOP", "Hop, hop, HOP"), [
        T("Chain jumps less than 1.5 s apart: the 2nd one is ×1.5 higher, the 3rd ×2, then the cycle starts over.",
          "Enchaîne les sauts à moins de 1,5 s d'écart : le 2e est ×1,5 plus haut, le 3e ×2, puis le cycle "
          "recommence."),
        C("steveparty:triple_jump_shoes", None, "Leather boots, a rabbit's foot and 2 slime balls.",
          "Bottes en cuir, une patte de lapin et 2 boules de slime."),
    ]),
    E("plunger", "steveparty:plunger", ("Plunger", "Ventouse"), ("Silence!", "Silence !"), [
        T("Right-click a mob: silenced! Again: it gets its voice back.",
          "Clic droit sur un mob : réduit au silence ! Encore une fois : il retrouve sa voix."),
        C("steveparty:plunger"),
    ]),
    E("villager_moods", "steveparty:villager_block", ("Villager Block Moods", "Humeurs du bloc de villageois"), ("About 70 reactions", "Environ 70 réactions"), [
        T("A Villager Block reacts to about 70 things with 16 faces: it waves when you arrive, blushes when "
          "stared at, drools when you eat, panics at zombies, dances to a jukebox (wild on Pigstep), begs when "
          "you break it…\n\nGive it bread or a flower, or click it 10 times quickly and watch it see stars.",
          "Un bloc de villageois réagit à environ 70 choses avec 16 visages : il salue quand tu arrives, rougit "
          "quand on le fixe, bave quand tu manges, panique devant un zombie, danse sur un jukebox (déchaîné sur "
          "Pigstep), supplie quand tu le casses…\n\nDonne-lui du pain ou une fleur, ou clique-le 10 fois vite pour "
          "qu'il voie des étoiles."),
    ]),
])

BOOK_NAME = ("Steve Party Maker Guide", "Guide de Steve Party Maker")
BOOK_TOOLTIP = ("Build a board, run a party, tame a Mula.", "Construis un plateau, mène une partie, apprivoise une Mula.")

PARALLAX = {
    "flow": dict(background="modonomicon:textures/gui/dark_slate_seamless.png", size=512, layers=[
        ("modonomicon:textures/gui/parallax/flow/base.png", 0.7),
        ("modonomicon:textures/gui/parallax/flow/1.png", 1.0),
        ("modonomicon:textures/gui/parallax/flow/2.png", 1.4)]),
    "nightsky": dict(background="modonomicon:textures/gui/parallax/nightsky/nightsky_0.png", size=256, layers=[
        ("modonomicon:textures/gui/parallax/nightsky/nightsky_0.png", 0.5),
        ("modonomicon:textures/gui/parallax/nightsky/nightsky_1.png", 0.7),
        ("modonomicon:textures/gui/parallax/nightsky/nightsky_2.png", 0.9),
        ("modonomicon:textures/gui/parallax/nightsky/nightsky_3.png", 1.1),
        ("modonomicon:textures/gui/parallax/nightsky/nightsky_4.png", 1.3)]),
}

# --------------------------------------------------------------------------------------------------


def dump(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def layout(entries):
    """Left-to-right tree layout: depth on x (3 apart), leaves stacked on y (2 apart)."""
    by_id = {e["id"]: e for e in entries}
    children = {e["id"]: [] for e in entries}
    for e in entries:
        if e["parents"]:
            children[e["parents"][0]].append(e["id"])
    pos, counter = {}, [0]

    def place(eid, depth):
        kids = children[eid]
        for k in kids:
            place(k, depth + 1)
        y = (pos[kids[0]][1] + pos[kids[-1]][1]) / 2 if kids else counter[0] * 2
        if not kids:
            counter[0] += 1
        pos[eid] = (depth * 3, y)

    roots = [e["id"] for e in entries if not e["parents"]]
    for r in roots:
        place(r, 0)
        counter[0] += 0.5  # a little gap between separate trees
    ys = [p[1] for p in pos.values()]
    mid = (min(ys) + max(ys)) / 2
    xs = [p[0] for p in pos.values()]
    xmid = (min(xs) + max(xs)) // 2
    return {k: (int(round(v[0] - xmid)), int(round(v[1] - mid))) for k, v in pos.items()}, by_id


def gates_of(entry, by_id):
    """All milestone gates on the entry's path to its root, so a child never shows before its parent."""
    gates, cur = [], entry
    while cur is not None:
        if cur["gate"] and cur["gate"] not in gates:
            gates.append(cur["gate"])
        cur = by_id[cur["parents"][0]] if cur["parents"] else None
    return gates


def condition_json(gates):
    def adv(g):
        return {"type": "modonomicon:advancement", "advancement_id": f"steveparty:guide/{g}",
                "tooltip": f"{P}.lock.{g}"}
    if not gates:
        return None
    if len(gates) == 1:
        return adv(gates[0])
    return {"type": "modonomicon:and", "children": [adv(g) for g in gates]}


def build():
    en, fr = {}, {}

    def tr(key, pair):
        en[key], fr[key] = pair

    if BOOK_DIR.exists():
        shutil.rmtree(BOOK_DIR)
    if ADV_DIR.exists():
        shutil.rmtree(ADV_DIR)

    tr(f"{P}.name", BOOK_NAME)
    tr(f"{P}.tooltip", BOOK_TOOLTIP)
    dump(BOOK_DIR / "book.json", {
        "name": f"{P}.name",
        "tooltip": f"{P}.tooltip",
        "model": "modonomicon:modonomicon_purple",
        "generate_book_item": True,
        "creative_tab": "steveparty:item_group",
        "display_mode": "node",
        "auto_add_read_conditions": False,
        "allow_open_book_with_invalid_links": True,
    })

    for g, m in MILESTONES.items():
        if m["lock"]:
            tr(f"{P}.lock.{g}", m["lock"])

    for sort, cat in enumerate(CATEGORIES):
        cid = cat["id"]
        ck = f"{P}.{cid}"
        tr(f"{ck}.name", cat["name"])
        px = PARALLAX[cat["parallax"]]
        dump(BOOK_DIR / "categories" / f"{cid}.json", {
            "name": f"{ck}.name",
            "icon": {"item": cat["icon"]},
            "sort_number": sort,
            "display_mode": "node",
            "background": px["background"],
            "background_width": px["size"],
            "background_height": px["size"],
            "background_parallax_layers": [{"background": b, "speed": s} for b, s in px["layers"]],
            "max_scroll_x": 512,
            "max_scroll_y": 512,
            "show_category_button": True,
        })
        pos, by_id = layout(cat["entries"])
        for esort, e in enumerate(cat["entries"]):
            ek = f"{ck}.{e['id']}"
            tr(f"{ek}.name", e["name"])
            tr(f"{ek}.description", e["desc"])
            pages, text_n = [], 0
            for i, pg in enumerate(e["pages"]):
                pk = f"{ek}.page{i}"
                if pg["kind"] == "text":
                    tr(f"{pk}.text", (pg["en"], pg["fr"]))
                    title_en = pg["title_en"] or (e["name"][0] if text_n == 0 else None)
                    title_fr = pg["title_fr"] or (e["name"][1] if text_n == 0 else None)
                    page = {"type": "modonomicon:text", "text": f"{pk}.text"}
                    if title_en:
                        tr(f"{pk}.title", (title_en, title_fr))
                        page["title"] = f"{pk}.title"
                    text_n += 1
                elif pg["kind"] == "spotlight":
                    tr(f"{pk}.text", (pg["en"], pg["fr"]))
                    page = {"type": "modonomicon:spotlight", "item": {"item": pg["item"]}, "text": f"{pk}.text"}
                else:
                    page = {"type": f"modonomicon:{pg['kind']}_recipe", "recipe_id_1": pg["r1"]}
                    if pg["r2"]:
                        page["recipe_id_2"] = pg["r2"]
                    if pg["en"]:
                        tr(f"{pk}.text", (pg["en"], pg["fr"]))
                        page["text"] = f"{pk}.text"
                pages.append(page)
            x, y = pos[e["id"]]
            data = {
                "category": cid,
                "name": f"{ek}.name",
                "description": f"{ek}.description",
                "icon": {"item": e["icon"]},
                "x": x,
                "y": y,
                "sort_number": esort,
                "hide_while_locked": False,
                "show_when_any_parent_unlocked": False,
            }
            if e["parents"]:
                data["parents"] = [{"entry": f"{cid}/{p}", "draw_arrow": True, "line_enabled": True}
                                   for p in e["parents"]]
            cond = condition_json(gates_of(e, by_id))
            if cond:
                data["condition"] = cond
            if e["gate"] and (not e["parents"] or by_id[e["parents"][0]]["gate"] != e["gate"]):
                data["background_u_index"] = 1  # the entry that a milestone opens gets the special frame
            data["pages"] = pages
            dump(BOOK_DIR / "entries" / cid / f"{e['id']}.json", data)

    for g, m in MILESTONES.items():
        ak = f"advancements.steveparty.guide.{g}"
        tr(f"{ak}.title", (m["en"][0], m["fr"][0]))
        tr(f"{ak}.description", (m["en"][1], m["fr"][1]))
        criteria = {}
        for name, kind, value in m["criteria"]:
            if kind == "has":
                items = value if isinstance(value, str) else (value[0] if len(value) == 1 else value)
                criteria[name] = {"trigger": "minecraft:inventory_changed",
                                  "conditions": {"items": [{"items": items}]}}
            else:
                criteria[name] = {"trigger": "minecraft:placed_block",
                                  "conditions": {"location": [{"condition": "minecraft:block_state_property",
                                                               "block": value}]}}
        display = {
            "icon": {"id": m["icon"]},
            "title": {"translate": f"{ak}.title"},
            "description": {"translate": f"{ak}.description"},
            "frame": m["frame"],
            "show_toast": m["toast"],
            "announce_to_chat": False,
            "hidden": False,
        }
        adv = {}
        if m["parent"]:
            adv["parent"] = f"steveparty:guide/{m['parent']}"
        else:
            display["background"] = "minecraft:textures/block/light_blue_concrete.png"
        adv["display"] = display
        adv["criteria"] = criteria
        adv["requirements"] = [list(criteria)]
        dump(ADV_DIR / f"{g}.json", adv)

    dump(LANG_DIR / "en_us.json", dict(sorted(en.items())))
    dump(LANG_DIR / "fr_fr.json", dict(sorted(fr.items())))
    n_entries = sum(len(c["entries"]) for c in CATEGORIES)
    print(f"book steveparty:{BOOK_ID}: {len(CATEGORIES)} categories, {n_entries} entries, "
          f"{len(MILESTONES)} advancements, {len(en)} lang keys")


if __name__ == "__main__":
    build()

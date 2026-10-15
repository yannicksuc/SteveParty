#!/usr/bin/env python3
"""Builds the FTB Quests book of the Steve Party Maker modpack (FTB Quests 2101.x, Minecraft 1.21.1).

Writes modpack/config/ftbquests/quests/: data.snbt, chapter_groups.snbt, chapters/*.snbt,
reward_tables/*.snbt and lang/en_us.snbt + lang/fr_fr.snbt (FTB Quests 2101 keeps every title and
description in the lang files, keyed by object type and hex id).

Object ids are derived from stable keys (sha1), so running the script again gives the same ids.
Item titles come from the mod's own lang files (EN/FR), so they always match the names in game.

Once the book has been edited in game (edit mode), the exported files become the reference:
do not run this script over them without porting the edits back here.

Usage: python modpack/tools/build_quests.py
"""
import hashlib
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "modpack", "config", "ftbquests", "quests")
MOD_LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "steveparty", "lang")

EN_NAMES = json.load(open(os.path.join(MOD_LANG, "en_us.json"), encoding="utf-8"))
FR_NAMES = json.load(open(os.path.join(MOD_LANG, "fr_fr.json"), encoding="utf-8"))


# ---------------------------------------------------------------------------------------------
# Ids and names
# ---------------------------------------------------------------------------------------------

def make_id(kind, key):
    """Stable positive 64-bit id (never 0 or 1, which FTB Quests reserves)."""
    h = int(hashlib.sha1(f"steveparty-quests:{kind}:{key}".encode()).hexdigest()[:16], 16)
    h &= 0x7FFFFFFFFFFFFFFF
    if h < 2:
        h += 2
    return h


def hexid(n):
    return "%016X" % n


FR_COLOR = {"white": "blanc", "orange": "orange", "magenta": "magenta", "light_blue": "bleu clair", "yellow": "jaune",
            "lime": "vert clair", "pink": "rose", "gray": "gris", "light_gray": "gris clair", "cyan": "cyan",
            "purple": "violet", "blue": "bleu", "brown": "marron", "green": "vert", "red": "rouge", "black": "noir"}
EN_COLOR = {c: c.replace("_", " ") for c in FR_COLOR}


def item_name(item_id, lang):
    ns, path = item_id.split(":")
    table = EN_NAMES if lang == "en" else FR_NAMES
    for prefix in ("item", "block"):
        k = f"{prefix}.{ns}.{path}"
        if k in table:
            return table[k]
    raise KeyError(f"no {lang} name for {item_id}")


# ---------------------------------------------------------------------------------------------
# Task / reward helpers
# ---------------------------------------------------------------------------------------------

def I(item, count=1, how="craft"):
    """Item task (steveparty: prefix added when missing).

    how = "craft": only items crafted in a crafting grid count (only_from_crafting), so items a player already
          owns (starter kit, rewards of other quests) never complete it; the build checks the recipe exists.
    how = "consume": the player hands the items in by clicking the task (consume_items), never automatic.
    how = "own": owning the items is enough (drops, stonecutter, items given on purpose by an earlier quest).
    """
    if ":" not in item:
        item = "steveparty:" + item
    assert how in ("craft", "consume", "own"), how
    return {"type": "item", "item": item, "count": count, "how": how}


def C():
    """Checkmark task (the player ticks it)."""
    return {"type": "checkmark"}


def A(advancement):
    """Advancement task: done when the player earns this advancement of the mod (an action, not an item)."""
    return {"type": "advancement", "advancement": "steveparty:" + advancement}


def O(entity, ticks=20):
    """Observation task: look at an entity type for a moment."""
    return {"type": "observation", "entity": entity, "ticks": ticks}


def XP(n):
    return {"type": "xp", "xp": n}


def LV(n):
    return {"type": "xp_levels", "xp_levels": n}


def IT(item, count=1):
    if ":" not in item:
        item = "steveparty:" + item
    return {"type": "item", "item": item, "count": count}


def COINS(n):
    return IT("steveparty:coin", n)


def RANDOM(table):
    return {"type": "random", "table": table}


def CHOICE(table):
    return {"type": "choice", "table": table}


# Reward tiers: small rewards on ordinary quests, bigger ones on milestones and chapter ends.
def T0():
    return [XP(10)]


def T1():
    return [XP(15), COINS(1)]


def T2():
    return [XP(25), COINS(2)]


def T3():
    return [XP(50), COINS(3)]


def MILESTONE():
    return [XP(50), RANDOM("party_favors")]


def CHAPTER_END():
    return [LV(5), COINS(10), RANDOM("star_chest")]


# ---------------------------------------------------------------------------------------------
# Book content
# ---------------------------------------------------------------------------------------------

GROUPS = [
    # key, icon, en, fr
    ("board_game", "steveparty:party_controller", "The Board Game", "Le jeu de plateau"),
    ("commerce", "steveparty:trading_stall", "Commerce", "Commerce"),
    ("stars", "steveparty:party_star", "Stars", "Étoiles"),
    ("creatures", "steveparty:magpie_nest", "Creatures", "Créatures"),
    ("decoration", "steveparty:red_plastic_block", "Decoration", "Décoration"),
]

CHAPTERS = []
_current = None


def chapter(key, group, icon, title, subtitle):
    global _current
    _current = {"key": key, "group": group, "icon": icon, "title": title, "subtitle": subtitle, "quests": []}
    CHAPTERS.append(_current)


def q(key, x, y, tasks, deps=(), rewards=None, title=None, desc=None, sub=None, icon=None,
      shape=None, size=None, optional=False, dep_req=None):
    """One quest. title/desc/sub are (english, french); desc items are lists of lines.
    deps: keys in this chapter, or 'chapter.key' for another chapter."""
    if isinstance(tasks, dict):
        tasks = [tasks]
    _current["quests"].append({
        "key": f"{_current['key']}.{key}", "x": x, "y": y, "tasks": tasks,
        "deps": [d if "." in d else f"{_current['key']}.{d}" for d in deps],
        "rewards": rewards if rewards is not None else T1(),
        "title": title, "desc": desc, "sub": sub, "icon": icon, "shape": shape, "size": size,
        "optional": optional, "dep_req": dep_req,
    })


def done(key, x, y, deps, title, desc, icon, sub=None):
    """Chapter-completion milestone. No task: FTB Quests completes a task-less quest by itself once its
    dependencies are complete, so there is no button a player could press too early."""
    q(key, x, y, [], deps=deps, rewards=CHAPTER_END(), title=title, desc=desc, sub=sub, icon=icon,
      shape="gear", size=2.0)


# === Welcome ===================================================================================
chapter("welcome", None, "steveparty:party_controller",
        ("Welcome", "Bienvenue"),
        (["Build a board game in Minecraft, and play it with your friends."],
         ["Construis un jeu de plateau dans Minecraft et joues-y entre amis."]))

q("welcome", 0, 0, C(), rewards=[XP(20)], icon="steveparty:party_controller", shape="gear", size=2.0,
  title=("Welcome to Steve Party Maker!", "Bienvenue dans Steve Party Maker !"),
  sub=("A party game, block by block", "Un party game, bloc par bloc"),
  desc=(["Build a board game in Minecraft and play it with friends: tiles, pawns, dice, mini-games and stars.",
         "",
         "One quest per item, a big milestone at the end of each chapter. Tick this quest to start!"],
        ["Construis un jeu de plateau dans Minecraft et joues-y entre amis : tuiles, pions, dés, mini-jeux et étoiles.",
         "",
         "Une quête par objet, une grande étape au bout de chaque chapitre. Coche cette quête pour commencer !"]))

q("pellets", -3, 2.25, I("plastic_pellets", 4, how="consume"), deps=["welcome"],
  rewards=[XP(15), IT("minecraft:sugar_cane", 8)],
  desc=(["Smelt sugar cane. Pellets go into almost everything: tiles, cartridges, Tokens, plastic blocks."],
        ["Fais cuire de la canne à sucre. Les granulés entrent dans presque tout : tuiles, cartouches, Réceptacles de Pion, blocs de plastique."]))

q("coin", 3, 2.25, I("coin", 1), deps=["welcome"],
  rewards=[XP(15), IT("minecraft:gold_nugget", 4)],
  desc=(["1 gold nugget = 1 Coin. The default currency of a party: mini-games pay Coins, shops sell for them."],
        ["1 pépite d'or = 1 Pièce. La monnaie par défaut d'une partie : les mini-jeux paient en Pièces, les boutiques vendent contre elles."]))

q("recipes", -1, 2.25, C(), deps=["welcome"], rewards=T0(), optional=True, icon="minecraft:knowledge_book",
  title=("Read the recipes", "Lire les recettes"),
  desc=(["Every recipe of the mod can be read in a recipe viewer (REI or EMI) if the pack has one: it also shows every tile with its cartridge.",
         "The mod's wiki (on its GitHub page) explains each block in detail."],
        ["Toutes les recettes du mod se lisent dans un visualiseur de recettes (REI ou EMI) si le pack en a un : il montre aussi chaque tuile avec sa cartouche.",
         "Le wiki du mod (sur sa page GitHub) explique chaque bloc en détail."]))

q("team", 1, 2.25, C(), deps=["welcome"], rewards=T0(), optional=True, icon="minecraft:white_banner",
  title=("Party up", "Faire équipe"),
  desc=(["Quests are shared per FTB Teams team. To share progress with friends, create a party with /ftbteams party create and invite them.",
         "Rewards stay personal: everyone claims their own."],
        ["Les quêtes sont partagées par équipe FTB Teams. Pour avancer ensemble, crée une équipe avec /ftbteams party create et invite tes amis.",
         "Les récompenses restent personnelles : chacun réclame les siennes."]))

done("done", 0, 4.5, ["pellets", "coin"],
     ("Ready to party", "Prêt pour la fête"),
     (["You have the basics. Next: build a board (The Board Game), then explore the other chapters in any order."],
      ["Tu as les bases. Ensuite : construire un plateau (Le jeu de plateau), puis explorer les autres chapitres dans l'ordre que tu veux."]),
     "steveparty:tile")
# Lighter end for the first chapter.
CHAPTERS[-1]["quests"][-1]["rewards"] = [XP(50), COINS(5), RANDOM("party_favors")]


# === The Board =================================================================================
chapter("board", "board_game", "steveparty:tile",
        ("The Board", "Le plateau"),
        (["Cartridges, tiles and the Tile Linker Brush: build the path your pawns will walk."],
         ["Cartouches, tuiles et Pinceau lieur : construis le parcours de tes pions."]))

q("cartridge", 0, -7, I("board_space_behavior", 2), deps=["welcome.pellets"], size=1.25,
  rewards=[XP(15), IT("board_space_behavior", 4)],
  desc=(["2 redstone + 2 paper + 2 plastic pellets.",
         "A cartridge gives a tile its role. This plain one has none: just a path. Every other cartridge is this one + an ingredient.",
         "Right-click in the air: its menu."],
        ["2 redstone + 2 papier + 2 granulés de plastique.",
         "Une cartouche donne son rôle à une case. Celle-ci n'en a aucun : juste un chemin. Toute autre cartouche, c'est elle + un ingrédient.",
         "Clic droit dans le vide : son menu."]))

q("tile", -6, -1.5, I("tile"), deps=["cartridge"], rewards=[XP(15), IT("plastic_pellets", 4), IT("default_dice", 2)], size=1.25,
  desc=(["4 white plastic slabs, a heavy pressure plate and a cartridge: a Tile holding that cartridge.",
         "One tile = one step. It goes in 8 directions, on slabs, carpets and even stairs."],
        ["4 dalles de plastique blanc, une plaque de pression lourde et une cartouche : une Tuile qui la contient.",
         "Une tuile = un pas. Elle se pose dans 8 directions, sur dalles, tapis et même escaliers."]))

q("brush", -4, -1.5, I("tile_linker_brush"), deps=["tile"], rewards=T2(),
  desc=(["Stick, redstone and feather, in a diagonal. It paints the links between spaces and shows the board while held."],
        ["Bâton, redstone et plume, en diagonale. Il peint les liens entre les cases et montre le plateau tant que tu le tiens."]))

q("link", -2, -1.5, C(), deps=["brush"], rewards=T2(), icon="steveparty:tile_linker_brush",
  title=("Paint the path", "Peindre le chemin"),
  desc=(["Hold right-click and sweep the tiles in walking order: each one links to the next.",
         "Paint a link again to erase it. Left-click: the brush's wheel (undo, redo, link modes)."],
        ["Maintiens le clic droit et balaie les tuiles dans l'ordre de marche : chacune se lie à la suivante.",
         "Repeins un lien pour l'effacer. Clic gauche : la roue du pinceau (annuler, rétablir, modes)."]))

q("loop", 0, -1.5, C(), deps=["link"], rewards=T2(), icon="minecraft:firework_rocket",
  title=("Close the loop", "Fermer la boucle"),
  desc=(["Paint back to the first tile: a loop with a Start space is all a party needs.",
         "Several links from a tile: a fork, the player chooses. No link: a dead end."],
        ["Repeins jusqu'à la première tuile : une boucle avec une case départ suffit pour une partie.",
         "Plusieurs liens depuis une case : un embranchement, le joueur choisit. Aucun lien : un cul-de-sac."]))

q("board_view", 2, -1.5, C(), deps=["loop", "wrench"], rewards=T1(), icon="minecraft:spyglass",
  title=("Board view and check", "Vue du plateau et diagnostic"),
  desc=(["Brush in hand: chevrons along the links, step numbers, \"?\" on forks, \"!\" on dead ends.",
         "Wrench on a Party Controller (or /steveparty board check): every problem of the board."],
        ["Pinceau en main : chevrons sur les liens, numéros de pas, « ? » aux bifurcations, « ! » aux impasses.",
         "Clé à molette sur un Contrôleur de Party (ou /steveparty board check) : tous les problèmes du plateau."]))

q("helmet", 4, -1.5, I("explorer_helmet"), deps=["board_view"], rewards=T2(),
  desc=(["Leather + glowstone dust + copper ingot.",
         "Lamp on (G): the whole board, the nearby spaces in detail, the exact numbers of the one you look at."],
        ["Cuir + poudre de pierre lumineuse + lingot de cuivre.",
         "Lampe allumée (G) : tout le plateau, le détail des cases proches, les chiffres de celle que tu regardes."]))

q("wrench", -4, 0.5, I("wrench"), deps=["tile"], rewards=T2(),
  desc=(["Redstone + iron ingot.",
         "Right-click opens tiles and Checkpoints. A cartridge in the off hand swaps the tile's own, links kept.",
         "It also sets podiums, poles and Spawn Bubbles, and breaks plastic in one hit."],
        ["Redstone + lingot de fer.",
         "Clic droit : ouvre tuiles et Checkpoints. Une cartouche en main secondaire remplace celle de la tuile, liens gardés.",
         "Elle règle aussi podiums, mâts et Bulles d'apparition, et casse le plastique d'un coup."]))

q("check_point", -6, 0.75, I("check_point", 4), deps=["tile"], rewards=T1(),
  desc=(["Prismarine shards + light blue panes + chest: 4 Checkpoints.",
         "Pawns float through without spending a step: free turns, forks and detours. Its role never triggers, except Stop and Shop."],
        ["Éclats de prismarine + vitres bleu clair + coffre : 4 Checkpoints.",
         "Les pions le traversent sans dépenser de pas : virages, bifurcations et détours gratuits. Son rôle ne joue jamais, sauf Stop et Boutique."]))

q("colours", -6, 2.25, C(), deps=["check_point"], rewards=T1(), icon="minecraft:glow_ink_sac",
  title=("Colours and glow", "Couleurs et lueur"),
  desc=(["Dye: colours the cartridge (on a Checkpoint, the whole cube).",
         "Glow ink sac: it shines. Ink sac: off. Sponge: dye removed."],
        ["Teinture : colore la cartouche (sur un Checkpoint, tout le cube).",
         "Poche d'encre luisante : il brille. Poche d'encre : éteint. Éponge : plus de teinture."]))

q("advanced_tile", -2, 0.5, I("advanced_tile"), deps=["tile"], rewards=T2(),
  desc=(["2 yellow plastic slabs, a light pressure plate, a trapped chest and 2 cartridges.",
         "It holds 16 cartridges: the redstone power picks the active one (0 = slot 1, 1 = slot 2...)."],
        ["2 dalles de plastique jaune, une plaque de pression légère, un coffre piégé et 2 cartouches.",
         "Elle contient 16 cartouches : la puissance redstone choisit l'active (0 = emplacement 1, 1 = emplacement 2...)."]))

q("redstone_slots", -2.75, 2.25, C(), deps=["advanced_tile"], rewards=T1(), icon="minecraft:comparator",
  title=("A tile that changes", "Une case qui change"),
  desc=(["Two different cartridges in an Advanced Tile, then power it: role, colour and destinations follow the signal.",
         "With a redstone counter, the space changes every turn."],
        ["Deux cartouches différentes dans une Tuile avancée, puis alimente-la : rôle, couleur et destinations suivent le signal.",
         "Avec un compteur redstone, la case change à chaque tour."]))

q("router", -1.25, 2.25, I("board_space_redstone_router"), deps=["advanced_tile"], rewards=T2(),
  desc=(["A tile with a switch lever: the lever leans with the power it receives, its torch lights while powered.",
         "Link it to its tiles with the Tile Linker Brush: one lever switches a whole board.",
         "A comparator reads its tiles: 14 bonus, 13 malus, 10 Stop, 8 Teleport, 1 a pawn passes..."],
        ["Une case avec un levier d'aiguillage : le levier penche avec la puissance reçue, sa torche s'allume quand il est alimenté.",
         "Lie-le à ses cases au Pinceau lieur : un levier change tout un plateau.",
         "Un comparateur lit ses cases : 14 Bonus, 13 Malus, 10 Stop, 8 Téléportation, 1 un pion passe..."]))

q("sizes", -8, -0.5, C(), deps=["tile"], rewards=T1(), icon="minecraft:stonecutter",
  title=("Small and large tiles", "Petites et grandes tuiles"),
  desc=(["Stonecutter: 1 Tile = 2 small tiles (1x1); 2 small ones = 1 Tile again at the crafting table.",
         "4 Tiles in a 2x2 square: 1 large tile (2x2 blocks)."],
        ["Tailleur de pierre : 1 Tuile = 2 petites tuiles (1x1) ; 2 petites = 1 Tuile à l'établi.",
         "4 Tuiles en carré 2x2 : 1 grande tuile (2x2 blocs)."]))

q("silk", -8, 1, C(), deps=["tile"], rewards=T0(), optional=True, icon="minecraft:enchanted_book",
  title=("Pack a tile", "Emporter une tuile"),
  desc=(["Break a tile with Silk Touch: one item that keeps its cartridges, their settings and its look."],
        ["Casse une tuile avec Toucher de soie : un seul objet qui garde ses cartouches, leurs réglages et son apparence."]))

# Cartridges in one row between the Cartridge (above) and the rest of the board (below).
q("start", -6, -5, I("tile_behavior_start"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + lime dye. A pawn placed on it binds to it, floats and takes its colour.",
         "The Party Controller only takes pawns bound to a start space."],
        ["Cartouche + teinture vert clair. Un pion posé dessus s'y lie, flotte et prend sa couleur.",
         "Le Contrôleur de Party ne prend que les pions liés à une case de départ."]))

q("stop", -4.5, -5, I("board_space_behavior_stop"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + red dye. A pawn reaching it stops there, whatever steps it has left. Works on a Checkpoint too."],
        ["Cartouche + teinture rouge. Un pion qui l'atteint s'y arrête, quels que soient ses pas restants. Marche aussi sur un Checkpoint."]))

q("advance", -3, -5, I("advance_back_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + piston. The pawn stopping here moves again, 1 to 6 spaces forward (green) or back (pink), pushed by a Boomcart.",
         "Sneak + scroll, cartridge in hand: count and direction."],
        ["Cartouche + piston. Le pion qui s'y arrête repart de 1 à 6 cases, en avant (vert) ou en arrière (rose), poussé par un Pétaroule.",
         "Accroupi + molette, cartouche en main : nombre et sens."]))

q("replay", -1.5, -5, I("replay_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + repeater. The player whose pawn stops here rolls again at once, with their die back. One extra turn only."],
        ["Cartouche + répéteur. Le joueur dont le pion s'y arrête rejoue aussitôt, avec son dé. Un seul tour en plus."]))

q("teleport", 0, -5, I("teleport_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + ender pearl. A pawn stopping here goes to another Teleport space of the same colour (purple, green, orange or blue)."],
        ["Cartouche + perle de l'Ender. Un pion qui s'y arrête part sur une autre case Téléportation de la même couleur (violet, vert, orange ou bleu)."]))

q("inventory", 1.5, -5, I("inventory_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + chest. It links up to 8 chests (right-click them) and gives or takes their items.",
         "Positive items: bonus space (blue). Negative: malus (red). None: item space (orange).",
         "No chest, in a party: the Party Controller's storage."],
        ["Cartouche + coffre. Elle lie jusqu'à 8 coffres (clic droit dessus) et donne ou prend leurs objets.",
         "Objets positifs : case Bonus (bleue). Négatifs : case Malus (rouge). Aucun : case objet (orange).",
         "Sans coffre, en partie : le stockage du Contrôleur de Party."]))

q("shop", 3, -5, I("shop_cartridge"), deps=["inventory", "shops.bandana"], rewards=T1(),
  desc=(["Inventory Cartridge + a bandana stolen from a Boxed Trader (see Shops).",
         "A pawn stopping here (or passing a Checkpoint with it) brings out a Boxed Trader. Up to 9 offers, set in the cartridge's stall; the goods come from its chests, else the Party Controller's storage."],
        ["Cartouche Bonus / Malus + un bandana volé à un Marchand en carton (voir Boutiques).",
         "Un pion qui s'y arrête (ou passe un Checkpoint qui la porte) fait sortir un Marchand en carton. 9 offres au plus, réglées dans l'étal de la cartouche ; la marchandise vient de ses coffres, sinon du stockage du Contrôleur de Party."]))

q("threshold", 4.5, -5, I("threshold_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + iron bars. A wall: a pawn with steps left only goes through if its roll meets the condition (7 or more by default, a double, a triple...). Otherwise it stops here.",
         "Barrier mode: a new try every turn."],
        ["Cartouche + barreaux de fer. Un mur : un pion avec des pas restants ne passe que si son lancer remplit la condition (7 ou plus par défaut, un double, un triple...). Sinon il s'arrête ici.",
         "Mode Barrière : un nouvel essai à chaque tour."]))

q("gate_key", 7.5, -5, I("gate_key"), deps=["cartridge"], rewards=T1(),
  desc=(["Gold ingot + amethyst shard. Opens a Key Gate once. An item like any other: sell it, give it as a prize, hide it in a Tricauldron."],
        ["Lingot d'or + éclat d'améthyste. Ouvre une fois un Portail à clé. Un objet comme un autre : vends-la, offre-la en gain, cache-la dans un Trichaudron."]))

q("key_gate", 6, -5, I("key_gate_cartridge"), deps=["gate_key"], rewards=T2(),
  desc=(["Cartridge + Gate Key. A gate rises on each locked exit: going through uses up a Gate Key.",
         "No key: wait, take another way, or go back to start. \"Open\": stays open a few turns, or for good."],
        ["Cartouche + Clé de portail. Un portail se dresse sur chaque sortie verrouillée : passer consomme une Clé de portail.",
         "Sans clé : attendre, prendre un autre chemin ou retourner au départ. « Ouvert » : ouvert quelques manches, ou pour toujours."]))

q("tp_pair", 0, -3.5, C(), deps=["teleport"], rewards=T1(), icon="minecraft:ender_pearl",
  title=("A teleport network", "Un réseau de téléportation"),
  desc=(["Two Teleport spaces of the same colour on one board. A dye on the space changes its network; a lone colour sends nobody anywhere."],
        ["Deux cases Téléportation de la même couleur sur un plateau. Une teinture sur la case change son réseau ; une couleur seule n'envoie personne."]))

q("bonus_malus", 1.5, -3.5, C(), deps=["inventory"], rewards=T1(), icon="minecraft:chest",
  title=("Bonus and malus", "Bonus et Malus"),
  desc=(["One blue (bonus) and one red (malus) space. In a party, the colour under each pawn makes the mini-game teams."],
        ["Une case Bonus (bleue) et une case Malus (rouge). En partie, la couleur sous chaque pion forme les équipes des mini-jeux."]))

done("done", 4.25, 2.75, ["loop", "wrench", "check_point", "advanced_tile", "router", "start", "stop", "advance",
                       "replay", "tp_pair", "bonus_malus", "threshold", "key_gate"],
     ("Board builder", "Bâtisseur de plateau"),
     (["Tiles, checkpoints, the main cartridges and a closed loop: your board is ready for pawns."],
      ["Tuiles, checkpoints, les principales cartouches et une boucle fermée : ton plateau attend ses pions."]),
     "steveparty:advanced_tile")


# === Pawns =====================================================================================
chapter("pawns", "board_game", "steveparty:tokenizer_wand",
        ("Pawns", "Pions"),
        (["Any mob can become a pawn with the Tokenizer Wand."],
         ["N'importe quelle créature devient un pion avec la Baguette de pions."]))

q("crystal_ball", 0, 0, I("lapis_crystal_ball"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["A Party Star in 4 lapis blocks (the star: see Mulas and Stars). The head of the Tokenizer Wand."],
        ["Une Party Star entre 4 blocs de lapis-lazuli (l'étoile : voir Mulas et étoiles). La tête de la Baguette de pions."]))

q("wand", 2, 0, I("tokenizer_wand"), deps=["crystal_ball"], rewards=T3(),
  desc=(["Lapis crystal ball, gold block and blaze rod, in a column. A golden wand with a lapis orb."],
        ["Boule de cristal en lapis, bloc d'or et bâton de Blaze, en colonne. Une baguette dorée à orbe de lapis."]))

q("pionificus", 4, 0, C(), deps=["wand"], rewards=T2(), icon="steveparty:tokenizer_wand",
  title=("Pionificus!", "Pionificus !"),
  desc=(["Right-click a mob with the wand (or shoot it a spark), then draw a circle around it: on the guide, a 1-block pawn; from 0.25 to 2 blocks.",
         "It becomes your still, silent, invulnerable figurine."],
        ["Clic droit sur une créature avec la baguette (ou lance-lui une flammèche), puis trace un cercle autour : sur le guide, un pion d'un bloc ; de 0,25 à 2 blocs.",
         "Elle devient ta figurine immobile, muette et invulnérable."]))

q("resize", 4, -1.75, C(), deps=["pionificus"], rewards=T0(), optional=True, icon="minecraft:blaze_rod",
  title=("Resizing spell", "Sort de redimensionnement"),
  desc=(["Right-click one of your pawns with the wand and draw a new circle to change its size."],
        ["Clic droit sur un de tes pions avec la baguette et trace un nouveau cercle pour changer sa taille."]))

q("dye", 4, 1.75, C(), deps=["pionificus"], rewards=T0(), optional=True, icon="minecraft:light_blue_dye",
  title=("Team colours", "Couleurs d'équipe"),
  desc=(["Right-click a pawn with a dye: its name and its base change colour (and its start space too)."],
        ["Clic droit sur un pion avec une teinture : son nom et son socle changent de couleur (et sa case de départ aussi)."]))

q("token", 6, 0, I("token"), deps=["pionificus"], rewards=T2(),
  desc=(["5 plastic pellets + chest. Right-click your pawn with an empty Token to store it, right-click a block to put it back: how a pawn moves by hand."],
        ["5 granulés de plastique + coffre. Clic droit sur ton pion avec un Réceptacle de Pion vide pour le ranger, clic droit sur un bloc pour le reposer : ainsi se déplace un pion à la main."]))

q("bind", 8, -1.25, C(), deps=["token", "board.start"], rewards=MILESTONE(), icon="steveparty:tile_behavior_start",
  shape="hexagon", size=1.25,
  title=("Take your place", "Prendre sa place"),
  desc=(["Put your pawn on a Start space: it binds to it and floats. Right-click the space empty-handed to own it.",
         "Only bound pawns join a party."],
        ["Pose ton pion sur une case de départ : il s'y lie et flotte. Clic droit main vide sur la case pour en devenir propriétaire.",
         "Seuls les pions liés entrent dans une partie."]))

q("game_master", 2, 1.75, C(), deps=["wand"], rewards=T2(), optional=True, icon="minecraft:enchanted_book",
  title=("Game Master", "Maître du jeu"),
  desc=(["A treasure enchantment for the wand: woodland mansion chests, evokers and illusioners.",
         "Resize anyone's pawn and exclude any pawn from a party, like a referee."],
        ["Un enchantement trésor pour la baguette : coffres de manoir, évocateurs et illusionnistes.",
         "Redimensionne le pion de n'importe qui et exclus n'importe quel pion d'une partie, comme un arbitre."]))

done("done", 10, 0, ["bind", "token"],
     ("Pawn master", "Maître des pions"),
     (["Your pawn waits on its start space. Time to roll some dice!"],
      ["Ton pion attend sur sa case de départ. À toi les dés !"]),
     "steveparty:token")


# === Dice ======================================================================================
chapter("dice", "board_game", "steveparty:default_dice",
        ("Dice", "Dés"),
        (["Roll, move, and engrave your own faces."],
         ["Lance, avance, et grave tes propres faces."]))

q("default_dice", 4.5, -4, I("default_dice", how="own"), deps=["welcome.done"], rewards=[XP(15), IT("default_dice", 1)], size=1.25,
  desc=(["Rolls 1 to 10. The first two come with the Tile quest; more come out of the Dice Forge."],
        ["Donne 1 à 10. Les deux premiers viennent avec la quête Tuile ; les suivants sortent de la Forge à dés."]))

q("roll", 6.25, -3.25, C(), deps=["default_dice"], rewards=T1(), icon="steveparty:default_dice",
  title=("Roll it!", "Lance-le !"),
  desc=(["Right-click: the die flies over the nearest mob (sneak: the nearest player) and spins. Hit it to stop it."],
        ["Clic droit : le dé part au-dessus de la créature la plus proche (accroupi : du joueur le plus proche) et tourne. Frappe-le pour l'arrêter."]))

q("move", 8.25, -3.25, C(), deps=["roll", "pawns.pionificus"], rewards=T2(), icon="steveparty:tile",
  title=("Move a pawn", "Faire avancer un pion"),
  desc=(["Roll near your pawn on a linked board: it walks the result. At a fork, right-click an arrow empty-handed."],
        ["Lance près de ton pion sur un plateau lié : il avance du résultat. À un embranchement, clic droit main vide sur une flèche."]))

q("double", 6.25, -4.5, I("double_dice"), deps=["default_dice"], rewards=T1(),
  desc=(["Two Default Dice, shapeless. Two linked dice: 2 to 20. Splits back into two."],
        ["Deux Dés simples, sans forme. Deux dés liés : 2 à 20. Se redécoupe en deux."]))

q("triple", 8.75, -4.5, I("triple_dice"), deps=["double"], rewards=T2(),
  desc=(["Three Default Dice, or a Double + a Default Die. Three linked dice: 3 to 30."],
        ["Trois Dés simples, ou un Dé double + un Dé simple. Trois dés liés : 3 à 30."]))

# Faces sub-tree
q("blank", 4.5, -0.5, I("blank_dice_face", 4), deps=["default_dice"], rewards=[XP(15), IT("blank_dice_face", 4)], size=1.25,
  desc=(["2 iron ingots + 2 nether quartz: 4 Blank Die Faces. Every face starts blank (stonecutter); the Dice Forge uses them up."],
        ["2 lingots de fer + 2 quartz du Nether : 4 Faces de dé vierges. Toute face part d'une vierge (tailleur de pierre) ; la Forge à dés en consomme."]))

# Faces around the Blank Die Face, modules in a row between it and "Fit a module": every arrow is a straight spoke.
q("face6", 2.75, -3.25, I("dice_face_6", how="own"), deps=["blank"], rewards=T1(),
  desc=(["Stonecutter: a blank face becomes any number from 0 to 10. Any face goes back to blank the same way."],
        ["Tailleur de pierre : une face vierge devient n'importe quel nombre de 0 à 10. Toute face redevient vierge de la même façon."]))

q("face0", 1.75, -2.25, I("dice_face_0", how="own"), deps=["blank"], rewards=T1(),
  desc=(["A special face: the pawn stays where it is and the space under it triggers again."],
        ["Une face spéciale : le pion reste sur place et la case où il se trouve rejoue son effet."]))

q("premium", 1.25, -1, I("premium_dice_face_10", how="own"), deps=["blank"], rewards=T1(),
  desc=(["Premium faces (1 to 10) shine in gold on the die. For now they count like normal numbers."],
        ["Les faces dorées (1 à 10) brillent en or sur le dé. Pour l'instant, elles comptent comme des nombres normaux."]))

q("cursed", 1.25, 0.25, I("cursed_dice_face_3", how="own"), deps=["blank"], rewards=T1(),
  desc=(["Cursed faces (1 to 3) show a purple skull. For now they count like normal numbers."],
        ["Les faces maudites (1 à 3) portent un crâne violet. Pour l'instant, elles comptent comme des nombres normaux."]))

q("coin_face", 7.75, -0.75, I("coin_dice_face_1"), deps=["blank", "welcome.coin"], rewards=T1(),
  desc=(["Blank face + Coin: a +1 Coins face (stonecutter: +2 to +10). Instead of moving, the roller gets that many Coins from the storage."],
        ["Face vierge + Pièce : une face Pièces +1 (tailleur de pierre : +2 à +10). Au lieu d'avancer, le lanceur reçoit autant de Pièces du stockage."]))

q("debt_face", 7.25, -2.25, I("debt_dice_face_1"), deps=["blank"], rewards=T1(),
  desc=(["Blank face + spider eye: a -1 Debt face (stonecutter: -2 to -10). The roller pays that many Coins to the storage, never more than they have."],
        ["Face vierge + œil d'araignée : une face Dette -1 (tailleur de pierre : -2 à -10). Le lanceur verse autant de Pièces au stockage, jamais plus qu'il n'en a."]))

q("swap_face", 7.5, 0.5, I("swap_dice_face"), deps=["blank"], rewards=T2(),
  desc=(["Blank face + ender pearl. The roller's pawn swaps places with another player's pawn of their choice."],
        ["Face vierge + perle de l'Ender. Le pion du lanceur échange sa place avec celui d'un autre joueur, à son choix."]))

# Modules sub-tree: a blank module first, then each module from 7 fragments of its colour (its icon's ring)
q("blank_module", 4.5, 0.875, I("blank_dice_module"), deps=["blank"], rewards=T1(),
  desc=(["A Blank Die Face in a cross of 4 gold nuggets: a Blank Die Module, the base of every module."],
        ["Une Face de dé vierge dans une croix de 4 pépites d'or : un Module de dé vierge, la base de tous les modules."]))

MODULES = [
    ("slow", "light_blue", "a clock", "une horloge",
     "The die spins slowly; its roller hits it to stop on the face shown.",
     "Le dé tourne lentement ; son lanceur le frappe pour l'arrêter sur la face affichée."),
    ("choice", "blue", "a compass", "une boussole",
     "The roller picks the face.", "Le lanceur choisit la face."),
    ("power_up", "purple", "an echo shard", "un éclat d'écho",
     "The die becomes a power-up: used once, in place of your roll (every other die comes back to its roller).",
     "Le dé devient un power-up : utilisé une fois, à la place de ton lancer (tout autre dé revient à son lanceur)."),
    ("lucky", "green", "a rabbit's foot", "une patte de lapin",
     "One extra roll per module (up to 5): keep the best one.",
     "Un lancer de plus par module (jusqu'à 5) : garde le meilleur."),
    ("reroll", "orange", "a wind charge", "une charge de vent",
     "Stop or again: after the result, keep it or roll again, once per module.",
     "Stop ou encore : après le résultat, garde-le ou relance, une fois par module."),
    ("reversed", "red", "a fermented spider eye", "un œil d'araignée fermenté",
     "A negative module: the pawn moves back, and Coins won are lost.",
     "Un module négatif : le pion recule, et les Pièces gagnées sont perdues."),
    ("skeleton_key", "yellow", "a tripwire hook", "un crochet",
     "During this move the pawn ignores Stop spaces and shop stops.",
     "Pendant ce déplacement, le pion ignore les cases Stop et les haltes boutique."),
    ("homing", "magenta", "an eye of ender", "un œil de l'Ender",
     "At forks the pawn chooses on its own, at random.",
     "Aux embranchements, le pion choisit tout seul, au hasard."),
    ("firecracker", "pink", "TNT", "de la TNT",
     "The die explodes when it vanishes: it hurts and knocks back everyone around, roller included.",
     "Le dé explose en disparaissant : il blesse et repousse tout le monde autour, lanceur compris."),
]
def fr_plural(colour):
    """French colour adjectives in the plural: orange, magenta and the compound ones never take an s."""
    name = FR_COLOR[colour]
    return name if colour in ("orange", "magenta") or " " in name else name + "s"


for i, (m, colour, ing_en, ing_fr, en, fr) in enumerate(MODULES):
    q("module_" + m, 0.125 + i * 1.25, 2.25, I("dice_module_" + m), deps=["blank_module", "mulas.frag_" + colour],
      rewards=T1(),
      desc=([f"A Blank Die Module ringed with 7 {EN_COLOR[colour]} star fragments, {ing_en} on top.", en],
            [f"Un Module de dé vierge entouré de 7 fragments d'étoile {fr_plural(colour)}, avec {ing_fr} au-dessus.", fr]))

q("fit_module", 4.5, 4.25, C(), deps=["module_" + m[0] for m in MODULES], dep_req="one_completed",
  rewards=T2(), icon="minecraft:crafting_table",
  title=("Fit a module", "Poser un module"),
  desc=(["Crafting table: a die + module items = the same die with those modules (the module items stay in the grid). Its tooltip lists them."],
        ["Établi : un dé + des objets module = le même dé avec ces modules (les objets module restent dans la grille). Son infobulle les liste."]))

done("done", 12.375, 2, ["triple", "move", "swap_face", "coin_face", "fit_module"],
     ("Dice collector", "Collectionneur de dés"),
     (["Dice, special faces and modules: every roll can now be a surprise."],
      ["Dés, faces spéciales et modules : chaque lancer peut maintenant être une surprise."]),
     "steveparty:triple_dice")


# === Running a party ===========================================================================
chapter("party", "board_game", "steveparty:party_controller",
        ("Running a Party", "Mener une partie"),
        (["The Party Controller runs turns, mini-games and prizes."],
         ["Le Contrôleur de Party enchaîne les manches, les mini-jeux et les gains."]))

q("controller", 0, 0, I("party_controller"), deps=["board.tile", "dice.default_dice"], rewards=T3(), size=1.25,
  desc=(["Map, glass, redstone torch, gold ingots, chest, and the redstone plate base (iron, redstone, iron).",
         "The heart of a party: turns, mini-games and prizes."],
        ["Carte, verre, torche de redstone, lingots d'or, coffre, et le socle redstone (fer, redstone, fer).",
         "Le cœur d'une partie : manches, mini-jeux et gains."]))

q("dashboard", 2.75, -0.25, C(), deps=["controller"], rewards=T1(), icon="steveparty:party_controller",
  title=("The dashboard", "Le tableau de bord"),
  desc=(["Right-click: six tabs (Status, Players, Program, Gains, Storage, Settings).",
         "Status lists what is missing before \"Start the party\" lights up; a \"!\" marks the tab to fix."],
        ["Clic droit : six onglets (État, Joueurs, Programme, Gains, Stockage, Réglages).",
         "État liste ce qui manque avant que « Lancer la partie » s'allume ; un « ! » marque l'onglet à régler."]))

q("bank", 7.75, 1.25, C(), deps=["dashboard"], rewards=T1(), icon="minecraft:chest",
  title=("Fill the storage", "Remplir le stockage"),
  desc=(["The controller creates nothing: put the party's Coins and Party Stars in its Storage tab (hoppers work too).",
         "Prizes, stars for sale and spaces without a chest are paid from it; what the party takes goes back there.",
         "Chests linked to it with the Tile Linker Brush pay after its own slots."],
        ["Le contrôleur ne crée rien : mets les Pièces et Party Stars de la partie dans son onglet Stockage (les entonnoirs marchent aussi).",
         "Gains, étoiles à vendre et cases sans coffre sont payés de là ; ce que la partie prend y retourne.",
         "Les coffres qui lui sont liés au Pinceau lieur paient après ses propres cases."]))

q("currency", 9.25, 1.25, C(), deps=["bank"], rewards=T1(), icon="minecraft:gold_nugget",
  title=("Your own currency", "Ta propre monnaie"),
  desc=(["Gains tab, above the columns: the item used as Coin and as Star. Click with any item to use it instead (nothing is taken), empty hand for the default.",
         "Messages and menus then name your items: \"5 × Gold Nugget\"."],
        ["Onglet Gains, au-dessus des colonnes : l'objet qui sert de Pièce et d'Étoile. Clique avec n'importe quel objet pour l'adopter (rien n'est pris), main vide pour revenir au défaut.",
         "Messages et menus nomment alors tes objets : « 5 × Pépite d'or »."]))

q("first_party", 6, -0.75, C(), deps=["dashboard", "pawns.bind"], rewards=MILESTONE(), icon="minecraft:firework_rocket",
  shape="hexagon", size=1.25,
  title=("Let's party!", "Que la fête commence !"),
  desc=(["Pawns on start spaces, then \"Start the party\" (or a redstone pulse). Everyone rolls for the order; each round is one turn per pawn and a mini-game.",
         "Most Party Stars wins, then most Coins."],
        ["Des pions sur les cases de départ, puis « Lancer la partie » (ou une impulsion redstone). Chacun lance pour l'ordre ; chaque manche = un tour par pion et un mini-jeu.",
         "Le plus de Party Stars gagne, puis le plus de Pièces."]))

q("hud", 8.25, -0.5, C(), deps=["first_party"], rewards=T0(), optional=True, icon="minecraft:painting",
  title=("Arrange the HUD", "Disposer l'interface"),
  desc=(["During a party, the turn strip, announcements and scoreboard appear on screen. Press M (or /partyhud) to move them, H to hide them."],
        ["Pendant une partie, la barre de tours, les annonces et le tableau des scores s'affichent. Touche M (ou /partyhud) pour les déplacer, H pour les masquer."]))

q("step", 4, -1.5, I("step_controller"), deps=["controller"], rewards=T2(),
  desc=(["A tape deck. Right-click a button on top: its mode. Previous · Replay · Next.",
         "Next + a pulse: skips what the party waits for (a turn not rolled, an antenna, the practice round); in a mini-game, ends it with the podiums' places.",
         "Replay + a pulse: the turn is undone and played again, or the round restarts. Previous: back to the turn before.",
         "Link it to any part of a game with the Tile Linker Brush: it drives that party; not linked, it does nothing (looked at, it shows the brush crossed out in red)."],
        ["Un magnétophone. Clic droit sur un bouton du dessus : son mode. Précédent · Rejouer · Suivant.",
         "Suivant + une impulsion : passe ce que la partie attend (tour pas lancé, antenne, manche de chauffe) ; en mini-jeu, le termine avec les places des podiums.",
         "Rejouer + une impulsion : le tour est annulé et rejoué, ou la manche recommence. Précédent : retour au tour d'avant.",
         "Lie-le au Pinceau lieur à un élément d'une partie : il pilote cette partie ; pas lié, il ne fait rien (regardé, il montre le pinceau barré de rouge)."]))

q("bell", 4, -4, I("party_bell"), deps=["controller"], rewards=T2(),
  desc=(["Right-click it with the Wrench: every moment it can listen to, by family (party, turns, coins and stars, events, mini-games). It pulses then, and a comparator reads a value (die result, rank...).",
         "Wait mode: the party freezes until the antenna gets a signal.",
         "Link it with the Tile Linker Brush to any part of the game (the Party Controller, a space...): it listens to that party; not linked, it hears nothing (its screen and bubble show the brush crossed out in red)."],
        ["Clic droit avec la Clé à molette : tous les moments qu'elle peut écouter, par famille (partie, tours, pièces et étoiles, évènements, mini-jeux). Elle émet alors une impulsion, et un comparateur lit une valeur (résultat du dé, rang...).",
         "Mode attente : la partie se fige jusqu'à ce qu'elle reçoive un signal.",
         "Lie-la au Pinceau lieur à un élément d'une partie (le Contrôleur de Party, une case...) : elle écoute cette partie ; pas liée, elle n'entend rien (son écran et sa bulle montrent le pinceau barré de rouge)."]))

q("piggy", 4, -2.75, I("piggy_bank"), deps=["controller", "board.inventory"], rewards=T2(),
  desc=(["A ceramic pig with an Inventory Cartridge. Each pulse gives (or takes) its items: player of the turn, mini-game winners, everyone or the nearest.",
         "All-or-nothing mode: sell a Party Star for Coins.",
         "Link it to any part of a game with the Tile Linker Brush: it serves that party; not linked, it does nothing (its panel and menu show the brush crossed out in red)."],
        ["Un cochon en céramique avec une Cartouche Bonus / Malus. Chaque impulsion donne (ou prend) ses objets : joueur du tour, gagnants du mini-jeu, tout le monde ou le plus proche.",
         "Mode tout ou rien : de quoi vendre une Party Star contre des Pièces.",
         "Lie-la au Pinceau lieur à un élément d'une partie : elle sert cette partie ; pas liée, elle ne fait rien (son panneau et son menu montrent le pinceau barré de rouge)."]))

CARDS = [
    ("turns", "blue", ["Every pawn plays once."], ["Chaque pion joue une fois."]),
    ("minigame", "lime", ["One mini-game from the catalogue."], ["Un mini-jeu du catalogue."]),
    ("event", "orange", ["Pings the Party Antennas set to Event; the channel is the stack size."],
     ["Fait capter les Antennes de Party réglées sur Évènement ; le canal est la taille de la pile."]),
    ("sequence_start", "pink", ["The next Repeat card starts again from here."],
     ["La carte Répéter suivante reprend d'ici."]),
    ("repeat", "purple", ["Plays the cards since the last Sequence Start again, as many times as cards in its stack."],
     ["Rejoue les cartes depuis le dernier Début de séquence, autant de fois qu'il y a de cartes dans sa pile."]),
]
for i, (card, stud, en, fr) in enumerate(CARDS):
    q("card_" + card, 1.5 + i * 1.35, 2.5, I("party_card_" + card, 4), deps=["dashboard"], rewards=T1(),
      desc=([f"A {EN_COLOR[stud]} plastic stud + paper + redstone: 4 cards.", en[0],
             "Program tab: 24 slots, read left to right."],
            [f"Un plot en plastique {FR_COLOR[stud]} + papier + redstone : 4 cartes.", fr[0],
             "Onglet Programme : 24 emplacements, lus de gauche à droite."]))

q("program", 6.75, 5, C(), deps=["card_turns", "card_minigame", "card_sequence_start", "card_repeat"], rewards=MILESTONE(),
  icon="steveparty:party_card_repeat", shape="hexagon", size=1.25,
  title=("Write a program", "Écrire un programme"),
  desc=(["Write your own party, for example: Sequence Start, Turns, Repeat x3, Sequence Start, Mini-game, Repeat x2.",
         "No cards: the number of rounds set in Settings."],
        ["Écris ta propre partie, par exemple : Début de séquence, Tour de jeu, Répéter x3, Début de séquence, Mini-jeu, Répéter x2.",
         "Sans carte : le nombre de manches des Réglages."]))

q("trap", 9.25, -3.5, I("powerup_trap"), deps=["first_party", "welcome.coin"], rewards=T1(),
  desc=(["Coin + stone pressure plate + string. On your turn, before rolling, right-click: it sits on your pawn's space.",
         "Blank, it steals 10 Coins. Sneak + right-click: choose its effect (Coins, steps back, a lost turn, an item) and sign it.",
         "Another player's pawn that stops there springs it, for you."],
        ["Pièce + plaque de pression en pierre + ficelle. Pendant ton tour, avant de lancer, clic droit : il se pose sur la case de ton pion.",
         "Vierge, il vole 10 Pièces. Accroupi + clic droit : choisis son effet (Pièces, recul, tour perdu, un objet) et signe-le.",
         "Le pion d'un autre joueur qui s'y arrête le déclenche, à ton profit."]))

q("star_space", 7, -3.75, I("star_cartridge"), deps=["controller", "mulas.power_star"], rewards=T2(),
  desc=(["Cartridge + Party Star. At the start of a party, the star lands on one Star space, under a golden beam, with a Mula playing with it.",
         "Its menu: the price (20 Coins by default), sold on landing or in passing."],
        ["Cartouche + Party Star. En début de partie, l'étoile se pose sur une case Étoile, sous un faisceau doré, avec un Mula qui joue avec.",
         "Son menu : le prix (20 Pièces par défaut), vendue à l'arrêt ou au passage."]))

q("buy_star", 9, -5.25, C(), deps=["star_space", "first_party"], rewards=MILESTONE(), icon="steveparty:party_star",
  shape="hexagon", size=1.25,
  title=("Catch a star!", "Attrape une étoile !"),
  desc=(["Reach the star and buy it ([Buy] in the chat): the price goes to the storage, a star comes out of it for you.",
         "The Mula throws it to you; the star may move on to another Star space."],
        ["Atteins l'étoile et achète-la ([Acheter] dans le chat) : le prix va au stockage, une étoile en sort pour toi.",
         "Le Mula te la lance ; l'étoile peut partir sur une autre case Étoile."]))

done("done", 10.5, -2, ["first_party", "step", "bell", "piggy", "program", "bank", "currency", "buy_star"],
     ("Party host", "Maître de cérémonie"),
     (["You can run a whole party, and drive it with redstone."],
      ["Tu sais mener une partie entière, et la piloter à la redstone."]),
     "steveparty:party_bell")


# === Mini-games ================================================================================
chapter("minigames", "board_game", "steveparty:mini_game_page",
        ("Mini-games", "Mini-jeux"),
        (["Pages, pipes, podiums and arenas: design your own mini-games."],
         ["Pages, tuyaux, podiums et arènes : crée tes propres mini-jeux."]))

q("page", 0, 0, I("mini_game_page", 4), deps=["board.cartridge"], rewards=T2(), size=1.25,
  desc=(["A Cartridge in 4 paper: 4 Mini-Game Pages. A page IS a mini-game: title, rules, image, formats, pipes, results and zone."],
        ["Une Cartouche entre 4 papiers : 4 Pages de mini-jeu. Une page EST un mini-jeu : titre, règles, image, modes de jeu, tuyaux, résultats et zone."]))

q("write_page", 5.75, -4.5, C(), deps=["page"], rewards=T1(), icon="minecraft:writable_book",
  title=("Write the page", "Écrire la page"),
  desc=(["Right-click in the air with the page: the editor. A title, a description (400 characters, with colours) and an image (drop a PNG or JPEG on the window), shown when the mini-game is drawn."],
        ["Clic droit dans le vide avec la page : l'éditeur. Un titre, une description (400 caractères, en couleurs) et une image (dépose un PNG ou un JPEG sur la fenêtre), affichée quand le mini-jeu est tiré."]))

q("formats", 9.5, -4.75, C(), deps=["write_page"], rewards=T1(), icon="minecraft:white_banner",
  title=("Formats", "Modes de jeu"),
  desc=(["Teams (2 to 4 sides, \"1 vs all\", \"2 vs 2\"...), free-for-all or all together. A page is drawn only if one of its formats fits the board's teams.",
         "Team A: players on positive spaces. Team B: on negative ones."],
        ["Équipes (2 à 4 camps, « 1 contre tous », « 2 contre 2 »...), chacun pour soi ou tous ensemble. Une page n'est tirée que si l'un de ses modes de jeu convient aux équipes du plateau.",
         "Équipe A : joueurs sur une case positive. Équipe B : sur une case négative."]))

q("zone", 8.5, -4, C(), deps=["write_page"], rewards=T1(), icon="minecraft:structure_void",
  title=("Draw the zone", "Tracer la zone"),
  desc=(["\"Draw the zone\" in the editor, then click two corners (128 blocks per side at most).",
         "Restore option: each round plays apart, with an empty inventory, and the arena is rebuilt at the end."],
        ["« Tracer la zone » dans l'éditeur, puis clique sur deux coins (128 blocs par côté au plus).",
         "Option Restaurer : chaque manche se joue à part, inventaire vide, et l'arène est reconstruite à la fin."]))

q("linked_copy", 4, -4.5, C(), deps=["write_page"], rewards=T0(), optional=True, icon="minecraft:paper",
  title=("Linked copies", "Copies liées"),
  desc=(["Page + paper (or the Copy button): a linked copy, edit one and they all change. Handy for pipes and podiums far apart."],
        ["Page + papier (ou le bouton Copier) : une copie liée, modifier l'une modifie toutes les autres. Pratique pour des tuyaux et podiums éloignés."]))

q("catalogue", 7.75, -3, I("mini_games_catalogue"), deps=["page", "party.controller"], rewards=T2(),
  desc=(["Book and quill + map + clock + the \"otherside\" disc. Holds 91 pages. In the Party Controller, the roulette draws its mini-games."],
        ["Livre et plume + carte + horloge + disque « otherside ». Contient 91 pages. Dans le Contrôleur de Party, la roulette y tire les mini-jeux."]))

# Pipe roles
ROLE_PIPES = [
    ("green_pipe", "Players", "Joueurs"),
    ("blue_pipe", "Team A", "Équipe A"),
    ("red_pipe", "Team B", "Équipe B"),
    ("white_pipe", "Spectators", "Spectateurs"),
    ("black_pipe", "Entry", "Entrée"),
    ("yellow_pipe", "Exit", "Sortie"),
]
for i, (pipe, en_role, fr_role) in enumerate(ROLE_PIPES):
    q("role_" + pipe, 3.75 - i * 1.25, 3, I(pipe, 6), deps=["page"], rewards=T1(),
      title=(f"{item_name('steveparty:' + pipe, 'en')}: {en_role}", f"{item_name('steveparty:' + pipe, 'fr')} : {fr_role}"),
      desc=([f"6 plastic blocks of the colour in two columns: 6 pipes. Linked to a page, a pipe of this colour has the role \"{en_role}\"."
             + (" Purple and orange pipes are teams C and D." if pipe == "red_pipe" else "")],
            [f"6 blocs de plastique de la couleur en deux colonnes : 6 tuyaux. Lié à une page, un tuyau de cette couleur a le rôle « {fr_role} »."
             + (" Les tuyaux violets et orange sont les équipes C et D." if pipe == "red_pipe" else "")]))

q("link_pipes", 5.25, 5.25, C(), deps=["role_green_pipe", "role_blue_pipe", "role_red_pipe"], dep_req="one_completed",
  rewards=T2(), icon="steveparty:green_pipe",
  title=("Link the pipes", "Lier les tuyaux"),
  desc=(["Page in hand, click a pipe mouth to link it (again to unlink); linked pipes glow while you hold the page.",
         "When the mini-game starts, each player comes out of a pipe of their role."],
        ["Page en main, clique sur l'embouchure d'un tuyau pour le lier (re-clic pour le délier) ; les tuyaux liés brillent tant que tu tiens la page.",
         "Au départ du mini-jeu, chaque joueur sort d'un tuyau de son rôle."]))

# Podiums
q("podium", 4.5, 0, I("podium"), deps=["page"], rewards=T2(),
  desc=(["Three redstone over three quartz blocks: 2 Redstone Podiums. A redstone pulse puts the nearest player on it: the highest column is 1st.",
         "Click podiums with the page to link them."],
        ["Trois redstone sur trois blocs de quartz : 2 podiums redstone. Une impulsion de redstone y place le joueur le plus proche : la colonne la plus haute est 1re.",
         "Clique sur les podiums avec la page pour les lier."]))

# The chapter's milestone gathers 10 arrows: its prerequisites stand on an arc around it.
for metal, en_m, fr_m, x, y in [("gold", "gold ingots", "lingots d'or", 7, -2),
                                ("silver", "iron ingots", "lingots de fer", 6.75, -0.75),
                                ("bronze", "copper ingots", "lingots de cuivre", 6.75, 0.75)]:
    q(metal + "_podium", x, y, I(metal + "_podium"), deps=["podium"], rewards=T1(),
      desc=([f"Three {en_m} over three quartz blocks: 2 podiums. Click one or sneak on it to take a place: the highest column is 1st."],
            [f"Trois {fr_m} sur trois blocs de quartz : 2 podiums. Clic ou accroupi dessus pour prendre une place : la colonne la plus haute est 1re."]))

q("results", 7, 2, C(), deps=["podium"], rewards=T1(), icon="steveparty:gold_podium",
  title=("Results", "Résultats"),
  desc=(["The page's Results tab lists what ends the mini-game: linked podiums, goal pole bases and Party Rewinders.",
         "A podium set with the Wrench can register the nearest player, or reset the group, on a pulse."],
        ["L'onglet Résultats de la page liste ce qui termine le mini-jeu : podiums liés, socles de mât d'arrivée et Magnétos de Party.",
         "Un podium réglé à la Clé à molette peut, sur une impulsion, inscrire le joueur le plus proche ou vider le groupe."]))

q("mg_controller", 5, 2.5, I("mini_game_controller"), deps=["page", "copper_pipe"], rewards=T2(),
  desc=(["Paper, glass, redstone torch, gold ingots, any mini-game pipe and the redstone plate base (iron, redstone, iron). In the arena, it holds the mini-game's page.",
         "Lamp: red (idle), orange (practice round), green (playing). A comparator reads it."],
        ["Papier, verre, torche de redstone, lingots d'or, un tuyau de mini-jeu et le socle redstone (fer, redstone, fer). Dans l'arène, il reçoit la page du mini-jeu.",
         "Lampe : rouge (repos), orange (manche de chauffe), verte (en jeu). Un comparateur la lit."]))

q("remote", 6, 3.75, I("mini_game_remote"), deps=["mg_controller"], rewards=T1(),
  desc=(["Redstone torch, gold nuggets, glass pane, stone button. Sneak + click a Mini-game Controller to link it.",
         "Out of a party: plays or stops that mini-game from afar. In a party: tells you're ready for the real round."],
        ["Torche de redstone, pépites d'or, vitre, bouton en pierre. Accroupi + clic sur un Contrôleur de mini-jeu pour la lier.",
         "Hors partie : lance ou arrête ce mini-jeu à distance. En partie : dit si tu es prêt pour la manche officielle."]))

q("test_run", 7.75, 3.25, C(), deps=["mg_controller", "link_pipes"], rewards=MILESTONE(), icon="steveparty:mini_game_controller",
  shape="hexagon", size=1.25,
  title=("Test run", "Partie d'essai"),
  desc=(["\"Play\" on the Mini-game Controller (or \"Test\" in the page editor): everyone within 5 blocks of a linked pipe plays, out of a party.",
         "Results show what each place would earn; nothing is paid, everyone comes back."],
        ["« Jouer » sur le Contrôleur de mini-jeu (ou « Tester » dans l'éditeur de page) : tous les joueurs à 5 blocs d'un tuyau lié jouent, hors partie.",
         "Les résultats montrent ce que vaudrait chaque place ; rien n'est payé, tout le monde revient."]))

q("trial", 9.25, 3.5, C(), deps=["test_run", "party.first_party"], rewards=T2(), icon="minecraft:lime_dye",
  title=("Ready? Press Y", "Prêt ? Touche Y"),
  desc=(["In a party, a mini-game with its controller starts with a practice round, for nothing.",
         "Press Y (or Ready) when you are; once everyone is, the real round starts and pays."],
        ["En partie, un mini-jeu qui a son contrôleur commence par une manche de chauffe, pour du beurre.",
         "Touche Y (ou Prêt) quand tu l'es ; quand tout le monde l'est, la manche officielle commence et paie."]))

# Mini-game pipes
q("copper_pipe", 10, 4.75, I("copper_minigame_pipe"), deps=["link_pipes"], rewards=T2(),
  desc=(["6 copper ingots around a paper. Click it with a page: the entrance to that mini-game out of a party, within 100 blocks.",
         "Coloured pipes linked to it send each player to their role."],
        ["6 lingots de cuivre autour d'un papier. Clique dessus avec une page : l'entrée de ce mini-jeu hors partie, à 100 blocs.",
         "Les tuyaux de couleur liés envoient chaque joueur vers son rôle."]))

q("iron_pipe", 11.25, 5.25, I("iron_minigame_pipe"), deps=["copper_pipe", "mulas.power_star"], rewards=T3(),
  desc=(["6 iron ingots, a Party Star and the copper mini-game pipe. Reaches the whole dimension."],
        ["6 lingots de fer, une Party Star et le tuyau de mini-jeu en cuivre. Atteint toute la dimension."]))

q("golden_pipe", 12.75, 5.25, I("golden_minigame_pipe"), deps=["iron_pipe"], rewards=[XP(100), COINS(5)],
  desc=(["4 gold ingots, an eye of ender, 2 Party Stars and the iron mini-game pipe. Reaches every dimension."],
        ["4 lingots d'or, un œil de l'Ender, 2 Party Stars et le tuyau de mini-jeu en fer. Atteint toutes les dimensions."]))

done("done", 12, 0, ["test_run", "trial", "catalogue", "results", "zone", "formats", "gold_podium",
                     "silver_podium", "bronze_podium", "copper_pipe"],
     ("Game designer", "Créateur de jeux"),
     (["Your mini-games are written, linked and tested. Let the roulette spin!"],
      ["Tes mini-jeux sont écrits, liés et testés. Que la roulette tourne !"]),
     "steveparty:mini_games_catalogue")


# === Finish pole ===============================================================================
chapter("finish", "board_game", "steveparty:goal_pole",
        ("Finish Pole", "Mât d'arrivée"),
        (["Jump to the top, score, and watch the flag come down."],
         ["Saute au sommet, marque, et regarde le drapeau descendre."]))

q("base", 0, 0, I("goal_pole_base"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["Bricks, sculk sensor, iron and redstone. It counts the points of the poles stacked on it. Set it with the Wrench."],
        ["Briques, capteur sculk, fer et redstone. Il compte les points des mâts empilés dessus. Il se règle à la Clé à molette."]))

q("pole", 2, 0.625, I("goal_pole"), deps=["base"], rewards=T1(),
  desc=(["A gold ingot over two iron bars, in a column. Stack as many segments as you like on the base."],
        ["Un lingot d'or sur deux barreaux de fer, en colonne. Empile autant de segments que tu veux sur le socle."]))

q("flag", 4, -0.625, I("flag"), deps=["pole"], rewards=T1(),
  desc=(["Three wool: the flag takes their colour (mixed like leather). Right-click a segment to hang it; it slides down when the goal is reached."],
        ["Trois laines : le drapeau prend leur couleur (mélangée comme le cuir). Clic droit sur un segment pour l'accrocher ; il descend quand le but est atteint."]))

q("one_up", 4, 0.625, A("main/pole/top"), deps=["pole"], rewards=T2(), icon="minecraft:golden_apple",
  title=("Extra Life!", "Vie bonus !"),
  desc=(["Land on top of a pole: \"+1 ♥\", a golden heart for 10 s, no fall damage, and you stand upside down. Each landing scores a point."],
        ["Atterris au sommet d'un mât : « +1 ♥ », un cœur doré pendant 10 s, aucun dégât de chute, et te voilà tête en bas. Chaque arrivée marque un point."]))

q("goal", 4, 1.875, C(), deps=["pole", "board.wrench"], rewards=T1(), icon="minecraft:target",
  title=("Set a goal", "Régler un but"),
  desc=(["Wrench on a segment: the pole lights up when the total reaches (or equals, or stays under) a value.",
         "\"Goal: per player\": each player who reaches it gets the highest free podium place."],
        ["Clé à molette sur un segment : le mât s'allume quand le total atteint (ou égale, ou reste sous) une valeur.",
         "« But : par joueur » : chaque joueur qui l'atteint prend la plus haute place libre des podiums."]))

q("criterion", 5.75, 2.75, C(), deps=["goal"], rewards=T1(), optional=True, icon="minecraft:redstone",
  title=("Count anything", "Tout compter"),
  desc=(["The base can count a scoreboard criterion instead: jumps, deaths, kills... For example 10 jumps, goal per player, podiums by the base."],
        ["Le socle peut compter un critère du tableau des scores à la place : sauts, morts, éliminations... Par exemple 10 sauts, but par joueur, podiums contre le socle."]))

q("shoes", 4, -1.875, I("triple_jump_shoes"), deps=["base"], rewards=T2(),
  desc=(["Leather boots + rabbit's foot + 2 slime balls. Chain jumps within 1.5 s: the 2nd is 1.5 times higher, the 3rd twice as high."],
        ["Bottes en cuir + patte de lapin + 2 boules de slime. Sauts enchaînés en 1,5 s : le 2e est 1,5 fois plus haut, le 3e deux fois."]))

done("done", 7, 0, ["one_up", "flag", "goal", "shoes"],
     ("Goal!", "But !"),
     (["Your finish line is ready: jump, score, win."],
      ["Ta ligne d'arrivée est prête : saute, marque, gagne."]),
     "steveparty:flag")


# === Shops =====================================================================================
chapter("shops", "commerce", "steveparty:trading_stall",
        ("Shops", "Boutiques"),
        (["A villager hidden in a box sells the offers of your stalls."],
         ["Un villageois caché dans un carton vend les offres de tes étals."]))

q("villager_block", 0, 0, I("villager_block", how="own"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["A piston pushing down on an adult villager's head squashes it into a block, alive inside (about 70 reactions).",
         "Break it by hand (it begs) to pick it up; a sticky piston lets it out."],
        ["Un piston qui s'étend sur la tête d'un villageois adulte l'écrase en un bloc, vivant dedans (environ 70 réactions).",
         "Casse-le à la main (il supplie) pour le ramasser ; un piston collant le fait ressortir."]))

q("trader", 2, 0, C(), deps=["villager_block"], rewards=T3(), icon="steveparty:boxed_trader_spawn_egg",
  title=("The fall", "La chute"),
  desc=(["Place the Villager Block on the block to imitate, then fall on it from high enough (12 blocks on dirt or hay, 16 on stone, 30 on iron): a Boxed Trader disguised as that block.",
         "Tick once you have one."],
        ["Pose le Bloc de villageois sur le bloc à imiter, puis tombe dessus d'assez haut (12 blocs sur terre ou foin, 16 sur pierre, 30 sur fer) : un Marchand en carton déguisé en ce bloc.",
         "Coche quand ton marchand est prêt."]))

q("stall", 4, -1.25, I("trading_stall"), deps=["trader"], rewards=T1(),
  desc=(["Two carpets over two barrels (the carpets colour the awning).",
         "One column per offer, 9 at most: the goods on top, prices 1 and 2 below. The goods are a model: the sale takes them from the linked chests."],
        ["Deux tapis sur deux tonneaux (les tapis colorent l'auvent).",
         "Une colonne par offre, 9 au plus : la marchandise en haut, les prix 1 et 2 dessous. La marchandise n'est qu'un modèle : la vente la prend dans les coffres liés."]))

q("register", 4, 0, I("cash_register"), deps=["trader"], rewards=T1(),
  desc=(["Stone buttons, gold, chest and iron. Payments land in the linked registers; each sale sends a redstone pulse."],
        ["Boutons en pierre, or, coffre et fer. Les paiements arrivent dans les caisses liées ; chaque vente envoie une impulsion redstone."]))

q("key", 4, 1.25, I("shopkeeper_key"), deps=["trader"], rewards=T1(),
  desc=(["A star fragment, a gold ingot and a gold nugget, in a column. Right-click the trader to link it (the first to link it owns it), then each block: stall, stock chests, registers."],
        ["Un fragment d'étoile, un lingot d'or et une pépite d'or, en colonne. Clic droit sur le marchand pour la lier (le premier à le faire en devient propriétaire), puis sur chaque bloc : étal, coffres de stock, caisses."]))

q("open_shop", 6, 0, C(), deps=["stall", "register", "key"], rewards=MILESTONE(), icon="minecraft:emerald",
  shape="hexagon", size=1.25,
  title=("Open for business", "Ouvert !"),
  desc=(["Trader, stall, a stock chest and a register, all linked with the key; an offer on the stall, stock in the chest.",
         "Customers right-click the trader: offers as cards (red: can't pay, crossed out: sold out). The owner's shop is protected."],
        ["Marchand, étal, un coffre de stock et une caisse, tous liés avec la clé ; une offre sur l'étal, du stock dans le coffre.",
         "Les clients font clic droit sur le marchand : les offres en cartes (rouge : trop cher, barrée : épuisée). La boutique du propriétaire est protégée."]))

q("shop_stop", 7.75, -1.25, C(), deps=["board.shop"], rewards=T2(), icon="steveparty:shop_cartridge",
  title=("Shop stop", "Halte boutique"),
  desc=(["In a party, a pawn stopping on a Shop space brings out its own Boxed Trader. Right-click it (or [Open the shop] in the chat) and buy, up to the cartridge's limit (1 by default).",
         "Payments go to the Party Controller's storage."],
        ["En partie, un pion qui s'arrête sur une case Boutique fait surgir son propre Marchand en carton. Clic droit dessus (ou [Ouvrir la boutique] dans le chat) et achète, jusqu'à la limite de la cartouche (1 par défaut).",
         "Les paiements vont au stockage du Contrôleur de Party."]))

q("bandana", 2, 2.25, I("bandana", how="own"), deps=["trader"], rewards=T1(),
  desc=(["Shears on an open trader wearing its bandana: it falls, the trader hides for 20 s.",
         "Wear it: traders ignore you. Give one to a bald trader to change its colour."],
        ["Des cisailles sur un marchand ouvert qui porte son bandana : il tombe, le marchand se cache 20 s.",
         "Porte-le : les marchands t'ignorent. Rends-en un à un marchand chauve pour changer sa couleur."]))

q("costume", 4, 2.75, I("box_costume", how="own"), deps=["bandana"], rewards=T2(),
  desc=(["Shears again on a trader without its bandana: you steal the look of its box.",
         "Wear it as a chestplate; sneak and stand still 2 s: you become a real block."],
        ["Encore des cisailles sur un marchand sans bandana : tu lui voles l'apparence de sa boîte.",
         "Porte-la en plastron ; accroupi et immobile 2 s : tu deviens un vrai bloc."]))

done("done", 9.5, 0.25, ["shop_stop", "open_shop", "costume"],
     ("Shopkeeper", "Commerçant"),
     (["Your shop is open, on and off the board."],
      ["Ta boutique est ouverte, sur le plateau comme ailleurs."]),
     "steveparty:shopkeeper_key")


# === Mulas and stars ===========================================================================
chapter("mulas", "stars", "steveparty:party_star",
        ("Mulas and Stars", "Mulas et étoiles"),
        (["Little flying star creatures, and the 16 star fragments they leave behind."],
         ["De petites créatures d'étoile volantes, et les 16 fragments d'étoile qu'elles laissent."]))

q("find", 0, 2, O("steveparty:mula"), deps=["welcome.done"], rewards=T3(), icon="steveparty:mula_spawn_egg", size=1.25,
  title=("A Mula!", "Un Mula !"),
  desc=(["Mulas fall from the sky on shooting-star nights (Ephemeride), mostly at full moon: follow the first wave of stars, 150 to 600 blocks away.",
         "Look at one to complete this quest."],
        ["Les Mulas tombent du ciel les nuits d'étoiles filantes (Éphéméride), surtout à la pleine lune : suis la première vague d'étoiles, à 150 à 600 blocs.",
         "Regardes-en une pour valider la quête."]))

q("ephemeride", 0, 6.75, C(), deps=["welcome.done"], rewards=T2(), icon="minecraft:clock",
  title=("Shooting-star night", "Nuit d'étoiles filantes"),
  desc=(["At dusk, a full moon has a 25% chance of an Ephemeride: 6 waves of shooting stars, then 3 to 5 Mulas land where they went.",
         "A Dice Forge at max level brings one every full moon."],
        ["Au crépuscule, une pleine lune a 25 % de chances d'apporter une Éphéméride : 6 vagues d'étoiles filantes, puis 3 à 5 Mulas descendent là où elles sont allées.",
         "Une Forge à dés au niveau maximal en amène une à chaque pleine lune."]))

q("telescope", 2, 6.75, I("telescope"), deps=["ephemeride"], rewards=T2(),
  desc=(["A spyglass on a copper ingot, over 3 sticks.",
         "At night, clear sky: the scroll wheel replays past shooting-star nights within 1000 blocks. Keep a star in the sights: a guide star lights up above the Mulas."],
        ["Une longue-vue sur un lingot de cuivre, au-dessus de 3 bâtons.",
         "La nuit, ciel dégagé : la molette rejoue les nuits d'étoiles filantes passées, à 1000 blocs. Garde une étoile dans le viseur : une étoile guide s'allume au-dessus des Mulas."]))

q("feed", 2, 2, A("stars/feed"), deps=["find"], rewards=T1(), icon="minecraft:cooked_cod",
  title=("Snack time", "À table !"),
  desc=(["A Mula only eats food of its colour (the tooltip says \"Mula food\"):",
         "blue: cod · red: apples, berries, salmon, raw meat · green: melon · yellow: golden food, bread, honey · purple: chorus, beetroot · black: dried kelp, cookies, stews, steak"],
        ["Un Mula ne mange que la nourriture de sa couleur (l'infobulle indique « Nourriture de Mula ») :",
         "bleu : morue · rouge : pommes, baies, saumon, viande crue · vert : pastèque · jaune : nourriture dorée, pain, miel · violet : chorus, betterave · noir : algue séchée, cookies, ragoûts, steak"]))

q("burst", 4, 1.25, C(), deps=["feed"], rewards=T2(), icon="minecraft:firework_star", shape="hexagon", size=1.25,
  title=("Pop!", "Pop !"),
  desc=(["Fed to 40, the Mula swells and bursts: 64 star fragments of its colour (black: 1). It flies off as a shooting star and is reborn 100 to 400 blocks away."],
        ["Nourri jusqu'à 40, le Mula enfle et éclate : 64 fragments d'étoile de sa couleur (noir : 1). Il part en étoile filante et renaît 100 à 400 blocs plus loin."]))

q("tame", 4, 5.5, A("stars/tame"), deps=["feed"], rewards=T2(), icon="steveparty:yellow_star_fragment",
  title=("Best friend", "Meilleur ami"),
  desc=(["Right-click a wild Mula with a star fragment of its colour: 1 chance in 3. Tamed, it floats above you like a balloon."],
        ["Clic droit sur un Mula sauvage avec un fragment d'étoile de sa couleur : 1 chance sur 3. Apprivoisé, il flotte au-dessus de toi comme un ballon."]))

q("balloon", 6, 4.5, C(), deps=["tame"], rewards=T1(), optional=True, icon="minecraft:lead",
  title=("Up, up and away", "Envol en laisse"),
  desc=(["At night, Mulas on a lead can lift you up like balloons: one big Mula, two half-full ones or three of any size. You land softly at dawn."],
        ["La nuit, des Mulas tenus en laisse peuvent t'emporter comme des ballons : un gros, deux à moitié pleins ou trois de n'importe quelle taille. Tu redescends en douceur au lever du jour."]))

# Natural fragments in a column (y), ordered top to bottom so the colour mixes stay close to their parents.
NATURAL = {"blue": 3, "red": -0.25, "yellow": -1.75, "green": 1.25, "purple": -3.25, "black": 6.5}
for c, y in NATURAL.items():
    extra_en = " (only one, and black Mulas are rare)." if c == "black" else "."
    extra_fr = " (un seul, et les Mulas noirs sont rares)." if c == "black" else "."
    q("frag_" + c, 6, y, I(f"{c}_star_fragment", how="own"), deps=["burst"],
      rewards=[XP(30), COINS(2)] if c == "black" else T1(),
      desc=([f"Dropped by a {EN_COLOR[c]} Mula when it bursts{extra_en}"],
            [f"Lâché par un Mula {FR_COLOR[c]} quand il éclate{extra_fr}"]))

MIXES = [
    # colour, deps, en mix, fr mix, x, y
    ("white", ["red", "blue", "yellow"], "red + blue + yellow", "rouge + bleu + jaune", 8, 2.25),
    ("orange", ["red", "yellow"], "red + yellow", "rouge + jaune", 10, -3.25),
    ("cyan", ["blue", "green"], "blue + green", "bleu + vert", 8, 3.75),
    ("brown", ["red", "green"], "red + green (or orange + black)", "rouge + vert (ou orange + noir)", 10, -0.25),
    ("pink", ["red", "white"], "red + white", "rouge + blanc", 10, 1.25),
    ("light_blue", ["blue", "white"], "blue + white", "bleu + blanc", 10, 3),
    ("lime", ["green", "white"], "green + white", "vert + blanc", 12, 2.5),
    ("gray", ["black", "white"], "black + white", "noir + blanc", 12, 6.75),
    ("magenta", ["purple", "pink"], "purple + pink", "violet + rose", 12, -2.25),
    ("light_gray", ["gray", "white"], "gray + white", "gris + blanc", 12, 5.25),
]
for c, deps, en_mix, fr_mix, x, y in MIXES:
    q("frag_" + c, x, y, I(f"{c}_star_fragment"), deps=["frag_" + d for d in deps], rewards=T1(),
      desc=([f"Mix star fragments like dyes: {en_mix}. You get back as many fragments as you put in."],
            [f"Mélange les fragments comme des teintures : {fr_mix}. On récupère autant de fragments qu'on en a mis."]))

q("frag_block", 10, 6, I("blue_star_fragments_block"), deps=["frag_blue"], rewards=T1(),
  desc=(["9 fragments of one colour: a glowing block of animated plasma (black: a starry night). It attracts curious Mulas."],
        ["9 fragments d'une couleur : un bloc lumineux de plasma animé (noir : une nuit étoilée). Il attire les Mulas curieux."]))

# Key kept from the Power Star days: quest ids derive from keys, renaming it would reset players' progress.
q("power_star", 10, -1.75, I("party_star"), deps=["frag_blue", "frag_green", "frag_yellow", "frag_red", "frag_purple"],
  rewards=[XP(100), COINS(5)], shape="hexagon", size=1.25,
  desc=(["One fragment each of blue, green, yellow, red and purple (or 5 black ones).",
         "The default Star of a party. It also goes into the Star Cartridge, the Lapis Crystal Ball, the Dice Forge and the iron and golden mini-game pipes."],
        ["Un fragment de chaque : bleu, vert, jaune, rouge et violet (ou 5 noirs).",
         "L'Étoile par défaut d'une partie. Elle entre aussi dans la Cartouche Étoile, la Boule de cristal en lapis, la Forge à dés et les tuyaux de mini-jeu en fer et en or."]))

q("luck", 4, -0.25, A("stars/luck_potion"), deps=["burst"], rewards=T1(), optional=True, icon="minecraft:potion",
  title=("Potion of Luck", "Potion de chance"),
  desc=(["Brewing stand: awkward potion + any star fragment gives a Potion of Luck (Luck I, 5:00), the only survival recipe for it."],
        ["Alambic : potion étrange + n'importe quel fragment d'étoile donne une Potion de chance (Chance I, 5:00), sa seule recette en survie."]))

done("done", 14, 3.5, ["power_star", "frag_magenta", "frag_light_gray", "frag_brown", "frag_lime", "frag_light_blue",
                       "frag_cyan", "frag_orange", "frag_black", "telescope", "tame"],
     ("Star collector", "Collectionneur d'étoiles"),
     (["All 16 colours of star fragments, and a Party Star of your own."],
      ["Les 16 couleurs de fragments d'étoile, et ta propre Party Star."]),
     "steveparty:white_star_fragments_block")


# === Dice Forge ================================================================================
chapter("forge", "stars", "steveparty:dice_forge",
        ("Dice Forge", "Forge à dés"),
        (["Engrave custom dice, and give the Mulas a home."],
         ["Grave des dés sur mesure, et offre une maison aux Mulas."]))

q("core", 0, 1.25, I("gravity_core"), deps=["mulas.frag_blue"], rewards=T3(), size=1.25,
  desc=(["Crying obsidian, 4 star fragments and a heavy core.",
         "Placed, it pulls everything within 8 blocks into orbit (no fall damage; full iron armour or heavier resists). It also powers the Dice Forge."],
        ["Obsidienne pleureuse, 4 fragments d'étoile et un noyau lourd.",
         "Posé, il met en orbite tout ce qui est à 8 blocs (pas de dégâts de chute ; une armure complète en fer ou plus lourde résiste). Il alimente aussi la Forge à dés."]))

q("forge", 0, -1.25, I("dice_forge"), deps=["mulas.power_star"], rewards=T3(), size=1.25,
  desc=(["A Party Star, obsidian, a blast furnace and a netherite ingot. It engraves dice with the faces and weights you choose, by hand, hoppers or redstone."],
        ["Une Party Star, de l'obsidienne, un haut fourneau et un lingot de netherite. Elle grave des dés aux faces et poids de ton choix, à la main, par entonnoirs ou à la redstone."]))

q("ignite", 2, 0, A("stars/ignition"), deps=["core", "forge"], rewards=T2(), icon="steveparty:gravity_core",
  title=("Ignite the forge", "Allumer la forge"),
  desc=(["Right-click the forge with the Gravity Core: it sinks in, then floats above. Sneak + right-click empty-handed: take it back.",
         "Hit the floating core and it explodes (lost)."],
        ["Clic droit sur la forge avec le Noyau de gravité : il s'enfonce, puis flotte au-dessus. Accroupi + clic droit main vide : le reprendre.",
         "Frappe le noyau en l'air et il explose (perdu)."]))

q("forged", 4, -1.5, C(), deps=["ignite", "dice.blank"], rewards=MILESTONE(), icon="steveparty:default_dice",
  shape="hexagon", size=1.25,
  title=("Your own die", "Ton propre dé"),
  desc=(["The ring: 1 to 12 faces (stack size = weight). The 5 fragment slots: 5 different colours (black can repeat and is never used up). Plus blank faces.",
         "Click the arrow: FORGE! One die every 5 s."],
        ["L'anneau : 1 à 12 faces (taille de la pile = poids). Les 5 emplacements de fragments : 5 couleurs différentes (le noir peut se répéter et n'est jamais consommé). Plus des faces vierges.",
         "Clique sur la flèche : FORGER ! Un dé toutes les 5 s."]))

q("automate", 5.75, -2.75, C(), deps=["forged"], rewards=T1(), optional=True, icon="minecraft:hopper",
  title=("Dice factory", "Usine à dés"),
  desc=(["Hoppers on top and sides fill it, a hopper below takes the dice out. With redstone power it restarts by itself once restocked."],
        ["Des entonnoirs dessus et sur les côtés la remplissent, un entonnoir dessous sort les dés. Alimentée en redstone, elle repart seule une fois réapprovisionnée."]))

q("max_level", 4, 0, C(), deps=["ignite"], rewards=T2(), icon="steveparty:black_star_fragment",
  title=("Highest orbit", "Orbite maximale"),
  desc=(["The core climbs with the fragments in the forge: 320 (5 full stacks, or 5 black ones worth 64 each) lift it 16 blocks.",
         "Max level: an Ephemeride every full moon."],
        ["Le noyau monte avec les fragments présents : 320 (5 piles pleines, ou 5 noirs qui valent 64 chacun) le hissent à 16 blocs.",
         "Niveau maximal : une Éphéméride à chaque pleine lune."]))

q("dance", 4, 1.5, C(), deps=["ignite", "mulas.find"], rewards=T2(), icon="steveparty:mula_spawn_egg",
  title=("Mula dance hall", "Le bal des Mulas"),
  desc=(["Mulas within 16 blocks of a lit forge move in and dance around the core, in rings of up to 12. A Mula that bursts at home lands back by the forge."],
        ["Les Mulas à 16 blocs d'une forge allumée s'y installent et dansent autour du noyau, en anneaux de 12 au plus. Un Mula qui éclate à la maison retombe près de la forge."]))

q("merry", 2, 2.5, C(), deps=["core"], rewards=T0(), optional=True, icon="minecraft:saddle",
  title=("Merry-go-round", "Manège"),
  desc=(["A placed Gravity Core never climbs and cannot explode: a fixed, safe attraction. Chicken carousel, item collector..."],
        ["Un Noyau de gravité posé ne monte pas et ne peut pas exploser : une attraction fixe et sûre. Manège à poules, ramasse-objets..."]))

done("done", 6.5, 0, ["forged", "max_level", "dance"],
     ("Forgemaster", "Maître forgeron"),
     (["Custom dice, a core at full height and Mulas dancing around it."],
      ["Des dés sur mesure, un noyau au plus haut et des Mulas qui dansent autour."]),
     "steveparty:dice_forge")


# === Magpies ===================================================================================
chapter("magpies", "creatures", "steveparty:magpie_nest",
        ("Magpies", "Pies"),
        (["Forest birds that love shiny things, their nests, and the Common Pot they guard."],
         ["Des oiseaux des forêts qui adorent ce qui brille, leurs nids, et la Cagnotte qu'ils gardent."]))

q("find", 0, 0, O("steveparty:wild_magpie"), deps=["welcome.done"], rewards=[XP(25), IT("minecraft:wheat_seeds", 8)],
  icon="steveparty:magpie_spawn_egg", size=1.25,
  title=("A Magpie!", "Une pie !"),
  desc=(["Forests, taigas and cherry groves, by day, 1 to 3 together. Six colours, one per forest. They hop from perch to perch and fly off within 6 blocks.",
         "Look at one to complete this quest."],
        ["Forêts, taïgas et bosquets de cerisiers, le jour, par 1 à 3. Six couleurs, selon la forêt. Elles sautent de perchoir en perchoir et s'envolent à moins de 6 blocs.",
         "Regardes-en une pour valider la quête."]))

q("seeds", 2, -1, C(), deps=["find"], rewards=T1(), icon="minecraft:wheat_seeds",
  title=("Seed charmer", "Charmeur de pies"),
  desc=(["Hold any seeds: magpies within 10 blocks stop fleeing, come to you in little hops and follow you. Chicks too."],
        ["Tiens des graines, n'importe lesquelles : les pies à 10 blocs ne fuient plus, viennent à toi par petits sauts et te suivent. Les piots aussi."]))

q("chick", 4, -1, C(), deps=["seeds"], rewards=T2(), icon="minecraft:egg",
  title=("A chick!", "Un petit piot !"),
  desc=(["Seeds to two wild magpies within 8 blocks of each other: a chick hatches, of one parent's colour.",
         "In a nest it sits and sleeps there; it grows up in 20 minutes (seeds speed it up)."],
        ["Des graines à deux pies sauvages à moins de 8 blocs l'une de l'autre : un petit piot éclôt, de la couleur d'un parent.",
         "Dans un nid, il s'assoit et y dort ; il grandit en 20 minutes (les graines l'accélèrent)."]))

q("nest", 2, 1, I("magpie_nest"), deps=["find"], rewards=T2(),
  desc=(["3 sticks + 1 feather (magpies drop feathers). 27 slots of up to 256 Coins or shiny items each.",
         "Right-click: put the stack in (sneak: one); empty hand: take the last one back. Hoppers and comparators work."],
        ["3 bâtons + 1 plume (les pies lâchent des plumes). 27 emplacements de 256 Pièces ou objets brillants chacun.",
         "Clic droit : y mettre la pile (accroupi : un seul) ; main vide : reprendre le dernier. Entonnoirs et comparateurs marchent."]))

q("shiny", 4, 2.25, C(), deps=["nest"], rewards=T1(), icon="minecraft:gold_nugget",
  title=("Thief of shiny things", "Voleuse de trésors"),
  desc=(["By day, a magpie picks up a shiny item lying within 12 blocks (gold, iron, diamond, emerald, Coins...) and carries it to its nest. At night it sleeps in a free nest."],
        ["Le jour, une pie ramasse un objet brillant posé à 12 blocs au plus (or, fer, diamant, émeraude, Pièces...) et le rapporte à son nid. La nuit, elle dort dans un nid libre."]))

q("tree_nest", 0, 2.5, C(), deps=["find"], rewards=T1(), optional=True, icon="minecraft:oak_leaves",
  title=("Nest hunting", "Chasse aux nids"),
  desc=(["In their forests, a nest sometimes tops a tree: often empty, sometimes with a few nuggets or Coins, home to 1 to 3 magpies."],
        ["Dans leurs forêts, un nid coiffe parfois un arbre : souvent vide, parfois avec quelques pépites ou Pièces, et 1 à 3 pies y vivent."]))

q("pot", 6, 1, I("pot_cartridge"), deps=["nest", "board.cartridge"], rewards=T2(),
  desc=(["Cartridge + Magpie Nest. A straw-coloured Common Pot space.",
         "Click a Magpie Nest with the cartridge (or paint from the space to it with the Tile Linker Brush): that nest IS the pot."],
        ["Cartouche + Nid de pie. Une case Cagnotte, couleur paille.",
         "Clique sur un Nid de pie avec la cartouche (ou peins de la case jusqu'à lui au Pinceau lieur) : ce nid EST la cagnotte."]))

q("pot_game", 8, 0, C(), deps=["pot", "party.first_party"], rewards=MILESTONE(), icon="steveparty:pot_cartridge",
  shape="hexagon", size=1.25,
  title=("The Common Pot", "La Cagnotte"),
  desc=(["A magpie moves onto the linked nest. Each pawn passing the space stakes Coins (1 by default), the magpie fetches them; the pawn that stops there wins the whole nest.",
         "Menu: stake, base (from the space's storage or the Party Controller's, never created) and cap."],
        ["Une pie s'installe sur le nid lié. Chaque pion qui passe mise des Pièces (1 par défaut), la pie va les chercher ; le pion qui s'y arrête rafle tout le nid.",
         "Menu : mise, base (prise au stockage de la case ou du Contrôleur de Party, jamais créée) et plafond."]))

done("done", 10.5, 0, ["chick", "shiny", "pot_game"],
     ("Bird watcher", "Ornithologue"),
     (["Magpies charmed, a chick born and a pot that grows on the board."],
      ["Des pies charmées, un petit piot éclos et une cagnotte qui grossit sur le plateau."]),
     "steveparty:magpie_spawn_egg")


# === Slackorns =================================================================================
chapter("glandouille", "creatures", "steveparty:acorn",
        ("Slackorns", "Glandouilles"),
        (["Grumpy acorns that shove, stack into towers and go squeak."],
         ["Des glands grognons qui bousculent, s'empilent en tours et font pouic."]))

q("find", 0, 0, O("steveparty:glandouille"), deps=["welcome.done"], rewards=[XP(25), IT("minecraft:bone_meal", 4)],
  icon="steveparty:glandouille_spawn_egg", size=1.25,
  title=("Grumpy acorn", "Gland grognon"),
  desc=(["Under trees, 2 to 4 together: green young ones, mossy old ones (old taigas), frosty ones (snowy taigas) that slide like curling stones.",
         "Come within 4 blocks: it charges. It never hurts, it shoves and stuns for 2 s. Look at one to complete this quest."],
        ["Sous les arbres, par 2 à 4 : des jeunes verts, des vieux moussus (vieilles taïgas), des glacés (taïgas enneigées) qui glissent comme des palets de curling.",
         "Approche à moins de 4 blocs : il charge. Il ne blesse jamais, il bouscule et étourdit 2 s. Regardes-en un pour valider la quête."]))

q("acorn", 2, -1, I("acorn", how="own"), deps=["find"], rewards=T1(),
  desc=(["Hits do nothing. Jump on its cap: squeak! The second squash finishes it (the third for a mossy one): 1 or 2 acorns.",
         "Bone meal on a Slackorn can also give one, once a minute at most."],
        ["Les coups ne lui font rien. Saute sur son chapeau : pouic ! Le deuxième écrasement l'achève (le troisième pour un moussu) : 1 ou 2 glands.",
         "La poudre d'os sur un Glandouille peut aussi en donner un, une fois par minute au plus."]))

q("plant", 4, -1, C(), deps=["acorn"], rewards=T1(), icon="minecraft:farmland",
  title=("Plant an acorn", "Planter un gland"),
  desc=(["Plant an acorn on farmland: it grows in 4 stages like a crop, then hatches by itself into a sleeping young Slackorn. Bone meal speeds it up."],
        ["Plante un gland sur de la terre labourée : il pousse en 4 étapes comme une culture, puis éclot tout seul en jeune Glandouille endormi. La poudre d'os l'accélère."]))

q("tower", 2, 1, C(), deps=["find"], rewards=T2(), icon="steveparty:glandouille_spawn_egg",
  title=("Slackorn tower", "Tour de Glandouilles"),
  desc=(["Two calm Slackorns that meet climb on each other, up to 5.",
         "Empty hand: right-click one to carry it (with those above), right-click another to stack, left-click to throw. Hit one mid-tower: it flies off like a missile."],
        ["Deux Glandouilles calmes qui se croisent grimpent l'un sur l'autre, jusqu'à 5.",
         "Main vide : clic droit sur l'un pour le porter (avec ceux du dessus), clic droit sur un autre pour empiler, clic gauche pour lancer. Frappes-en un au milieu d'une tour : il part comme un missile."]))

q("hat", 0, 2.5, I("acorn_hat", how="own"), deps=["find"], rewards=T1(), optional=True,
  desc=(["Very rarely, a violent knock sends its cap flying; without it, the Slackorn turns shy. Wear the cap, or right-click its owner with it to give it back."],
        ["Très rarement, un choc violent fait s'envoler son chapeau ; sans lui, le Glandouille devient timide. Porte-le, ou clic droit sur son propriétaire pour le lui rendre."]))

q("cartridge", 6, 0, I("glandouille_cartridge"), deps=["acorn", "board.cartridge"], rewards=T2(),
  desc=(["Cartridge + acorn. A tower of Slackorns pushes the pawn that stops here along the path (menu: distance, height), with every pawn it meets.",
         "Each pawn met knocks one Slackorn off; none left, the tower stops."],
        ["Cartouche + gland. Une tour de Glandouilles pousse le pion qui s'y arrête sur le chemin (menu : distance, hauteur), avec chaque pion rencontré.",
         "Chaque pion rencontré fait tomber un Glandouille ; quand il n'en reste plus, la tour s'arrête."]))

done("done", 8.5, 0, ["plant", "tower", "cartridge"],
     ("Acorn keeper", "Gardien des glands"),
     (["Squashed, planted, stacked and put on the board."],
      ["Écrasés, plantés, empilés et mis sur le plateau."]),
     "steveparty:acorn")


# === Mistigri ==================================================================================
chapter("mistigri", "creatures", "steveparty:mistigri_cartridge",
        ("Mistigri", "Mistigri"),
        (["The big black cat of bad luck, and the sentences of its loaded die."],
         ["Le gros chat noir de la malchance, et les sentences de son dé pipé."]))

q("summon", 0, 0, O("steveparty:mistigri"), deps=["welcome.done", "dice.default_dice"], rewards=[XP(50), IT("minecraft:cod", 4)],
  icon="steveparty:mistigri_spawn_egg", size=1.25,
  title=("A stroke of bad luck", "Un coup de malchance"),
  desc=(["A black cat in or near a witch hut (16 blocks). Right-click it with any die of the mod: a 1 (or 0) and it swells into a Mistigri; anything else, it hisses.",
         "Look at a Mistigri to complete this quest."],
        ["Un chat noir dans une cabane de sorcière ou à 16 blocs. Clic droit dessus avec n'importe quel dé du mod : un 1 (ou 0) et il enfle en Mistigri ; sinon, il crache.",
         "Regarde un Mistigri pour valider la quête."]))

q("bad_luck", 2, -1.25, C(), deps=["summon"], rewards=T1(), icon="minecraft:fermented_spider_eye",
  title=("Don't cross its path", "Ne croise pas son chemin"),
  desc=(["Walk in front of it: Bad Luck for 1 minute; hit it: 2 minutes (it gets angry, never fights).",
         "It knocks items off item frames and naps on chests, which stay shut until raw fish wakes it."],
        ["Passer devant lui : Malchance 1 minute ; le frapper : 2 minutes (il se fâche, sans jamais se battre).",
         "Il fait tomber les objets des cadres et dort sur les coffres, fermés jusqu'à ce qu'un poisson cru le réveille."]))

q("acorn_play", 2, 1.25, C(), deps=["summon", "glandouille.acorn"], rewards=T0(), optional=True, icon="steveparty:acorn",
  title=("Ball of yarn", "Pelote de laine"),
  desc=(["Throw an acorn within 12 blocks of it: it forgets everything, even its anger, and plays with it for 30 s to a minute."],
        ["Lance un gland à 12 blocs de lui au plus : il oublie tout, même sa colère, et joue avec pendant 30 s à une minute."]))

q("tame", 4, -1.25, C(), deps=["summon"], rewards=T2(), icon="minecraft:cod",
  title=("A lucky cat", "Un chat porte-bonheur"),
  desc=(["Raw cod or salmon, one at a time: 6 to 10 of them.",
         "Tamed, it follows you, gives you Luck within 16 blocks, and monsters near it miss one hit in three. Empty hand: sit or stand."],
        ["De la morue ou du saumon crus, un par un : il en faut 6 à 10.",
         "Apprivoisé, il te suit, te donne Chance à 16 blocs, et les monstres près de lui ratent un coup sur trois. Main vide : assis ou debout."]))

q("cartridge", 4, 1.25, I("mistigri_cartridge"), deps=["summon", "board.cartridge"], rewards=T2(),
  desc=(["Cartridge + the Mistigri's Die (its loot).",
         "In a party, the pawn stopping here brings out the Mistigri: it juggles its loaded die, the whole party watches a roulette of sentences, then one falls."],
        ["Cartouche + Dé du Mistigri (son butin).",
         "En partie, le pion qui s'y arrête fait venir le Mistigri : il jongle avec son dé pipé, toute la partie regarde la roulette des sentences, puis l'une tombe."]))

q("sentences", 6, 1.25, C(), deps=["cartridge"], rewards=T2(), icon="minecraft:writable_book",
  title=("Write the sentences", "Écrire les sentences"),
  desc=(["Right-click in the air with the cartridge: up to 12 sentences.",
         "Each one: a kind (Theft, Redistribute, Move back, Cursed die, Pity), a target (who lands here, 1st, last, richest, everyone...), a number and a weight.",
         "Nothing is created: thefts go to the party's storage, pity comes out of it."],
        ["Clic droit dans le vide avec la cartouche : jusqu'à 12 sentences.",
         "Chacune : un type (Vol, Redistribue, Recul, Dé maudit, Pitié), une cible (qui tombe ici, 1er, dernier, plus riche, tout le monde...), un nombre et un poids.",
         "Rien n'est créé : les vols vont au stockage de la partie, la pitié en sort."]))

done("done", 8.5, 0, ["bad_luck", "tame", "sentences"],
     ("Black cat", "Chat noir"),
     (["Bad luck summoned, tamed and turned into a board game."],
      ["La malchance invoquée, apprivoisée et mise en jeu."]),
     "steveparty:mistigri_spawn_egg")


# === Wicklings =================================================================================
chapter("frousseux", "creatures", "steveparty:frousseux_cartridge",
        ("Wicklings", "Frousseux"),
        (["Little candle ghosts of the caves: thieves of shiny things, then lamps that follow you."],
         ["De petits fantômes-bougies des grottes : voleurs d'objets brillants, puis lampes qui te suivent."]))

q("find", 0, 0, O("steveparty:frousseux"), deps=["welcome.done"], rewards=[XP(25), IT("minecraft:flint_and_steel", 1)],
  icon="steveparty:frousseux_spawn_egg", size=1.25,
  title=("A light in the dark", "Une lueur dans le noir"),
  desc=(["Dark Overworld caves, alone or in pairs: a wax ghost with a candle flame (17 colours). Its light is real, and no monster comes within 8 blocks.",
         "Look at one to complete this quest."],
        ["Grottes sombres de l'Overworld, seuls ou à deux : un fantôme de cire à flamme de bougie (17 couleurs). Sa lumière est réelle, et aucun monstre n'approche à moins de 8 blocs.",
         "Regardes-en un pour valider la quête."]))

q("thief", 2, -1.25, C(), deps=["find"], rewards=T1(), icon="minecraft:torch",
  title=("Stop, thief!", "Au voleur !"),
  desc=(["A wild Wickling steals a shiny item from a player within 2 blocks and runs off giggling, sometimes to drop it at another player's feet.",
         "A torch or lantern in hand protects you. Melee does nothing; projectiles do."],
        ["Un Frousseux sauvage vole un objet brillant à un joueur à moins de 2 blocs et s'enfuit en ricanant, parfois pour le lâcher aux pieds d'un autre joueur.",
         "Une torche ou une lanterne en main te protège. Le corps à corps ne fait rien ; les projectiles, si."]))

q("tame", 2, 1.25, C(), deps=["find"], rewards=T2(), icon="minecraft:flint_and_steel",
  title=("Rekindle the flame", "Raviver la flamme"),
  desc=(["Flint and steel on a wild Wickling: the first strike makes it give back its loot, then 1 chance in 3 per strike to tame it.",
         "Tamed, it follows you as a lamp. Flint and steel heals it."],
        ["Un briquet sur un Frousseux sauvage : le premier coup lui fait rendre son butin, puis 1 chance sur 3 par coup de l'apprivoiser.",
         "Apprivoisé, il te suit comme une lampe. Le briquet le soigne."]))

q("holder", 4, 1.25, I("frousseux_candle_holder", how="own"), deps=["tame"], rewards=T1(),
  desc=(["Sneak + right-click your Wickling empty-handed: it sits; again: it falls asleep as a candle holder that gives light and keeps everything. Empty hand: wake it up.",
         "Broken, it drops as an item: a Wickling is never lost."],
        ["Accroupi + clic droit main vide sur ton Frousseux : il s'assoit ; encore : il s'endort en bougeoir, un bloc qui éclaire et garde tout. Main vide : le réveiller.",
         "Cassé, il tombe en objet : un Frousseux n'est jamais perdu."]))

q("saucer", 6, 1.25, I("candle_saucer"), deps=["holder"], rewards=T0(), optional=True,
  desc=(["5 gold nuggets. Candle holder + saucer: it sits on its saucer. Alone in the grid, it comes off again."],
        ["5 pépites d'or. Bougeoir + soucoupe : il se pose sur sa soucoupe. Seul dans la grille, il en ressort."]))

q("cartridge", 4, -1.25, I("frousseux_cartridge"), deps=["thief", "board.cartridge"], rewards=T2(),
  desc=(["Cartridge + candle. In a party, the pawn stopping here brings out a Wickling: its player picks a victim, the Wickling robs them (Coins or Party Stars) and brings the loot back.",
         "With the defence on, the victim has 7 s to hit it: each hit gives some back."],
        ["Cartouche + bougie. En partie, le pion qui s'y arrête fait venir un Frousseux : son joueur choisit une victime, le Frousseux la vole (Pièces ou Party Stars) et rapporte le butin.",
         "Avec la défense, la victime a 7 s pour le frapper : chaque coup en rend une partie."]))

done("done", 8.5, 0, ["holder", "cartridge"],
     ("Candlelight", "À la bougie"),
     (["A thief tamed into a lamp, and a robbery on the board."],
      ["Un voleur devenu lampe, et un cambriolage sur le plateau."]),
     "steveparty:frousseux_candle_holder")


# === Boomcarts =================================================================================
chapter("boomcart", "creatures", "minecraft:tnt_minecart",
        ("Boomcarts", "Pétaroules"),
        (["A grumpy living minecart loaded with TNT or fireworks."],
         ["Un wagonnet vivant et grognon, chargé de TNT ou de fusées."]))

q("find", 0, 0, O("steveparty:boomcart"), deps=["welcome.done"], rewards=[XP(25), IT("minecraft:firework_rocket", 3)],
  icon="steveparty:boomcart_spawn_egg", size=1.25,
  title=("A living minecart", "Un wagonnet vivant"),
  desc=(["Abandoned mineshafts (rarely) and the dark caves around, always alone. On rails, it follows the track. It roars when you come within 3 blocks.",
         "Look at one to complete this quest."],
        ["Puits de mine abandonnés (rarement) et grottes sombres autour, toujours seul. Sur des rails, il suit la voie. Il rugit quand on approche à moins de 3 blocs.",
         "Regardes-en un pour valider la quête."]))

q("load", 2, -1, C(), deps=["find"], rewards=T1(), icon="minecraft:firework_rocket",
  title=("Feed it", "Nourris-le"),
  desc=(["Right-click it with TNT or a firework rocket: it swallows the new load and spits out the old one. Loaded by a player, it never despawns."],
        ["Clic droit dessus avec de la TNT ou une fusée : il avale la nouvelle charge et recrache l'ancienne. Chargé par un joueur, il ne disparaît plus."]))

q("hot_potato", 4, -1, C(), deps=["load"], rewards=T2(), icon="minecraft:flint_and_steel",
  title=("Hot potato", "Patate chaude"),
  desc=(["Flint and steel: an 8 s fuse, and it rushes at the nearest other player. Relight it to send it elsewhere (+3 s, +2, +1, then no more).",
         "Rocket load: harmless fireworks. TNT: a real explosion."],
        ["Un briquet : mèche de 8 s, et il fonce sur le joueur le plus proche, un autre que toi. Rallume-le pour l'envoyer ailleurs (+3 s, +2, +1, puis plus rien).",
         "Chargé d'une fusée : un feu d'artifice sans danger. De TNT : une vraie explosion."]))

q("board", 2, 1, C(), deps=["find", "board.advance"], rewards=T2(), icon="steveparty:advance_back_cartridge",
  title=("Advance or back", "Avancer ou reculer"),
  desc=(["On an Advance / Back space, a Boomcart hologram rolls out (from the space's Spawn Bubble, if linked): forward, it blasts the pawn ahead; back, it rams it.",
         "Only a show: nothing broken, nobody hurt."],
        ["Sur une case Avancer / Reculer, un Pétaroule hologramme sort (de la Bulle d'apparition de la case, si elle est liée) : en avant, il propulse le pion ; en arrière, il le percute.",
         "Rien qu'un spectacle : rien de cassé, personne de blessé."]))

done("done", 6, 0, ["hot_potato", "board"],
     ("Demolition crew", "Artificier"),
     (["Fed, lit, passed around, and booming on the board."],
      ["Nourri, allumé, refilé, et explosif sur le plateau."]),
     "steveparty:boomcart_spawn_egg")


# === Tricauldrons ==============================================================================
chapter("trichaudron", "creatures", "steveparty:trichaudron_cartridge",
        ("Tricauldrons", "Trichaudrons"),
        (["A huge three-headed Nether tortoise with a lava tank on its back."],
         ["Une énorme tortue du Nether à trois têtes, une cuve de lave sur le dos."]))

q("find", 0, 0, O("steveparty:trichaudron"), deps=["welcome.done"], rewards=[XP(50), IT("minecraft:bucket", 2)],
  icon="steveparty:trichaudron_spawn_egg", size=1.25,
  title=("Three heads, one cauldron", "Trois têtes, un chaudron"),
  desc=(["Nether wastes, basalt deltas, soul sand valleys, by the lava or in it: rare and alone. Neutral unless hit; near a bastion it may carry piglins and hunt players.",
         "Look at one to complete this quest."],
        ["Terres du Nether, deltas de basalte, vallées de sable des âmes, au bord de la lave ou dedans : rare et seul. Neutre tant qu'on ne le frappe pas ; près d'un bastion, il peut porter des piglins et chasser les joueurs.",
         "Regardes-en un pour valider la quête."]))

q("shield", 2, -1.25, C(), deps=["find"], rewards=T1(), icon="minecraft:shield",
  title=("Raise your shield", "Lève le bouclier"),
  desc=(["Each head charges, then spits scalding steam up to 30 blocks. Its aim freezes just before: step aside, or raise a shield towards it.",
         "Each jet costs it a bucket of lava."],
        ["Chaque tête se charge, puis crache de la vapeur brûlante jusqu'à 30 blocs. Sa visée se fige juste avant : fais un pas de côté, ou lève un bouclier vers elle.",
         "Chaque jet lui coûte un seau de lave."]))

q("tame", 2, 1.25, C(), deps=["find"], rewards=T2(), icon="minecraft:lava_bucket",
  title=("Drain and tame", "Vider et apprivoiser"),
  desc=(["Empty buckets on it: 1 lava bucket each (up to 27), no anger. Only someone who drained it can tame it: mount it empty-handed.",
         "It yields (70% empty, never full, +10% per failed try) or throws you off."],
        ["Des seaux vides dessus : 1 seau de lave à chaque fois (jusqu'à 27), sans le fâcher. Seul un joueur qui l'a vidé peut l'apprivoiser : monte dessus main vide.",
         "Il cède (70 % vide, jamais plein, +10 % par essai raté) ou t'éjecte."]))

q("ride", 4, 1.25, C(), deps=["tame"], rewards=T2(), icon="minecraft:saddle",
  title=("Three riders", "Trois cavaliers"),
  desc=(["Saddle it in its screen. Up to 3 riders, one head each: everyone's keys add up.",
         "Lava is its fuel (lava bucket while riding). Click: your head spits. Hold Jump: a steam leap."],
        ["Selle-le dans son écran. Jusqu'à 3 cavaliers, une tête chacun : les touches de tous s'additionnent.",
         "La lave est son carburant (seau de lave, monté). Clic : ta tête crache. Saut maintenu : un bond à vapeur."]))

q("cartridge", 4, -1.25, I("trichaudron_cartridge"), deps=["find", "board.inventory"], rewards=T2(),
  desc=(["Inventory Cartridge + lava bucket (the bucket comes back).",
         "In a party, the pawn stopping here brings out a Tricauldron, one head per prize (1 to 5), from the linked chests or the storage. The player hits a head, blind by default: it spits its prize."],
        ["Cartouche Bonus / Malus + seau de lave (tu récupères le seau).",
         "En partie, le pion qui s'y arrête fait surgir un Trichaudron, une tête par lot (1 à 5), pris dans les coffres liés ou le stockage. Le joueur frappe une tête, à l'aveugle par défaut : elle lui crache son lot."]))

done("done", 6.5, 0, ["shield", "ride", "cartridge"],
     ("Lava rider", "Cavalier de lave"),
     (["Dodged, drained, tamed, ridden, and put on the board."],
      ["Esquivé, vidé, apprivoisé, monté, et mis sur le plateau."]),
     "steveparty:trichaudron_spawn_egg")


# === Plastic ===================================================================================
chapter("plastic", "decoration", "steveparty:red_plastic_block",
        ("Plastic", "Plastique"),
        (["Toy-like blocks in 16 colours that float."],
         ["Des blocs façon jouet en 16 couleurs, qui flottent."]))

q("block", 0, 0, I("red_plastic_block", 4), deps=["welcome.pellets"], rewards=[XP(15), IT("plastic_pellets", 4)], size=1.25,
  desc=(["4 plastic pellets + 1 dye: 4 blocks of its colour. 8 blocks around a dye: recoloured.",
         "Neighbours of the same colour merge into one piece (up to 4x2x4)."],
        ["4 granulés de plastique + 1 teinture : 4 blocs de sa couleur. 8 blocs autour d'une teinture : reteints.",
         "Les voisins de même couleur fusionnent en une pièce (jusqu'à 4x2x4)."]))

q("stud", 3.5, -2.375, I("blue_plastic_stud", 4), deps=["block"], rewards=T1(),
  desc=(["One plastic block gives 4 studs of its colour (4 studs in a square give the block back). A small piece on floor, wall or ceiling that needs no support."],
        ["Un bloc de plastique donne 4 plots de sa couleur (4 plots en carré redonnent le bloc). Une petite pièce au sol, au mur ou au plafond, sans support."]))

q("slab", 3.5, -1.125, I("yellow_plastic_slab", 6), deps=["block"], rewards=T1(),
  desc=(["3 blocks in a row: 6 slabs (or the stonecutter). Slabs, stairs and walls do not float and cut bubble columns."],
        ["3 blocs en ligne : 6 dalles (ou le tailleur de pierre). Dalles, escaliers et murets ne flottent pas et coupent les colonnes de bulles."]))

q("stairs", 3.5, 0.125, I("green_plastic_stairs", 4), deps=["block"], rewards=T1(),
  desc=(["6 blocks in steps: 4 stairs. Aligned shapes of the same colour connect their textures too."],
        ["6 blocs en escalier : 4 escaliers. Les formes alignées de même couleur raccordent aussi leurs textures."]))

q("wall", 3.5, 1.375, I("purple_plastic_wall", 6), deps=["block"], rewards=T1(),
  desc=(["Two rows of 3 blocks: 6 walls."],
        ["Deux lignes de 3 blocs : 6 murets."]))

q("stick", 1.75, -3.5, I("plastic_stick", 4), deps=["block"], rewards=T1(),
  desc=(["Two plastic blocks (any colour) on top of each other: 4 sticks. The plastic fences are made of them."],
        ["Deux blocs de plastique (n'importe quelle couleur) l'un sur l'autre : 4 bâtons. Les barrières en plastique en sont faites."]))

q("fence", 3.5, -3.75, I("orange_plastic_fence", 3), deps=["stick"], rewards=T1(),
  desc=(["4 blocks of a colour and 2 plastic sticks: 3 fences. They connect to each other and carry road signs."],
        ["4 blocs d'une couleur et 2 bâtons en plastique : 3 barrières. Elles se lient entre elles et portent les panneaux routiers."]))

q("float", 3.5, 2.625, C(), deps=["block"], rewards=T2(), icon="minecraft:soul_sand",
  title=("It floats!", "Ça flotte !"),
  desc=(["Plastic in still water rises to the surface, carrying what stands on it. Over soul sand, a bubble column is an elevator; over magma, down. A chain anchors it."],
        ["Le plastique dans l'eau calme remonte à la surface, avec ce qui est dessus. Sur du sable des âmes, une colonne de bulles fait ascenseur ; sur du magma, il descend. Une chaîne l'ancre."]))

q("break", 1.25, 3.5, C(), deps=["block", "board.wrench"], rewards=T0(), optional=True, icon="steveparty:wrench",
  title=("Quick clean-up", "Démontage express"),
  desc=(["The Wrench breaks any plastic block in one hit, even under water. Shears are fast too."],
        ["La Clé à molette casse tout bloc de plastique d'un coup, même sous l'eau. Les cisailles sont rapides aussi."]))

q("hop_switch", 3.5, 3.875, I("hop_switch"), deps=["block", "board.cartridge"], rewards=T2(),
  desc=(["Blue concrete, stone pressure plate, clock, iron and redstone.",
         "Give it a Cartridge whose destinations are blocks: jump on it and they vanish, appear or toggle for a while (clock: the time). Plastic toggles by default."],
        ["Béton bleu, plaque de pression en pierre, horloge, fer et redstone.",
         "Donne-lui une Cartouche dont les destinations sont des blocs : saute dessus et ils disparaissent, apparaissent ou basculent un moment (horloge : la durée). Le plastique bascule par défaut."]))

done("done", 6.25, 0, ["stud", "slab", "stairs", "wall", "fence", "float", "hop_switch"],
     ("Toy box", "Coffre à jouets"),
     (["Every plastic shape, and blocks that float and vanish on cue."],
      ["Toutes les formes de plastique, et des blocs qui flottent et disparaissent sur commande."]),
     "steveparty:lime_plastic_block")


# === Pipes =====================================================================================
chapter("pipes", "decoration", "steveparty:lime_pipe",
        ("Pipes", "Tuyaux"),
        (["Travel pipes: go in one mouth, come out of another."],
         ["Des tuyaux de voyage : on entre par une embouchure, on ressort par une autre."]))

q("pipe", 0, 0, I("lime_pipe", 6), deps=["plastic.block"], rewards=T1(), size=1.25,
  desc=(["6 plastic blocks of one colour in two columns: 6 pipes (16 colours). A pipe placed against another (or before its mouth) connects to it."],
        ["6 blocs de plastique d'une couleur en deux colonnes : 6 tuyaux (16 couleurs). Un tuyau posé contre un autre (ou devant son embouchure) s'y raccorde."]))

q("windowed", 2, -1.5, I("cyan_windowed_pipe", 6), deps=["pipe"], rewards=T1(),
  desc=(["4 plastic blocks and 2 glass: 6 windowed pipes, with a continuous window along the tube."],
        ["4 blocs de plastique et 2 verres : 6 tuyaux à hublot, avec une vitre continue le long du tube."]))

q("glass", 2, 0, I("glass_pipe", 6), deps=["pipe"], rewards=T1(),
  desc=(["6 glass: 6 clear pipes. Linked to a mini-game page, a glass pipe is for spectators."],
        ["6 verres : 6 tuyaux transparents. Lié à une page de mini-jeu, un tuyau en verre est pour les spectateurs."]))

q("stained", 4, 0, I("magenta_stained_glass_pipe", 6), deps=["glass"], rewards=T1(),
  desc=(["6 stained glass of one colour: 6 stained glass pipes (16 colours)."],
        ["6 verres teintés d'une couleur : 6 tuyaux en verre teinté (16 couleurs)."]))

q("travel", 2, 1.5, A("workshop/pipes/travel"), deps=["pipe"], rewards=T2(), icon="minecraft:slime_ball",
  title=("Whoosh!", "Fiouuu !"),
  desc=(["Right-click a mouth, sneak in front of it, or fall in: you rush through and come out at another end of the network, at random. Mobs, pawns and items too."],
        ["Clic droit sur une embouchure, accroupi devant, ou en tombant dedans : tu files et ressors à une autre extrémité du réseau, au hasard. Créatures, pions et objets aussi."]))

q("dead_end", 4, 1.5, C(), deps=["travel"], rewards=T2(), icon="minecraft:ender_eye",
  title=("Warp", "Téléportation"),
  desc=(["A pipe that ends in a solid block warps you to the nearest mouth of its colour within 100 blocks (opaque and windowed count as one colour)."],
        ["Un tuyau qui finit dans un bloc solide te téléporte vers l'embouchure la plus proche de sa couleur, à 100 blocs (opaque et à hublot comptent comme une couleur)."]))

q("attach", 1, 3, C(), deps=["pipe", "board.wrench"], rewards=T0(), optional=True, icon="steveparty:wrench",
  title=("Pipe fittings", "Fixations"),
  desc=(["Placed against a solid block, a pipe is fixed to it (one block per pipe). The Wrench changes which one, down to none."],
        ["Posé contre un bloc solide, un tuyau s'y fixe (un bloc par tuyau). La Clé à molette change lequel, jusqu'à aucun."]))

done("done", 6.25, 0, ["windowed", "stained", "dead_end"],
     ("Plumber", "Plombier"),
     (["Every kind of pipe, and a warp network of your own. For mini-game pipes, see the Mini-games chapter."],
      ["Toutes les sortes de tuyaux, et ton propre réseau de téléportation. Pour les tuyaux de mini-jeu, voir le chapitre Mini-jeux."]),
     "steveparty:glass_pipe")


# === Bubbles ===================================================================================
chapter("bubbles", "decoration", "steveparty:bubbler",
        ("Bubbles", "Bulles"),
        (["Soap bubbles you can ride and steer, and the Spawn Bubbles of the board."],
         ["Des bulles de savon qu'on pilote, et les Bulles d'apparition du plateau."]))

q("bubbler", 0, 0, I("bubbler"), deps=["welcome.pellets"], rewards=[XP(25), IT("minecraft:snowball", 8)], size=1.25,
  desc=(["4 plastic pellets, a water bucket and redstone (the bucket comes back).",
         "Walk onto it: a soap bubble lifts you 30 blocks (2 per redstone level), then floats. Sneak to cross it. Mobs and items get bubbled too."],
        ["4 granulés de plastique, un seau d'eau et de la redstone (tu récupères le seau).",
         "Marche dessus : une bulle de savon te monte de 30 blocs (2 par niveau de redstone), puis flotte. Accroupi, on passe dessus. Créatures et objets aussi."]))

q("steer", 2, -1, C(), deps=["bubbler"], rewards=T2(), icon="minecraft:feather",
  title=("Bubble pilot", "Pilote de bulle"),
  desc=(["Hold Jump to charge a puff, release: the bubble blows where you look. WASD drifts it.",
         "Puffs too close together stretch it until it pops. Attack or sneak to get out: you fall slowly.",
         "Left idle, it slowly rises... and bursts once too high: you fall for real."],
        ["Maintiens Saut pour charger un souffle, relâche : la bulle file là où tu regardes. ZQSD la font dériver.",
         "Des souffles trop rapprochés la tendent jusqu'à l'éclatement. Frappe ou accroupis-toi pour sortir : tu tombes en douceur.",
         "Laissée sans rien faire, elle monte doucement... et éclate une fois trop haut : là, tu tombes pour de bon."]))

q("merge", 4, -1, C(), deps=["steer"], rewards=T1(), optional=True, icon="minecraft:slime_ball",
  title=("Two in a bubble", "À deux dans la bulle"),
  desc=(["Two occupied bubbles that touch merge (8 passengers at most). Everyone steers: opposite pushes cancel out."],
        ["Deux bulles occupées qui se touchent fusionnent (8 passagers au plus). Tout le monde pilote : les poussées opposées s'annulent."]))

q("cannon", 2, 1, C(), deps=["bubbler"], rewards=T1(), optional=True, icon="minecraft:snowball",
  title=("Bubble cannon", "Canon à bulles"),
  desc=(["Outside the Bubbler's column, a bubble pops on a block, a mob, an arrow, or too high.",
         "A snowball pushes it: a dispenser of snowballs is a bubble cannon. Silk Touch pushes it without popping it."],
        ["Hors de la colonne du Bulleur, une bulle éclate contre un bloc, une créature, une flèche, ou trop haut.",
         "Une boule de neige la pousse : un distributeur de boules de neige fait un canon à bulles. Toucher de soie la pousse sans l'éclater."]))

q("bubbled", 4.5, 1.5, C(), deps=["steer"], rewards=T1(), optional=True, icon="minecraft:arrow",
  title=("Bubbled!", "Bullé !"),
  desc=(["Shoot or throw from inside a bubble (arrow, snowball, potion...): a sparkly trail, and the creature hit gets a bubble of its own."],
        ["Tire ou lance depuis une bulle (flèche, boule de neige, potion...) : une traînée irisée, et la créature touchée a sa propre bulle."]))

q("parachute", 2.5, 2.5, I("parachute"), deps=["bubbler"], rewards=T1(),
  desc=(["Wool on top, string under it. Held while falling, it opens by itself: you float down, steering with your keys, no fall damage.",
         "Use it in the air to open or fold it. Use it on a mob: it opens when the mob falls."],
        ["De la laine dessus, de la ficelle dessous. Tenu en tombant, il s'ouvre tout seul : tu descends doucement, tu te diriges avec tes touches, sans dégâts de chute.",
         "Utilise-le en l'air pour l'ouvrir ou le replier. Sur une créature : il s'ouvre quand elle tombe."]))

q("soft_landing", 4.5, 3, A("workshop/bubbles/soft_landing"), deps=["parachute"], rewards=T1(), optional=True,
  icon="steveparty:parachute",
  title=("Soft landing", "Tout en douceur"),
  desc=(["Land under your parachute."],
        ["Atterris sous ton parachute."]))

q("spawn_bubble", 0, 2.5, I("spawn_marker"), deps=["bubbler", "board.link"], rewards=T2(),
  desc=(["A glass block over a Bubbler.",
         "It shows where the creature of a space appears and where it faces: link it with the Tile Linker Brush, from the space to the bubble.",
         "Wrench: \"Always visible\" and height. Sneak + Wrench: turn it."],
        ["Un bloc de verre au-dessus d'un Bulleur.",
         "Elle montre où apparaît la créature d'une case et vers où elle regarde : lie-la au Pinceau lieur, de la case vers la bulle.",
         "Clé à molette : « Toujours visible » et hauteur. Accroupi + Clé à molette : la tourner."]))

done("done", 6.5, 0, ["steer", "spawn_bubble"],
     ("Bubble blower", "Souffleur de bulles"),
     (["Ridden, steered, merged, and placed on the board."],
      ["Montées, pilotées, fusionnées, et posées sur le plateau."]),
     "steveparty:spawn_marker")


# === Signs and stencils ========================================================================
chapter("signs", "decoration", "steveparty:stencil",
        ("Signs and Stencils", "Panneaux et pochoirs"),
        (["Cut a 16x16 pattern, then paint or engrave it everywhere."],
         ["Découpe un motif 16x16, puis peins-le ou grave-le partout."]))

q("stencil", 0, 0, I("stencil", 2), deps=["welcome.done"], rewards=T1(), size=1.25,
  desc=(["4 iron nuggets around a paper: 2 blank stencils. Structure chests may hold one of 50 built-in patterns. A cut stencil + blank ones: copies."],
        ["4 pépites de fer autour d'un papier : 2 pochoirs vierges. Les coffres de structures peuvent en cacher un des 50 motifs intégrés. Un pochoir découpé + des vierges : des copies."]))

q("maker", -3.75, 0.75, I("stencil_maker"), deps=["stencil"], rewards=T2(),
  desc=(["Iron ingots, a smooth stone slab and an iron block. Insert a stencil and open the editor: left-click cuts a pixel, right-click fills it.",
         "Your library keeps 128 patterns and learns those you carry."],
        ["Lingots de fer, dalle de pierre lisse et bloc de fer. Insère un pochoir et ouvre l'éditeur : clic gauche découpe un pixel, clic droit le bouche.",
         "Ta bibliothèque garde 128 motifs et apprend ceux que tu portes."]))

q("cut", -3.75, 2.5, C(), deps=["maker"], rewards=T1(), icon="minecraft:shears",
  title=("Cut a pattern", "Découper un motif"),
  desc=(["Draw your own 16x16 pattern on the grid (flip, rotate, invert, undo...) and save it on the stencil."],
        ["Dessine ton propre motif 16x16 sur la grille (symétrie, pivoter, inverser, annuler...) et enregistre-le sur le pochoir."]))

q("paint", 2.25, 0, C(), deps=["stencil"], rewards=T1(), icon="minecraft:red_dye",
  title=("Paint a wall", "Peindre un mur"),
  desc=(["Stencil in one hand, dye in the other, right-click a sign or any full block face: the pattern is painted (1 dye, the stencil stays).",
         "On a wall, only a wet sponge or a brush wipes it off."],
        ["Pochoir dans une main, teinture dans l'autre, clic droit sur un panneau ou une face pleine : le motif est peint (1 teinture, le pochoir reste).",
         "Sur un mur, seuls l'éponge mouillée ou le pinceau l'effacent."]))

q("glow", 4, 0, C(), deps=["paint"], rewards=T0(), optional=True, icon="minecraft:glow_ink_sac",
  title=("Glow in the dark", "Ça brille la nuit"),
  desc=(["A glow ink sac on a painted pattern makes it shine at night; a dry sponge turns it off."],
        ["Une poche d'encre luisante sur un motif peint le fait briller la nuit ; une éponge sèche l'éteint."]))

q("hammer", 3.75, 2.5, I("stencil_gun"), deps=["stencil"], rewards=T2(),
  desc=(["Leather, planks, a stencil and a stick. Loads 9 stencils and 9 dyes (sneak + right-click).",
         "Sneak + scroll: stencil or colour (G switches). Right-click stamps signs, walls, tiles and podiums."],
        ["Cuir, planches, pochoir et bâton. Il charge 9 pochoirs et 9 teintures (accroupi + clic droit).",
         "Accroupi + molette : pochoir ou couleur (G pour basculer). Clic droit : tamponne panneaux, murs, tuiles et podiums."]))

q("stamp_tile", 5.25, 2.5, A("workshop/signs/stencil_hammer"), deps=["hammer", "board.tile"], rewards=T1(), optional=True, icon="steveparty:tile",
  title=("Stamp a tile", "Tamponner une tuile"),
  desc=(["Stamp a board tile: it takes the dye's colour, the pattern darker (with a cartridge inside, the cartridge keeps the look). A wet sponge wipes it."],
        ["Tamponne une case du plateau : elle prend la couleur de la teinture, motif plus foncé (avec une cartouche dedans, c'est elle qui garde l'apparence). L'éponge mouillée l'efface."]))

SIGNS = [
    ("easel_sign", ["Planks and sticks (PPP / PPP / S S): 2 easel signs, in any wood, modded ones included. Stands on the ground."],
     ["Planches et bâtons (PPP / PPP / B B) : 2 chevalets, dans n'importe quel bois, mods compris. Se pose au sol."]),
    ("wooden_panel", ["9 identical planks: 2 big wooden boards, on a post, hanging or flat."],
     ["9 planches identiques : 2 grands panneaux en bois, sur poteau, suspendus ou à plat."]),
    ("wooden_cutout_panel", ["A cross of 5 planks: 2 panels. Stencil + axe cut the board along the pattern: a silhouette."],
     ["Une croix de 5 planches : 2 panneaux. Pochoir + hache découpent la planche suivant le motif : une silhouette."]),
    ("rock_sign", ["4 identical stones (stone, granite, deepslate, sandstone...): 1 engraved stone. The stencil really digs the pattern; side by side, their tops merge into one lintel."],
     ["4 roches identiques (pierre, granite, ardoise des abîmes, grès...) : 1 pierre gravée. Le pochoir creuse vraiment le motif ; côte à côte, leurs sommets se soudent en un linteau."]),
    ("plastic_road_sign", ["A plastic block over plastic pellets: 2 road signs of the block's colour. The Wrench cycles the plate: round, square, diamond, triangle, star, heart. Off its post, it floats."],
     ["Un bloc de plastique au-dessus de granulés de plastique : 2 panneaux routiers de la couleur du bloc. La Clé à molette change la plaque : ronde, carrée, losange, triangle, étoile, cœur. Sans poteau, il flotte."]),
]
for i, (sign, en, fr) in enumerate(SIGNS):
    q(sign, -2.5 + i * 1.25, 2.5, I(sign, 1), deps=["stencil"] + (["plastic.block"] if sign == "plastic_road_sign" else []),
      rewards=T1(), desc=(en, fr))

done("done", 0, 5, ["cut", "hammer", "easel_sign", "wooden_panel", "wooden_cutout_panel", "rock_sign",
                    "plastic_road_sign"],
     ("Sign painter", "Peintre en enseignes"),
     (["Every sign, your own patterns and the Stencil Hammer."],
      ["Tous les panneaux, tes propres motifs et le Marteau à pochoirs."]),
     "steveparty:stencil_gun")


# === Building blocks ===========================================================================
chapter("building", "decoration", "steveparty:polished_orange_terracotta_bricks",
        ("Building Blocks", "Blocs de construction"),
        (["Polished terracotta and concrete, bevelled bricks and checkerboard tiles."],
         ["Terre cuite et béton polis, briques biseautées et carrelages en damier."]))


def family(prefix, color, y, row_y, deps, en_family, fr_family, vanilla_en, vanilla_fr):
    """y: the block and bricks; row_y: the row of shapes (and the tiles), between them and the milestone."""
    base = f"polished_{color}_{prefix}"
    bricks = f"polished_{color}_{prefix}_bricks"
    q(prefix + "_block", 0.625, y, I(base, 4), deps=deps, rewards=T1(), size=1.25,
      desc=([f"4 {vanilla_en} in a square: 4 polished blocks (17 colours for terracotta, 16 for concrete). The stonecutter makes every shape from the vanilla block directly."],
            [f"4 {vanilla_fr} en carré : 4 blocs polis (17 couleurs pour la terre cuite, 16 pour le béton). Le tailleur de pierre fait toutes les formes directement depuis le bloc vanilla."]))
    q(prefix + "_bricks", 5, y, I(bricks, 4), deps=[prefix + "_block"], rewards=T1(),
      desc=(["4 polished blocks in a square: 4 bevelled bricks. Each brick has its own shade, and big walls never repeat."],
            ["4 blocs polis en carré : 4 briques biseautées. Chaque brique a sa nuance, et un grand mur ne se répète jamais."]))
    shapes = [("stairs", 4, "6 blocks in steps: 4 stairs.", "6 blocs en escalier : 4 escaliers."),
              ("slab", 6, "3 blocks in a row: 6 slabs.", "3 blocs en ligne : 6 dalles."),
              ("wall", 6, "Two rows of 3: 6 walls.", "Deux lignes de 3 : 6 murets.")]
    for j, (shape, n, en, fr) in enumerate(shapes):
        q(f"{prefix}_{shape}", j * 1.25, row_y, I(f"{color}_polished_{prefix}_{shape}", n), deps=[prefix + "_block"],
          rewards=T0(), desc=([en], [fr]))
        q(f"{prefix}_bricks_{shape}", 3.75 + j * 1.25, row_y, I(f"{color}_polished_{prefix}_bricks_{shape}", n),
          deps=[prefix + "_bricks"], rewards=T0(), desc=([en.replace("blocks", "bricks")], [fr.replace("blocs", "briques")]))


family("terracotta", "orange", -4.5, -2.25, ["welcome.done"], "terracotta", "terre cuite",
       "orange terracotta", "terres cuites orange")
family("concrete", "light_blue", 4.5, 2.25, ["welcome.done"], "concrete", "béton",
       "light blue concrete", "bétons bleu clair")

q("terracotta_tiles", -1.25, -2.25, I("polished_terracotta_tiles", 4), deps=["terracotta_block"], rewards=T1(),
  desc=(["Two polished terracotta blocks of different colours as a checkerboard in a 2x2 grid: 4 tiles, each face a 2x2 checkerboard. Swap the colours for the shifted checkerboard."],
        ["Deux blocs de terre cuite polie de couleurs différentes en damier dans une grille 2x2 : 4 carrelages, chaque face en damier 2x2. Inverse les couleurs pour le damier décalé."]))

q("concrete_tiles", -1.25, 2.25, I("polished_concrete_tiles", 4), deps=["concrete_block"], rewards=T1(),
  desc=(["The same with two polished concrete colours. Every pair of colours works; the item keeps its colours in its name."],
        ["Pareil avec deux couleurs de béton poli. Toutes les paires de couleurs marchent ; l'objet garde ses couleurs dans son nom."]))

done("done", 2.5, 0, ["terracotta_bricks_stairs", "terracotta_bricks_slab", "terracotta_bricks_wall",
                          "terracotta_stairs", "terracotta_slab", "terracotta_wall",
                          "concrete_bricks_stairs", "concrete_bricks_slab", "concrete_bricks_wall",
                          "concrete_stairs", "concrete_slab", "concrete_wall",
                          "terracotta_tiles", "concrete_tiles"],
     ("Master builder", "Maître bâtisseur"),
     (["Both families in every shape, and checkerboard tiles."],
      ["Les deux familles dans toutes les formes, et les carrelages en damier."]),
     "steveparty:polished_concrete_tiles")


# === Odds and ends =============================================================================
chapter("misc", "decoration", "steveparty:looting_box",
        ("Odds and Ends", "Objets divers"),
        (["A few more toys for your party world."],
         ["Quelques jouets de plus pour ton monde de fête."]))

q("looting_box", 0, 0, I("looting_box"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["Gold ingots, polished terracotta, a chest and an amethyst shard."],
        ["Des lingots d'or, de la terre cuite polie, un coffre et un éclat d'améthyste."]))

q("bonk", 2, 0, C(), deps=["looting_box", "board.inventory"], rewards=T2(), icon="minecraft:gold_ingot",
  title=("Bonk!", "Bonk !"),
  desc=(["Put an Inventory Cartridge linked to a chest in it (Wrench), hang it with 2 or 3 blocks of air below, then jump and hit it with your head: it drops the chest's items.",
         "2 hits, then 3 s to recharge."],
        ["Mets-y une Cartouche Bonus / Malus liée à un coffre (Clé à molette), suspends-la avec 2 ou 3 blocs d'air dessous, puis saute et frappe-la de la tête : elle lâche les objets du coffre.",
         "2 coups, puis 3 s de recharge."]))

q("plunger", 0, 2.25, I("plunger"), deps=["welcome.done"], rewards=T1(), size=1.25,
  desc=(["A stick, a slime ball and a bowl. Right-click a mob to mute it; again to give its voice back."],
        ["Un bâton, une boule de slime et un bol. Clic droit sur une créature pour la rendre muette ; encore une fois pour lui rendre sa voix."]))

q("squishy", 2, 3.5, C(), deps=["shops.villager_block"], rewards=T1(), optional=True, icon="steveparty:villager_block",
  title=("Squishy friend", "Ami tout plat"),
  desc=(["The Villager Block reacts to everything: stare at it, jump, show it emeralds, play a disc nearby... About 70 reactions to find."],
        ["Le Bloc de villageois réagit à tout : fixe-le, saute, montre-lui des émeraudes, joue un disque à côté... Environ 70 réactions à découvrir."]))

done("done", 4.25, 1.125, ["bonk", "plunger"],
     ("Odds and ends", "Bric-à-brac"),
     (["The little extras of the mod, all found."],
      ["Les petits plus du mod, tous trouvés."]),
     "steveparty:plunger")


# === The grand finale (in Welcome) =============================================================
for ch in CHAPTERS:
    if ch["key"] == "welcome":
        _current = ch
q("finale", 0, 7.25, [], deps=[c["key"] + ".done" for c in CHAPTERS if c["key"] != "welcome"],
  rewards=[LV(10), IT("party_star", 3), IT("dice_module_lucky", 1), COINS(50)],
  icon="steveparty:party_star", shape="heart", size=2.5,
  title=("Steve Party Master", "Maître de Steve Party"),
  sub=("Every chapter complete", "Tous les chapitres terminés"),
  desc=(["You have built, played and collected everything Steve Party Maker has to offer. Thank you for playing the beta!"],
        ["Tu as construit, joué et collectionné tout ce que Steve Party Maker propose. Merci d'avoir joué la bêta !"]))


# ---------------------------------------------------------------------------------------------
# Reward tables
# ---------------------------------------------------------------------------------------------

REWARD_TABLES = [
    {"key": "party_favors", "title": ("Party Favors", "Cotillons"), "icon": "steveparty:coin",
     "rewards": [  # (reward, weight)
         (IT("coin", 5), 10), (IT("coin", 12), 3), (IT("plastic_pellets", 8), 8), (IT("blank_dice_face", 4), 6),
         (IT("board_space_behavior", 3), 6), (IT("default_dice", 1), 4), (IT("tile", 4), 5),
         (IT("blue_star_fragment", 4), 2), (IT("red_star_fragment", 4), 2), (IT("yellow_star_fragment", 4), 2),
         (IT("green_star_fragment", 4), 2), (IT("purple_star_fragment", 4), 2), (XP(100), 4),
     ]},
    {"key": "star_chest", "title": ("Star Chest", "Coffre étoilé"), "icon": "steveparty:party_star",
     "rewards": [
         (IT("party_star", 1), 3), (IT("coin", 25), 4), (IT("triple_dice", 1), 3), (IT("premium_dice_face_10", 2), 3),
         (IT("dice_module_lucky", 1), 2), (IT("dice_module_reroll", 1), 2), (IT("dice_module_choice", 1), 1),
         (IT("dice_module_power_up", 1), 1), (IT("dice_module_skeleton_key", 1), 2), (IT("swap_dice_face", 2), 2),
         (IT("black_star_fragment", 1), 1), (IT("white_star_fragments_block", 1), 2), (LV(10), 3),
     ]},
]


# ---------------------------------------------------------------------------------------------
# SNBT writer (same layout as FTB Quests: tabs, one entry per line, sorted keys)
# ---------------------------------------------------------------------------------------------

class Raw(str):
    """A value written as-is (typed numbers such as 1.5d or 123L)."""


def d(v):
    return Raw(repr(float(v)) + "d")


def L(v):
    return Raw(f"{int(v)}L")


def f32(v):
    return Raw(repr(float(v)) + "f")


def quote(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def key_str(k):
    ok = all(c.isalnum() or c in "._+-" for c in k)
    return k if ok else quote(k)


def snbt(v, indent=0):
    tab = "\t"
    if isinstance(v, Raw):
        return str(v)
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, float):
        return repr(v) + "d"
    if isinstance(v, str):
        return quote(v)
    if isinstance(v, dict):
        if not v:
            return "{ }"
        lines = ["{"]
        for k in sorted(v):
            lines.append(tab * (indent + 1) + key_str(k) + ": " + snbt(v[k], indent + 1))
        lines.append(tab * indent + "}")
        return "\n".join(lines)
    if isinstance(v, list):
        if not v:
            return "[ ]"
        lines = ["["]
        for e in v:
            lines.append(tab * (indent + 1) + snbt(e, indent + 1))
        lines.append(tab * indent + "]")
        return "\n".join(lines)
    raise TypeError(v)


def write(path, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(snbt(value) + "\n")


# ---------------------------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------------------------

def item_stack(item, count=1):
    return {"count": count, "id": item}


def icon(item):
    return {"id": item}


def drop_observation_quests():
    """Observation tasks (look at an entity) proved unreliable on the server: their quests are left out, and a
    quest that needed one depends on what that one needed instead."""
    gone = {}
    for ch in CHAPTERS:
        for qq in ch["quests"]:
            if any(t.get("type") == "observation" for t in qq["tasks"]):
                gone[qq["key"]] = qq["deps"]

    def resolve(deps):
        out = []
        for d in deps:
            for r in (resolve(gone[d]) if d in gone else [d]):
                if r not in out:
                    out.append(r)
        return out

    for ch in CHAPTERS:
        ch["quests"] = [qq for qq in ch["quests"] if qq["key"] not in gone]
        for qq in ch["quests"]:
            qq["deps"] = resolve(qq["deps"])


def build():
    drop_observation_quests()
    lang = {"en": {}, "fr": {}}

    def tr(kind, oid, sub, en, fr):
        k = f"{kind}.{hexid(oid)}.{sub}"
        lang["en"][k] = en
        lang["fr"][k] = fr

    tables = {t["key"]: make_id("reward_table", t["key"]) for t in REWARD_TABLES}
    crafted = crafting_grid_outputs()
    quest_ids = {}
    for ch in CHAPTERS:
        for qq in ch["quests"]:
            if qq["key"] in quest_ids:
                raise SystemExit("duplicate quest key " + qq["key"])
            quest_ids[qq["key"]] = make_id("quest", qq["key"])

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)

    # data.snbt
    write(os.path.join(OUT, "data.snbt"), {
        "default_autoclaim_rewards": "disabled",
        "default_consume_items": False,
        "default_quest_disable_jei": False,
        "default_quest_shape": "circle",
        "default_reward_team": False,
        "detection_delay": 20,
        "disable_gui": False,
        "drop_book_on_death": False,
        "drop_loot_crates": False,
        "emergency_items_cooldown": 300,
        "fallback_locale": "en_us",
        "grid_scale": d(0.5),
        "hide_excluded_quests": False,
        "lock_message": "",
        "loot_crate_no_drop": {"boss": 0, "monster": 600, "passive": 4000},
        "pause_game": False,
        # linear: a quest's tasks only progress once its dependencies are complete (flexible let players tick
        # locked checkmarks and pre-complete whole chains, which then unlocked in cascade).
        "progression_mode": "linear",
        "show_lock_icons": True,
        "verify_on_load": False,
        "version": 13,
    })
    tr("file", 1, "title", "Steve Party Maker", "Steve Party Maker")

    # chapter groups
    group_ids = {}
    groups = []
    for key, gicon, en, fr in GROUPS:
        gid = make_id("chapter_group", key)
        group_ids[key] = gid
        groups.append({"icon": icon(gicon), "id": hexid(gid)})
        tr("chapter_group", gid, "title", en, fr)
    write(os.path.join(OUT, "chapter_groups.snbt"), {"chapter_groups": groups})

    # reward tables
    for i, t in enumerate(REWARD_TABLES):
        tid = tables[t["key"]]
        rewards = []
        for j, (r, w) in enumerate(t["rewards"]):
            rid = make_id("reward", f"table.{t['key']}.{j}")
            rewards.append(reward_nbt(r, rid, tables, weight=w))
        write(os.path.join(OUT, "reward_tables", t["key"] + ".snbt"), {
            "icon": icon(t["icon"]), "id": hexid(tid), "loot_size": 1, "order_index": i,
            "rewards": rewards, "use_title": True,
        })
        tr("reward_table", tid, "title", *t["title"])

    # chapters
    order_in_group = {}
    for ch in CHAPTERS:
        cid = make_id("chapter", ch["key"])
        group = ch["group"]
        idx = order_in_group.get(group, 0)
        order_in_group[group] = idx + 1
        tr("chapter", cid, "title", *ch["title"])
        tr("chapter", cid, "chapter_subtitle", *ch["subtitle"])
        quests = []
        for qq in ch["quests"]:
            qid = quest_ids[qq["key"]]
            nbt = {"id": hexid(qid), "x": d(qq["x"]), "y": d(qq["y"])}
            if qq["deps"]:
                nbt["dependencies"] = [hexid(quest_ids[k]) for k in qq["deps"]]
            if qq["dep_req"]:
                nbt["dependency_requirement"] = qq["dep_req"]
            if qq["shape"]:
                nbt["shape"] = qq["shape"]
            if qq["size"]:
                nbt["size"] = d(qq["size"])
            if qq["optional"]:
                nbt["optional"] = True
            if qq["icon"]:
                nbt["icon"] = icon(qq["icon"])
            tasks = []
            for k, t in enumerate(qq["tasks"]):
                tid = make_id("task", f"{qq['key']}.{k}")
                tn = {"id": hexid(tid), "type": t["type"]}
                if t["type"] == "item":
                    tn["item"] = item_stack(t["item"])
                    if t["count"] > 1:
                        tn["count"] = L(t["count"])
                    if t["how"] == "craft":
                        if t["item"] not in crafted:
                            raise SystemExit(f"{qq['key']}: no crafting-grid recipe makes {t['item']}, use how='own' or 'consume'")
                        tn["only_from_crafting"] = True
                    elif t["how"] == "consume":
                        tn["consume_items"] = True
                elif t["type"] == "advancement":
                    if not os.path.isfile(os.path.join(MOD_DATA, "advancement", t["advancement"].split(":")[1] + ".json")):
                        raise SystemExit(f"{qq['key']}: unknown advancement {t['advancement']}")
                    tn["advancement"] = t["advancement"]
                    tn["criterion"] = ""
                elif t["type"] == "observation":
                    tn["observation_type"] = "entity_type"
                    tn["to_observe"] = t["entity"]
                    tn["timer"] = L(t["ticks"])
                tasks.append(tn)
            nbt["tasks"] = tasks
            rewards = []
            for k, r in enumerate(qq["rewards"]):
                rewards.append(reward_nbt(r, make_id("reward", f"{qq['key']}.{k}"), tables))
            # One point on the quests leaderboard (the TAB list), claimed by itself and never shown
            rewards.append({"id": hexid(make_id("reward", f"{qq['key']}.score")), "type": "command",
                            "command": f"/scoreboard players add {{p}} {SCORE_OBJECTIVE} 1",
                            "elevate_perms": True, "silent": True, "auto": "invisible"})
            if rewards:
                nbt["rewards"] = rewards
            quests.append(nbt)

            # Texts: title from the item when not given.
            title = qq["title"]
            if title is None:
                first = qq["tasks"][0]
                if first["type"] != "item":
                    raise SystemExit("quest without title: " + qq["key"])
                title = (item_name(first["item"], "en"), item_name(first["item"], "fr"))
            tr("quest", qid, "title", *title)
            sub = qq["sub"]
            hows = {t.get("how") for t in qq["tasks"]}
            if not sub and "craft" in hows:
                sub = ("Craft it (owned ones don't count)", "À fabriquer (ceux de ton inventaire ne comptent pas)")
            elif not sub and "consume" in hows:
                sub = ("Click the task to hand them in", "Clique sur la tâche pour livrer les granulés")
            if sub:
                tr("quest", qid, "quest_subtitle", *sub)
            if qq["desc"]:
                tr("quest", qid, "quest_desc", qq["desc"][0], qq["desc"][1])

        filename = ch["key"]
        write(os.path.join(OUT, "chapters", filename + ".snbt"), {
            "default_hide_dependency_lines": False,
            "default_quest_shape": "",
            "filename": filename,
            "group": hexid(group_ids[group]) if group else "",
            "icon": icon(ch["icon"]),
            "id": hexid(cid),
            "images": [],
            "order_index": idx,
            "quest_links": [],
            "quests": quests,
        })

    for code, table in (("en_us", lang["en"]), ("fr_fr", lang["fr"])):
        write(os.path.join(OUT, "lang", code + ".snbt"), table)

    n_quests = sum(len(c["quests"]) for c in CHAPTERS)
    print(f"{len(CHAPTERS)} chapters, {n_quests} quests, {len(REWARD_TABLES)} reward tables -> {OUT}")
    for c in CHAPTERS:
        print(f"  {c['key']:<10} {len(c['quests']):>3} quests  {c['title'][0]}")


# The scoreboard shown in the TAB list: one point per quest completed (create it once on the server:
#   /scoreboard objectives add steveparty_quests dummy "Quests"   then   /scoreboard objectives setdisplay list steveparty_quests)
SCORE_OBJECTIVE = "steveparty_quests"


MOD_DATA = os.path.join(ROOT, "src", "main", "resources", "data", "steveparty")
# Recipe types crafted in a crafting grid (FTB Quests' only_from_crafting counts the crafting result slot).
CRAFTING_GRID_TYPES = {"minecraft:crafting_shaped", "minecraft:crafting_shapeless", "steveparty:material_shaped",
                       "steveparty:shaped_unmirrored", "steveparty:flag_from_wool", "steveparty:tile_shaped"}


def crafting_grid_outputs():
    """Item ids made by at least one crafting-grid recipe of the mod (hand-written or datagen)."""
    out = set()
    for base in ("resources", "generated"):
        for dirpath, _, files in os.walk(os.path.join(ROOT, "src", "main", base, "data", "steveparty", "recipe")):
            for name in files:
                if not name.endswith(".json"):
                    continue
                recipe = json.load(open(os.path.join(dirpath, name), encoding="utf-8"))
                if recipe.get("type") not in CRAFTING_GRID_TYPES:
                    continue
                result = recipe.get("result")
                rid = result if isinstance(result, str) else (result or {}).get("id")
                if rid:
                    out.add(rid)
    return out


def reward_nbt(r, rid, tables, weight=None):
    n = {"id": hexid(rid)}
    t = r["type"]
    if t == "item":
        n["item"] = item_stack(r["item"])
        if r["count"] > 1:
            n["count"] = r["count"]
        if weight is None:
            n["type"] = "item"
    elif t == "xp":
        n["type"] = "xp"
        n["xp"] = r["xp"]
    elif t == "xp_levels":
        n["type"] = "xp_levels"
        n["xp_levels"] = r["xp_levels"]
    elif t in ("random", "choice"):
        n["type"] = t
        n["table_id"] = L(tables[r["table"]])
    else:
        raise ValueError(t)
    if weight is not None and weight != 1:
        n["weight"] = f32(weight)
    return n


if __name__ == "__main__":
    build()

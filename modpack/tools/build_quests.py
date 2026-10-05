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

def I(item, count=1):
    """Obtain-item task (steveparty: prefix added when missing)."""
    if ":" not in item:
        item = "steveparty:" + item
    return {"type": "item", "item": item, "count": count}


def C():
    """Checkmark task (the player ticks it)."""
    return {"type": "checkmark"}


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
    ("stars", "steveparty:power_star", "Stars", "Étoiles"),
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
    """Chapter-completion milestone."""
    q(key, x, y, C(), deps=deps, rewards=CHAPTER_END(), title=title, desc=desc, sub=sub, icon=icon,
      shape="gear", size=2.0)


# === Welcome ===================================================================================
chapter("welcome", None, "steveparty:party_controller",
        ("Welcome", "Bienvenue"),
        (["Build a board game in Minecraft, and play it with your friends."],
         ["Construisez un jeu de plateau dans Minecraft et jouez-y entre amis."]))

q("welcome", 0, 0, C(), rewards=[XP(20), COINS(3)], icon="steveparty:party_controller", shape="gear", size=2.0,
  title=("Welcome to Steve Party Maker!", "Bienvenue dans Steve Party Maker !"),
  sub=("A party game, block by block", "Un party game, bloc par bloc"),
  desc=(["Steve Party Maker turns Minecraft into a party board game: build a board of tiles, turn any mob into a pawn, roll dice, play mini-games and collect stars.",
         "",
         "This book follows the mod step by step, one quest per item. Every chapter ends with a big milestone.",
         "Tick this quest to start!"],
        ["Steve Party Maker transforme Minecraft en jeu de plateau : construisez un plateau de tuiles, transformez n'importe quel mob en pion, lancez les dés, jouez des mini-jeux et collectionnez les étoiles.",
         "",
         "Ce livre suit le mod pas à pas, une quête par objet. Chaque chapitre se termine par une grande étape.",
         "Cochez cette quête pour commencer !"]))

q("pellets", -3, 2.5, I("plastic_pellets", 4), deps=["welcome"],
  rewards=[XP(15), IT("minecraft:sugar_cane", 8)],
  desc=(["Smelt sugar cane in a furnace. Plastic pellets are in almost everything: tiles, cartridges, tokens and every plastic block."],
        ["Faites cuire de la canne à sucre au four. Les granulés de plastique entrent dans presque tout : tuiles, cartouches, jetons et tous les blocs de plastique."]))

q("coin", 3, 2.5, I("coin", 1), deps=["welcome"],
  rewards=[XP(15), IT("minecraft:gold_nugget", 4)],
  desc=(["One gold nugget makes one Coin (shapeless). Coins are the default currency of a party: mini-games pay them out, and shops can sell for them."],
        ["Une pépite d'or donne une Pièce (sans forme). La Pièce est la monnaie par défaut d'une partie : les mini-jeux en rapportent et les boutiques peuvent vendre contre elle."]))

q("recipes", -1, 2.5, C(), deps=["welcome"], rewards=T0(), optional=True, icon="minecraft:knowledge_book",
  title=("Read the recipes", "Lire les recettes"),
  desc=(["Every recipe of the mod can be read in a recipe viewer (REI or EMI) if the pack has one: it also shows every tile with its cartridge.",
         "The mod's wiki (on its GitHub page) explains each block in detail."],
        ["Toutes les recettes du mod se lisent dans un visualiseur de recettes (REI ou EMI) si le pack en a un : il montre aussi chaque tuile avec sa cartouche.",
         "Le wiki du mod (sur sa page GitHub) explique chaque bloc en détail."]))

q("team", 1, 2.5, C(), deps=["welcome"], rewards=T0(), optional=True, icon="minecraft:white_banner",
  title=("Party up", "Faire équipe"),
  desc=(["Quests are shared per FTB Teams team. To share progress with friends, create a party with /ftbteams party create and invite them.",
         "Rewards stay personal: everyone claims their own."],
        ["Les quêtes sont partagées par équipe FTB Teams. Pour avancer ensemble, créez une équipe avec /ftbteams party create et invitez vos amis.",
         "Les récompenses restent personnelles : chacun réclame les siennes."]))

done("done", 0, 5, ["pellets", "coin"],
     ("Ready to party", "Prêt pour la fête"),
     (["You have the basics. Next: build a board (The Board Game), then explore the other chapters in any order."],
      ["Vous avez les bases. Ensuite : construire un plateau (Le jeu de plateau), puis explorer les autres chapitres dans l'ordre que vous voulez."]),
     "steveparty:tile")
# Lighter end for the first chapter.
CHAPTERS[-1]["quests"][-1]["rewards"] = [XP(50), COINS(5), RANDOM("party_favors")]


# === The Board =================================================================================
chapter("board", "board_game", "steveparty:tile",
        ("The Board", "Le plateau"),
        (["Tiles, cartridges and the Wrench: build the path your pawns will walk."],
         ["Tuiles, cartouches et Clé : construisez le parcours de vos pions."]))

q("tile", 0, 0, I("tile", 2), deps=["welcome.pellets"], rewards=[XP(15), IT("plastic_pellets", 4)], size=1.25,
  desc=(["Three carpets over plastic pellets and a light weighted pressure plate: 2 Tiles.",
         "A Tile is one board space and counts as one step. It holds a single cartridge, which gives it its role. Place it in 8 directions, on slabs, carpets, and even on stairs, where it follows the slope."],
        ["Trois tapis au-dessus de granulés de plastique et d'une plaque de pression légère : 2 Tuiles.",
         "Une Tuile est une case du plateau et compte comme un pas. Elle reçoit une seule cartouche, qui lui donne son rôle. Elle se pose dans 8 directions, sur les dalles, les tapis et même sur les escaliers, dont elle suit la pente."]))

q("wrench", 2, 0, I("wrench"), deps=["tile"], rewards=T2(),
  desc=(["Redstone and an iron ingot. The Wrench links tiles together (modes Trace, Edit, Cut: R or sneak + scroll), shows the board and its problems, opens cartridge blocks with sneak + right-click, and breaks any plastic block in one hit."],
        ["De la redstone et un lingot de fer. La Clé relie les cases (modes Tracé, Éditer, Couper : R ou accroupi + molette), montre le plateau et ses problèmes, ouvre les blocs à cartouches accroupi + clic droit, et casse tout bloc de plastique d'un coup."]))

q("link", 4, 0, C(), deps=["wrench", "cartridge"], rewards=T2(), icon="steveparty:wrench",
  title=("Trace the path", "Tracer le chemin"),
  desc=(["Wrench in Trace mode: right-click tile after tile (or hold the click and sweep) to link them. Each new tile automatically gets a Cartridge from your inventory, and turns towards the next one.",
         "Left-click in the air undoes, sneak + left-click redoes."],
        ["Clé en mode Tracé : clic droit de case en case (ou maintenez le clic en balayant) pour les relier. Chaque nouvelle case reçoit automatiquement une Cartouche de votre inventaire et se tourne vers la suivante.",
         "Clic gauche dans le vide pour annuler, accroupi + clic gauche pour rétablir."]))

q("loop", 6, 0, C(), deps=["link"], rewards=T2(), icon="minecraft:firework_rocket",
  title=("Close the loop", "Fermer la boucle"),
  desc=(["Click the first tile again to close the loop: confetti! A loop with a Start tile is all a party needs.",
         "Several links from one tile make a fork: the player chooses the way. No link: a dead end."],
        ["Recliquez la première case pour fermer la boucle : confettis ! Une boucle avec une case départ suffit pour une partie.",
         "Plusieurs liens depuis une case font un embranchement : le joueur choisit. Aucun lien : un cul-de-sac."]))

q("board_view", 8, 0, C(), deps=["loop"], rewards=T1(), icon="minecraft:spyglass",
  title=("Board view and check", "Vue du plateau et diagnostic"),
  desc=(["Wrench in hand, the board appears: chevrons along each link, step numbers, \"?\" on forks, \"!\" on dead ends.",
         "Click a Party Controller with the Wrench (or /steveparty board check) to list every problem of the board."],
        ["Clé en main, le plateau apparaît : chevrons le long des liens, numéros de pas, « ? » aux bifurcations, « ! » aux impasses.",
         "Clic de Clé sur un Contrôleur de fête (ou /steveparty board check) : la liste des problèmes du plateau."]))

q("check_point", 0, -2.5, I("check_point", 4), deps=["tile"], rewards=T1(),
  desc=(["Prismarine shards, light blue panes and a chest: 4 Checkpoints. A floating glass cube that a pawn crosses without spending a step: free turns, forks and detours. Its role never triggers on arrival, except Stop and Shop."],
        ["Éclats de prismarine, vitres bleu clair et un coffre : 4 Points de déclenchement. Un cube de verre flottant que le pion traverse sans dépenser de pas : virages, bifurcations et détours gratuits. Son rôle ne se déclenche jamais à l'arrivée, sauf Stop et Boutique."]))

q("advanced_tile", 2, -2.5, I("advanced_tile"), deps=["tile"], rewards=T2(),
  desc=(["A Tile with a comparator, gold ingots and a chest. It holds 16 cartridges: the redstone power it receives picks the active one (power 0 = slot 1, power 1 = slot 2...)."],
        ["Une Tuile avec un comparateur, des lingots d'or et un coffre. Elle contient 16 cartouches : la puissance redstone reçue choisit l'active (puissance 0 = emplacement 1, puissance 1 = emplacement 2...)."]))

q("redstone_slots", 4, -2.5, C(), deps=["advanced_tile"], rewards=T1(), icon="minecraft:comparator",
  title=("A tile that changes", "Une case qui change"),
  desc=(["Put two different cartridges in an Advanced Tile and power it: the tile changes role, colour and destinations with the signal. A comparator or a redstone counter makes tiles that change every turn."],
        ["Mettez deux cartouches différentes dans une Tuile avancée et alimentez-la : la case change de rôle, de couleur et de destinations avec le signal. Un comparateur ou un compteur redstone fait des cases qui changent à chaque tour."]))

q("router", 6, -2.5, I("board_space_redstone_router"), deps=["advanced_tile"], rewards=T2(),
  desc=(["Gold, a redstone torch, a chest and an amethyst shard. Give it a cartridge whose destinations are the tiles to drive: its redstone input replaces theirs (one lever switches a whole board).",
         "A comparator on it reads what happens on its tiles: 14 bonus, 13 malus, 10 Stop, 8 Teleport, 1 a pawn passes..."],
        ["De l'or, une torche de redstone, un coffre et un éclat d'améthyste. Donnez-lui une cartouche dont les destinations sont les cases à piloter : sa redstone remplace la leur (un levier change tout un plateau).",
         "Un comparateur dessus capte ce qui se passe sur ses cases : 14 bonus, 13 malus, 10 Stop, 8 Téléportation, 1 un pion passe..."]))

q("sizes", -2, -1.25, C(), deps=["tile"], rewards=T1(), icon="minecraft:stonecutter",
  title=("Small and large tiles", "Petites et grandes tuiles"),
  desc=(["Stonecutter: 1 Tile gives 2 small tiles (1x1). Four standard tiles in a 2x2 square give one large tile (exactly 2x2 blocks). The tooltip and the icon show the size."],
        ["Tailleur de pierre : 1 Tuile donne 2 petites tuiles (1x1). Quatre tuiles standard en carré 2x2 donnent une grande tuile (exactement 2x2 blocs). L'infobulle et l'icône indiquent la taille."]))

q("silk", -2, 1.25, C(), deps=["tile"], rewards=T0(), optional=True, icon="minecraft:enchanted_book",
  title=("Pack a tile", "Emporter une tuile"),
  desc=(["Break a tile with a Silk Touch pickaxe: it drops as one item that keeps all its cartridges, their settings and its look. Place it again and it is back as it was."],
        ["Cassez une tuile avec une pioche Toucher de soie : elle tombe en un seul objet qui garde ses cartouches, leurs réglages et son look. Reposée, elle redevient comme avant."]))

# Cartridges sub-tree
q("cartridge", 0, 3.5, I("board_space_behavior", 2), deps=["welcome.pellets"], size=1.25,
  rewards=[XP(15), IT("board_space_behavior", 4)],
  desc=(["2 redstone, 2 paper and 2 plastic pellets. A cartridge gives a tile its role and its destinations. The plain Cartridge has no role: it is just a path.",
         "Right-click a block with it to add a destination; right-click in the air to open its menu. Every other cartridge is this one plus an ingredient."],
        ["2 redstone, 2 papier et 2 granulés de plastique. Une cartouche donne à une case son rôle et ses destinations. La Cartouche simple n'a pas de rôle : c'est juste un chemin.",
         "Clic droit sur un bloc avec elle pour ajouter une destination ; clic droit dans le vide pour ouvrir son menu. Toutes les autres cartouches, c'est elle plus un ingrédient."]))

CART_Y = 6
q("start", -4.5, CART_Y, I("tile_behavior_start"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + lime dye. A start space: a pawn placed on it binds to it, floats and takes its colour. The Party Controller only takes pawns bound to a start space."],
        ["Cartouche + teinture vert clair. Une case de départ : un pion posé dessus s'y lie, flotte et lui donne sa couleur. Le Contrôleur de fête ne prend que les pions liés à une case de départ."]))

q("stop", -3, CART_Y, I("board_space_behavior_stop"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + red dye. A pawn that reaches a Stop space ends its move there, whatever steps it has left. Works on a Checkpoint too."],
        ["Cartouche + teinture rouge. Un pion qui atteint une case Stop y termine son déplacement, quels que soient ses pas restants. Marche aussi sur un Point de déclenchement."]))

q("shop", -1.5, CART_Y, I("shop_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + emerald + gold nugget. A pawn that ends on it (or passes a Checkpoint with it) opens the shop of the nearest Boxed Trader for its player, Mario Party style. See the Shops chapter."],
        ["Cartouche + émeraude + pépite d'or. Un pion qui s'y arrête (ou qui passe un Point de déclenchement qui la porte) ouvre à son joueur la boutique du Marchand en carton le plus proche, façon Mario Party. Voir le chapitre Boutiques."]))

q("advance", 0, CART_Y, I("advance_back_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + piston. The pawn that ends on it moves again, 1 to 6 spaces forward (green) or back (pink). Sneak + scroll with it in hand sets the count and direction."],
        ["Cartouche + piston. Le pion qui s'y arrête repart de 1 à 6 cases en avant (vert) ou en arrière (rose). Accroupi + molette, cartouche en main, règle le nombre et le sens."]))

q("replay", 1.5, CART_Y, I("replay_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + repeater. The player whose pawn ends on it rolls again at once, and gets the die back. One extra turn only."],
        ["Cartouche + répéteur. Le joueur dont le pion s'y arrête rejoue aussitôt, et récupère son dé. Un seul tour en plus."]))

q("teleport", 3, CART_Y, I("teleport_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + ender pearl. A pawn that ends on it is sent to another Teleport space of the same network colour (violet, green, orange or blue)."],
        ["Cartouche + perle de l'Ender. Un pion qui s'y arrête est envoyé sur une autre case Téléportation du même réseau de couleur (violet, vert, orange ou bleu)."]))

q("inventory", 4.5, CART_Y, I("inventory_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + chest. Links up to 8 chests (right-click them) and gives or takes items: positive items are bonus spaces (blue), negative ones malus spaces (red), empty is an item space (orange). The scroll wheel sets the amounts."],
        ["Cartouche + coffre. Elle retient jusqu'à 8 coffres (clic droit dessus) et donne ou prend des objets : objets positifs = case bonus (bleue), négatifs = case malus (rouge), vide = case objet (orange). La molette règle les quantités."]))

q("tp_pair", 3, CART_Y + 1.75, C(), deps=["teleport"], rewards=T1(), icon="minecraft:ender_pearl",
  title=("A teleport network", "Un réseau de téléportation"),
  desc=(["Place two Teleport spaces of the same colour on one board. A dye on the tile (purple, green, orange, blue...) changes its network. A lone space of its colour sends nobody anywhere."],
        ["Posez deux cases Téléportation de la même couleur sur un plateau. Une teinture sur la case (violet, vert, orange, bleu...) change son réseau. Une case seule de sa couleur n'envoie personne."]))

q("bonus_malus", 4.5, CART_Y + 1.75, C(), deps=["inventory"], rewards=T1(), icon="minecraft:chest",
  title=("Bonus and malus", "Bonus et malus"),
  desc=(["Make one blue (bonus) and one red (malus) space. They matter: in a party, the colour of the space under each pawn makes the mini-game teams."],
        ["Faites une case bleue (bonus) et une rouge (malus). Elles comptent : en partie, la couleur de la case sous chaque pion forme les équipes des mini-jeux."]))

done("done", 10, 2.5, ["loop", "check_point", "advanced_tile", "router", "start", "stop", "shop", "advance",
                       "replay", "teleport", "inventory"],
     ("Board builder", "Bâtisseur de plateau"),
     (["Tiles, checkpoints, every cartridge and a closed loop: your board is ready for pawns."],
      ["Tuiles, points de déclenchement, toutes les cartouches et une boucle fermée : votre plateau attend ses pions."]),
     "steveparty:advanced_tile")


# === Pawns =====================================================================================
chapter("pawns", "board_game", "steveparty:tokenizer_wand",
        ("Pawns", "Pions"),
        (["Any mob can become a pawn with the Tokenizer Wand."],
         ["N'importe quel mob devient un pion avec la Baguette de pions."]))

q("crystal_ball", 0, 0, I("garnet_crystal_ball"), deps=["welcome.done"], rewards=T2(),
  desc=(["A nether star surrounded by 4 blocks of redstone. Its only use: the head of the Tokenizer Wand."],
        ["Une étoile du Nether entourée de 4 blocs de redstone. Son seul usage : la tête de la Baguette de pions."]))

q("wand", 2, 0, I("tokenizer_wand"), deps=["crystal_ball"], rewards=T3(), size=1.25,
  desc=(["Garnet crystal ball, gold block and blaze rod, in a column. A golden wand in Kamek style."],
        ["Boule de cristal en grenat, bloc d'or et bâton de Blaze, en colonne. Une baguette dorée façon Kamek."]))

q("pionificus", 4, 0, C(), deps=["wand"], rewards=T2(), icon="steveparty:tokenizer_wand",
  title=("Pionificus!", "Pionificus !"),
  desc=(["Right-click a mob with the wand (or shoot a spark at it), then draw a circle around it with the mouse. On the guide: a 1-block pawn; twice as big: 2 blocks; smaller: down to 0.25.",
         "The mob becomes a still, silent, invulnerable figurine, and you own it."],
        ["Clic droit sur un mob avec la baguette (ou lancez-lui une flammèche), puis dessinez un cercle autour de lui à la souris. Sur le guide : pion d'un bloc ; deux fois plus grand : 2 blocs ; plus petit : jusqu'à 0,25.",
         "Le mob devient une figurine immobile, muette et invulnérable, dont vous êtes le propriétaire."]))

q("resize", 4, -1.75, C(), deps=["pionificus"], rewards=T0(), optional=True, icon="minecraft:blaze_rod",
  title=("Resizing spell", "Sort de redimensionnement"),
  desc=(["Right-click one of your pawns with the wand and draw a new circle to change its size."],
        ["Clic droit sur un de vos pions avec la baguette et tracez un nouveau cercle pour changer sa taille."]))

q("dye", 4, 1.75, C(), deps=["pionificus"], rewards=T0(), optional=True, icon="minecraft:light_blue_dye",
  title=("Team colours", "Couleurs d'équipe"),
  desc=(["Right-click a pawn with a dye: its name and its base change colour (and its start space too)."],
        ["Clic droit sur un pion avec une teinture : son nom et son socle changent de couleur (et sa case de départ aussi)."]))

q("token", 6, 0, I("token"), deps=["pionificus"], rewards=T2(),
  desc=(["Plastic pellets around a shulker box. Right-click your pawn with an empty Token to store it, right-click a block to put it back: the way to move a pawn by hand."],
        ["Des granulés de plastique autour d'une boîte de Shulker. Clic droit sur votre pion avec un Jeton vide pour le ranger, clic droit sur un bloc pour le reposer : la façon de déplacer un pion à la main."]))

q("bind", 8, 0, C(), deps=["token", "board.start"], rewards=MILESTONE(), icon="steveparty:tile_behavior_start",
  shape="hexagon", size=1.25,
  title=("Take your place", "Prendre sa place"),
  desc=(["Put your pawn on a Start space: it binds to it, floats and looks at the nearest player. Right-click the space empty-handed to own it. Only bound pawns join a party."],
        ["Posez votre pion sur une case de départ : il s'y lie, flotte et regarde le joueur le plus proche. Clic droit main vide sur la case pour en devenir propriétaire. Seuls les pions liés entrent dans une partie."]))

q("game_master", 2, 2, C(), deps=["wand"], rewards=T2(), optional=True, icon="minecraft:enchanted_book",
  title=("Game Master", "Maître du jeu"),
  desc=(["A treasure enchantment for the wand only: found in woodland mansion chests, or dropped by evokers and illusioners killed by a player. With it you can resize anyone's pawn and exclude any pawn from a party, like a referee."],
        ["Un enchantement trésor, uniquement pour la baguette : dans les coffres de manoir, ou lâché par les évocateurs et illusionnistes tués par un joueur. Il permet de redimensionner le pion de n'importe qui et d'exclure n'importe quel pion d'une partie, comme un arbitre."]))

done("done", 10, 0, ["bind", "token"],
     ("Pawn master", "Maître des pions"),
     (["Your pawn waits on its start space. Time to roll some dice!"],
      ["Votre pion attend sur sa case de départ. À vous les dés !"]),
     "steveparty:token")


# === Dice ======================================================================================
chapter("dice", "board_game", "steveparty:default_dice",
        ("Dice", "Dés"),
        (["Roll, move, and engrave your own faces."],
         ["Lancez, avancez, et gravez vos propres faces."]))

q("default_dice", 0, 0, I("default_dice"), deps=["welcome.done"], rewards=[XP(15), IT("default_dice", 1)], size=1.25,
  desc=(["8 iron ingots around a wind charge. Rolls 1 to 10."],
        ["8 lingots de fer autour d'une charge de vent. Donne 1 à 10."]))

q("roll", 2, 0, C(), deps=["default_dice"], rewards=T1(), icon="steveparty:default_dice",
  title=("Roll it!", "Lancez-le !"),
  desc=(["Right-click: the die flies over the nearest mob (sneak: the nearest player) and spins. Hit it to stop it. The result is announced in the chat."],
        ["Clic droit : le dé part au-dessus du mob le plus proche (accroupi : du joueur le plus proche) et tourne. Frappez-le pour l'arrêter. Le résultat s'affiche dans le chat."]))

q("move", 4, 0, C(), deps=["roll", "pawns.pionificus"], rewards=T2(), icon="steveparty:tile",
  title=("Move a pawn", "Faire avancer un pion"),
  desc=(["Roll near one of your pawns standing on a linked board: it walks the result, step by step. At a fork, right-click an arrow empty-handed to choose the way."],
        ["Lancez près d'un de vos pions posé sur un plateau relié : il avance du résultat, case par case. À un embranchement, clic droit main vide sur une flèche pour choisir."]))

q("double", 2, -2, I("double_dice"), deps=["default_dice"], rewards=T1(),
  desc=(["Two Default Dice, shapeless. Two linked dice: 2 to 20. Splits back into two."],
        ["Deux Dés par défaut, sans forme. Deux dés liés : 2 à 20. Se redécoupe en deux."]))

q("triple", 4, -2, I("triple_dice"), deps=["double"], rewards=T2(),
  desc=(["Three Default Dice, or a Double + a Default Die. Three linked dice: 3 to 30."],
        ["Trois Dés par défaut, ou un Dé double + un Dé par défaut. Trois dés liés : 3 à 30."]))

# Faces sub-tree
q("blank", 0, 3, I("blank_dice_face", 4), deps=["default_dice"], rewards=[XP(15), IT("blank_dice_face", 4)], size=1.25,
  desc=(["2 iron ingots and 2 nether quartz in a square: 4 Blank Dice Faces. Every face starts from a blank one (stonecutter), and the Dice Forge uses them up."],
        ["2 lingots de fer et 2 quartz du Nether en carré : 4 Faces de dé vierges. Toute face part d'une vierge (tailleur de pierre), et la Forge à dés en consomme."]))

FACE_Y = 5
q("face6", -3, FACE_Y, I("dice_face_6"), deps=["blank"], rewards=T1(),
  desc=(["Stonecutter: a blank face becomes any number from 0 to 10. Any face goes back to blank the same way."],
        ["Tailleur de pierre : une face vierge devient n'importe quel nombre de 0 à 10. Toute face redevient vierge de la même façon."]))

q("face0", -1.5, FACE_Y, I("dice_face_0"), deps=["blank"], rewards=T1(),
  desc=(["A special face: the pawn stays where it is and the space under it triggers again."],
        ["Une face spéciale : le pion reste sur place et la case où il se trouve rejoue son effet."]))

q("premium", 0, FACE_Y, I("premium_dice_face_10"), deps=["blank"], rewards=T1(),
  desc=(["Premium faces (1 to 10) shine in gold on the die. For now they count like normal numbers."],
        ["Les faces premium (1 à 10) brillent en or sur le dé. Pour l'instant, elles comptent comme des nombres normaux."]))

q("cursed", 1.5, FACE_Y, I("cursed_dice_face_3"), deps=["blank"], rewards=T1(),
  desc=(["Cursed faces (1 to 3) show a purple skull. For now they count like normal numbers."],
        ["Les faces maudites (1 à 3) portent un crâne violet. Pour l'instant, elles comptent comme des nombres normaux."]))

q("coin_face", 3, FACE_Y, I("coin_dice_face_1"), deps=["blank", "welcome.coin"], rewards=T1(),
  desc=(["Blank face + Coin: a +1 Coins face (stonecutter for +2 to +10). Instead of moving, the roller gains that many coins of the party."],
        ["Face vierge + Pièce : une face Pièces +1 (tailleur de pierre pour +2 à +10). Au lieu d'avancer, le lanceur gagne autant de pièces de la partie."]))

q("debt_face", 4.5, FACE_Y, I("debt_dice_face_1"), deps=["blank"], rewards=T1(),
  desc=(["Blank face + spider eye: a -1 Debt face (stonecutter for -2 to -10). The roller loses that many coins, never more than they have."],
        ["Face vierge + œil d'araignée : une face Dette -1 (tailleur de pierre pour -2 à -10). Le lanceur perd autant de pièces, jamais plus que ce qu'il a."]))

q("swap_face", 6, FACE_Y, I("swap_dice_face"), deps=["blank"], rewards=T2(),
  desc=(["Blank face + ender pearl. The roller's pawn swaps places with another player's pawn of their choice."],
        ["Face vierge + perle de l'Ender. Le pion du lanceur échange sa place avec celui d'un autre joueur, à son choix."]))

# Modules sub-tree
MODULES = [
    ("slow", ["Blank face + clock. The die spins slowly; its roller hits it to stop on the face shown."],
     ["Face vierge + horloge. Le dé tourne lentement ; son lanceur le frappe pour l'arrêter sur la face affichée."]),
    ("choice", ["Blank face + compass. The roller picks the face."],
     ["Face vierge + boussole. Le lanceur choisit la face."]),
    ("infinity", ["Blank face + echo shard. The die comes back to its roller after the roll."],
     ["Face vierge + éclat d'écho. Le dé revient à son lanceur après le lancer."]),
    ("lucky", ["Blank face + rabbit's foot. One extra roll per module (up to 5): keep the best one."],
     ["Face vierge + patte de lapin. Un lancer de plus par module (jusqu'à 5) : gardez le meilleur."]),
    ("reroll", ["Blank face + wind charge. Stop or again: after the result, keep it or roll again, once per module."],
     ["Face vierge + charge de vent. Stop ou encore : après le résultat, gardez-le ou relancez, une fois par module."]),
    ("reversed", ["Blank face + fermented spider eye. A negative module: the pawn moves back, and coins won are lost."],
     ["Face vierge + œil d'araignée fermenté. Un module négatif : le pion recule, et les pièces gagnées sont perdues."]),
    ("skeleton_key", ["Blank face + tripwire hook. During this move the pawn ignores Stop spaces and shop stops."],
     ["Face vierge + crochet. Pendant ce déplacement, le pion ignore les cases Stop et les haltes boutique."]),
    ("homing", ["Blank face + eye of ender. At forks the pawn chooses on its own, at random."],
     ["Face vierge + œil de l'Ender. Aux embranchements, le pion choisit tout seul, au hasard."]),
]
MOD_Y = 8
for i, (m, en, fr) in enumerate(MODULES):
    q("module_" + m, -4.5 + i * 1.5, MOD_Y, I("dice_module_" + m), deps=["blank"], rewards=T1(), desc=(en, fr))

q("fit_module", 0.75, MOD_Y + 2, C(), deps=["module_" + m for m, _, _ in MODULES], dep_req="one_completed",
  rewards=T2(), icon="minecraft:crafting_table",
  title=("Fit a module", "Poser un module"),
  desc=(["Crafting table: a die (default, forged, double or triple) + one or more module items gives the same die with those modules. The module items stay in the grid. A die can carry several modules: they are listed in its tooltip."],
        ["Établi : un dé (simple, forgé, double ou triple) + un ou plusieurs objets module donnent le même dé avec ces modules. Les objets module restent dans la grille. Un dé peut en porter plusieurs : ils sont listés dans son infobulle."]))

done("done", 8, 0, ["triple", "move", "swap_face", "coin_face", "fit_module"],
     ("Dice collector", "Collectionneur de dés"),
     (["Dice, special faces and modules: every roll can now be a surprise."],
      ["Dés, faces spéciales et modules : chaque lancer peut maintenant être une surprise."]),
     "steveparty:triple_dice")


# === Running a party ===========================================================================
chapter("party", "board_game", "steveparty:party_controller",
        ("Running a Party", "Mener une partie"),
        (["The Party Controller runs turns, mini-games and prizes."],
         ["Le Contrôleur de fête enchaîne les tours, les mini-jeux et les gains."]))

q("controller", 0, 0, I("party_controller"), deps=["board.tile", "dice.default_dice"], rewards=T3(), size=1.5,
  desc=(["Map, stained glass, redstone torch, mushroom stems, a crafter, iron and redstone. The heart of a party."],
        ["Carte, verre teinté, torche de redstone, pieds de champignon, un fabricateur, du fer et de la redstone. Le cœur d'une partie."]))

q("dashboard", 2, 0, C(), deps=["controller"], rewards=T1(), icon="steveparty:party_controller",
  title=("The dashboard", "Le tableau de bord"),
  desc=(["Right-click it: five tabs (Status, Players, Program, Prizes, Settings). The Status tab lists what is missing before the \"Start the party\" button lights up. A \"!\" on a tab shows what to fix."],
        ["Clic droit : cinq onglets (État, Joueurs, Programme, Gains, Réglages). L'onglet État liste ce qui manque avant que « Lancer la partie » s'allume. Un « ! » sur un onglet montre quoi régler."]))

q("bank", 2, 2, C(), deps=["dashboard", "board.inventory"], rewards=T1(), icon="minecraft:chest",
  title=("The prize chest", "Le coffre des gains"),
  desc=(["The controller creates no coins and no stars: it pays mini-game prizes from chests. Put an Inventory Cartridge linked to a chest in the bank slot of the Prizes tab, and fill the chest with the party's coins and stars (Power Stars by default)."],
        ["Le contrôleur ne crée ni pièce ni étoile : il paie les gains des mini-jeux depuis des coffres. Mettez une Cartouche d'inventaire liée à un coffre dans la case banque de l'onglet Gains, et remplissez le coffre des pièces et étoiles de la partie (Super étoiles par défaut)."]))

q("first_party", 4, 0, C(), deps=["dashboard", "pawns.bind"], rewards=MILESTONE(), icon="minecraft:firework_rocket",
  shape="hexagon", size=1.5,
  title=("Let's party!", "Que la fête commence !"),
  desc=(["Pawns bound to start spaces, then \"Start the party\" (or a redstone pulse). Everyone rolls for the order, then each round is one turn per pawn and a mini-game. Stars first, then coins, decide the winner."],
        ["Des pions liés aux cases de départ, puis « Lancer la partie » (ou une impulsion redstone). Chacun lance un dé pour l'ordre, puis chaque manche = un tour par pion et un mini-jeu. Les étoiles, puis les pièces, désignent le gagnant."]))

q("hud", 4, -2, C(), deps=["first_party"], rewards=T0(), optional=True, icon="minecraft:painting",
  title=("Arrange the HUD", "Disposer l'interface"),
  desc=(["During a party, the turn strip, announcements and scoreboard appear on screen. Press M (or /partyhud) to move them, H to hide them."],
        ["Pendant une partie, la bande des tours, les annonces et le tableau des scores s'affichent. Touche M (ou /partyhud) pour les déplacer, H pour les masquer."]))

q("step", 6, -2, I("step_controller"), deps=["controller"], rewards=T2(),
  desc=(["Right-click to pick its mode: next step, restart the step, previous step. A redstone pulse applies it to the nearest party (64 blocks), or to a mini-game when linked to its page."],
        ["Clic droit pour choisir son mode : étape suivante, recommencer l'étape, étape précédente. Une impulsion redstone l'applique à la partie la plus proche (64 blocs), ou à un mini-jeu s'il est lié à sa page."]))

q("bell", 6, 0, I("party_bell"), deps=["controller"], rewards=T2(),
  desc=(["Right-click to choose a moment (party start, turn start, die rolled, mini-game end...): it sends a redstone pulse then, and a comparator reads a value (die result, pawn rank...). In wait mode (Wrench) the party freezes until the bell gets a signal."],
        ["Clic droit pour choisir un moment (début de partie, début de tour, dé lancé, fin de mini-jeu...) : elle envoie alors une impulsion, et un comparateur lit une valeur (résultat du dé, rang du pion...). En mode attente (Clé), la partie se fige jusqu'à ce qu'elle reçoive un signal."]))

q("piggy", 6, 2, I("piggy_bank"), deps=["controller", "board.inventory"], rewards=T2(),
  desc=(["A ceramic pig with an Inventory Cartridge: each redstone pulse gives (or takes) its items to a target: the player of the turn, the mini-game winners, everyone, or the nearest player. All-or-nothing mode can sell a star for coins."],
        ["Un cochon en céramique avec une Cartouche d'inventaire : chaque impulsion donne (ou prend) ses objets à une cible : le joueur du tour, les gagnants du mini-jeu, tout le monde ou le plus proche. En mode « tous les objets », c'est tout ou rien : de quoi vendre une étoile contre des pièces."]))

CARDS = [
    ("turns", "blue", ["Every pawn plays once."], ["Chaque pion joue une fois."]),
    ("minigame", "lime", ["One mini-game from the catalogue."], ["Un mini-jeu du catalogue."]),
    ("event", "orange", ["Rings the Party Bells set to Event; the channel is the stack size."],
     ["Fait sonner les Cloches réglées sur Événement ; le canal est la taille de la pile."]),
    ("sequence_start", "pink", ["The next Repeat card starts again from here."],
     ["La carte Répéter suivante reprend d'ici."]),
    ("repeat", "purple", ["Plays the cards since the last Sequence Start again, as many times as cards in its stack."],
     ["Rejoue les cartes depuis le dernier Début de séquence, autant de fois qu'il y a de cartes dans sa pile."]),
]
for i, (card, stud, en, fr) in enumerate(CARDS):
    q("card_" + card, 0 + i * 1.5, 4, I("party_card_" + card, 4), deps=["dashboard"], rewards=T1(),
      desc=([f"A {EN_COLOR[stud]} plastic stud + paper + redstone: 4 cards. " + en[0],
             "Cards go in the 24 slots of the Program tab and are read left to right."],
            [f"Un plot en plastique {FR_COLOR[stud]} + papier + redstone : 4 cartes. " + fr[0],
             "Les cartes se posent dans les 24 emplacements de l'onglet Programme et se lisent de gauche à droite."]))

q("program", 3, 6, C(), deps=["card_turns", "card_minigame", "card_sequence_start", "card_repeat"], rewards=MILESTONE(),
  icon="steveparty:party_card_repeat", shape="hexagon",
  title=("Write a program", "Écrire un programme"),
  desc=(["Build your own party: for example Sequence Start, Turns, Repeat x3, Sequence Start, Mini-game, Repeat x2. Without cards, the party plays the number of rounds set in Settings."],
        ["Écrivez votre propre partie : par exemple Début de séquence, Tour de jeu, Répéter x3, Début de séquence, Mini-jeu, Répéter x2. Sans carte, la partie joue le nombre de manches réglé dans les Réglages."]))

done("done", 9, 0, ["first_party", "step", "bell", "piggy", "program", "bank"],
     ("Party host", "Maître de cérémonie"),
     (["You can run a whole party, and drive it with redstone."],
      ["Vous savez mener une partie entière, et la piloter à la redstone."]),
     "steveparty:party_bell")


# === Mini-games ================================================================================
chapter("minigames", "board_game", "steveparty:mini_game_page",
        ("Mini-games", "Mini-jeux"),
        (["Pages, pipes, podiums and arenas: design your own mini-games."],
         ["Pages, tuyaux, podiums et arènes : créez vos propres mini-jeux."]))

q("page", 0, 0, I("mini_game_page", 4), deps=["board.cartridge"], rewards=T2(), size=1.5,
  desc=(["A Cartridge surrounded by 4 paper: 4 Mini-Game Pages. A page IS a mini-game: its title, rules, image, formats, pipes, results and zone."],
        ["Une Cartouche entourée de 4 papiers : 4 Pages de mini-jeu. Une page EST un mini-jeu : son titre, ses règles, son image, ses formats, ses tuyaux, ses résultats et sa zone."]))

q("write_page", 2, 0, C(), deps=["page"], rewards=T1(), icon="minecraft:writable_book",
  title=("Write the page", "Écrire la page"),
  desc=(["Right-click in the air with the page: the editor opens. Give it a title, a description (bold, italic, colours, 400 characters) and an image (PNG or JPEG dropped on the window). The image shows on the big card when the mini-game is drawn."],
        ["Clic droit dans le vide avec la page : l'éditeur s'ouvre. Donnez-lui un titre, une description (gras, italique, couleurs, 400 caractères) et une image (PNG ou JPEG déposé sur la fenêtre). L'image s'affiche sur la grande carte quand le mini-jeu est tiré."]))

q("formats", 4, -0.75, C(), deps=["write_page"], rewards=T1(), icon="minecraft:white_banner",
  title=("Formats", "Formats"),
  desc=(["Formats tab: team games (2 to 4 sides, \"1 vs all\", \"2 vs 2\"...), free-for-all or all together. In a party only a page whose format fits the teams on the board can be drawn. Team A is the players on positive spaces, team B those on negative ones."],
        ["Onglet Formats : jeux en équipes (2 à 4 camps, « 1 contre tous », « 2 contre 2 »...), chacun pour soi ou tous ensemble. En partie, seule une page dont un format convient aux équipes du plateau peut être tirée. L'équipe A, ce sont les joueurs sur une case positive, l'équipe B ceux sur une case négative."]))

q("zone", 4, 0.75, C(), deps=["write_page"], rewards=T1(), icon="minecraft:structure_void",
  title=("Draw the zone", "Tracer la zone"),
  desc=(["\"Draw the zone\" in the editor, then click two corners (128 blocks per side at most). With the Restore option, each round plays apart in the zone with an empty round inventory, and the arena is put back as built at the end."],
        ["« Tracer la zone » dans l'éditeur, puis cliquez deux coins (128 blocs par côté au plus). Avec l'option Restaurer, chaque manche se joue à part dans la zone, avec un inventaire de manche vide, et l'arène est remise comme construite à la fin."]))

q("linked_copy", 2, 1.5, C(), deps=["write_page"], rewards=T0(), optional=True, icon="minecraft:paper",
  title=("Linked copies", "Copies liées"),
  desc=(["Page + paper at the crafting table (or the Copy button) makes a linked copy: editing one edits them all. Handy to link pipes and podiums far apart."],
        ["Page + papier à l'établi (ou le bouton Copier) donnent une copie liée : modifier l'une modifie toutes les autres. Pratique pour lier tuyaux et podiums éloignés."]))

q("catalogue", -2, 0, I("mini_games_catalogue"), deps=["page", "party.controller"], rewards=T2(),
  desc=(["Book and quill + map + clock + the \"otherside\" disc, shapeless. Holds 91 pages. Put it in the Party Controller: the roulette draws its mini-games."],
        ["Livre et plume + carte + horloge + disque « otherside », sans forme. Contient 91 pages. Posez-le dans le Contrôleur de fête : la roulette y tire les mini-jeux."]))

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
    q("role_" + pipe, -2 + i * 1.5, 4, I(pipe, 6), deps=["page"], rewards=T1(),
      title=(f"{item_name('steveparty:' + pipe, 'en')}: {en_role}", f"{item_name('steveparty:' + pipe, 'fr')} : {fr_role}"),
      desc=([f"6 plastic blocks of the colour in two columns: 6 pipes. Linked to a page, a pipe of this colour has the role \"{en_role}\"."
             + (" Purple and orange pipes are teams C and D." if pipe == "red_pipe" else "")],
            [f"6 blocs de plastique de la couleur en deux colonnes : 6 tuyaux. Lié à une page, un tuyau de cette couleur a le rôle « {fr_role} »."
             + (" Les tuyaux violets et orange sont les équipes C et D." if pipe == "red_pipe" else "")]))

q("link_pipes", 1.75, 6, C(), deps=["role_green_pipe", "role_blue_pipe", "role_red_pipe"], dep_req="one_completed",
  rewards=T2(), icon="steveparty:green_pipe",
  title=("Link the pipes", "Lier les tuyaux"),
  desc=(["Page in hand, click a pipe mouth to link it (click again to unlink); linked pipes glow while you hold the page. When the mini-game starts, each player comes out of a pipe of their role."],
        ["Page en main, cliquez l'embouchure d'un tuyau pour le lier (re-clic pour le délier) ; les tuyaux liés brillent tant que vous tenez la page. Au départ du mini-jeu, chaque joueur sort d'un tuyau de son rôle."]))

# Podiums
q("podium", 8, 1.5, I("podium"), deps=["page"], rewards=T2(),
  desc=(["Gold, a heavy pressure plate, redstone and quartz blocks. Podiums record who won: the highest column is first place. Sneak on one or right-click it to take it. Link podiums to the page by clicking them with it."],
        ["De l'or, une plaque de pression lourde, de la redstone et des blocs de quartz. Les podiums retiennent qui a gagné : la colonne la plus haute est la 1re place. Accroupissez-vous dessus ou clic droit pour le prendre. Liez-les à la page en cliquant dessus avec elle."]))

for i, (metal, en_m, fr_m) in enumerate([("gold", "a gold ingot", "un lingot d'or"),
                                         ("silver", "an iron ingot", "un lingot de fer"),
                                         ("bronze", "a copper ingot", "un lingot de cuivre")]):
    q(metal + "_podium", 10, 0.75 + i * 1.5, I(metal + "_podium"), deps=["podium"], rewards=T1(),
      desc=([f"A Podium + {en_m}, shapeless. Only a look: the height of the column decides the place."],
            [f"Un Podium + {fr_m}, sans forme. Seulement une apparence : c'est la hauteur de la colonne qui donne la place."]))

q("results", 8, 3, C(), deps=["podium"], rewards=T1(), icon="steveparty:gold_podium",
  title=("Results", "Résultats"),
  desc=(["The Results tab of the page lists what ends the mini-game: linked podiums, goal pole bases (counters) and Step Controllers. A pulse on a podium, set with the Wrench, can register the nearest player or reset the group."],
        ["L'onglet Résultats de la page liste ce qui termine le mini-jeu : podiums liés, socles de mât d'arrivée (compteurs) et Contrôleurs de pas. Une impulsion sur un podium, réglé à la Clé, peut inscrire le joueur le plus proche ou vider le groupe."]))

q("mg_controller", 2, -2.5, I("mini_game_controller"), deps=["page"], rewards=T2(),
  desc=(["Iron, glass, mushroom stems, paper and redstone. Placed in the arena, it holds the mini-game's page. Its lamp is red (idle), orange (trial round) or green (playing). A comparator reads its state."],
        ["Du fer, du verre, des pieds de champignon, du papier et de la redstone. Posé dans l'arène, il reçoit la page du mini-jeu. Sa lampe est rouge (repos), orange (manche d'essai) ou verte (en jeu). Un comparateur lit son état."]))

q("test_run", 4, -2.5, C(), deps=["mg_controller", "link_pipes"], rewards=MILESTONE(), icon="steveparty:mini_game_controller",
  shape="hexagon",
  title=("Test run", "Partie d'essai"),
  desc=(["Press \"Play\" on the Mini-Game Controller (or \"Test\" in the page editor): everyone within 5 blocks of a linked pipe plays, without a party. Results show what each place would earn, nothing is paid, and everyone comes back."],
        ["Appuyez sur « Jouer » sur le Contrôleur de mini-jeu (ou « Tester » dans l'éditeur de page) : tous les joueurs à 5 blocs d'un tuyau lié jouent, sans partie. Les résultats montrent ce que vaudrait chaque place, rien n'est payé, et tout le monde revient."]))

q("trial", 6, -2.5, C(), deps=["test_run", "party.first_party"], rewards=T2(), icon="minecraft:lime_dye",
  title=("Ready? Press Y", "Prêt ? Touche Y"),
  desc=(["In a party, a mini-game whose page is in a controller starts with a trial round, for nothing. Each player presses Y (or the Ready button) when ready; when everyone is, the real round starts and pays prizes."],
        ["En partie, un mini-jeu dont la page est dans un contrôleur commence par une manche d'essai, pour du beurre. Chaque joueur appuie sur Y (ou le bouton Prêt) quand il est prêt ; quand tout le monde l'est, la vraie manche commence et paie les gains."]))

# Mini-game pipes
q("copper_pipe", -2, 7.5, I("copper_minigame_pipe"), deps=["link_pipes"], rewards=T2(),
  desc=(["6 copper ingots around a paper. Click it with a page: it becomes the entrance to that mini-game outside a party, within 100 blocks. Coloured pipes linked to it send each player to their role."],
        ["6 lingots de cuivre autour d'un papier. Cliquez-le avec une page : il devient l'entrée de ce mini-jeu hors partie, à 100 blocs. Les tuyaux de couleur reliés à lui envoient chaque joueur vers son rôle."]))

q("iron_pipe", 0, 7.5, I("iron_minigame_pipe"), deps=["copper_pipe", "mulas.power_star"], rewards=T3(),
  desc=(["6 iron ingots, a Power Star and the copper mini-game pipe. Reaches the whole dimension."],
        ["6 lingots de fer, une Super étoile et le tuyau de mini-jeu en cuivre. Atteint toute la dimension."]))

q("golden_pipe", 2, 7.5, I("golden_minigame_pipe"), deps=["iron_pipe"], rewards=[XP(100), COINS(5)],
  desc=(["4 gold ingots, an eye of ender, 2 Power Stars and the iron mini-game pipe. Reaches every dimension."],
        ["4 lingots d'or, un œil de l'Ender, 2 Super étoiles et le tuyau de mini-jeu en fer. Atteint toutes les dimensions."]))

done("done", 12.5, 0, ["test_run", "trial", "catalogue", "results", "zone", "formats", "gold_podium",
                     "silver_podium", "bronze_podium", "copper_pipe"],
     ("Game designer", "Créateur de jeux"),
     (["Your mini-games are written, linked and tested. Let the roulette spin!"],
      ["Vos mini-jeux sont écrits, liés et testés. Que la roulette tourne !"]),
     "steveparty:mini_games_catalogue")


# === Finish pole ===============================================================================
chapter("finish", "board_game", "steveparty:goal_pole",
        ("Finish Pole", "Mât d'arrivée"),
        (["Jump to the top, score, and watch the flag come down."],
         ["Sautez au sommet, marquez, et regardez le drapeau descendre."]))

q("base", 0, 0, I("goal_pole_base"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["Bricks, a sculk sensor, iron and redstone. The base counts the points of the poles stacked on it. Set it up with the Wrench."],
        ["Des briques, un capteur sculk, du fer et de la redstone. Le socle compte les points des mâts empilés dessus. Il se règle à la Clé."]))

q("pole", 2, 0, I("goal_pole"), deps=["base"], rewards=T1(),
  desc=(["A gold ingot over two iron bars, in a column. Stack as many segments as you like on the base."],
        ["Un lingot d'or sur deux barreaux de fer, en colonne. Empilez autant de segments que vous voulez sur le socle."]))

q("flag", 4, -1.5, I("flag"), deps=["pole"], rewards=T1(),
  desc=(["Three wool: the colour of the wool makes the flag's (mixed like leather armour). Right-click a segment to hang it; it slides down when the goal is reached. Dye it again in the grid."],
        ["Trois laines : la couleur de la laine fait celle du drapeau (mélangée comme le cuir). Clic droit sur un segment pour l'accrocher ; il descend quand le but est atteint. Reteignez-le dans la grille."]))

q("one_up", 4, 0, C(), deps=["pole"], rewards=T2(), icon="minecraft:golden_apple",
  title=("1up!", "1up !"),
  desc=(["Land on top of a pole: \"1up\", a golden heart for 10 s, no fall damage, and you stand upside down. Each landing scores a point for the base."],
        ["Atterrissez au sommet d'un mât : « 1up », un cœur doré pendant 10 s, aucun dégât de chute, et vous voilà tête en bas. Chaque arrivée marque un point pour le socle."]))

q("goal", 4, 1.5, C(), deps=["pole", "board.wrench"], rewards=T1(), icon="minecraft:target",
  title=("Set a goal", "Régler un but"),
  desc=(["Wrench on a segment: the pole lights up when the total is at least (or exactly, or less than) a value. \"Goal: per player\" gives each player who reaches it the highest free podium place."],
        ["Clé sur un segment : le mât s'allume quand le total atteint (ou égale, ou reste sous) une valeur. « But : par joueur » donne à chaque joueur qui l'atteint la plus haute place libre des podiums."]))

q("criterion", 6, 1.5, C(), deps=["goal"], rewards=T1(), optional=True, icon="minecraft:redstone",
  title=("Count anything", "Tout compter"),
  desc=(["The base can count a scoreboard criterion instead of landings: jumps, deaths, kills... For example 10 jumps, goal per player, and podiums next to the base."],
        ["Le socle peut compter un critère de scoreboard au lieu des arrivées : sauts, morts, éliminations... Par exemple 10 sauts, but par joueur, et des podiums contre le socle."]))

q("shoes", 2, 2.5, I("triple_jump_shoes"), deps=["base"], rewards=T2(),
  desc=(["Leather boots + rabbit's foot + 2 slime balls, shapeless. Chain jumps within 1.5 s: the second is 1.5 times higher, the third twice as high. Made for poles."],
        ["Bottes en cuir + patte de lapin + 2 boules de slime, sans forme. Sauts enchaînés à moins de 1,5 s : le 2e est 1,5 fois plus haut, le 3e deux fois. Parfaits pour les mâts."]))

done("done", 8, 0, ["one_up", "flag", "goal", "shoes"],
     ("Goal!", "But !"),
     (["Your finish line is ready: jump, score, win."],
      ["Votre ligne d'arrivée est prête : sautez, marquez, gagnez."]),
     "steveparty:flag")


# === Shops =====================================================================================
chapter("shops", "commerce", "steveparty:trading_stall",
        ("Shops", "Boutiques"),
        (["A villager hidden in a box sells the offers of your stalls."],
         ["Un villageois caché dans un carton vend les offres de vos étals."]))

q("villager_block", 0, 0, I("villager_block"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["A downward piston extending onto an adult villager's head squashes it into a block that keeps the villager inside, alive, with about 70 reactions. Break it by hand (it begs) to pick it up; a sticky piston lets it out."],
        ["Un piston tourné vers le bas qui s'étend sur la tête d'un villageois adulte l'écrase en un bloc qui garde le villageois dedans, vivant, avec environ 70 réactions. Cassez-le à la main (il supplie) pour le ramasser ; un piston collant le fait ressortir."]))

q("trader", 2, 0, O("steveparty:boxed_trader"), deps=["villager_block"], rewards=T3(), icon="steveparty:boxed_trader_spawn_egg",
  title=("The fall", "La chute"),
  desc=(["Place the Villager Block on the block to imitate, then fall on it from high enough (12 blocks on dirt or hay, 16 on stone, 30 on iron): a Boxed Trader appears, disguised as that block. Look at one to complete this quest."],
        ["Posez le Bloc de villageois sur le bloc à imiter, puis tombez dessus d'assez haut (12 blocs sur terre ou foin, 16 sur pierre, 30 sur fer) : un Marchand en carton apparaît, déguisé en ce bloc. Regardez-en un pour valider la quête."]))

q("stall", 4, -1.5, I("trading_stall"), deps=["trader"], rewards=T1(),
  desc=(["Two carpets over two barrels; the carpets give the awning's colours. Each column is an offer (9 max): the goods on top, prices 1 and 2 below. The goods are only a model: the sale takes the same item from the linked chests."],
        ["Deux tapis au-dessus de deux tonneaux ; les tapis donnent les couleurs de l'auvent. Chaque colonne est une offre (9 max) : la marchandise en haut, les prix 1 et 2 dessous. La marchandise n'est qu'un modèle : la vente prend le même objet dans les coffres liés."]))

q("register", 4, 0, I("cash_register"), deps=["trader"], rewards=T1(),
  desc=(["Stone buttons, gold, a chest and iron. Payments go into the linked registers; each sale sends a redstone pulse. Mine it with an iron pickaxe."],
        ["Des boutons en pierre, de l'or, un coffre et du fer. Les paiements arrivent dans les caisses liées ; chaque vente envoie une impulsion redstone. Se récupère avec une pioche en fer."]))

q("key", 4, 1.5, I("shopkeeper_key"), deps=["trader"], rewards=T1(),
  desc=(["A star fragment (any colour), a gold ingot and a gold nugget, in a column. Right-click the trader to link it (the first to do so owns it), then each block: stall, stock chests, registers."],
        ["Un fragment d'étoile (n'importe quelle couleur), un lingot d'or et une pépite d'or, en colonne. Clic droit sur le marchand pour la lier (le premier devient propriétaire), puis sur chaque bloc : étal, coffres de stock, caisses."]))

q("open_shop", 6, 0, C(), deps=["stall", "register", "key"], rewards=MILESTONE(), icon="minecraft:emerald",
  shape="hexagon", size=1.25,
  title=("Open for business", "Ouvert !"),
  desc=(["Trader, stall, a stock chest and a register, all linked with the key; an offer on the stall and stock in the chest. Customers right-click the trader and buy. The owner's shop is protected."],
        ["Marchand, étal, un coffre de stock et une caisse, tous liés avec la clé ; une offre sur l'étal et du stock dans le coffre. Les clients font clic droit sur le marchand et achètent. La boutique du propriétaire est protégée."]))

q("shop_stop", 8, 0, C(), deps=["open_shop", "board.shop"], rewards=T2(), icon="steveparty:shop_cartridge",
  title=("Shop stop", "Halte boutique"),
  desc=(["Put a Shop Cartridge on your board within 32 blocks of the trader: a pawn that stops there opens the shop for its player, wherever they are. The turn waits for the purchases."],
        ["Posez une Cartouche Boutique sur votre plateau à moins de 32 blocs du marchand : un pion qui s'y arrête ouvre la boutique à son joueur, où qu'il soit. Le tour attend la fin des achats."]))

q("bandana", 2, 2, I("bandana"), deps=["trader"], rewards=T1(),
  desc=(["Shears on an open trader that wears its bandana: it falls, and the trader hides for 20 s. Wear it on your head: traders ignore you. Give one back to a bald trader to change its colour."],
        ["Des cisailles sur un marchand ouvert qui porte son bandana : il tombe, et le marchand se cache 20 s. Portez-le sur la tête : les marchands vous ignorent. Rendez-en un à un marchand chauve pour changer sa couleur."]))

q("costume", 2, 3.5, I("box_costume"), deps=["bandana"], rewards=T2(),
  desc=(["Shears again on a trader that has already lost its bandana: you steal the look of its box. Wear it as a chestplate; sneak to hide inside: stand still 2 s and you become a real block."],
        ["Encore des cisailles sur un marchand qui a déjà perdu son bandana : vous lui volez l'apparence de sa boîte. Portez-la comme un plastron ; accroupi, vous vous cachez dedans : immobile 2 s, vous devenez un vrai bloc."]))

done("done", 10, 0, ["shop_stop", "open_shop", "costume"],
     ("Shopkeeper", "Commerçant"),
     (["Your shop is open, on and off the board."],
      ["Votre boutique est ouverte, sur le plateau comme ailleurs."]),
     "steveparty:shopkeeper_key")


# === Mulas and stars ===========================================================================
chapter("mulas", "stars", "steveparty:power_star",
        ("Mulas and Stars", "Mulas et étoiles"),
        (["Little flying star creatures, and the 16 star fragments they leave behind."],
         ["De petites créatures d'étoile volantes, et les 16 fragments d'étoile qu'elles laissent."]))

q("find", 0, 0, O("steveparty:mula"), deps=["welcome.done"], rewards=T3(), icon="steveparty:mula_spawn_egg", size=1.5,
  title=("A Mula!", "Une Mula !"),
  desc=(["Mulas fall from the sky on shooting-star nights (Ephemeride), mostly at full moon: follow the direction of the first wave of stars, 150 to 600 blocks away. Look at a Mula to complete this quest."],
        ["Les Mulas tombent du ciel les nuits d'étoiles filantes (Éphéméride), surtout à la pleine lune : suivez la direction de la première vague d'étoiles, à 150 à 600 blocs. Regardez une Mula pour valider la quête."]))

q("ephemeride", -2.5, -2, C(), deps=["welcome.done"], rewards=T2(), icon="minecraft:clock",
  title=("Shooting-star night", "Nuit d'étoiles filantes"),
  desc=(["At dusk, a full moon has a 25% chance of an Ephemeride: 6 waves of low shooting stars over each player, then a group of 3 to 5 Mulas lands where they went. A Dice Forge at full power guarantees it every full moon."],
        ["Au crépuscule, une pleine lune a 25 % de chances d'apporter une Éphéméride : 6 vagues d'étoiles filantes basses au-dessus de chaque joueur, puis un groupe de 3 à 5 Mulas descend là où elles sont allées. Une Forge à dés au niveau maximal la garantit à chaque pleine lune."]))

q("telescope", -2.5, 0, I("telescope"), deps=["ephemeride"], rewards=T2(),
  desc=(["A spyglass on a copper ingot, over 3 sticks. At night, under a clear sky, look into it: the scroll wheel replays past shooting-star nights within 1000 blocks. Keep a star in the sights to light a guide star above the Mulas."],
        ["Une longue-vue sur un lingot de cuivre, au-dessus de 3 bâtons. La nuit, ciel dégagé, regardez dedans : la molette rejoue les nuits d'étoiles filantes passées, à 1000 blocs. Gardez une étoile dans le viseur pour allumer une étoile guide au-dessus des Mulas."]))

q("feed", 2, 0, C(), deps=["find"], rewards=T1(), icon="minecraft:cooked_cod",
  title=("Snack time", "À table !"),
  desc=(["A Mula eats only edible food of its colour: blue = cod, red = apples and red meat, green = melon, yellow = golden food and bread, purple = chorus and beetroot, black = cookies, stews and steak. The tooltip tells \"Mula food\"."],
        ["Une Mula ne mange que la nourriture de sa couleur : bleu = morue, rouge = pommes et viande rouge, vert = pastèque, jaune = nourriture dorée et pain, violet = chorus et betterave, noir = cookies, ragoûts et steak. L'infobulle indique « Nourriture de Mula »."]))

q("burst", 4, 0, C(), deps=["feed"], rewards=T2(), icon="minecraft:firework_star", shape="hexagon",
  title=("Pop!", "Pop !"),
  desc=(["Fed to 40, the Mula grows three times bigger and bursts: 64 star fragments of its colour (a black one: 1). It flies away as a shooting star and is reborn 100 to 400 blocks away."],
        ["Nourrie jusqu'à 40, la Mula grossit trois fois et éclate : 64 fragments d'étoile de sa couleur (une noire : 1). Elle part en étoile filante et renaît 100 à 400 blocs plus loin."]))

q("tame", 2, -2, C(), deps=["feed"], rewards=T2(), icon="steveparty:yellow_star_fragment",
  title=("Best friend", "Meilleure amie"),
  desc=(["Right-click a wild Mula with a star fragment of its own colour: 1 chance in 3. Tamed, it floats above you like a balloon and orbits your head when you stand still."],
        ["Clic droit sur une Mula sauvage avec un fragment d'étoile de sa couleur : 1 chance sur 3. Apprivoisée, elle flotte au-dessus de vous comme un ballon et tourne autour de votre tête quand vous restez immobile."]))

q("balloon", 4, -2, C(), deps=["tame"], rewards=T1(), optional=True, icon="minecraft:lead",
  title=("Up, up and away", "Envol en laisse"),
  desc=(["At night, Mulas on a lead can lift you up like balloons: one big Mula, two half-full ones or three of any size. You land softly at dawn."],
        ["La nuit, des Mulas tenues en laisse peuvent vous emporter comme des ballons : une grosse, deux à moitié pleines ou trois de n'importe quelle taille. Vous redescendez en douceur au lever du jour."]))

NATURAL = ["blue", "red", "yellow", "green", "purple", "black"]
for i, c in enumerate(NATURAL):
    extra_en = " (only one, and black Mulas are rare)." if c == "black" else "."
    extra_fr = " (un seul, et les Mulas noires sont rares)." if c == "black" else "."
    q("frag_" + c, 6, -3.75 + i * 1.5, I(f"{c}_star_fragment"), deps=["burst"],
      rewards=[XP(30), COINS(2)] if c == "black" else T1(),
      desc=([f"Dropped by a {EN_COLOR[c]} Mula when it bursts{extra_en}"],
            [f"Lâché par une Mula {FR_COLOR[c]} quand elle éclate{extra_fr}"]))

MIXES = [
    # colour, deps, en mix, fr mix, x, y
    ("white", ["red", "blue", "yellow"], "red + blue + yellow", "rouge + bleu + jaune", 8, -3.75),
    ("orange", ["red", "yellow"], "red + yellow", "rouge + jaune", 8, -2.25),
    ("cyan", ["blue", "green"], "blue + green", "bleu + vert", 8, -0.75),
    ("brown", ["red", "green"], "red + green (or orange + black)", "rouge + vert (ou orange + noir)", 8, 0.75),
    ("pink", ["red", "white"], "red + white", "rouge + blanc", 10, -3.75),
    ("light_blue", ["blue", "white"], "blue + white", "bleu + blanc", 10, -2.25),
    ("lime", ["green", "white"], "green + white", "vert + blanc", 10, -0.75),
    ("gray", ["black", "white"], "black + white", "noir + blanc", 10, 0.75),
    ("magenta", ["purple", "pink"], "purple + pink", "violet + rose", 12, -3.75),
    ("light_gray", ["gray", "white"], "gray + white", "gris + blanc", 12, 0.75),
]
for c, deps, en_mix, fr_mix, x, y in MIXES:
    q("frag_" + c, x, y, I(f"{c}_star_fragment"), deps=["frag_" + d for d in deps], rewards=T1(),
      desc=([f"Mix star fragments like dyes: {en_mix}. You get back as many fragments as you put in."],
            [f"Mélangez les fragments comme des colorants : {fr_mix}. On récupère autant de fragments qu'on en a mis."]))

q("frag_block", 6, 5.25, I("blue_star_fragments_block"), deps=["frag_blue"], rewards=T1(),
  desc=(["9 fragments of one colour: a glowing, glass-like block of animated plasma (16 colours, 12 variants each). The black one is a starry night. It attracts curious Mulas."],
        ["9 fragments d'une couleur : un bloc lumineux, translucide, de plasma animé (16 couleurs, 12 variantes chacune). Le noir est une nuit étoilée. Il attire les Mulas curieuses."]))

q("power_star", 12, 3, I("power_star"), deps=["frag_blue", "frag_green", "frag_yellow", "frag_red", "frag_purple"],
  rewards=[XP(100), COINS(5)], shape="hexagon", size=1.5,
  desc=(["One fragment each of blue, green, yellow, red and purple (or 5 black fragments), shapeless. Always shining. It is the default Star of a party, and goes into the Dice Forge and the iron and golden mini-game pipes."],
        ["Un fragment de chaque : bleu, vert, jaune, rouge et violet (ou 5 fragments noirs), sans forme. Toujours brillante. C'est l'Étoile par défaut d'une partie, et elle entre dans la Forge à dés et les tuyaux de mini-jeu en fer et en or."]))

q("luck", 8, 3, C(), deps=["burst"], rewards=T1(), optional=True, icon="minecraft:potion",
  title=("Potion of Luck", "Potion de chance"),
  desc=(["Brewing stand: awkward potion + any star fragment gives a Potion of Luck (Luck I, 5:00), the only survival recipe for it."],
        ["Alambic : potion étrange + n'importe quel fragment d'étoile donne une Potion de chance (Chance I, 5:00), sa seule recette en survie."]))

done("done", 14.5, 0, ["power_star", "frag_magenta", "frag_light_gray", "frag_brown", "frag_lime", "frag_light_blue",
                       "frag_cyan", "frag_orange", "frag_black", "telescope", "tame"],
     ("Star collector", "Collectionneur d'étoiles"),
     (["All 16 colours of star fragments, and a Power Star of your own."],
      ["Les 16 couleurs de fragments d'étoile, et votre propre Super étoile."]),
     "steveparty:white_star_fragments_block")


# === Dice Forge ================================================================================
chapter("forge", "stars", "steveparty:dice_forge",
        ("Dice Forge", "Forge à dés"),
        (["Engrave custom dice, and give the Mulas a home."],
         ["Gravez des dés sur mesure, et offrez une maison aux Mulas."]))

q("core", 0, 0, I("gravity_core"), deps=["mulas.frag_black"], rewards=T3(),
  desc=(["Crying obsidian, 4 black star fragments and a heavy core. Placed, it pulls everything within 8 blocks into orbit (no fall damage inside; full netherite armour resists). It also powers the Dice Forge."],
        ["Obsidienne pleureuse, 4 fragments d'étoile noirs et un noyau lourd. Posé, il attire tout à 8 blocs en orbite (pas de dégâts de chute dans le champ ; une armure complète en netherite résiste). Il alimente aussi la Forge à dés."]))

q("forge", 0, -2.5, I("dice_forge"), deps=["mulas.power_star"], rewards=T3(), size=1.5,
  desc=(["A Power Star on top, obsidian, a blast furnace and a netherite ingot. It engraves dice with the faces and weights you choose, in a loop, by hand, hoppers or redstone."],
        ["Une Super étoile en haut, de l'obsidienne, un haut fourneau et un lingot de netherite. Elle grave des dés avec les faces et les poids que vous choisissez, en boucle, à la main, par entonnoirs ou à la redstone."]))

q("ignite", 2, -1.25, C(), deps=["core", "forge"], rewards=T2(), icon="steveparty:gravity_core",
  title=("Ignite the forge", "Allumer la forge"),
  desc=(["Right-click the forge with the Gravity Core: it sinks in, then rises and floats above the forge. Sneak + right-click empty-handed takes it back. Hitting the floating core makes it explode (and it is lost)."],
        ["Clic droit sur la forge avec le Noyau de gravité : il s'enfonce, puis s'élève et flotte au-dessus. Accroupi + clic droit à mains vides pour le reprendre. Frapper le noyau en l'air le fait exploser (et il est perdu)."]))

q("forged", 4, -1.25, C(), deps=["ignite", "dice.blank"], rewards=MILESTONE(), icon="steveparty:default_dice",
  shape="hexagon", size=1.25,
  title=("Your own die", "Votre propre dé"),
  desc=(["Fill the ring with 1 to 12 faces (the stack size is the weight), the 4 fragment slots with 4 different colours (black is never used up), and blank faces. Click the core: FORGE! One die every 5 s."],
        ["Remplissez l'anneau de 1 à 12 faces (la taille de la pile est le poids), les 4 emplacements de fragments de 4 couleurs différentes (le noir n'est jamais consommé), et des faces vierges. Cliquez le noyau : FORGER ! Un dé toutes les 5 s."]))

q("automate", 6, -2.5, C(), deps=["forged"], rewards=T1(), optional=True, icon="minecraft:hopper",
  title=("Dice factory", "Usine à dés"),
  desc=(["Hoppers on top and sides fill the forge, a hopper below takes the dice out. Powered by redstone, it restarts on its own once restocked. A comparator reads the output."],
        ["Des entonnoirs dessus et sur les côtés remplissent la forge, un entonnoir dessous sort les dés. Alimentée en redstone, elle repart seule une fois réapprovisionnée. Un comparateur lit la sortie."]))

q("max_level", 4, 1, C(), deps=["ignite"], rewards=T2(), icon="steveparty:black_star_fragment",
  title=("Highest orbit", "Orbite maximale"),
  desc=(["The core climbs with the fragments in the forge: 256 fragments (4 full stacks, or 4 black ones, each black counts as 64) lift it 16 blocks up. A forge at max level guarantees an Ephemeride every full moon."],
        ["Le noyau monte avec les fragments présents : 256 fragments (4 piles pleines, ou 4 noirs, chaque noir compte pour 64) le hissent à 16 blocs. Une forge au niveau maximal garantit une Éphéméride à chaque pleine lune."]))

q("dance", 6, 1, C(), deps=["ignite", "mulas.find"], rewards=T2(), icon="steveparty:mula_spawn_egg",
  title=("Mula dance hall", "Le bal des Mulas"),
  desc=(["Any Mula within 16 blocks of a lit forge moves in and dances around the core: 10 dances of 26 s each, in rings of up to 12. A full Mula that bursts at home lands back 2 to 4 blocks from the forge."],
        ["Toute Mula à moins de 16 blocs d'une forge allumée s'y installe et danse autour du noyau : 10 danses de 26 s, en anneaux de 12 au plus. Une Mula pleine qui éclate à la maison retombe à 2 à 4 blocs de la forge."]))

q("merry", 2, 1.25, C(), deps=["core"], rewards=T0(), optional=True, icon="minecraft:saddle",
  title=("Merry-go-round", "Manège"),
  desc=(["A placed Gravity Core never climbs and cannot explode: a fixed, safe attraction. Chicken carousel, item collector..."],
        ["Un Noyau de gravité posé ne monte pas et ne peut pas exploser : une attraction fixe et sûre. Manège à poules, ramasse-objets..."]))

done("done", 8, -0.5, ["forged", "max_level", "dance"],
     ("Forgemaster", "Maître forgeron"),
     (["Custom dice, a core at full height and Mulas dancing around it."],
      ["Des dés sur mesure, un noyau au plus haut et des Mulas qui dansent autour."]),
     "steveparty:dice_forge")


# === Plastic ===================================================================================
chapter("plastic", "decoration", "steveparty:red_plastic_block",
        ("Plastic", "Plastique"),
        (["Toy-like blocks in 16 colours that float."],
         ["Des blocs façon jouet en 16 couleurs, qui flottent."]))

q("block", 0, 0, I("red_plastic_block", 4), deps=["welcome.pellets"], rewards=[XP(15), IT("plastic_pellets", 4)], size=1.25,
  desc=(["4 plastic pellets + 1 dye + 1 amethyst shard, shapeless: 4 blocks of the dye's colour. 8 blocks around a dye recolour them. Neighbours of the same colour merge into one piece (up to 4x2x4)."],
        ["4 granulés de plastique + 1 colorant + 1 éclat d'améthyste, sans forme : 4 blocs de la couleur du colorant. 8 blocs autour d'un colorant les reteignent. Les voisins de même couleur fusionnent en une seule pièce (jusqu'à 4x2x4)."]))

q("stud", 2, -2, I("blue_plastic_stud", 4), deps=["block"], rewards=T1(),
  desc=(["One plastic block gives 4 studs of its colour (4 studs in a square give the block back). A small piece on floor, wall or ceiling that needs no support."],
        ["Un bloc de plastique donne 4 plots de sa couleur (4 plots en carré redonnent le bloc). Une petite pièce au sol, au mur ou au plafond, sans support."]))

q("slab", 2, -0.5, I("yellow_plastic_slab", 6), deps=["block"], rewards=T1(),
  desc=(["3 blocks in a row: 6 slabs (or the stonecutter). Slabs, stairs and walls do not float and cut bubble columns."],
        ["3 blocs en ligne : 6 dalles (ou le tailleur de pierre). Dalles, escaliers et murets ne flottent pas et coupent les colonnes de bulles."]))

q("stairs", 2, 1, I("green_plastic_stairs", 4), deps=["block"], rewards=T1(),
  desc=(["6 blocks in steps: 4 stairs. Aligned shapes of the same colour connect their textures too."],
        ["6 blocs en escalier : 4 escaliers. Les formes alignées de même couleur raccordent aussi leurs textures."]))

q("wall", 2, 2.5, I("purple_plastic_wall", 6), deps=["block"], rewards=T1(),
  desc=(["Two rows of 3 blocks: 6 walls."],
        ["Deux lignes de 3 blocs : 6 murets."]))

q("stick", 4, -2, I("plastic_stick", 4), deps=["block"], rewards=T1(),
  desc=(["Two plastic blocks (any colour) on top of each other: 4 sticks. The plastic fences are made of them."],
        ["Deux blocs de plastique (n'importe quelle couleur) l'un sur l'autre : 4 bâtons. Les barrières en plastique en sont faites."]))

q("fence", 6, -2, I("orange_plastic_fence", 3), deps=["stick"], rewards=T1(),
  desc=(["4 blocks of a colour and 2 plastic sticks: 3 fences. They connect to each other and carry road signs."],
        ["4 blocs d'une couleur et 2 bâtons en plastique : 3 barrières. Elles se relient entre elles et portent les panneaux routiers."]))

q("float", 4, 0.25, C(), deps=["block"], rewards=T2(), icon="minecraft:soul_sand",
  title=("It floats!", "Ça flotte !"),
  desc=(["A plastic block in still water rises to the surface, carrying whatever stands on it. Over soul sand, a bubble column sends it up fast (an elevator); over magma, down. A chain anchors it."],
        ["Un bloc de plastique dans l'eau calme remonte à la surface, en emportant ce qui est dessus. Au-dessus du sable des âmes, une colonne de bulles le fait monter vite (un ascenseur) ; au-dessus du magma, descendre. Une chaîne l'ancre."]))

q("break", 4, 2.5, C(), deps=["block", "board.wrench"], rewards=T0(), optional=True, icon="steveparty:wrench",
  title=("Quick clean-up", "Démontage express"),
  desc=(["The Wrench breaks any plastic block in one hit, even under water. Shears are fast too."],
        ["La Clé casse tout bloc de plastique d'un coup, même sous l'eau. Les cisailles sont rapides aussi."]))

q("hop_switch", 6, 0.25, I("hop_switch"), deps=["block", "board.cartridge"], rewards=T2(),
  desc=(["Blue concrete, a stone pressure plate, a clock, iron and redstone. Give it a Cartridge whose destinations are blocks: jump on it and they vanish, appear or toggle for a set time (clock to change it). Plastic is switchable by default."],
        ["Béton bleu, plaque de pression en pierre, horloge, fer et redstone. Donnez-lui une Cartouche dont les destinations sont des blocs : sautez dessus et ils disparaissent, apparaissent ou basculent pour une durée réglable (horloge). Le plastique est basculable par défaut."]))

done("done", 8.5, 0.25, ["stud", "slab", "stairs", "wall", "fence", "float", "hop_switch"],
     ("Toy box", "Coffre à jouets"),
     (["Every plastic shape, and blocks that float and vanish on cue."],
      ["Toutes les formes de plastique, et des blocs qui flottent et disparaissent sur commande."]),
     "steveparty:lime_plastic_block")


# === Pipes =====================================================================================
chapter("pipes", "decoration", "steveparty:lime_pipe",
        ("Pipes", "Tuyaux"),
        (["Mario-style pipes: go in one mouth, come out of another."],
         ["Des tuyaux façon Mario : on entre par une embouchure, on ressort par une autre."]))

q("pipe", 0, 0, I("lime_pipe", 6), deps=["plastic.block"], rewards=T1(), size=1.25,
  desc=(["6 plastic blocks of one colour in two columns: 6 opaque pipes (16 colours). A pipe placed against another (or in front of its mouth) connects to it."],
        ["6 blocs de plastique d'une couleur en deux colonnes : 6 tuyaux opaques (16 couleurs). Un tuyau posé contre un autre (ou devant son embouchure) s'y raccorde."]))

q("windowed", 2, -1.5, I("cyan_windowed_pipe", 6), deps=["pipe"], rewards=T1(),
  desc=(["4 plastic blocks and 2 glass: 6 windowed pipes, with a continuous window along the tube."],
        ["4 blocs de plastique et 2 verres : 6 tuyaux à hublot, avec une vitre continue le long du tube."]))

q("glass", 2, 0, I("glass_pipe", 6), deps=["pipe"], rewards=T1(),
  desc=(["6 glass: 6 clear pipes. Linked to a mini-game page, a glass pipe is for spectators."],
        ["6 verres : 6 tuyaux transparents. Lié à une page de mini-jeu, un tuyau en verre est pour les spectateurs."]))

q("stained", 2, 1.5, I("magenta_stained_glass_pipe", 6), deps=["glass"], rewards=T1(),
  desc=(["6 stained glass of one colour: 6 stained glass pipes (16 colours)."],
        ["6 verres teintés d'une couleur : 6 tuyaux en verre teinté (16 couleurs)."]))

q("travel", 4, 0, C(), deps=["pipe"], rewards=T2(), icon="minecraft:slime_ball",
  title=("Wahoo!", "Wahoo !"),
  desc=(["Right-click a mouth, sneak in front of it, or fall into it: you rush through the pipe and come out of another end of the network, at random. Mobs, pawns and items go through too."],
        ["Clic droit sur une embouchure, accroupissez-vous devant, ou tombez dedans : vous filez dans le tuyau et ressortez par une autre extrémité du réseau, au hasard. Mobs, pions et objets passent aussi."]))

q("dead_end", 6, 0, C(), deps=["travel"], rewards=T2(), icon="minecraft:ender_eye",
  title=("Warp", "Téléportation"),
  desc=(["A pipe that ends inside a solid block is a dead end: it warps you to the nearest mouth of the same colour within 100 blocks. Opaque and windowed pipes of one colour count as one colour."],
        ["Un tuyau qui rentre dans un bloc solide est un bout bouché : il vous téléporte vers l'embouchure la plus proche de la même couleur, à moins de 100 blocs. Tuyaux opaques et à hublot d'une même couleur comptent comme une seule couleur."]))

q("attach", 4, 2, C(), deps=["pipe", "board.wrench"], rewards=T0(), optional=True, icon="steveparty:wrench",
  title=("Pipe fittings", "Fixations"),
  desc=(["Placed against a solid block, a pipe is fixed to it (one block per pipe). The Wrench changes which one, down to none."],
        ["Posé contre un bloc solide, un tuyau s'y fixe (un bloc par tuyau). La Clé change lequel, jusqu'à aucun."]))

done("done", 8, 0, ["windowed", "stained", "dead_end"],
     ("Plumber", "Plombier"),
     (["Every kind of pipe, and a warp network of your own. For mini-game pipes, see the Mini-games chapter."],
      ["Toutes les sortes de tuyaux, et votre propre réseau de téléportation. Pour les tuyaux de mini-jeu, voir le chapitre Mini-jeux."]),
     "steveparty:glass_pipe")


# === Signs and stencils ========================================================================
chapter("signs", "decoration", "steveparty:stencil",
        ("Signs and Stencils", "Panneaux et pochoirs"),
        (["Cut a 16x16 pattern, then paint or engrave it everywhere."],
         ["Découpez un motif 16x16, puis peignez-le ou gravez-le partout."]))

q("stencil", 0, 0, I("stencil", 2), deps=["welcome.done"], rewards=T1(), size=1.25,
  desc=(["4 iron nuggets around a paper: 2 blank stencils. Structure chests sometimes hold stencils with one of 50 built-in patterns. A cut stencil + blank ones copy it."],
        ["4 pépites de fer autour d'un papier : 2 pochoirs vierges. Les coffres de structures contiennent parfois des pochoirs à l'un des 50 motifs intégrés. Un pochoir découpé + des vierges le copient."]))

q("maker", 2, -1.5, I("stencil_maker"), deps=["stencil"], rewards=T2(),
  desc=(["Iron ingots, a smooth stone slab and an iron block. Insert a stencil and open the editor: left-click cuts a pixel, right-click fills it. Your library keeps up to 128 patterns, and learns every pattern you carry."],
        ["Des lingots de fer, une dalle de pierre lisse et un bloc de fer. Insérez un pochoir et ouvrez l'éditeur : clic gauche découpe un pixel, clic droit le bouche. Votre bibliothèque garde jusqu'à 128 motifs, et apprend tous ceux que vous portez."]))

q("cut", 4, -1.5, C(), deps=["maker"], rewards=T1(), icon="minecraft:shears",
  title=("Cut a pattern", "Découper un motif"),
  desc=(["Draw your own 16x16 pattern on the grid (flip, rotate, invert, undo...) and save it on the stencil."],
        ["Dessinez votre propre motif 16x16 sur la grille (retourner, pivoter, inverser, annuler...) et enregistrez-le sur le pochoir."]))

q("paint", 2, 0, C(), deps=["stencil"], rewards=T1(), icon="minecraft:red_dye",
  title=("Paint a wall", "Peindre un mur"),
  desc=(["Stencil in one hand, dye in the other, right-click a sign or the full face of any block: the pattern is painted (1 dye; the stencil is never used up). On a wall, the paint can only be wiped with a wet sponge or a brush."],
        ["Pochoir dans une main, colorant dans l'autre, clic droit sur un panneau ou la face pleine de n'importe quel bloc : le motif est peint (1 colorant ; le pochoir n'est jamais consommé). Sur un mur, la peinture ne part qu'à l'éponge mouillée ou au pinceau."]))

q("glow", 4, 0, C(), deps=["paint"], rewards=T0(), optional=True, icon="minecraft:glow_ink_sac",
  title=("Glow in the dark", "Ça brille la nuit"),
  desc=(["A glow ink sac on a painted pattern makes it shine at night; a dry sponge turns it off."],
        ["Une poche d'encre luisante sur un motif peint le fait briller la nuit ; une éponge sèche l'éteint."]))

q("hammer", 2, 1.5, I("stencil_gun"), deps=["stencil"], rewards=T2(),
  desc=(["Leather, planks, a stencil and a stick. Loads 9 stencils and 9 dyes (sneak + right-click). Sneak + scroll picks the stencil or the colour (G switches), right-click stamps it, signs, walls, tiles and podiums included."],
        ["Du cuir, des planches, un pochoir et un bâton. Il charge 9 pochoirs et 9 colorants (accroupi + clic droit). Accroupi + molette choisit le pochoir ou la couleur (G pour basculer), clic droit tamponne, panneaux, murs, tuiles et podiums compris."]))

q("stamp_tile", 4, 1.5, C(), deps=["hammer", "board.tile"], rewards=T1(), optional=True, icon="steveparty:tile",
  title=("Stamp a tile", "Tamponner une tuile"),
  desc=(["Stamp a board tile: it takes the dye's colour with the pattern drawn darker. With a cartridge inside, the cartridge keeps the look. A wet sponge wipes it."],
        ["Tamponnez une case du plateau : elle prend la couleur de la teinture, motif plus foncé. Avec une cartouche dedans, c'est la cartouche qui garde le look. L'éponge mouillée l'efface."]))

SIGNS = [
    ("easel_sign", ["Planks and sticks (PPP / PPP / S S): 2 easel signs, in any wood, modded ones included. Stands on the ground."],
     ["Planches et bâtons (PPP / PPP / S S) : 2 chevalets, dans n'importe quel bois, mods compris. Se pose au sol."]),
    ("wooden_panel", ["9 identical planks: 2 big wooden boards, on a post, hanging or flat."],
     ["9 planches identiques : 2 grands panneaux en bois, sur poteau, suspendus ou à plat."]),
    ("wooden_cutout_panel", ["A cross of 5 planks: 2 panels. Stencil + axe cut the board along the pattern: a silhouette."],
     ["Une croix de 5 planches : 2 panneaux. Pochoir + hache découpent la planche suivant le motif : une silhouette."]),
    ("rock_sign", ["4 identical stones (stone, granite, deepslate, sandstone...): 1 engraved stone. The stencil really digs the pattern; side by side, their tops merge into one lintel."],
     ["4 roches identiques (pierre, granite, ardoise des abîmes, grès...) : 1 pierre gravée. Le pochoir creuse vraiment le motif ; côte à côte, leurs sommets se soudent en un linteau."]),
    ("plastic_road_sign", ["A plastic block over plastic pellets: 2 road signs of the block's colour. The Wrench cycles the plate: round, square, diamond, triangle, star, heart. Off its post, it floats."],
     ["Un bloc de plastique au-dessus de granulés de plastique : 2 panneaux routiers de la couleur du bloc. La Clé change la plaque : ronde, carrée, losange, triangle, étoile, cœur. Sans poteau, il flotte."]),
]
for i, (sign, en, fr) in enumerate(SIGNS):
    q(sign, -1 + i * 1.5, 4, I(sign, 1), deps=["stencil"] + (["plastic.block"] if sign == "plastic_road_sign" else []),
      rewards=T1(), desc=(en, fr))

done("done", 7, 0, ["cut", "hammer", "easel_sign", "wooden_panel", "wooden_cutout_panel", "rock_sign",
                    "plastic_road_sign"],
     ("Sign painter", "Peintre en enseignes"),
     (["Every sign, your own patterns and the Stencil Hammer."],
      ["Tous les panneaux, vos propres motifs et le Marteau à pochoirs."]),
     "steveparty:stencil_gun")


# === Building blocks ===========================================================================
chapter("building", "decoration", "steveparty:polished_orange_terracotta_bricks",
        ("Building Blocks", "Blocs de construction"),
        (["Polished terracotta and concrete, bevelled bricks and checkerboard tiles."],
         ["Terre cuite et béton polis, briques biseautées et carrelages en damier."]))


def family(prefix, color, y, deps, en_family, fr_family, vanilla_en, vanilla_fr):
    base = f"polished_{color}_{prefix}"
    bricks = f"polished_{color}_{prefix}_bricks"
    q(prefix + "_block", 0, y, I(base, 4), deps=deps, rewards=T1(), size=1.25,
      desc=([f"4 {vanilla_en} in a square: 4 polished blocks (17 colours for terracotta, 16 for concrete). The stonecutter makes every shape from the vanilla block directly."],
            [f"4 {vanilla_fr} en carré : 4 blocs polis (17 couleurs pour la terre cuite, 16 pour le béton). Le tailleur de pierre fait toutes les formes directement depuis le bloc vanilla."]))
    q(prefix + "_bricks", 2, y, I(bricks, 4), deps=[prefix + "_block"], rewards=T1(),
      desc=(["4 polished blocks in a square: 4 bevelled bricks. Each brick has its own shade, and big walls never repeat."],
            ["4 blocs polis en carré : 4 briques biseautées. Chaque brique a sa nuance, et un grand mur ne se répète jamais."]))
    shapes = [("stairs", 4, "6 blocks in steps: 4 stairs.", "6 blocs en escalier : 4 escaliers."),
              ("slab", 6, "3 blocks in a row: 6 slabs.", "3 blocs en ligne : 6 dalles."),
              ("wall", 6, "Two rows of 3: 6 walls.", "Deux lignes de 3 : 6 murets.")]
    for j, (shape, n, en, fr) in enumerate(shapes):
        q(f"{prefix}_{shape}", 4 + j * 1.5, y - 0.75, I(f"{color}_polished_{prefix}_{shape}", n), deps=[prefix + "_block"],
          rewards=T0(), desc=([en], [fr]))
        q(f"{prefix}_bricks_{shape}", 4 + j * 1.5, y + 0.75, I(f"{color}_polished_{prefix}_bricks_{shape}", n),
          deps=[prefix + "_bricks"], rewards=T0(), desc=([en.replace("blocks", "bricks")], [fr.replace("blocs", "briques")]))


family("terracotta", "orange", 0, ["welcome.done"], "terracotta", "terre cuite",
       "orange terracotta", "terres cuites orange")
family("concrete", "light_blue", 3.5, ["welcome.done"], "concrete", "béton",
       "light blue concrete", "bétons bleu clair")

q("terracotta_tiles", 9, 0, I("polished_terracotta_tiles", 4), deps=["terracotta_block"], rewards=T1(),
  desc=(["Two polished terracotta blocks of different colours as a checkerboard in a 2x2 grid: 4 tiles, each face a 2x2 checkerboard. Swap the colours for the shifted checkerboard."],
        ["Deux blocs de terre cuite polie de couleurs différentes en damier dans une grille 2x2 : 4 carrelages, chaque face en damier 2x2. Inversez les couleurs pour le damier décalé."]))

q("concrete_tiles", 9, 3.5, I("polished_concrete_tiles", 4), deps=["concrete_block"], rewards=T1(),
  desc=(["The same with two polished concrete colours. Every pair of colours works; the item keeps its colours in its name."],
        ["Pareil avec deux couleurs de béton poli. Toutes les paires de couleurs marchent ; l'objet garde ses couleurs dans son nom."]))

done("done", 11.5, 1.75, ["terracotta_bricks_stairs", "terracotta_bricks_slab", "terracotta_bricks_wall",
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
         ["Quelques jouets de plus pour votre monde de fête."]))

q("looting_box", 0, 0, I("looting_box"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["Gold ingots, polished terracotta, a chest and an amethyst shard."],
        ["Des lingots d'or, de la terre cuite polie, un coffre et un éclat d'améthyste."]))

q("bonk", 2, 0, C(), deps=["looting_box", "board.inventory"], rewards=T2(), icon="minecraft:gold_ingot",
  title=("Bonk!", "Bonk !"),
  desc=(["Put an Inventory Cartridge linked to a chest in it (Wrench), hang it with 2 or 3 blocks of air below, and hit it with your head while jumping: it drops items from the chest. 2 hits, then 3 s to recharge."],
        ["Mettez-y une Cartouche d'inventaire liée à un coffre (Clé), suspendez-la avec 2 ou 3 blocs d'air dessous, et frappez-la de la tête en sautant : elle lâche des objets du coffre. 2 coups, puis 3 s de recharge."]))

q("plunger", 0, 2, I("plunger"), deps=["welcome.done"], rewards=T1(),
  desc=(["A stick, a slime ball and a bowl. Right-click a mob to mute it; again to give its voice back."],
        ["Un bâton, une boule de slime et un bol. Clic droit sur un mob pour le rendre muet ; encore une fois pour lui rendre sa voix."]))

q("squishy", 2, 2, C(), deps=["shops.villager_block"], rewards=T1(), optional=True, icon="steveparty:villager_block",
  title=("Squishy friend", "Ami tout plat"),
  desc=(["The Villager Block reacts to everything: stare at it, jump in front of it, show it emeralds, play a jukebox nearby... About 70 reactions to discover."],
        ["Le Bloc de villageois réagit à tout : fixez-le, sautez devant lui, montrez-lui des émeraudes, jouez un disque à côté... Environ 70 réactions à découvrir."]))

done("done", 4.5, 1, ["bonk", "plunger"],
     ("Odds and ends", "Bric-à-brac"),
     (["The little extras of the mod, all found."],
      ["Les petits plus du mod, tous trouvés."]),
     "steveparty:plunger")


# === The grand finale (in Welcome) =============================================================
for ch in CHAPTERS:
    if ch["key"] == "welcome":
        _current = ch
q("finale", 0, 7.5, C(), deps=[c["key"] + ".done" for c in CHAPTERS if c["key"] != "welcome"],
  rewards=[LV(10), IT("power_star", 3), IT("dice_module_lucky", 1), COINS(50)],
  icon="steveparty:power_star", shape="heart", size=2.5,
  title=("Steve Party Master", "Maître de Steve Party"),
  sub=("Every chapter complete", "Tous les chapitres terminés"),
  desc=(["You have built, played and collected everything Steve Party Maker has to offer. Thank you for playing the beta!"],
        ["Vous avez construit, joué et collectionné tout ce que Steve Party Maker propose. Merci d'avoir joué la bêta !"]))


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
    {"key": "star_chest", "title": ("Star Chest", "Coffre étoilé"), "icon": "steveparty:power_star",
     "rewards": [
         (IT("power_star", 1), 3), (IT("coin", 25), 4), (IT("triple_dice", 1), 3), (IT("premium_dice_face_10", 2), 3),
         (IT("dice_module_lucky", 1), 2), (IT("dice_module_reroll", 1), 2), (IT("dice_module_choice", 1), 1),
         (IT("dice_module_infinity", 1), 1), (IT("dice_module_skeleton_key", 1), 2), (IT("swap_dice_face", 2), 2),
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


def build():
    lang = {"en": {}, "fr": {}}

    def tr(kind, oid, sub, en, fr):
        k = f"{kind}.{hexid(oid)}.{sub}"
        lang["en"][k] = en
        lang["fr"][k] = fr

    tables = {t["key"]: make_id("reward_table", t["key"]) for t in REWARD_TABLES}
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
        "progression_mode": "flexible",
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
                elif t["type"] == "observation":
                    tn["observation_type"] = "entity_type"
                    tn["to_observe"] = t["entity"]
                    tn["timer"] = L(t["ticks"])
                tasks.append(tn)
            nbt["tasks"] = tasks
            rewards = []
            for k, r in enumerate(qq["rewards"]):
                rewards.append(reward_nbt(r, make_id("reward", f"{qq['key']}.{k}"), tables))
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
            if qq["sub"]:
                tr("quest", qid, "quest_subtitle", *qq["sub"])
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

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
         ["Construisez un jeu de plateau dans Minecraft et jouez-y entre amis."]))

q("welcome", 0, 0, C(), rewards=[XP(20)], icon="steveparty:party_controller", shape="gear", size=2.0,
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

q("pellets", -3, 2.25, I("plastic_pellets", 4, how="consume"), deps=["welcome"],
  rewards=[XP(15), IT("minecraft:sugar_cane", 8)],
  desc=(["Smelt sugar cane in a furnace. Plastic pellets are in almost everything: tiles, cartridges, tokens and every plastic block."],
        ["Faites cuire de la canne à sucre au four. Les granulés de plastique entrent dans presque tout : tuiles, cartouches, jetons et tous les blocs de plastique."]))

q("coin", 3, 2.25, I("coin", 1), deps=["welcome"],
  rewards=[XP(15), IT("minecraft:gold_nugget", 4)],
  desc=(["One gold nugget makes one Coin (shapeless). Coins are the default currency of a party: mini-games pay them out, and shops can sell for them."],
        ["Une pépite d'or donne une Pièce (sans forme). La Pièce est la monnaie par défaut d'une partie : les mini-jeux en rapportent et les boutiques peuvent vendre contre elle."]))

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
        ["Les quêtes sont partagées par équipe FTB Teams. Pour avancer ensemble, créez une équipe avec /ftbteams party create et invitez vos amis.",
         "Les récompenses restent personnelles : chacun réclame les siennes."]))

done("done", 0, 4.5, ["pellets", "coin"],
     ("Ready to party", "Prêt pour la fête"),
     (["You have the basics. Next: build a board (The Board Game), then explore the other chapters in any order."],
      ["Vous avez les bases. Ensuite : construire un plateau (Le jeu de plateau), puis explorer les autres chapitres dans l'ordre que vous voulez."]),
     "steveparty:tile")
# Lighter end for the first chapter.
CHAPTERS[-1]["quests"][-1]["rewards"] = [XP(50), COINS(5), RANDOM("party_favors")]


# === The Board =================================================================================
chapter("board", "board_game", "steveparty:tile",
        ("The Board", "Le plateau"),
        (["Cartridges, tiles and the Tile Linker Brush: build the path your pawns will walk."],
         ["Cartouches, tuiles et Pinceau lieur : construisez le parcours de vos pions."]))

q("cartridge", 0, -7, I("board_space_behavior", 2), deps=["welcome.pellets"], size=1.25,
  rewards=[XP(15), IT("board_space_behavior", 4)],
  desc=(["2 redstone, 2 paper and 2 plastic pellets. A cartridge gives a tile its role and its destinations. The plain Cartridge has no role: it is just a path.",
         "Right-click in the air with it to open its menu. Every other cartridge is this one plus an ingredient."],
        ["2 redstone, 2 papier et 2 granulés de plastique. Une cartouche donne à une case son rôle et ses destinations. La Cartouche simple n'a pas de rôle : c'est juste un chemin.",
         "Clic droit dans le vide avec elle pour ouvrir son menu. Toutes les autres cartouches, c'est elle plus un ingrédient."]))

q("tile", -6, -1.5, I("tile"), deps=["cartridge"], rewards=[XP(15), IT("plastic_pellets", 4), IT("default_dice", 2)], size=1.25,
  desc=(["4 white plastic slabs around a heavy weighted pressure plate, and a cartridge in the middle of the bottom row: 1 Tile, already holding that cartridge.",
         "A Tile is one board space and counts as one step. Place it in 8 directions, on slabs, carpets, and even on stairs, where it follows the slope."],
        ["4 dalles de plastique blanc autour d'une plaque de pression lourde, et une cartouche au milieu de la ligne du bas : 1 Tuile, qui contient déjà cette cartouche.",
         "Une Tuile est une case du plateau et compte comme un pas. Elle se pose dans 8 directions, sur les dalles, les tapis et même sur les escaliers, dont elle suit la pente."]))

q("brush", -4, -1.5, I("tile_linker_brush"), deps=["tile"], rewards=T2(),
  desc=(["A stick, redstone and a feather, in a diagonal. The brush paints the links between spaces and shows the board while you hold it."],
        ["Un bâton, de la redstone et une plume, en diagonale. Le pinceau peint les liens entre les cases et montre le plateau tant que vous le tenez."]))

q("link", -2, -1.5, C(), deps=["brush"], rewards=T2(), icon="steveparty:tile_linker_brush",
  title=("Paint the path", "Peindre le chemin"),
  desc=(["Hold right-click with the brush and sweep the tiles in the order the pawns will walk: each one is linked to the next. A tile without a cartridge gets a Cartridge from your inventory.",
         "Paint a link again in the same direction to erase it. Left-click opens the brush's wheel: undo, redo, link modes."],
        ["Maintenez le clic droit avec le pinceau et balayez les tuiles dans l'ordre où les pions les parcourront : chacune est reliée à la suivante. Une tuile sans cartouche reçoit une Cartouche de votre inventaire.",
         "Repeignez un lien dans le même sens pour l'effacer. Le clic gauche ouvre la roue du pinceau : annuler, rétablir, modes de liaison."]))

q("loop", 0, -1.5, C(), deps=["link"], rewards=T2(), icon="minecraft:firework_rocket",
  title=("Close the loop", "Fermer la boucle"),
  desc=(["Paint back to the first tile to close the loop. A loop with a Start space is all a party needs.",
         "Several links from one tile make a fork: the player chooses the way. No link: a dead end."],
        ["Repeignez jusqu'à la première tuile pour fermer la boucle. Une boucle avec une case départ suffit pour une partie.",
         "Plusieurs liens depuis une case font un embranchement : le joueur choisit. Aucun lien : un cul-de-sac."]))

q("board_view", 2, -1.5, C(), deps=["loop", "wrench"], rewards=T1(), icon="minecraft:spyglass",
  title=("Board view and check", "Vue du plateau et diagnostic"),
  desc=(["Brush in hand, the board appears: chevrons along each link, step numbers, \"?\" on forks, \"!\" on dead ends.",
         "Click a Party Controller with the Wrench (or /steveparty board check) to list every problem of the board."],
        ["Pinceau en main, le plateau apparaît : chevrons le long des liens, numéros de pas, « ? » aux bifurcations, « ! » aux impasses.",
         "Clic de Clé sur un Party Controller (ou /steveparty board check) : la liste des problèmes du plateau."]))

q("helmet", 4, -1.5, I("explorer_helmet"), deps=["board_view"], rewards=T2(),
  desc=(["Leather, glowstone dust and a copper ingot. Worn with its lamp lit (G), it shows the whole board all the time, the details of the nearby spaces and the exact numbers of the one you look at."],
        ["Du cuir, de la poudre de pierre lumineuse et un lingot de cuivre. Porté lampe allumée (G), il montre tout le plateau en permanence, les détails des cases proches et les chiffres exacts de celle que vous regardez."]))

q("wrench", -4, 0.5, I("wrench"), deps=["tile"], rewards=T2(),
  desc=(["Redstone and an iron ingot. Right-click opens a tile, a Checkpoint or a router, no need to sneak; another cartridge in the off hand swaps the tile's cartridge, links kept. It also sets podiums, poles and Spawn Bubbles, and breaks any plastic block in one hit."],
        ["De la redstone et un lingot de fer. Le clic droit ouvre une tuile, un Point de déclenchement ou un routeur, sans s'accroupir ; une autre cartouche en main secondaire échange celle de la tuile, liens gardés. Elle règle aussi podiums, mâts et Bulles d'apparition, et casse tout bloc de plastique d'un coup."]))

q("check_point", -6, 0.75, I("check_point", 4), deps=["tile"], rewards=T1(),
  desc=(["Prismarine shards, light blue panes and a chest: 4 Checkpoints. A floating glass cube that a pawn crosses without spending a step: free turns, forks and detours. Its role never triggers on arrival, except Stop and Shop."],
        ["Éclats de prismarine, vitres bleu clair et un coffre : 4 Points de déclenchement. Un cube de verre flottant que le pion traverse sans dépenser de pas : virages, bifurcations et détours gratuits. Son rôle ne se déclenche jamais à l'arrivée, sauf Stop et Boutique."]))

q("colours", -6, 2.25, C(), deps=["check_point"], rewards=T1(), icon="minecraft:glow_ink_sac",
  title=("Colours and glow", "Couleurs et lueur"),
  desc=(["A dye on a tile colours its cartridge; on a Checkpoint, the whole cube. A glow ink sac makes the tile or the cube shine in full light, an ink sac turns it off. A sponge removes the dye. The tile's item keeps it all."],
        ["Une teinture sur une tuile colore sa cartouche ; sur un Point de déclenchement, tout le cube. Une poche d'encre luisante fait briller la tuile ou le cube en pleine lumière, une poche d'encre l'éteint. Une éponge enlève la teinture. L'objet de la tuile garde tout."]))

q("advanced_tile", -2, 0.75, I("advanced_tile"), deps=["tile"], rewards=T2(),
  desc=(["2 yellow plastic slabs, a light weighted pressure plate, a trapped chest and 2 cartridges: an Advanced Tile holding them. It takes 16 cartridges: the redstone power it receives picks the active one (power 0 = slot 1, power 1 = slot 2...)."],
        ["2 dalles de plastique jaune, une plaque de pression légère, un coffre piégé et 2 cartouches : une Tuile avancée qui les contient. Elle en prend 16 : la puissance redstone reçue choisit l'active (puissance 0 = emplacement 1, puissance 1 = emplacement 2...)."]))

q("redstone_slots", -2.75, 2.25, C(), deps=["advanced_tile"], rewards=T1(), icon="minecraft:comparator",
  title=("A tile that changes", "Une case qui change"),
  desc=(["Put two different cartridges in an Advanced Tile and power it: the tile changes role, colour and destinations with the signal. A comparator or a redstone counter makes tiles that change every turn."],
        ["Mettez deux cartouches différentes dans une Tuile avancée et alimentez-la : la case change de rôle, de couleur et de destinations avec le signal. Un comparateur ou un compteur redstone fait des cases qui changent à chaque tour."]))

q("router", -1.25, 2.25, I("board_space_redstone_router"), deps=["advanced_tile"], rewards=T2(),
  desc=(["Gold, a redstone torch, a chest and an amethyst shard. Give it a cartridge whose destinations are the tiles to drive: its redstone input replaces theirs (one lever switches a whole board).",
         "A comparator on it reads what happens on its tiles: 14 bonus, 13 malus, 10 Stop, 8 Teleport, 1 a pawn passes..."],
        ["De l'or, une torche de redstone, un coffre et un éclat d'améthyste. Donnez-lui une cartouche dont les destinations sont les cases à piloter : sa redstone remplace la leur (un levier change tout un plateau).",
         "Un comparateur dessus capte ce qui se passe sur ses cases : 14 bonus, 13 malus, 10 Stop, 8 Téléportation, 1 un pion passe..."]))

q("sizes", -8, -0.5, C(), deps=["tile"], rewards=T1(), icon="minecraft:stonecutter",
  title=("Small and large tiles", "Petites et grandes tuiles"),
  desc=(["Stonecutter: 1 Tile gives 2 small tiles (1x1); 2 small ones give a standard tile back at the crafting table. Four standard tiles in a 2x2 square give one large tile (exactly 2x2 blocks)."],
        ["Tailleur de pierre : 1 Tuile donne 2 petites tuiles (1x1) ; 2 petites redonnent une tuile standard à l'établi. Quatre tuiles standard en carré 2x2 donnent une grande tuile (exactement 2x2 blocs)."]))

q("silk", -8, 1, C(), deps=["tile"], rewards=T0(), optional=True, icon="minecraft:enchanted_book",
  title=("Pack a tile", "Emporter une tuile"),
  desc=(["Break a tile with a Silk Touch pickaxe: it drops as one item that keeps all its cartridges, their settings and its look. Place it again and it is back as it was."],
        ["Cassez une tuile avec une pioche Toucher de soie : elle tombe en un seul objet qui garde ses cartouches, leurs réglages et son look. Reposée, elle redevient comme avant."]))

# Cartridges in one row between the Cartridge (above) and the rest of the board (below).
q("start", -6, -5, I("tile_behavior_start"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + lime dye. A start space: a pawn placed on it binds to it, floats and takes its colour. The Party Controller only takes pawns bound to a start space."],
        ["Cartouche + teinture vert clair. Une case de départ : un pion posé dessus s'y lie, flotte et lui donne sa couleur. Le Party Controller ne prend que les pions liés à une case de départ."]))

q("stop", -4.5, -5, I("board_space_behavior_stop"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + red dye. A pawn that reaches a Stop space ends its move there, whatever steps it has left. Works on a Checkpoint too."],
        ["Cartouche + teinture rouge. Un pion qui atteint une case Stop y termine son déplacement, quels que soient ses pas restants. Marche aussi sur un Point de déclenchement."]))

q("advance", -3, -5, I("advance_back_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + piston. The pawn that ends on it moves again, 1 to 6 spaces forward (green) or back (pink): a Boomcart rolls in, explodes behind it or rams it. Sneak + scroll with it in hand sets the count and direction."],
        ["Cartouche + piston. Le pion qui s'y arrête repart de 1 à 6 cases en avant (vert) ou en arrière (rose) : un Pétaroule arrive, explose derrière lui ou le percute. Accroupi + molette, cartouche en main, règle le nombre et le sens."]))

q("replay", -1.5, -5, I("replay_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + repeater. The player whose pawn ends on it rolls again at once, and gets the die back. One extra turn only."],
        ["Cartouche + répéteur. Le joueur dont le pion s'y arrête rejoue aussitôt, et récupère son dé. Un seul tour en plus."]))

q("teleport", 0, -5, I("teleport_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + ender pearl. A pawn that ends on it is sent to another Teleport space of the same network colour (violet, green, orange or blue)."],
        ["Cartouche + perle de l'Ender. Un pion qui s'y arrête est envoyé sur une autre case Téléportation du même réseau de couleur (violet, vert, orange ou bleu)."]))

q("inventory", 1.5, -5, I("inventory_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + chest. Links up to 8 chests (right-click them) and gives or takes items: positive items make a bonus space (blue), negative ones a malus space (red), empty an item space (orange). The scroll wheel sets the amounts. Without a chest, in a party, it uses the Party Controller's bank."],
        ["Cartouche + coffre. Elle retient jusqu'à 8 coffres (clic droit dessus) et donne ou prend des objets : objets positifs = case bonus (bleue), négatifs = case malus (rouge), vide = case objet (orange). La molette règle les quantités. Sans coffre, en partie, elle puise dans la banque du Party Controller."]))

q("shop", 3, -5, I("shop_cartridge"), deps=["inventory", "shops.bandana"], rewards=T1(),
  desc=(["An Inventory Cartridge + a bandana stolen from a Boxed Trader (see Shops). A pawn that ends on it (or passes a Checkpoint with it) brings out a Boxed Trader for its player. Its offers (9 at most) are set in the cartridge's own little stall; the goods come from its linked chests, else from the Party Controller's bank."],
        ["Une Cartouche d'inventaire + un bandana volé à un Marchand en carton (voir Boutiques). Un pion qui s'y arrête (ou qui passe un Point de déclenchement qui la porte) fait sortir un Marchand en carton pour son joueur. Ses offres (9 au plus) se règlent dans le petit étal de la cartouche ; la marchandise vient de ses coffres liés, sinon de la banque du Party Controller."]))

q("threshold", 4.5, -5, I("threshold_cartridge"), deps=["cartridge"], rewards=T1(),
  desc=(["Cartridge + iron bars. A wall on the path: a pawn arriving with steps left only goes through if its roll meets the condition (7 or more by default, or a double, a triple); otherwise it stops here. In Barrier mode, each turn is a new try until it passes."],
        ["Cartouche + barreaux de fer. Un mur sur le chemin : un pion qui arrive avec des pas restants ne passe que si son lancer remplit la condition (7 ou plus par défaut, ou un double, un triple) ; sinon il s'arrête ici. En mode Barrière, chaque tour est un nouvel essai jusqu'à ce qu'il passe."]))

q("gate_key", 7.5, -5, I("gate_key"), deps=["cartridge"], rewards=T1(),
  desc=(["A gold ingot + an amethyst shard, shapeless. It opens the gates of Key Gate spaces, once. Sell it in a shop, put it in a prize chest or a Tricauldron: like any item."],
        ["Un lingot d'or + un éclat d'améthyste, sans forme. Elle ouvre une fois les portails des cases Portail à clé. Vendez-la en boutique, mettez-la dans un coffre de gains ou un Trichaudron : comme n'importe quel objet."]))

q("key_gate", 6, -5, I("key_gate_cartridge"), deps=["gate_key"], rewards=T2(),
  desc=(["Cartridge + Gate Key. A gate rises on each locked exit of the space: a pawn needs a Gate Key (used up) to go through. Without one: wait, redo the move to take another way, or go back to start. \"Open\" keeps the gate open for a few turns, or for good."],
        ["Cartouche + Clé de portail. Un portail se dresse sur chaque sortie verrouillée de la case : un pion a besoin d'une Clé de portail (consommée) pour passer. Sans clé : attendre, refaire le déplacement pour prendre un autre chemin, ou retourner au départ. « Ouvert » garde le portail ouvert quelques tours, ou pour toujours."]))

q("tp_pair", 0, -3.5, C(), deps=["teleport"], rewards=T1(), icon="minecraft:ender_pearl",
  title=("A teleport network", "Un réseau de téléportation"),
  desc=(["Place two Teleport spaces of the same colour on one board. A dye on the tile (purple, green, orange, blue...) changes its network. A lone space of its colour sends nobody anywhere."],
        ["Posez deux cases Téléportation de la même couleur sur un plateau. Une teinture sur la case (violet, vert, orange, bleu...) change son réseau. Une case seule de sa couleur n'envoie personne."]))

q("bonus_malus", 1.5, -3.5, C(), deps=["inventory"], rewards=T1(), icon="minecraft:chest",
  title=("Bonus and malus", "Bonus et malus"),
  desc=(["Make one blue (bonus) and one red (malus) space. They matter: in a party, the colour of the space under each pawn makes the mini-game teams."],
        ["Faites une case bleue (bonus) et une rouge (malus). Elles comptent : en partie, la couleur de la case sous chaque pion forme les équipes des mini-jeux."]))

done("done", 3, 1.25, ["loop", "wrench", "check_point", "advanced_tile", "router", "start", "stop", "advance",
                       "replay", "teleport", "inventory", "threshold", "key_gate"],
     ("Board builder", "Bâtisseur de plateau"),
     (["Tiles, checkpoints, the main cartridges and a closed loop: your board is ready for pawns."],
      ["Tuiles, points de déclenchement, les principales cartouches et une boucle fermée : votre plateau attend ses pions."]),
     "steveparty:advanced_tile")


# === Pawns =====================================================================================
chapter("pawns", "board_game", "steveparty:tokenizer_wand",
        ("Pawns", "Pions"),
        (["Any mob can become a pawn with the Tokenizer Wand."],
         ["N'importe quel mob devient un pion avec la Baguette de pions."]))

q("crystal_ball", 0, 0, I("lapis_crystal_ball"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["A Party Star surrounded by 4 blocks of lapis lazuli (see Mulas and Stars for the star). Its only use: the head of the Tokenizer Wand."],
        ["Une Party Star entourée de 4 blocs de lapis-lazuli (l'étoile : voir Mulas et étoiles). Son seul usage : la tête de la Baguette de pions."]))

q("wand", 2, 0, I("tokenizer_wand"), deps=["crystal_ball"], rewards=T3(),
  desc=(["Lapis crystal ball, gold block and blaze rod, in a column. A golden wand with a lapis orb."],
        ["Boule de cristal en lapis, bloc d'or et bâton de Blaze, en colonne. Une baguette dorée à orbe de lapis."]))

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
  desc=(["5 plastic pellets and a chest. Right-click your pawn with an empty Token to store it, right-click a block to put it back: the way to move a pawn by hand."],
        ["5 granulés de plastique et un coffre. Clic droit sur votre pion avec un Jeton vide pour le ranger, clic droit sur un bloc pour le reposer : la façon de déplacer un pion à la main."]))

q("bind", 8, -1.25, C(), deps=["token", "board.start"], rewards=MILESTONE(), icon="steveparty:tile_behavior_start",
  shape="hexagon", size=1.25,
  title=("Take your place", "Prendre sa place"),
  desc=(["Put your pawn on a Start space: it binds to it, floats and looks at the nearest player. Right-click the space empty-handed to own it. Only bound pawns join a party."],
        ["Posez votre pion sur une case de départ : il s'y lie, flotte et regarde le joueur le plus proche. Clic droit main vide sur la case pour en devenir propriétaire. Seuls les pions liés entrent dans une partie."]))

q("game_master", 2, 1.75, C(), deps=["wand"], rewards=T2(), optional=True, icon="minecraft:enchanted_book",
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

q("default_dice", 4.5, -4, I("default_dice", how="own"), deps=["welcome.done"], rewards=[XP(15), IT("default_dice", 1)], size=1.25,
  desc=(["Rolls 1 to 10. Your first two come with the Tile quest (and the hub's starter chest); more come out of the Dice Forge."],
        ["Donne 1 à 10. Les deux premiers viennent avec la quête Tuile (et le coffre de départ du hub) ; les suivants sortent de la forge à dés."]))

q("roll", 6.25, -3.25, C(), deps=["default_dice"], rewards=T1(), icon="steveparty:default_dice",
  title=("Roll it!", "Lancez-le !"),
  desc=(["Right-click: the die flies over the nearest mob (sneak: the nearest player) and spins. Hit it to stop it. The result shows above the hotbar."],
        ["Clic droit : le dé part au-dessus du mob le plus proche (accroupi : du joueur le plus proche) et tourne. Frappez-le pour l'arrêter. Le résultat s'affiche au-dessus de la barre d'inventaire."]))

q("move", 8.25, -3.25, C(), deps=["roll", "pawns.pionificus"], rewards=T2(), icon="steveparty:tile",
  title=("Move a pawn", "Faire avancer un pion"),
  desc=(["Roll near one of your pawns standing on a linked board: it walks the result, step by step. At a fork, right-click an arrow empty-handed to choose the way."],
        ["Lancez près d'un de vos pions posé sur un plateau relié : il avance du résultat, case par case. À un embranchement, clic droit main vide sur une flèche pour choisir."]))

q("double", 6.25, -4.5, I("double_dice"), deps=["default_dice"], rewards=T1(),
  desc=(["Two Default Dice, shapeless. Two linked dice: 2 to 20. Splits back into two."],
        ["Deux Dés par défaut, sans forme. Deux dés liés : 2 à 20. Se redécoupe en deux."]))

q("triple", 8.75, -4.5, I("triple_dice"), deps=["double"], rewards=T2(),
  desc=(["Three Default Dice, or a Double + a Default Die. Three linked dice: 3 to 30."],
        ["Trois Dés par défaut, ou un Dé double + un Dé par défaut. Trois dés liés : 3 à 30."]))

# Faces sub-tree
q("blank", 4.5, -0.5, I("blank_dice_face", 4), deps=["default_dice"], rewards=[XP(15), IT("blank_dice_face", 4)], size=1.25,
  desc=(["2 iron ingots and 2 nether quartz in a square: 4 Blank Die Faces. Every face starts from a blank one (stonecutter), and the Dice Forge uses them up."],
        ["2 lingots de fer et 2 quartz du Nether en carré : 4 Faces de dé vierges. Toute face part d'une vierge (tailleur de pierre), et la Forge à dés en consomme."]))

# Faces around the Blank Die Face, modules in a row between it and "Fit a module": every arrow is a straight spoke.
q("face6", 2.75, -3.25, I("dice_face_6", how="own"), deps=["blank"], rewards=T1(),
  desc=(["Stonecutter: a blank face becomes any number from 0 to 10. Any face goes back to blank the same way."],
        ["Tailleur de pierre : une face vierge devient n'importe quel nombre de 0 à 10. Toute face redevient vierge de la même façon."]))

q("face0", 1.75, -2.25, I("dice_face_0", how="own"), deps=["blank"], rewards=T1(),
  desc=(["A special face: the pawn stays where it is and the space under it triggers again."],
        ["Une face spéciale : le pion reste sur place et la case où il se trouve rejoue son effet."]))

q("premium", 1.25, -1, I("premium_dice_face_10", how="own"), deps=["blank"], rewards=T1(),
  desc=(["Premium faces (1 to 10) shine in gold on the die. For now they count like normal numbers."],
        ["Les faces premium (1 à 10) brillent en or sur le dé. Pour l'instant, elles comptent comme des nombres normaux."]))

q("cursed", 1.25, 0.25, I("cursed_dice_face_3", how="own"), deps=["blank"], rewards=T1(),
  desc=(["Cursed faces (1 to 3) show a purple skull. For now they count like normal numbers."],
        ["Les faces maudites (1 à 3) portent un crâne violet. Pour l'instant, elles comptent comme des nombres normaux."]))

q("coin_face", 7.75, -0.75, I("coin_dice_face_1"), deps=["blank", "welcome.coin"], rewards=T1(),
  desc=(["Blank face + Coin: a +1 Coins face (stonecutter for +2 to +10). Instead of moving, the roller gains that many coins of the party."],
        ["Face vierge + Pièce : une face Pièces +1 (tailleur de pierre pour +2 à +10). Au lieu d'avancer, le lanceur gagne autant de pièces de la partie."]))

q("debt_face", 7.25, -2.25, I("debt_dice_face_1"), deps=["blank"], rewards=T1(),
  desc=(["Blank face + spider eye: a -1 Debt face (stonecutter for -2 to -10). The roller loses that many coins, never more than they have."],
        ["Face vierge + œil d'araignée : une face Dette -1 (tailleur de pierre pour -2 à -10). Le lanceur perd autant de pièces, jamais plus que ce qu'il a."]))

q("swap_face", 7.5, 0.5, I("swap_dice_face"), deps=["blank"], rewards=T2(),
  desc=(["Blank face + ender pearl. The roller's pawn swaps places with another player's pawn of their choice."],
        ["Face vierge + perle de l'Ender. Le pion du lanceur échange sa place avec celui d'un autre joueur, à son choix."]))

# Modules sub-tree: a blank module first, then each module from 7 fragments of its colour (its icon's ring)
q("blank_module", 4.5, 0.75, I("blank_dice_module"), deps=["blank"], rewards=T1(),
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
     "Le dé devient un bonus : utilisé une fois, à la place de votre lancer (tout autre dé revient à son lanceur)."),
    ("lucky", "green", "a rabbit's foot", "une patte de lapin",
     "One extra roll per module (up to 5): keep the best one.",
     "Un lancer de plus par module (jusqu'à 5) : gardez le meilleur."),
    ("reroll", "orange", "a wind charge", "une charge de vent",
     "Stop or again: after the result, keep it or roll again, once per module.",
     "Stop ou encore : après le résultat, gardez-le ou relancez, une fois par module."),
    ("reversed", "red", "a fermented spider eye", "un œil d'araignée fermenté",
     "A negative module: the pawn moves back, and coins won are lost.",
     "Un module négatif : le pion recule, et les pièces gagnées sont perdues."),
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
    q("module_" + m, 0.125 + i * 1.25, 2, I("dice_module_" + m), deps=["blank_module", "mulas.frag_" + colour],
      rewards=T1(),
      desc=([f"A Blank Die Module ringed with 7 {EN_COLOR[colour]} star fragments, {ing_en} on top. {en}"],
            [f"Un Module de dé vierge entouré de 7 fragments d'étoile {fr_plural(colour)}, avec {ing_fr} au-dessus. {fr}"]))

q("fit_module", 4.5, 4.25, C(), deps=["module_" + m[0] for m in MODULES], dep_req="one_completed",
  rewards=T2(), icon="minecraft:crafting_table",
  title=("Fit a module", "Poser un module"),
  desc=(["Crafting table: a die (default, forged, double or triple) + one or more module items gives the same die with those modules. The module items stay in the grid. A die can carry several modules: they are listed in its tooltip."],
        ["Établi : un dé (simple, forgé, double ou triple) + un ou plusieurs objets module donnent le même dé avec ces modules. Les objets module restent dans la grille. Un dé peut en porter plusieurs : ils sont listés dans son infobulle."]))

done("done", 12.25, 0, ["triple", "move", "swap_face", "coin_face", "fit_module"],
     ("Dice collector", "Collectionneur de dés"),
     (["Dice, special faces and modules: every roll can now be a surprise."],
      ["Dés, faces spéciales et modules : chaque lancer peut maintenant être une surprise."]),
     "steveparty:triple_dice")


# === Running a party ===========================================================================
chapter("party", "board_game", "steveparty:party_controller",
        ("Running a Party", "Mener une partie"),
        (["The Party Controller runs turns, mini-games and prizes."],
         ["Le Party Controller enchaîne les tours, les mini-jeux et les gains."]))

q("controller", 0, 0, I("party_controller"), deps=["board.tile", "dice.default_dice"], rewards=T3(), size=1.25,
  desc=(["A map, glass and a redstone torch over gold ingots and a chest, on 3 iron ingots. The heart of a party: it runs the turns, the mini-games and the prizes."],
        ["Une carte, du verre et une torche de redstone au-dessus de lingots d'or et d'un coffre, sur 3 lingots de fer. Le cœur d'une partie : il enchaîne les tours, les mini-jeux et les gains."]))

q("dashboard", 2.75, -0.25, C(), deps=["controller"], rewards=T1(), icon="steveparty:party_controller",
  title=("The dashboard", "Le tableau de bord"),
  desc=(["Right-click it: five tabs (Status, Players, Program, Prizes, Settings). The Status tab lists what is missing before the \"Start the party\" button lights up. A \"!\" on a tab shows what to fix."],
        ["Clic droit : cinq onglets (État, Joueurs, Programme, Gains, Réglages). L'onglet État liste ce qui manque avant que « Lancer la partie » s'allume. Un « ! » sur un onglet montre quoi régler."]))

q("bank", 7.75, 1.25, C(), deps=["dashboard"], rewards=T1(), icon="minecraft:chest",
  title=("Fill the bank", "Remplir la banque"),
  desc=(["Nothing comes from nothing: the controller creates no coins and no stars. The Bank button of the Prizes tab opens its own 27 slots (hoppers work too); fill them with the party's coins and stars. Prizes, sold stars, coin faces, pots and spaces without a chest are paid from there, and fines go back there.",
         "An Inventory Cartridge next to the Bank button adds its linked chests."],
        ["Rien ne naît de rien : le contrôleur ne crée ni pièce ni étoile. Le bouton Banque de l'onglet Gains ouvre ses 27 emplacements (les entonnoirs marchent aussi) ; remplissez-les des pièces et étoiles de la partie. Les gains, les étoiles vendues, les faces Pièces, les pots et les cases sans coffre sont payés de là, et les amendes y retournent.",
         "Une Cartouche d'inventaire à côté du bouton Banque y ajoute ses coffres liés."]))

q("currency", 9.25, 1.25, C(), deps=["bank"], rewards=T1(), icon="minecraft:gold_nugget",
  title=("Your own currency", "Votre propre monnaie"),
  desc=(["Above the columns of the Prizes tab: the item used as the Coin (Coin by default) and as the Star (Party Star by default). Click with any item to use it instead, gold nuggets or diamonds for example (nothing is taken); empty hand for the default. Messages, menus and spaces then name your items."],
        ["Au-dessus des colonnes de l'onglet Gains : l'objet qui sert de Pièce (la Pièce par défaut) et d'Étoile (la Party Star par défaut). Cliquez avec n'importe quel objet pour le remplacer, des pépites d'or ou des diamants par exemple (rien n'est pris) ; main vide pour revenir au défaut. Messages, menus et cases nomment alors vos objets."]))

q("first_party", 6, -0.75, C(), deps=["dashboard", "pawns.bind"], rewards=MILESTONE(), icon="minecraft:firework_rocket",
  shape="hexagon", size=1.25,
  title=("Let's party!", "Que la fête commence !"),
  desc=(["Pawns bound to start spaces, then \"Start the party\" (or a redstone pulse). Everyone rolls for the order, then each round is one turn per pawn and a mini-game. Stars first, then coins, decide the winner."],
        ["Des pions liés aux cases de départ, puis « Lancer la partie » (ou une impulsion redstone). Chacun lance un dé pour l'ordre, puis chaque manche = un tour par pion et un mini-jeu. Les étoiles, puis les pièces, désignent le gagnant."]))

q("hud", 8.25, -0.5, C(), deps=["first_party"], rewards=T0(), optional=True, icon="minecraft:painting",
  title=("Arrange the HUD", "Disposer l'interface"),
  desc=(["During a party, the turn strip, announcements and scoreboard appear on screen. Press M (or /partyhud) to move them, H to hide them."],
        ["Pendant une partie, la bande des tours, les annonces et le tableau des scores s'affichent. Touche M (ou /partyhud) pour les déplacer, H pour les masquer."]))

q("step", 4, -1.5, I("step_controller"), deps=["controller"], rewards=T2(),
  desc=(["An hourglass: right-click stands it up (Next) or lays it on its side (Replay). A redstone pulse on Next skips what the party waits for: a turn not rolled yet, a waiting bell, the practice round; during a mini-game it ends it with the podiums' places. On Replay, the player plays again after this turn, or the round starts again.",
         "Link it with the Tile Linker Brush to any part of a game (space, Party Controller, Mini-game Controller, podium...), even far away; otherwise it drives the nearest party within 64 blocks."],
        ["Un sablier : clic droit pour le dresser (Suivant) ou le coucher (Rejouer). Une impulsion redstone sur Suivant passe ce que la partie attend : un tour pas encore lancé, une cloche qui retient, la manche d'essai ; pendant un mini-jeu, elle le termine avec les places des podiums. Sur Rejouer, le joueur rejoue après ce tour, ou la manche recommence.",
         "Reliez-le au Pinceau lieur à n'importe quel élément d'une partie (case, Party Controller, Contrôleur de mini-jeu, podium...), même très loin ; sinon il pilote la partie la plus proche à 64 blocs."]))

q("bell", 4, -4, I("party_bell"), deps=["controller"], rewards=T2(),
  desc=(["Right-click to choose a moment (party start, turn start, die rolled, mini-game end...): it sends a redstone pulse then, and a comparator reads a value (die result, pawn rank...). In wait mode (Wrench) the party freezes until the bell gets a signal."],
        ["Clic droit pour choisir un moment (début de partie, début de tour, dé lancé, fin de mini-jeu...) : elle envoie alors une impulsion, et un comparateur lit une valeur (résultat du dé, rang du pion...). En mode attente (Clé), la partie se fige jusqu'à ce qu'elle reçoive un signal."]))

q("piggy", 4, -2.75, I("piggy_bank"), deps=["controller", "board.inventory"], rewards=T2(),
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
    q("card_" + card, 1.5 + i * 1.35, 2.5, I("party_card_" + card, 4), deps=["dashboard"], rewards=T1(),
      desc=([f"A {EN_COLOR[stud]} plastic stud + paper + redstone: 4 cards. " + en[0],
             "Cards go in the 24 slots of the Program tab and are read left to right."],
            [f"Un plot en plastique {FR_COLOR[stud]} + papier + redstone : 4 cartes. " + fr[0],
             "Les cartes se posent dans les 24 emplacements de l'onglet Programme et se lisent de gauche à droite."]))

q("program", 6.75, 5, C(), deps=["card_turns", "card_minigame", "card_sequence_start", "card_repeat"], rewards=MILESTONE(),
  icon="steveparty:party_card_repeat", shape="hexagon", size=1.25,
  title=("Write a program", "Écrire un programme"),
  desc=(["Build your own party: for example Sequence Start, Turns, Repeat x3, Sequence Start, Mini-game, Repeat x2. Without cards, the party plays the number of rounds set in Settings."],
        ["Écrivez votre propre partie : par exemple Début de séquence, Tour de jeu, Répéter x3, Début de séquence, Mini-jeu, Répéter x2. Sans carte, la partie joue le nombre de manches réglé dans les Réglages."]))

q("trap", 8.25, -2, I("powerup_trap"), deps=["first_party", "welcome.coin"], rewards=T1(),
  desc=(["A Coin, a stone pressure plate and string, shapeless. A power-up: during your turn, before rolling, right-click to set it on the space where your pawn stands. Blank, it steals 10 coins; sneak + right-click to choose what it does (coins, steps back, a lost turn, an item) and sign it.",
         "The next pawn of another player that stops there springs it, for you."],
        ["Une Pièce, une plaque de pression en pierre et de la ficelle, sans forme. Un bonus : pendant votre tour, avant de lancer, clic droit pour le poser sur la case où se tient votre pion. Vierge, il vole 10 pièces ; accroupi + clic droit pour choisir ce qu'il fait (pièces, recul, tour perdu, un objet) et le signer.",
         "Le prochain pion d'un autre joueur qui s'y arrête le déclenche, à votre profit."]))

q("star_space", 6, -2.75, I("star_cartridge"), deps=["controller", "mulas.power_star"], rewards=T2(),
  desc=(["Cartridge + Party Star. At the start of a party, the party's star lands on one of the Star spaces, under a golden beam. A Mula waits there with the star and plays with it (on the space's Spawn Bubble, if linked). Its menu: the price (20 coins by default), sold on landing or in passing."],
        ["Cartouche + Party Star. Au début d'une partie, l'étoile de la partie se pose sur l'une des cases Étoile, sous un faisceau doré. Une Mula y attend avec l'étoile et joue avec (sur la Bulle d'apparition de la case, si elle est reliée). Son menu : le prix (20 pièces par défaut), vendue à l'arrêt ou au passage."]))

q("buy_star", 7.5, -3.75, C(), deps=["star_space", "first_party"], rewards=MILESTONE(), icon="steveparty:party_star",
  shape="hexagon", size=1.25,
  title=("Catch a star!", "Attrapez une étoile !"),
  desc=(["Reach the star during a party and buy it ([Buy] in the chat): the price goes to the bank, a star from the bank goes to you. The Mula dances, throws you the star and vanishes; the star may move on to another Star space."],
        ["Atteignez l'étoile pendant une partie et achetez-la ([Acheter] dans le chat) : le prix part à la banque, une étoile de la banque vient à vous. La Mula danse, vous lance l'étoile et disparaît ; l'étoile peut partir sur une autre case Étoile."]))

done("done", 10.5, -2, ["first_party", "step", "bell", "piggy", "program", "bank", "currency", "buy_star"],
     ("Party host", "Maître de cérémonie"),
     (["You can run a whole party, and drive it with redstone."],
      ["Vous savez mener une partie entière, et la piloter à la redstone."]),
     "steveparty:party_bell")


# === Mini-games ================================================================================
chapter("minigames", "board_game", "steveparty:mini_game_page",
        ("Mini-games", "Mini-jeux"),
        (["Pages, pipes, podiums and arenas: design your own mini-games."],
         ["Pages, tuyaux, podiums et arènes : créez vos propres mini-jeux."]))

q("page", 0, 0, I("mini_game_page", 4), deps=["board.cartridge"], rewards=T2(), size=1.25,
  desc=(["A Cartridge surrounded by 4 paper: 4 Mini-Game Pages. A page IS a mini-game: its title, rules, image, formats, pipes, results and zone."],
        ["Une Cartouche entourée de 4 papiers : 4 Pages de mini-jeu. Une page EST un mini-jeu : son titre, ses règles, son image, ses formats, ses tuyaux, ses résultats et sa zone."]))

q("write_page", 5.75, -4.5, C(), deps=["page"], rewards=T1(), icon="minecraft:writable_book",
  title=("Write the page", "Écrire la page"),
  desc=(["Right-click in the air with the page: the editor opens. Give it a title, a description (bold, italic, colours, 400 characters) and an image (PNG or JPEG dropped on the window). The image shows on the big card when the mini-game is drawn."],
        ["Clic droit dans le vide avec la page : l'éditeur s'ouvre. Donnez-lui un titre, une description (gras, italique, couleurs, 400 caractères) et une image (PNG ou JPEG déposé sur la fenêtre). L'image s'affiche sur la grande carte quand le mini-jeu est tiré."]))

q("formats", 9.5, -4.75, C(), deps=["write_page"], rewards=T1(), icon="minecraft:white_banner",
  title=("Formats", "Formats"),
  desc=(["Formats tab: team games (2 to 4 sides, \"1 vs all\", \"2 vs 2\"...), free-for-all or all together. In a party only a page whose format fits the teams on the board can be drawn. Team A is the players on positive spaces, team B those on negative ones."],
        ["Onglet Formats : jeux en équipes (2 à 4 camps, « 1 contre tous », « 2 contre 2 »...), chacun pour soi ou tous ensemble. En partie, seule une page dont un format convient aux équipes du plateau peut être tirée. L'équipe A, ce sont les joueurs sur une case positive, l'équipe B ceux sur une case négative."]))

q("zone", 8.5, -4, C(), deps=["write_page"], rewards=T1(), icon="minecraft:structure_void",
  title=("Draw the zone", "Tracer la zone"),
  desc=(["\"Draw the zone\" in the editor, then click two corners (128 blocks per side at most). With the Restore option, each round plays apart in the zone with an empty round inventory, and the arena is put back as built at the end."],
        ["« Tracer la zone » dans l'éditeur, puis cliquez deux coins (128 blocs par côté au plus). Avec l'option Restaurer, chaque manche se joue à part dans la zone, avec un inventaire de manche vide, et l'arène est remise comme construite à la fin."]))

q("linked_copy", 4, -4.5, C(), deps=["write_page"], rewards=T0(), optional=True, icon="minecraft:paper",
  title=("Linked copies", "Copies liées"),
  desc=(["Page + paper at the crafting table (or the Copy button) makes a linked copy: editing one edits them all. Handy to link pipes and podiums far apart."],
        ["Page + papier à l'établi (ou le bouton Copier) donnent une copie liée : modifier l'une modifie toutes les autres. Pratique pour lier tuyaux et podiums éloignés."]))

q("catalogue", 7.75, -3, I("mini_games_catalogue"), deps=["page", "party.controller"], rewards=T2(),
  desc=(["Book and quill + map + clock + the \"otherside\" disc, shapeless. Holds 91 pages. Put it in the Party Controller: the roulette draws its mini-games."],
        ["Livre et plume + carte + horloge + disque « otherside », sans forme. Contient 91 pages. Posez-le dans le Party Controller : la roulette y tire les mini-jeux."]))

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
  desc=(["Page in hand, click a pipe mouth to link it (click again to unlink); linked pipes glow while you hold the page. When the mini-game starts, each player comes out of a pipe of their role."],
        ["Page en main, cliquez l'embouchure d'un tuyau pour le lier (re-clic pour le délier) ; les tuyaux liés brillent tant que vous tenez la page. Au départ du mini-jeu, chaque joueur sort d'un tuyau de son rôle."]))

# Podiums
q("podium", 4.5, 0, I("podium"), deps=["page"], rewards=T2(),
  desc=(["Gold, a heavy pressure plate, redstone and quartz blocks. Podiums record who won: the highest column is first place. Sneak on one or right-click it to take it. Link podiums to the page by clicking them with it."],
        ["De l'or, une plaque de pression lourde, de la redstone et des blocs de quartz. Les podiums retiennent qui a gagné : la colonne la plus haute est la 1re place. Accroupissez-vous dessus ou clic droit pour le prendre. Liez-les à la page en cliquant dessus avec elle."]))

# The chapter's milestone gathers 10 arrows: its prerequisites stand on an arc around it.
for metal, en_m, fr_m, x, y in [("gold", "a gold ingot", "un lingot d'or", 7, -2),
                                ("silver", "an iron ingot", "un lingot de fer", 6.75, -0.75),
                                ("bronze", "a copper ingot", "un lingot de cuivre", 6.75, 0.75)]:
    q(metal + "_podium", x, y, I(metal + "_podium"), deps=["podium"], rewards=T1(),
      desc=([f"A Podium + {en_m}, shapeless. Only a look: the height of the column decides the place."],
            [f"Un Podium + {fr_m}, sans forme. Seulement une apparence : c'est la hauteur de la colonne qui donne la place."]))

q("results", 7, 2, C(), deps=["podium"], rewards=T1(), icon="steveparty:gold_podium",
  title=("Results", "Résultats"),
  desc=(["The Results tab of the page lists what ends the mini-game: linked podiums, goal pole bases (counters) and Step Controllers. A pulse on a podium, set with the Wrench, can register the nearest player or reset the group."],
        ["L'onglet Résultats de la page liste ce qui termine le mini-jeu : podiums liés, socles de mât d'arrivée (compteurs) et Contrôleurs de pas. Une impulsion sur un podium, réglé à la Clé, peut inscrire le joueur le plus proche ou vider le groupe."]))

q("mg_controller", 5, 2.5, I("mini_game_controller"), deps=["page", "copper_pipe"], rewards=T2(),
  desc=(["Paper, glass and a redstone torch over gold ingots and any mini-game pipe, on 3 iron ingots. Placed in the arena, it holds the mini-game's page. Its lamp is red (idle), orange (trial round) or green (playing). A comparator reads its state."],
        ["Du papier, du verre et une torche de redstone au-dessus de lingots d'or et d'un tuyau de mini-jeu (n'importe lequel), sur 3 lingots de fer. Posé dans l'arène, il reçoit la page du mini-jeu. Sa lampe est rouge (repos), orange (manche d'essai) ou verte (en jeu). Un comparateur lit son état."]))

q("remote", 6.5, 4.25, I("mini_game_remote"), deps=["mg_controller"], rewards=T1(),
  desc=(["A redstone torch, gold nuggets, a glass pane and a stone button. Sneak + click a Mini-game Controller to link it: out of a party it plays or stops that mini-game from afar; in a party it says whether you are ready for the real round."],
        ["Une torche de redstone, des pépites d'or, une vitre et un bouton en pierre. Accroupi + clic sur un Contrôleur de mini-jeu pour la lier : hors partie, elle lance ou arrête ce mini-jeu à distance ; en partie, elle dit si vous êtes prêt pour la vraie manche."]))

q("test_run", 7.75, 3.25, C(), deps=["mg_controller", "link_pipes"], rewards=MILESTONE(), icon="steveparty:mini_game_controller",
  shape="hexagon", size=1.25,
  title=("Test run", "Partie d'essai"),
  desc=(["Press \"Play\" on the Mini-Game Controller (or \"Test\" in the page editor): everyone within 5 blocks of a linked pipe plays, without a party. Results show what each place would earn, nothing is paid, and everyone comes back."],
        ["Appuyez sur « Jouer » sur le Contrôleur de mini-jeu (ou « Tester » dans l'éditeur de page) : tous les joueurs à 5 blocs d'un tuyau lié jouent, sans partie. Les résultats montrent ce que vaudrait chaque place, rien n'est payé, et tout le monde revient."]))

q("trial", 8.75, 4.25, C(), deps=["test_run", "party.first_party"], rewards=T2(), icon="minecraft:lime_dye",
  title=("Ready? Press Y", "Prêt ? Touche Y"),
  desc=(["In a party, a mini-game whose page is in a controller starts with a trial round, for nothing. Each player presses Y (or the Ready button) when ready; when everyone is, the real round starts and pays prizes."],
        ["En partie, un mini-jeu dont la page est dans un contrôleur commence par une manche d'essai, pour du beurre. Chaque joueur appuie sur Y (ou le bouton Prêt) quand il est prêt ; quand tout le monde l'est, la vraie manche commence et paie les gains."]))

# Mini-game pipes
q("copper_pipe", 10, 4.75, I("copper_minigame_pipe"), deps=["link_pipes"], rewards=T2(),
  desc=(["6 copper ingots around a paper. Click it with a page: it becomes the entrance to that mini-game outside a party, within 100 blocks. Coloured pipes linked to it send each player to their role."],
        ["6 lingots de cuivre autour d'un papier. Cliquez-le avec une page : il devient l'entrée de ce mini-jeu hors partie, à 100 blocs. Les tuyaux de couleur reliés à lui envoient chaque joueur vers son rôle."]))

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

q("pole", 2, 0.625, I("goal_pole"), deps=["base"], rewards=T1(),
  desc=(["A gold ingot over two iron bars, in a column. Stack as many segments as you like on the base."],
        ["Un lingot d'or sur deux barreaux de fer, en colonne. Empilez autant de segments que vous voulez sur le socle."]))

q("flag", 4, -0.625, I("flag"), deps=["pole"], rewards=T1(),
  desc=(["Three wool: the colour of the wool makes the flag's (mixed like leather armour). Right-click a segment to hang it; it slides down when the goal is reached. Dye it again in the grid."],
        ["Trois laines : la couleur de la laine fait celle du drapeau (mélangée comme le cuir). Clic droit sur un segment pour l'accrocher ; il descend quand le but est atteint. Reteignez-le dans la grille."]))

q("one_up", 4, 0.625, A("main/pole/top"), deps=["pole"], rewards=T2(), icon="minecraft:golden_apple",
  title=("Extra Life!", "Vie bonus !"),
  desc=(["Land on top of a pole: \"+1 ♥\", an extra life: a golden heart for 10 s, no fall damage, and you stand upside down. Each landing scores a point for the base."],
        ["Atterrissez au sommet d'un mât : « +1 ♥ », une vie bonus : un cœur doré pendant 10 s, aucun dégât de chute, et vous voilà tête en bas. Chaque arrivée marque un point pour le socle."]))

q("goal", 4, 1.875, C(), deps=["pole", "board.wrench"], rewards=T1(), icon="minecraft:target",
  title=("Set a goal", "Régler un but"),
  desc=(["Wrench on a segment: the pole lights up when the total is at least (or exactly, or less than) a value. \"Goal: per player\" gives each player who reaches it the highest free podium place."],
        ["Clé sur un segment : le mât s'allume quand le total atteint (ou égale, ou reste sous) une valeur. « But : par joueur » donne à chaque joueur qui l'atteint la plus haute place libre des podiums."]))

q("criterion", 5.75, 2.75, C(), deps=["goal"], rewards=T1(), optional=True, icon="minecraft:redstone",
  title=("Count anything", "Tout compter"),
  desc=(["The base can count a scoreboard criterion instead of landings: jumps, deaths, kills... For example 10 jumps, goal per player, and podiums next to the base."],
        ["Le socle peut compter un critère de scoreboard au lieu des arrivées : sauts, morts, éliminations... Par exemple 10 sauts, but par joueur, et des podiums contre le socle."]))

q("shoes", 4, -1.875, I("triple_jump_shoes"), deps=["base"], rewards=T2(),
  desc=(["Leather boots + rabbit's foot + 2 slime balls, shapeless. Chain jumps within 1.5 s: the second is 1.5 times higher, the third twice as high. Made for poles."],
        ["Bottes en cuir + patte de lapin + 2 boules de slime, sans forme. Sauts enchaînés à moins de 1,5 s : le 2e est 1,5 fois plus haut, le 3e deux fois. Parfaits pour les mâts."]))

done("done", 7, 0, ["one_up", "flag", "goal", "shoes"],
     ("Goal!", "But !"),
     (["Your finish line is ready: jump, score, win."],
      ["Votre ligne d'arrivée est prête : sautez, marquez, gagnez."]),
     "steveparty:flag")


# === Shops =====================================================================================
chapter("shops", "commerce", "steveparty:trading_stall",
        ("Shops", "Boutiques"),
        (["A villager hidden in a box sells the offers of your stalls."],
         ["Un villageois caché dans un carton vend les offres de vos étals."]))

q("villager_block", 0, 0, I("villager_block", how="own"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["A downward piston extending onto an adult villager's head squashes it into a block that keeps the villager inside, alive, with about 70 reactions. Break it by hand (it begs) to pick it up; a sticky piston lets it out."],
        ["Un piston tourné vers le bas qui s'étend sur la tête d'un villageois adulte l'écrase en un bloc qui garde le villageois dedans, vivant, avec environ 70 réactions. Cassez-le à la main (il supplie) pour le ramasser ; un piston collant le fait ressortir."]))

q("trader", 2, 0, C(), deps=["villager_block"], rewards=T3(), icon="steveparty:boxed_trader_spawn_egg",
  title=("The fall", "La chute"),
  desc=(["Place the Villager Block on the block to imitate, then fall on it from high enough (12 blocks on dirt or hay, 16 on stone, 30 on iron): a Boxed Trader appears, disguised as that block. Tick this quest once you have made one."],
        ["Posez le Bloc de villageois sur le bloc à imiter, puis tombez dessus d'assez haut (12 blocs sur terre ou foin, 16 sur pierre, 30 sur fer) : un Marchand en carton apparaît, déguisé en ce bloc. Cochez la quête une fois le vôtre créé."]))

q("stall", 4, -1.25, I("trading_stall"), deps=["trader"], rewards=T1(),
  desc=(["Two carpets over two barrels; the carpets give the awning's colours. Each column is an offer (9 max): the goods on top, prices 1 and 2 below. The goods are only a model: the sale takes the same item from the linked chests."],
        ["Deux tapis au-dessus de deux tonneaux ; les tapis donnent les couleurs de l'auvent. Chaque colonne est une offre (9 max) : la marchandise en haut, les prix 1 et 2 dessous. La marchandise n'est qu'un modèle : la vente prend le même objet dans les coffres liés."]))

q("register", 4, 0, I("cash_register"), deps=["trader"], rewards=T1(),
  desc=(["Stone buttons, gold, a chest and iron. Payments go into the linked registers; each sale sends a redstone pulse. Mine it with an iron pickaxe."],
        ["Des boutons en pierre, de l'or, un coffre et du fer. Les paiements arrivent dans les caisses liées ; chaque vente envoie une impulsion redstone. Se récupère avec une pioche en fer."]))

q("key", 4, 1.25, I("shopkeeper_key"), deps=["trader"], rewards=T1(),
  desc=(["A star fragment (any colour), a gold ingot and a gold nugget, in a column. Right-click the trader to link it (the first to do so owns it), then each block: stall, stock chests, registers."],
        ["Un fragment d'étoile (n'importe quelle couleur), un lingot d'or et une pépite d'or, en colonne. Clic droit sur le marchand pour la lier (le premier devient propriétaire), puis sur chaque bloc : étal, coffres de stock, caisses."]))

q("open_shop", 6, 0, C(), deps=["stall", "register", "key"], rewards=MILESTONE(), icon="minecraft:emerald",
  shape="hexagon", size=1.25,
  title=("Open for business", "Ouvert !"),
  desc=(["Trader, stall, a stock chest and a register, all linked with the key; an offer on the stall and stock in the chest. Customers right-click the trader: its cardboard screen shows the offers as cards (price → item; red when you can't pay, crossed out when sold out). The owner's shop is protected."],
        ["Marchand, étal, un coffre de stock et une caisse, tous liés avec la clé ; une offre sur l'étal et du stock dans le coffre. Les clients font clic droit sur le marchand : son écran en carton montre les offres en cartes (prix → objet ; en rouge si on ne peut pas payer, barrée si épuisée). La boutique du propriétaire est protégée."]))

q("shop_stop", 7.75, -1.25, C(), deps=["board.shop"], rewards=T2(), icon="steveparty:shop_cartridge",
  title=("Shop stop", "Halte boutique"),
  desc=(["In a party, a pawn stops on a Shop space: its own Boxed Trader pops out, with its stall. Its player right-clicks the trader (or clicks [Open the shop] in the chat) and buys up to the cartridge's limit (1 by default); the turn waits for the purchases. Payments go to the bank."],
        ["En partie, un pion s'arrête sur une case Boutique : son propre Marchand en carton surgit, avec son étal. Son joueur fait clic droit sur le marchand (ou clique [Ouvrir la boutique] dans le chat) et achète jusqu'à la limite de la cartouche (1 par défaut) ; le tour attend la fin des achats. Les paiements vont à la banque."]))

q("bandana", 2, 2.25, I("bandana", how="own"), deps=["trader"], rewards=T1(),
  desc=(["Shears on an open trader that wears its bandana: it falls, and the trader hides for 20 s. Wear it on your head: traders ignore you. Give one back to a bald trader to change its colour."],
        ["Des cisailles sur un marchand ouvert qui porte son bandana : il tombe, et le marchand se cache 20 s. Portez-le sur la tête : les marchands vous ignorent. Rendez-en un à un marchand chauve pour changer sa couleur."]))

q("costume", 4, 2.75, I("box_costume", how="own"), deps=["bandana"], rewards=T2(),
  desc=(["Shears again on a trader that has already lost its bandana: you steal the look of its box. Wear it as a chestplate; sneak to hide inside: stand still 2 s and you become a real block."],
        ["Encore des cisailles sur un marchand qui a déjà perdu son bandana : vous lui volez l'apparence de sa boîte. Portez-la comme un plastron ; accroupi, vous vous cachez dedans : immobile 2 s, vous devenez un vrai bloc."]))

done("done", 9.5, 0.25, ["shop_stop", "open_shop", "costume"],
     ("Shopkeeper", "Commerçant"),
     (["Your shop is open, on and off the board."],
      ["Votre boutique est ouverte, sur le plateau comme ailleurs."]),
     "steveparty:shopkeeper_key")


# === Mulas and stars ===========================================================================
chapter("mulas", "stars", "steveparty:party_star",
        ("Mulas and Stars", "Mulas et étoiles"),
        (["Little flying star creatures, and the 16 star fragments they leave behind."],
         ["De petites créatures d'étoile volantes, et les 16 fragments d'étoile qu'elles laissent."]))

q("find", 0, 2, O("steveparty:mula"), deps=["welcome.done"], rewards=T3(), icon="steveparty:mula_spawn_egg", size=1.25,
  title=("A Mula!", "Une Mula !"),
  desc=(["Mulas fall from the sky on shooting-star nights (Ephemeride), mostly at full moon: follow the direction of the first wave of stars, 150 to 600 blocks away. Look at a Mula to complete this quest."],
        ["Les Mulas tombent du ciel les nuits d'étoiles filantes (Éphéméride), surtout à la pleine lune : suivez la direction de la première vague d'étoiles, à 150 à 600 blocs. Regardez une Mula pour valider la quête."]))

q("ephemeride", 0, 6.75, C(), deps=["welcome.done"], rewards=T2(), icon="minecraft:clock",
  title=("Shooting-star night", "Nuit d'étoiles filantes"),
  desc=(["At dusk, a full moon has a 25% chance of an Ephemeride: 6 waves of low shooting stars over each player, then a group of 3 to 5 Mulas lands where they went. A Dice Forge at full power guarantees it every full moon."],
        ["Au crépuscule, une pleine lune a 25 % de chances d'apporter une Éphéméride : 6 vagues d'étoiles filantes basses au-dessus de chaque joueur, puis un groupe de 3 à 5 Mulas descend là où elles sont allées. Une Forge à dés au niveau maximal la garantit à chaque pleine lune."]))

q("telescope", 2, 6.75, I("telescope"), deps=["ephemeride"], rewards=T2(),
  desc=(["A spyglass on a copper ingot, over 3 sticks. At night, under a clear sky, look into it: the scroll wheel replays past shooting-star nights within 1000 blocks. Keep a star in the sights to light a guide star above the Mulas."],
        ["Une longue-vue sur un lingot de cuivre, au-dessus de 3 bâtons. La nuit, ciel dégagé, regardez dedans : la molette rejoue les nuits d'étoiles filantes passées, à 1000 blocs. Gardez une étoile dans le viseur pour allumer une étoile guide au-dessus des Mulas."]))

q("feed", 2, 2, A("stars/feed"), deps=["find"], rewards=T1(), icon="minecraft:cooked_cod",
  title=("Snack time", "À table !"),
  desc=(["A Mula eats only edible food of its colour: blue = cod, red = apples, berries, salmon and raw meat, green = melon, yellow = golden food, bread and honey, purple = chorus and beetroot, black = dried kelp, cookies, stews and steak. The tooltip tells \"Mula food\"."],
        ["Une Mula ne mange que la nourriture de sa couleur : bleu = morue, rouge = pommes, baies, saumon et viande crue, vert = pastèque, jaune = nourriture dorée, pain et miel, violet = chorus et betterave, noir = algue séchée, cookies, ragoûts et steak. L'infobulle indique « Nourriture de Mula »."]))

q("burst", 4, 1.25, C(), deps=["feed"], rewards=T2(), icon="minecraft:firework_star", shape="hexagon", size=1.25,
  title=("Pop!", "Pop !"),
  desc=(["Fed to 40, the Mula grows three times bigger and bursts: 64 star fragments of its colour (a black one: 1). It flies away as a shooting star and is reborn 100 to 400 blocks away."],
        ["Nourrie jusqu'à 40, la Mula grossit trois fois et éclate : 64 fragments d'étoile de sa couleur (une noire : 1). Elle part en étoile filante et renaît 100 à 400 blocs plus loin."]))

q("tame", 4, 5.5, A("stars/tame"), deps=["feed"], rewards=T2(), icon="steveparty:yellow_star_fragment",
  title=("Best friend", "Meilleure amie"),
  desc=(["Right-click a wild Mula with a star fragment of its own colour: 1 chance in 3. Tamed, it floats above you like a balloon and orbits your head when you stand still."],
        ["Clic droit sur une Mula sauvage avec un fragment d'étoile de sa couleur : 1 chance sur 3. Apprivoisée, elle flotte au-dessus de vous comme un ballon et tourne autour de votre tête quand vous restez immobile."]))

q("balloon", 6, 4.5, C(), deps=["tame"], rewards=T1(), optional=True, icon="minecraft:lead",
  title=("Up, up and away", "Envol en laisse"),
  desc=(["At night, Mulas on a lead can lift you up like balloons: one big Mula, two half-full ones or three of any size. You land softly at dawn."],
        ["La nuit, des Mulas tenues en laisse peuvent vous emporter comme des ballons : une grosse, deux à moitié pleines ou trois de n'importe quelle taille. Vous redescendez en douceur au lever du jour."]))

# Natural fragments in a column (y), ordered top to bottom so the colour mixes stay close to their parents.
NATURAL = {"blue": 3, "red": -0.25, "yellow": -1.75, "green": 1.25, "purple": -3.25, "black": 6.5}
for c, y in NATURAL.items():
    extra_en = " (only one, and black Mulas are rare)." if c == "black" else "."
    extra_fr = " (un seul, et les Mulas noires sont rares)." if c == "black" else "."
    q("frag_" + c, 6, y, I(f"{c}_star_fragment", how="own"), deps=["burst"],
      rewards=[XP(30), COINS(2)] if c == "black" else T1(),
      desc=([f"Dropped by a {EN_COLOR[c]} Mula when it bursts{extra_en}"],
            [f"Lâché par une Mula {FR_COLOR[c]} quand elle éclate{extra_fr}"]))

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
            [f"Mélangez les fragments comme des colorants : {fr_mix}. On récupère autant de fragments qu'on en a mis."]))

q("frag_block", 10, 6, I("blue_star_fragments_block"), deps=["frag_blue"], rewards=T1(),
  desc=(["9 fragments of one colour: a glowing, glass-like block of animated plasma (16 colours, 12 variants each). The black one is a starry night. It attracts curious Mulas."],
        ["9 fragments d'une couleur : un bloc lumineux, translucide, de plasma animé (16 couleurs, 12 variantes chacune). Le noir est une nuit étoilée. Il attire les Mulas curieuses."]))

# Key kept from the Power Star days: quest ids derive from keys, renaming it would reset players' progress.
q("power_star", 10, -1.75, I("party_star"), deps=["frag_blue", "frag_green", "frag_yellow", "frag_red", "frag_purple"],
  rewards=[XP(100), COINS(5)], shape="hexagon", size=1.25,
  desc=(["One fragment each of blue, green, yellow, red and purple (or 5 black fragments), shapeless. Always shining. It is the default Star of a party, and goes into the Star Cartridge, the Lapis Crystal Ball, the Dice Forge and the iron and golden mini-game pipes."],
        ["Un fragment de chaque : bleu, vert, jaune, rouge et violet (ou 5 fragments noirs), sans forme. Toujours brillante. C'est l'Étoile par défaut d'une partie, et elle entre dans la Cartouche Étoile, la Boule de cristal en lapis, la Forge à dés et les tuyaux de mini-jeu en fer et en or."]))

q("luck", 4, -0.25, A("stars/luck_potion"), deps=["burst"], rewards=T1(), optional=True, icon="minecraft:potion",
  title=("Potion of Luck", "Potion de chance"),
  desc=(["Brewing stand: awkward potion + any star fragment gives a Potion of Luck (Luck I, 5:00), the only survival recipe for it."],
        ["Alambic : potion étrange + n'importe quel fragment d'étoile donne une Potion de chance (Chance I, 5:00), sa seule recette en survie."]))

done("done", 14, 3.5, ["power_star", "frag_magenta", "frag_light_gray", "frag_brown", "frag_lime", "frag_light_blue",
                       "frag_cyan", "frag_orange", "frag_black", "telescope", "tame"],
     ("Star collector", "Collectionneur d'étoiles"),
     (["All 16 colours of star fragments, and a Party Star of your own."],
      ["Les 16 couleurs de fragments d'étoile, et votre propre Party Star."]),
     "steveparty:white_star_fragments_block")


# === Dice Forge ================================================================================
chapter("forge", "stars", "steveparty:dice_forge",
        ("Dice Forge", "Forge à dés"),
        (["Engrave custom dice, and give the Mulas a home."],
         ["Gravez des dés sur mesure, et offrez une maison aux Mulas."]))

q("core", 0, 1.25, I("gravity_core"), deps=["mulas.frag_blue"], rewards=T3(), size=1.25,
  desc=(["Crying obsidian, 4 star fragments of any colour and a heavy core. Placed, it pulls everything within 8 blocks into orbit (no fall damage inside; full iron armour or heavier resists). It also powers the Dice Forge."],
        ["Obsidienne pleureuse, 4 fragments d'étoile de n'importe quelle couleur et un noyau lourd. Posé, il attire tout à 8 blocs en orbite (pas de dégâts de chute dans le champ ; une armure complète en fer ou plus lourde résiste). Il alimente aussi la Forge à dés."]))

q("forge", 0, -1.25, I("dice_forge"), deps=["mulas.power_star"], rewards=T3(), size=1.25,
  desc=(["A Party Star on top, obsidian, a blast furnace and a netherite ingot. It engraves dice with the faces and weights you choose, in a loop, by hand, hoppers or redstone."],
        ["Une Party Star en haut, de l'obsidienne, un haut fourneau et un lingot de netherite. Elle grave des dés avec les faces et les poids que vous choisissez, en boucle, à la main, par entonnoirs ou à la redstone."]))

q("ignite", 2, 0, A("stars/ignition"), deps=["core", "forge"], rewards=T2(), icon="steveparty:gravity_core",
  title=("Ignite the forge", "Allumer la forge"),
  desc=(["Right-click the forge with the Gravity Core: it sinks in, then rises and floats above the forge. Sneak + right-click empty-handed takes it back. Hitting the floating core makes it explode (and it is lost)."],
        ["Clic droit sur la forge avec le Noyau de gravité : il s'enfonce, puis s'élève et flotte au-dessus. Accroupi + clic droit à mains vides pour le reprendre. Frapper le noyau en l'air le fait exploser (et il est perdu)."]))

q("forged", 4, -1.5, C(), deps=["ignite", "dice.blank"], rewards=MILESTONE(), icon="steveparty:default_dice",
  shape="hexagon", size=1.25,
  title=("Your own die", "Votre propre dé"),
  desc=(["Fill the ring with 1 to 12 faces (the stack size is the weight), the 5 fragment slots with 5 different colours (black can repeat and is never used up), and blank faces. Click the arrow between the blank faces and the die: FORGE! One die every 5 s."],
        ["Remplissez l'anneau de 1 à 12 faces (la taille de la pile est le poids), les 5 emplacements de fragments de 5 couleurs différentes (le noir peut se répéter et n'est jamais consommé), et des faces vierges. Cliquez la flèche entre les faces vierges et le dé : FORGER ! Un dé toutes les 5 s."]))

q("automate", 5.75, -2.75, C(), deps=["forged"], rewards=T1(), optional=True, icon="minecraft:hopper",
  title=("Dice factory", "Usine à dés"),
  desc=(["Hoppers on top and sides fill the forge, a hopper below takes the dice out. Powered by redstone, it restarts on its own once restocked. A comparator reads the output."],
        ["Des entonnoirs dessus et sur les côtés remplissent la forge, un entonnoir dessous sort les dés. Alimentée en redstone, elle repart seule une fois réapprovisionnée. Un comparateur lit la sortie."]))

q("max_level", 4, 0, C(), deps=["ignite"], rewards=T2(), icon="steveparty:black_star_fragment",
  title=("Highest orbit", "Orbite maximale"),
  desc=(["The core climbs with the fragments in the forge: 320 fragments (5 full stacks, or 5 black ones, each black counts as 64) lift it 16 blocks up. A forge at max level guarantees an Ephemeride every full moon."],
        ["Le noyau monte avec les fragments présents : 320 fragments (5 piles pleines, ou 5 noirs, chaque noir compte pour 64) le hissent à 16 blocs. Une forge au niveau maximal garantit une Éphéméride à chaque pleine lune."]))

q("dance", 4, 1.5, C(), deps=["ignite", "mulas.find"], rewards=T2(), icon="steveparty:mula_spawn_egg",
  title=("Mula dance hall", "Le bal des Mulas"),
  desc=(["Any Mula within 16 blocks of a lit forge moves in and dances around the core: 10 dances of 26 s each, in rings of up to 12. A full Mula that bursts at home lands back 2 to 4 blocks from the forge."],
        ["Toute Mula à moins de 16 blocs d'une forge allumée s'y installe et danse autour du noyau : 10 danses de 26 s, en anneaux de 12 au plus. Une Mula pleine qui éclate à la maison retombe à 2 à 4 blocs de la forge."]))

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
         ["Des oiseaux des forêts qui adorent ce qui brille, leurs nids, et le Pot commun qu'ils gardent."]))

q("find", 0, 0, O("steveparty:wild_magpie"), deps=["welcome.done"], rewards=[XP(25), IT("minecraft:wheat_seeds", 8)],
  icon="steveparty:magpie_spawn_egg", size=1.25,
  title=("A Magpie!", "Une pie !"),
  desc=(["Wild magpies live in forests, taigas and the cherry grove, 1 to 3 together, by day. They hop from perch to perch (fences, walls, logs, chains) and fly off when you come within 6 blocks. Six colours, depending on the forest. Look at one to complete this quest."],
        ["Les pies sauvages vivent dans les forêts, les taïgas et le bosquet de cerisiers, par 1 à 3, le jour. Elles sautent de perchoir en perchoir (barrières, murets, bûches, chaînes) et s'envolent quand on approche à moins de 6 blocs. Six couleurs, selon la forêt. Regardez-en une pour valider la quête."]))

q("seeds", 2, -1, C(), deps=["find"], rewards=T1(), icon="minecraft:wheat_seeds",
  title=("Seed charmer", "Charmeur de pies"),
  desc=(["Hold any seeds: magpies within 10 blocks stop fleeing, come to you in little hops and follow you. Chicks too."],
        ["Tenez des graines, n'importe lesquelles : les pies à 10 blocs ne fuient plus, viennent à vous par petits sauts et vous suivent. Les poussins aussi."]))

q("chick", 4, -1, C(), deps=["seeds"], rewards=T2(), icon="minecraft:egg",
  title=("A chick!", "Un poussin !"),
  desc=(["Feed seeds to two wild magpies within 8 blocks of each other: they fall in love and a chick hatches, of one parent's colour. In a nest it sits inside and sleeps there at night; it grows up in 20 minutes (seeds make it faster)."],
        ["Donnez des graines à deux pies sauvages à moins de 8 blocs l'une de l'autre : elles tombent amoureuses et un poussin naît, de la couleur de l'un des parents. Dans un nid, il reste assis dedans et y dort la nuit ; il grandit en 20 minutes (les graines l'accélèrent)."]))

q("nest", 2, 1, I("magpie_nest"), deps=["find"], rewards=T2(),
  desc=(["3 sticks + 1 feather, shapeless (magpies drop feathers). A nest of woven twigs: 27 slots of up to 256 coins or shiny items each. Right-click to put the stack in (sneak: one), empty hand to take the last one back. Hoppers fill and empty it, a comparator reads it, and a pile of coins rises from it."],
        ["3 bâtons + 1 plume, sans forme (les pies lâchent des plumes). Un nid de brindilles tressées : 27 emplacements de 256 pièces ou objets brillants chacun. Clic droit pour y mettre la pile (accroupi : un seul), main vide pour reprendre le dernier. Les entonnoirs le remplissent et le vident, un comparateur le lit, et une pile de pièces monte du nid."]))

q("shiny", 4, 1, C(), deps=["nest"], rewards=T1(), icon="minecraft:gold_nugget",
  title=("Thief of shiny things", "Voleuse de trésors"),
  desc=(["By day, a magpie picks up a shiny item lying on the ground within 12 blocks (gold, iron, copper, diamond, emerald, lapis, redstone, amethyst, coins...) and carries it to its nest; without a nest it adopts the nearest one. At night it sleeps in a free nest."],
        ["Le jour, une pie ramasse un objet brillant posé au sol à 12 blocs au plus (or, fer, cuivre, diamant, émeraude, lapis, redstone, améthyste, pièces...) et le rapporte à son nid ; sans nid, elle adopte le plus proche. La nuit, elle dort dans un nid libre."]))

q("tree_nest", 0, 2.5, C(), deps=["find"], rewards=T1(), optional=True, icon="minecraft:oak_leaves",
  title=("Nest hunting", "Chasse aux nids"),
  desc=(["In their forests, a nest sometimes sits on a treetop: often empty, sometimes with a few nuggets or coins, and 1 to 3 magpies live in it. It never rots with the leaves."],
        ["Dans leurs forêts, un nid se trouve parfois sur la cime d'un arbre : souvent vide, parfois avec quelques pépites ou pièces, et 1 à 3 pies y vivent. Il ne pourrit pas avec les feuilles."]))

q("pot", 6, 1, I("pot_cartridge"), deps=["nest", "board.cartridge"], rewards=T2(),
  desc=(["Cartridge + Magpie Nest, shapeless. A Common Pot space, the colour of straw. Click a Magpie Nest with the cartridge (or paint from the space to it with the Tile Linker Brush) to link it: that nest IS the pot."],
        ["Cartouche + Nid de pie, sans forme. Une case Pot commun, couleur paille. Cliquez un Nid de pie avec la cartouche (ou peignez de la case jusqu'à lui au Pinceau lieur) pour le relier : ce nid EST le pot."]))

q("pot_game", 8, 0, C(), deps=["pot", "party.first_party"], rewards=MILESTONE(), icon="steveparty:pot_cartridge",
  shape="hexagon", size=1.25,
  title=("The Common Pot", "Le Pot commun"),
  desc=(["A magpie comes to live on the linked nest. Every pawn that passes the space stakes coins (1 by default; less if its player has less), the magpie fetches them, and the pawn that stops there wins the whole nest. Its menu: stake, base (taken from the space's storage or the bank, never created) and cap. At the end of the party the nest's coins go back to that storage."],
        ["Une pie vient vivre sur le nid relié. Chaque pion qui passe sur la case mise des pièces (1 par défaut ; moins si son joueur en a moins), la pie va les chercher, et le pion qui s'y arrête remporte tout le nid. Son menu : mise, base (prise au stockage de la case ou à la banque, jamais créée) et plafond. À la fin de la partie, les pièces du nid retournent à ce stockage."]))

done("done", 10.5, 0, ["chick", "shiny", "pot_game"],
     ("Bird watcher", "Ornithologue"),
     (["Magpies charmed, a chick born and a pot that grows on the board."],
      ["Des pies charmées, un poussin né et un pot qui grossit sur le plateau."]),
     "steveparty:magpie_spawn_egg")


# === Slackorns =================================================================================
chapter("glandouille", "creatures", "steveparty:acorn",
        ("Slackorns", "Glandouilles"),
        (["Grumpy acorns that shove, stack into towers and go squeak."],
         ["Des glands grognons qui bousculent, s'empilent en tours et font pouic."]))

q("find", 0, 0, O("steveparty:glandouille"), deps=["welcome.done"], rewards=[XP(25), IT("minecraft:bone_meal", 4)],
  icon="steveparty:glandouille_spawn_egg", size=1.25,
  title=("Grumpy acorn", "Gland grognon"),
  desc=(["Slackorns live under trees, 2 to 4 together: green young ones, mossy old ones in old taigas, frosty ones in snowy taigas that slide like curling stones. Come within 4 blocks: it stamps twice and charges. It never hurts: it shoves, and stuns a player for 2 s. Look at one to complete this quest."],
        ["Les Glandouilles vivent sous les arbres, par 2 à 4 : des jeunes verts, des vieux moussus dans les vieilles taïgas, des glacés dans les taïgas enneigées qui glissent comme des palets de curling. Approchez à moins de 4 blocs : il tape deux fois du pied et charge. Il ne blesse jamais : il bouscule, et étourdit un joueur 2 s. Regardez-en un pour valider la quête."]))

q("acorn", 2, -1, I("acorn", how="own"), deps=["find"], rewards=T1(),
  desc=(["Hits do no harm. Jump on its cap: squeak! It flattens and you bounce; the second squash finishes it (the third for a mossy one) and it drops 1 or 2 acorns. Bone meal on a Slackorn can also give one, at most once a minute."],
        ["Les coups ne lui font rien. Sautez sur son chapeau : pouic ! Il s'aplatit et vous rebondissez ; le deuxième écrasement l'achève (le troisième pour un moussu) et il lâche 1 ou 2 glands. La poudre d'os sur un Glandouille peut aussi en donner un, au plus une fois par minute."]))

q("plant", 4, -1, C(), deps=["acorn"], rewards=T1(), icon="minecraft:farmland",
  title=("Plant an acorn", "Planter un gland"),
  desc=(["Plant an acorn on farmland: it grows in 4 stages like a crop, then hatches by itself into a sleeping young Slackorn. Bone meal speeds it up."],
        ["Plantez un gland sur de la terre labourée : il pousse en 4 étapes comme une culture, puis éclot tout seul en jeune Glandouille endormi. La poudre d'os l'accélère."]))

q("tower", 2, 1, C(), deps=["find"], rewards=T2(), icon="steveparty:glandouille_spawn_egg",
  title=("Slackorn tower", "Tour de Glandouilles"),
  desc=(["Two calm Slackorns that meet climb on each other, up to 5. Right-click one empty-handed to carry it with those above, right-click another to stack them, left-click to throw the bottom one. Hit one in the middle of a tower: it flies off like a missile."],
        ["Deux Glandouilles calmes qui se croisent grimpent l'un sur l'autre, jusqu'à 5. Clic droit main vide sur l'un pour le porter avec ceux du dessus, clic droit sur un autre pour les empiler, clic gauche pour lancer celui du bas. Frappez-en un au milieu d'une tour : il part comme un missile."]))

q("hat", 0, 2.5, I("acorn_hat", how="own"), deps=["find"], rewards=T1(), optional=True,
  desc=(["Very rarely, a violent knock sends its cap flying. Without it, the Slackorn is shy and looks for it. Wear the cap, or right-click its owner with it to give it back."],
        ["Très rarement, un choc violent fait s'envoler son chapeau. Sans lui, le Glandouille est timide et le cherche. Portez le chapeau, ou faites clic droit sur son propriétaire avec pour le lui rendre."]))

q("cartridge", 6, 0, I("glandouille_cartridge"), deps=["acorn", "board.cartridge"], rewards=T2(),
  desc=(["Cartridge + acorn, shapeless. On the board, a tower of Slackorns pushes the pawn that stops there forward or back along the path (its menu: the distance and the tower's height), with every pawn the tower meets. Each pawn met knocks a Slackorn off the tower; with none left, the tower stops."],
        ["Cartouche + gland, sans forme. Sur le plateau, une tour de Glandouilles pousse le pion qui s'y arrête en avant ou en arrière sur le chemin (son menu : la distance et la hauteur de la tour), avec chaque pion que la tour rencontre. Chaque pion rencontré fait tomber un Glandouille ; quand il n'en reste plus, la tour s'arrête."]))

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
  desc=(["Find a black cat in or within 16 blocks of a witch hut. Right-click it with any die of the mod: the die is thrown above it. If it lands on 1 (or 0), the cat swells into a Mistigri. Any other result: it just hisses. Look at a Mistigri to complete this quest."],
        ["Trouvez un chat noir dans une cabane de sorcière ou à 16 blocs au plus. Clic droit dessus avec n'importe quel dé du mod : le dé est lancé au-dessus de lui. S'il tombe sur 1 (ou 0), le chat enfle et devient un Mistigri. Tout autre résultat : il crache seulement. Regardez un Mistigri pour valider la quête."]))

q("bad_luck", 2, -1.25, C(), deps=["summon"], rewards=T1(), icon="minecraft:fermented_spider_eye",
  title=("Don't cross its path", "Ne croisez pas son chemin"),
  desc=(["Walking in front of it gives Bad Luck for 1 minute; hitting it, 2 minutes (it gets angry, but never fights). It knocks items off item frames and naps on chests, which won't open until you wake it with raw fish."],
        ["Passer devant lui donne Malchance pendant 1 minute ; le frapper, 2 minutes (il se fâche, mais ne se bat jamais). Il fait tomber les objets des cadres et fait la sieste sur les coffres, qui ne s'ouvrent plus tant qu'on ne le réveille pas avec du poisson cru."]))

q("acorn_play", 2, 1.25, C(), deps=["summon", "glandouille.acorn"], rewards=T0(), optional=True, icon="steveparty:acorn",
  title=("Ball of yarn", "Pelote de laine"),
  desc=(["Throw an acorn within 12 blocks of it: it forgets everything, even its anger, and plays with it for 30 s to a minute."],
        ["Lancez un gland à 12 blocs de lui au plus : il oublie tout, même sa colère, et joue avec pendant 30 s à une minute."]))

q("tame", 4, -1.25, C(), deps=["summon"], rewards=T2(), icon="minecraft:cod",
  title=("A lucky cat", "Un chat porte-bonheur"),
  desc=(["Give it raw cod or salmon, one at a time: it takes 6 to 10. Tamed, it follows you everywhere, gives you Luck within 16 blocks, and monsters within 10 blocks of it miss one hit in three. Empty hand: sit or stand."],
        ["Donnez-lui de la morue ou du saumon crus, un par un : il en faut 6 à 10. Apprivoisé, il vous suit partout, vous donne Chance à 16 blocs, et les monstres à 10 blocs de lui ratent un coup sur trois. Main vide : assis ou debout."]))

q("cartridge", 4, 1.25, I("mistigri_cartridge"), deps=["summon", "board.cartridge"], rewards=T2(),
  desc=(["Cartridge + the Mistigri's Die, shapeless. Its loot when it dies: a die with the cursed 1, 2 and 3, twice each. In a party, a pawn that stops on its space brings out the Mistigri, which rolls on its back and juggles its loaded die: the whole party watches a roulette of sentences, then one falls."],
        ["Cartouche + le Dé du Mistigri, sans forme. Son butin quand il meurt : un dé aux 1, 2 et 3 maudits, deux fois chacun. En partie, un pion qui s'arrête sur sa case fait venir le Mistigri, qui se roule sur le dos et jongle avec son dé pipé : toute la partie voit une roulette de sentences, puis l'une tombe."]))

q("sentences", 6, 1.25, C(), deps=["cartridge"], rewards=T2(), icon="minecraft:writable_book",
  title=("Write the sentences", "Écrire les sentences"),
  desc=(["Right-click in the air with the cartridge: its list of sentences (12 at most). Each one has a type (steal, redistribute, step back, cursed die, mercy), a target (who lands there, 1st, last, everyone, the others, richest, poorest, random), a number and a weight. \"On the roulette\" sets how many are drawn at each stop.",
         "Nothing is created: fines go to the party's bank, mercy comes from it."],
        ["Clic droit dans le vide avec la cartouche : sa liste de sentences (12 au plus). Chacune a un type (vol, redistribution, recul, dé maudit, pitié), une cible (qui tombe ici, 1er, dernier, tout le monde, les autres, plus riche, plus pauvre, au hasard), un nombre et un poids. « Sur la roulette » règle combien sont tirées à chaque arrêt.",
         "Rien n'est créé : les amendes vont à la banque de la partie, la pitié en vient."]))

done("done", 8.5, 0, ["bad_luck", "tame", "sentences"],
     ("Black cat", "Chat noir"),
     (["Bad luck summoned, tamed and turned into a board game."],
      ["La malchance invoquée, apprivoisée et mise en jeu."]),
     "steveparty:mistigri_spawn_egg")


# === Wicklings =================================================================================
chapter("frousseux", "creatures", "steveparty:frousseux_cartridge",
        ("Wicklings", "Frousseux"),
        (["Little candle ghosts of the caves: thieves of shiny things, then lamps that follow you."],
         ["De petits fantômes-bougies des grottes : voleurs d'objets brillants, puis lampes qui vous suivent."]))

q("find", 0, 0, O("steveparty:frousseux"), deps=["welcome.done"], rewards=[XP(25), IT("minecraft:flint_and_steel", 1)],
  icon="steveparty:frousseux_spawn_egg", size=1.25,
  title=("A light in the dark", "Une lueur dans le noir"),
  desc=(["Wicklings float in dark Overworld caves, alone or in pairs: a wax ghost with a candle flame in one of 17 colours. Its light is real, and no monster comes within 8 blocks of it. Stare at it: it hides its eyes. Look at one to complete this quest."],
        ["Les Frousseux flottent dans les grottes sombres de l'Overworld, seuls ou à deux : un fantôme de cire avec une flamme de bougie, en 17 couleurs. Sa lumière est réelle, et aucun monstre n'approche à moins de 8 blocs. Fixez-le : il se cache les yeux. Regardez-en un pour valider la quête."]))

q("thief", 2, -1.25, C(), deps=["find"], rewards=T1(), icon="minecraft:torch",
  title=("Stop, thief!", "Au voleur !"),
  desc=(["A wild Wickling steals one shiny item from a player within 2 blocks and runs off giggling. With another player nearby, it flies the item to them and drops it at their feet! A torch or a lantern in hand protects you. Melee hits do nothing (it vanishes and reappears); projectiles work."],
        ["Un Frousseux sauvage vole un objet brillant à un joueur à moins de 2 blocs et s'enfuit en ricanant. Avec un autre joueur près de là, il lui apporte l'objet et le lui laisse tomber aux pieds ! Une torche ou une lanterne en main vous protège. Les coups au corps à corps ne font rien (il disparaît et réapparaît) ; les projectiles, si."]))

q("tame", 2, 1.25, C(), deps=["find"], rewards=T2(), icon="minecraft:flint_and_steel",
  title=("Rekindle the flame", "Raviver la flamme"),
  desc=(["Flint and steel on a wild Wickling: the first strike makes it give back what it stole, then each strike has 1 chance in 3 to tame it. Tamed, it follows you as a lamp: in front of you in the light, at your shoulder in the dark, where your crosshair goes through it. Flint and steel also heals it."],
        ["Un briquet sur un Frousseux sauvage : le premier coup lui fait rendre ce qu'il a volé, puis chaque coup a 1 chance sur 3 de l'apprivoiser. Apprivoisé, il vous suit comme une lampe : devant vous à la lumière, à votre épaule dans le noir, où votre viseur passe à travers lui. Le briquet le soigne aussi."]))

q("holder", 4, 1.25, I("frousseux_candle_holder", how="own"), deps=["tame"], rewards=T1(),
  desc=(["Sneak + right-click your Wickling empty-handed: it sits. Again: it falls asleep and becomes a candle holder block that gives light and keeps everything (colour, name, health). Right-click it empty-handed to wake it up. Broken, it drops as an item: a Wickling is never lost."],
        ["Accroupi + clic droit main vide sur votre Frousseux : il s'assoit. Encore une fois : il s'endort et devient un bougeoir, un bloc qui éclaire et garde tout (couleur, nom, vie). Clic droit main vide dessus pour le réveiller. Cassé, il tombe en objet : un Frousseux n'est jamais perdu."]))

q("saucer", 6, 1.25, I("candle_saucer"), deps=["holder"], rewards=T0(), optional=True,
  desc=(["5 gold nuggets: a Candle Saucer. A candle holder + the saucer at the crafting table: the holder sits on its saucer. Alone in the grid, it comes off again, nothing lost."],
        ["5 pépites d'or : une Soucoupe à bougie. Un bougeoir + la soucoupe à l'établi : le bougeoir se pose sur sa soucoupe. Seul dans la grille, il en ressort, rien de perdu."]))

q("cartridge", 4, -1.25, I("frousseux_cartridge"), deps=["thief", "board.cartridge"], rewards=T2(),
  desc=(["Cartridge + candle, shapeless. In a party, a pawn that stops there brings out a Wickling: its player picks a victim, the Wickling flies off to rob them (coins or stars, set in its menu) and brings the loot back. With the defence on, the victim has 7 seconds to hit it: each hit gives some back."],
        ["Cartouche + bougie, sans forme. En partie, un pion qui s'y arrête fait venir un Frousseux : son joueur choisit une victime, le Frousseux file la voler (pièces ou étoiles, réglé dans son menu) et rapporte le butin. Avec la défense, la victime a 7 secondes pour le frapper : chaque coup en rend une partie."]))

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
  desc=(["Boomcarts roll in abandoned mineshafts (rarely) and the dark caves around them, always alone. On rails they follow the track like a minecart. Grumpy: it roars when you come within 3 blocks. Look at one to complete this quest."],
        ["Les Pétaroules roulent dans les puits de mine abandonnés (rarement) et les grottes sombres autour, toujours seuls. Sur des rails, ils suivent la voie comme un wagonnet. Grognon : il rugit quand on approche à moins de 3 blocs. Regardez-en un pour valider la quête."]))

q("load", 2, -1, C(), deps=["find"], rewards=T1(), icon="minecraft:firework_rocket",
  title=("Feed it", "Nourris-le"),
  desc=(["Right-click it with TNT or a firework rocket: it swallows the new load and gives back the old one. Hold one within 5 blocks: it opens its mouth wide. A Boomcart loaded by a player never despawns."],
        ["Clic droit dessus avec de la TNT ou une fusée : il engloutit la nouvelle charge et rend l'ancienne. Tenez-en une à moins de 5 blocs : il ouvre grand la gueule. Un Pétaroule chargé par un joueur ne disparaît plus."]))

q("hot_potato", 4, -1, C(), deps=["load"], rewards=T2(), icon="minecraft:flint_and_steel",
  title=("Hot potato", "Patate chaude"),
  desc=(["Flint and steel on it: its fuse burns for 8 s, it panics and rushes at the nearest other player. Another player relights it to send it elsewhere (+3 s, then +2, then +1, then no more). Loaded with a rocket, it bursts into fireworks that hurt nobody; with TNT, it is a real explosion."],
        ["Un briquet dessus : sa mèche brûle 8 s, il panique et fonce sur le joueur le plus proche, un autre que vous. Un autre joueur le rallume pour l'envoyer ailleurs (+3 s, puis +2, puis +1, puis plus rien). Chargé d'une fusée, il éclate en feu d'artifice sans blesser personne ; avec de la TNT, c'est une vraie explosion."]))

q("board", 2, 1, C(), deps=["find", "board.advance"], rewards=T2(), icon="steveparty:advance_back_cartridge",
  title=("Advance or back", "Avancer ou reculer"),
  desc=(["On an Advance / Back space, a Boomcart hologram rolls out (from the space's Spawn Bubble, if linked): forward, it explodes behind the pawn and launches it from space to space; backward, it rams it. Only a show: no block broken, nobody hurt."],
        ["Sur une case Avancer / Reculer, un Pétaroule hologramme sort (de la Bulle d'apparition de la case, si elle est reliée) : en avant, il explose derrière le pion et le propulse de case en case ; en arrière, il le percute. Rien qu'un spectacle : aucun bloc cassé, personne de blessé."]))

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
  desc=(["In the Nether wastes, basalt deltas and soul sand valleys, by the lava or swimming in it: rare and alone. Neutral unless you hit it. Near a bastion it may carry piglins, and then it hunts players. Look at one to complete this quest."],
        ["Dans les Terres du Nether, les deltas de basalte et les vallées de sable des âmes, au bord de la lave ou en train d'y nager : rare et seul. Neutre tant qu'on ne le frappe pas. Près d'un bastion, il peut porter des piglins, et alors il chasse les joueurs. Regardez-en un pour valider la quête."]))

q("shield", 2, -1.25, C(), deps=["find"], rewards=T1(), icon="minecraft:shield",
  title=("Raise your shield", "Levez le bouclier"),
  desc=(["Each head is a turret: it charges, then spits a jet of scalding steam up to 30 blocks. Its aim freezes just before it fires: step aside. A shield raised towards the jet stops it completely. Each jet costs it a bucket of lava."],
        ["Chaque tête est une tourelle : elle se charge, puis crache un jet de vapeur brûlante jusqu'à 30 blocs. Sa visée se fige juste avant le tir : faites un pas de côté. Un bouclier levé vers le jet l'arrête complètement. Chaque jet lui coûte un seau de lave."]))

q("tame", 2, 1.25, C(), deps=["find"], rewards=T2(), icon="minecraft:lava_bucket",
  title=("Drain and tame", "Vider et apprivoiser"),
  desc=(["Right-click it with empty buckets: 1 bucket of lava each (up to 27), without angering it. Only someone who emptied it can tame it: mount it empty-handed. It yields (70% when empty, never when full, +10% per failed try) or throws you off."],
        ["Clic droit dessus avec des seaux vides : 1 seau de lave à chaque fois (jusqu'à 27), sans le fâcher. Seul quelqu'un qui l'a vidé peut l'apprivoiser : montez dessus main vide. Il cède (70 % vide, jamais plein, +10 % par essai raté) ou vous éjecte."]))

q("ride", 4, 1.25, C(), deps=["tame"], rewards=T2(), icon="minecraft:saddle",
  title=("Three riders", "Trois cavaliers"),
  desc=(["Saddle it in its screen, like a horse. Up to 3 players sit on the rim, each holding a head: everyone's keys add up. Lava is its fuel (click with a lava bucket while riding). Click to make your head spit; hold Jump for a steam-powered leap."],
        ["Sellez-le dans son écran, comme un cheval. Jusqu'à 3 joueurs s'assoient sur le rebord, chacun tient une tête : les touches de tous s'additionnent. La lave est son carburant (clic avec un seau de lave, monté). Cliquez pour que votre tête crache ; maintenez Saut pour un bond propulsé par la vapeur."]))

q("cartridge", 4, -1.25, I("trichaudron_cartridge"), deps=["find", "board.inventory"], rewards=T2(),
  desc=(["Inventory Cartridge + lava bucket, shapeless (the bucket comes back). In a party, a pawn that stops there brings out a Tricauldron with one head per prize (1 to 5), taken from the linked chests or the bank. The player hits a head, blind by default, and that head spits its prize at them."],
        ["Cartouche d'inventaire + seau de lave, sans forme (le seau est rendu). En partie, un pion qui s'y arrête fait surgir un Trichaudron avec une tête par lot (1 à 5), pris dans les coffres liés ou la banque. Le joueur frappe une tête, à l'aveugle par défaut, et cette tête lui crache son lot."]))

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
  desc=(["4 plastic pellets + 1 dye, shapeless: 4 blocks of the dye's colour. 8 blocks around a dye recolour them. Neighbours of the same colour merge into one piece (up to 4x2x4)."],
        ["4 granulés de plastique + 1 colorant, sans forme : 4 blocs de la couleur du colorant. 8 blocs autour d'un colorant les reteignent. Les voisins de même couleur fusionnent en une seule pièce (jusqu'à 4x2x4)."]))

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
        ["4 blocs d'une couleur et 2 bâtons en plastique : 3 barrières. Elles se relient entre elles et portent les panneaux routiers."]))

q("float", 3.5, 2.625, C(), deps=["block"], rewards=T2(), icon="minecraft:soul_sand",
  title=("It floats!", "Ça flotte !"),
  desc=(["A plastic block in still water rises to the surface, carrying whatever stands on it. Over soul sand, a bubble column sends it up fast (an elevator); over magma, down. A chain anchors it."],
        ["Un bloc de plastique dans l'eau calme remonte à la surface, en emportant ce qui est dessus. Au-dessus du sable des âmes, une colonne de bulles le fait monter vite (un ascenseur) ; au-dessus du magma, descendre. Une chaîne l'ancre."]))

q("break", 1.25, 3.5, C(), deps=["block", "board.wrench"], rewards=T0(), optional=True, icon="steveparty:wrench",
  title=("Quick clean-up", "Démontage express"),
  desc=(["The Wrench breaks any plastic block in one hit, even under water. Shears are fast too."],
        ["La Clé casse tout bloc de plastique d'un coup, même sous l'eau. Les cisailles sont rapides aussi."]))

q("hop_switch", 3.5, 3.875, I("hop_switch"), deps=["block", "board.cartridge"], rewards=T2(),
  desc=(["Blue concrete, a stone pressure plate, a clock, iron and redstone. Give it a Cartridge whose destinations are blocks: jump on it and they vanish, appear or toggle for a set time (clock to change it). Plastic is switchable by default."],
        ["Béton bleu, plaque de pression en pierre, horloge, fer et redstone. Donnez-lui une Cartouche dont les destinations sont des blocs : sautez dessus et ils disparaissent, apparaissent ou basculent pour une durée réglable (horloge). Le plastique est basculable par défaut."]))

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
  desc=(["6 plastic blocks of one colour in two columns: 6 opaque pipes (16 colours). A pipe placed against another (or in front of its mouth) connects to it."],
        ["6 blocs de plastique d'une couleur en deux colonnes : 6 tuyaux opaques (16 couleurs). Un tuyau posé contre un autre (ou devant son embouchure) s'y raccorde."]))

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
  desc=(["Right-click a mouth, sneak in front of it, or fall into it: you rush through the pipe and come out of another end of the network, at random. Mobs, pawns and items go through too."],
        ["Clic droit sur une embouchure, accroupissez-vous devant, ou tombez dedans : vous filez dans le tuyau et ressortez par une autre extrémité du réseau, au hasard. Mobs, pions et objets passent aussi."]))

q("dead_end", 4, 1.5, C(), deps=["travel"], rewards=T2(), icon="minecraft:ender_eye",
  title=("Warp", "Téléportation"),
  desc=(["A pipe that ends inside a solid block is a dead end: it warps you to the nearest mouth of the same colour within 100 blocks. Opaque and windowed pipes of one colour count as one colour."],
        ["Un tuyau qui rentre dans un bloc solide est un bout bouché : il vous téléporte vers l'embouchure la plus proche de la même couleur, à moins de 100 blocs. Tuyaux opaques et à hublot d'une même couleur comptent comme une seule couleur."]))

q("attach", 1, 3, C(), deps=["pipe", "board.wrench"], rewards=T0(), optional=True, icon="steveparty:wrench",
  title=("Pipe fittings", "Fixations"),
  desc=(["Placed against a solid block, a pipe is fixed to it (one block per pipe). The Wrench changes which one, down to none."],
        ["Posé contre un bloc solide, un tuyau s'y fixe (un bloc par tuyau). La Clé change lequel, jusqu'à aucun."]))

done("done", 6.25, 0, ["windowed", "stained", "dead_end"],
     ("Plumber", "Plombier"),
     (["Every kind of pipe, and a warp network of your own. For mini-game pipes, see the Mini-games chapter."],
      ["Toutes les sortes de tuyaux, et votre propre réseau de téléportation. Pour les tuyaux de mini-jeu, voir le chapitre Mini-jeux."]),
     "steveparty:glass_pipe")


# === Bubbles ===================================================================================
chapter("bubbles", "decoration", "steveparty:bubbler",
        ("Bubbles", "Bulles"),
        (["Soap bubbles you can ride and steer, and the Spawn Bubbles of the board."],
         ["Des bulles de savon qu'on pilote, et les Bulles d'apparition du plateau."]))

q("bubbler", 0, 0, I("bubbler"), deps=["welcome.pellets"], rewards=[XP(25), IT("minecraft:snowball", 8)], size=1.25,
  desc=(["4 plastic pellets around a water bucket and redstone (the bucket comes back empty). Walk onto it: you are closed in a soap bubble that rises 30 blocks (2 per level of redstone signal), then floats. Sneak to walk over it without being caught. Mobs and dropped items get bubbled too."],
        ["4 granulés de plastique autour d'un seau d'eau et de redstone (le seau est rendu vide). Marchez dessus : vous voilà dans une bulle de savon qui monte de 30 blocs (2 par niveau de signal redstone), puis flotte. Accroupi, on passe dessus sans être pris. Les mobs et les objets lâchés aussi."]))

q("steer", 2, -1, C(), deps=["bubbler"], rewards=T2(), icon="minecraft:feather",
  title=("Bubble pilot", "Pilote de bulle"),
  desc=(["Hold Jump to charge a puff, release: the bubble blows towards where you look. WASD drifts it slowly. Puffs chained too fast stretch it (it swells, turns pink and creaks) until it pops. Attack or sneak to get out: you fall slowly."],
        ["Maintenez Saut pour charger un souffle, relâchez : la bulle file là où vous regardez. ZQSD la font dériver lentement. Des souffles trop rapprochés la tendent (elle gonfle, rosit et grince) jusqu'à ce qu'elle éclate. Frappez ou accroupissez-vous pour sortir : vous tombez en douceur."]))

q("merge", 4, -1, C(), deps=["steer"], rewards=T1(), optional=True, icon="minecraft:slime_ball",
  title=("Two in a bubble", "À deux dans la bulle"),
  desc=(["Two occupied bubbles that touch merge into a bigger one (8 passengers at most). Steering is shared: everyone's keys count, and opposite pushes cancel out."],
        ["Deux bulles occupées qui se touchent fusionnent en une plus grosse (8 passagers au plus). Le pilotage est partagé : les touches de chacun comptent, et des poussées opposées s'annulent."]))

q("cannon", 2, 1, C(), deps=["bubbler"], rewards=T1(), optional=True, icon="minecraft:snowball",
  title=("Bubble cannon", "Canon à bulles"),
  desc=(["Outside the Bubbler's column, a bubble pops on a block, a mob, an arrow, or after 15 s without moving. A snowball pushes it instead: a dispenser firing snowballs makes a bubble cannon. A Silk Touch tool pushes it without ever popping it."],
        ["Hors de la colonne du Bulleur, une bulle éclate contre un bloc, un mob, une flèche, ou après 15 s immobile. Une boule de neige la pousse au lieu de l'éclater : un distributeur de boules de neige fait un canon à bulles. Un outil Toucher de soie la pousse sans jamais l'éclater."]))

q("bubbled", 4, 1, C(), deps=["steer"], rewards=T1(), optional=True, icon="minecraft:arrow",
  title=("Bubbled!", "Bullé !"),
  desc=(["Shoot or throw something from inside a bubble (arrow, snowball, potion...): it leaves a sparkly trail, and the creature it hits is trapped in a bubble of its own."],
        ["Tirez ou lancez quelque chose depuis l'intérieur d'une bulle (flèche, boule de neige, potion...) : il laisse une traînée irisée, et la créature touchée se retrouve dans sa propre bulle."]))

q("spawn_bubble", 0, 2.5, I("spawn_marker"), deps=["bubbler", "board.link"], rewards=T2(),
  desc=(["A glass block over a Bubbler at the crafting table. It tells where the creature of a space appears (Boxed Trader, Boomcart, Mula, Wickling, Slackorn, Mistigri, Tricauldron) and which way it faces: link it with the Tile Linker Brush, from the space to the bubble. Wrench: \"Always visible\" (the creature lives on the board during a party) and height; sneak + Wrench turns it."],
        ["Un bloc de verre au-dessus d'un Bulleur, à l'établi. Elle dit où apparaît la créature d'une case (Marchand en carton, Pétaroule, Mula, Frousseux, Glandouille, Mistigri, Trichaudron) et vers où elle regarde : reliez-la au Pinceau lieur, de la case vers la bulle. Clé : « Toujours visible » (la créature habite le plateau pendant une partie) et hauteur ; accroupi + Clé pour la tourner."]))

done("done", 6.5, 0, ["steer", "spawn_bubble"],
     ("Bubble blower", "Souffleur de bulles"),
     (["Ridden, steered, merged, and placed on the board."],
      ["Montées, pilotées, fusionnées, et posées sur le plateau."]),
     "steveparty:spawn_marker")


# === Signs and stencils ========================================================================
chapter("signs", "decoration", "steveparty:stencil",
        ("Signs and Stencils", "Panneaux et pochoirs"),
        (["Cut a 16x16 pattern, then paint or engrave it everywhere."],
         ["Découpez un motif 16x16, puis peignez-le ou gravez-le partout."]))

q("stencil", 0, 0, I("stencil", 2), deps=["welcome.done"], rewards=T1(), size=1.25,
  desc=(["4 iron nuggets around a paper: 2 blank stencils. Structure chests sometimes hold stencils with one of 50 built-in patterns. A cut stencil + blank ones copy it."],
        ["4 pépites de fer autour d'un papier : 2 pochoirs vierges. Les coffres de structures contiennent parfois des pochoirs à l'un des 50 motifs intégrés. Un pochoir découpé + des vierges le copient."]))

q("maker", -3.75, 0.75, I("stencil_maker"), deps=["stencil"], rewards=T2(),
  desc=(["Iron ingots, a smooth stone slab and an iron block. Insert a stencil and open the editor: left-click cuts a pixel, right-click fills it. Your library keeps up to 128 patterns, and learns every pattern you carry."],
        ["Des lingots de fer, une dalle de pierre lisse et un bloc de fer. Insérez un pochoir et ouvrez l'éditeur : clic gauche découpe un pixel, clic droit le bouche. Votre bibliothèque garde jusqu'à 128 motifs, et apprend tous ceux que vous portez."]))

q("cut", -3.75, 2.5, C(), deps=["maker"], rewards=T1(), icon="minecraft:shears",
  title=("Cut a pattern", "Découper un motif"),
  desc=(["Draw your own 16x16 pattern on the grid (flip, rotate, invert, undo...) and save it on the stencil."],
        ["Dessinez votre propre motif 16x16 sur la grille (retourner, pivoter, inverser, annuler...) et enregistrez-le sur le pochoir."]))

q("paint", 2.25, 0, C(), deps=["stencil"], rewards=T1(), icon="minecraft:red_dye",
  title=("Paint a wall", "Peindre un mur"),
  desc=(["Stencil in one hand, dye in the other, right-click a sign or the full face of any block: the pattern is painted (1 dye; the stencil is never used up). On a wall, the paint can only be wiped with a wet sponge or a brush."],
        ["Pochoir dans une main, colorant dans l'autre, clic droit sur un panneau ou la face pleine de n'importe quel bloc : le motif est peint (1 colorant ; le pochoir n'est jamais consommé). Sur un mur, la peinture ne part qu'à l'éponge mouillée ou au pinceau."]))

q("glow", 4, 0, C(), deps=["paint"], rewards=T0(), optional=True, icon="minecraft:glow_ink_sac",
  title=("Glow in the dark", "Ça brille la nuit"),
  desc=(["A glow ink sac on a painted pattern makes it shine at night; a dry sponge turns it off."],
        ["Une poche d'encre luisante sur un motif peint le fait briller la nuit ; une éponge sèche l'éteint."]))

q("hammer", 3.75, 2.5, I("stencil_gun"), deps=["stencil"], rewards=T2(),
  desc=(["Leather, planks, a stencil and a stick. Loads 9 stencils and 9 dyes (sneak + right-click). Sneak + scroll picks the stencil or the colour (G switches), right-click stamps it, signs, walls, tiles and podiums included."],
        ["Du cuir, des planches, un pochoir et un bâton. Il charge 9 pochoirs et 9 colorants (accroupi + clic droit). Accroupi + molette choisit le pochoir ou la couleur (G pour basculer), clic droit tamponne, panneaux, murs, tuiles et podiums compris."]))

q("stamp_tile", 5.25, 2.5, A("workshop/signs/stencil_hammer"), deps=["hammer", "board.tile"], rewards=T1(), optional=True, icon="steveparty:tile",
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
    q(sign, -2.5 + i * 1.25, 2.5, I(sign, 1), deps=["stencil"] + (["plastic.block"] if sign == "plastic_road_sign" else []),
      rewards=T1(), desc=(en, fr))

done("done", 0, 5, ["cut", "hammer", "easel_sign", "wooden_panel", "wooden_cutout_panel", "rock_sign",
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
        ["Deux blocs de terre cuite polie de couleurs différentes en damier dans une grille 2x2 : 4 carrelages, chaque face en damier 2x2. Inversez les couleurs pour le damier décalé."]))

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
         ["Quelques jouets de plus pour votre monde de fête."]))

q("looting_box", 0, 0, I("looting_box"), deps=["welcome.done"], rewards=T2(), size=1.25,
  desc=(["Gold ingots, polished terracotta, a chest and an amethyst shard."],
        ["Des lingots d'or, de la terre cuite polie, un coffre et un éclat d'améthyste."]))

q("bonk", 2, 0, C(), deps=["looting_box", "board.inventory"], rewards=T2(), icon="minecraft:gold_ingot",
  title=("Bonk!", "Bonk !"),
  desc=(["Put an Inventory Cartridge linked to a chest in it (Wrench), hang it with 2 or 3 blocks of air below, and hit it with your head while jumping: it drops items from the chest. 2 hits, then 3 s to recharge."],
        ["Mettez-y une Cartouche d'inventaire liée à un coffre (Clé), suspendez-la avec 2 ou 3 blocs d'air dessous, et frappez-la de la tête en sautant : elle lâche des objets du coffre. 2 coups, puis 3 s de recharge."]))

q("plunger", 0, 2.25, I("plunger"), deps=["welcome.done"], rewards=T1(), size=1.25,
  desc=(["A stick, a slime ball and a bowl. Right-click a mob to mute it; again to give its voice back."],
        ["Un bâton, une boule de slime et un bol. Clic droit sur un mob pour le rendre muet ; encore une fois pour lui rendre sa voix."]))

q("squishy", 2, 3.5, C(), deps=["shops.villager_block"], rewards=T1(), optional=True, icon="steveparty:villager_block",
  title=("Squishy friend", "Ami tout plat"),
  desc=(["The Villager Block reacts to everything: stare at it, jump in front of it, show it emeralds, play a jukebox nearby... About 70 reactions to discover."],
        ["Le Bloc de villageois réagit à tout : fixez-le, sautez devant lui, montrez-lui des émeraudes, jouez un disque à côté... Environ 70 réactions à découvrir."]))

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


def build():
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
                sub = ("Craft it: items you already own do not count",
                       "À fabriquer : les objets déjà possédés ne comptent pas")
            elif not sub and "consume" in hows:
                sub = ("Hand the items in: click the task", "Remettez les objets : cliquez sur la tâche")
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

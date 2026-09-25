"""Generates the SteveParty test world as a datapack: a separate flat area with every feature of the mod.

Writes <run-server>/world/datapacks/steveparty-test/, then in game (or through `.\\scripts\\dev.ps1 cmd`):
    /forceload add 1936 1952 2095 2079     (once, a few seconds before: the chunks must be loaded)
    /reload
    /function steveparty_test:build        (terrain, board, stations, chests of every item)
    /function steveparty_test:welcome      (with the player online: kit, day, clear weather, tp to the board)

The area (x 1936..2095, z 1952..2079, floor y=99, everything built at y=100), far from the demo board:
  - the board (west): a loop of 30 tiles spaced 4 blocks apart (2 block gap, tiles are 2 blocks wide), each one turned
    toward the next tile of the path, with diagonal sides, and a shortcut: the fork tile has two destinations (the
    main route east, or the shortcut straight south through the middle, which rejoins the loop further on);
  - the stations (east), one per 20x20 plot: dice, shops, goal pole, plastic, signs, building blocks, misc,
    chests holding one of every item, and the Mulas' glass enclosure around an activated Dice Forge.
"""
import hashlib
import json
import math
import os
import re
import sys
import uuid

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')
RUN_DIR = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'run-server')
SRC = os.path.join(ROOT, 'src', 'main', 'java', 'fr', 'lordfinn', 'steveparty')
PLAYER = 'LordFinn'
NS = 'steveparty_test'
TAG = 'sp_test'

Y = 100                                   # everything stands on the grass floor (y = 99)
AREA = (1936, 1952, 2095, 2079)           # x0, z0, x1, z1: whole chunks (121..130, 122..129)

COLORS = ['white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray',
          'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black']
COLORS_WITH_DEFAULT = ['default'] + COLORS
WOODS = ['oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak', 'mangrove', 'cherry', 'crimson', 'warped']
PLANKS = WOODS[:8] + ['bamboo', 'crimson', 'warped']
DYE_RGB = {  # DyeColor.getEntityColor()
    'white': 0xF9FFFE, 'orange': 0xF9801D, 'magenta': 0xC74EBD, 'light_blue': 0x3AB3DA, 'yellow': 0xFED83D,
    'lime': 0x80C71F, 'pink': 0xF38BAA, 'gray': 0x474F52, 'light_gray': 0x9D9D97, 'cyan': 0x169C9C,
    'purple': 0x8932B8, 'blue': 0x3C44AA, 'brown': 0x835432, 'green': 0x5E7C16, 'red': 0xB02E26, 'black': 0x1D1D21}

WHITE, GOOD, BAD = 0xE8E8E8, 0x0083DF, 0xC41C24   # InventoryInteractorTileBehavior GOOD_COLOR / BAD_COLOR
SHORTCUT_COLOR = DYE_RGB['lime']


# ---------------------------------------------------------------------------------------------------- helpers

def offline_uuid(name):
    md5 = bytearray(hashlib.md5(('OfflinePlayer:' + name).encode()).digest())
    md5[6] = (md5[6] & 0x0F) | 0x30
    md5[8] = (md5[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(md5)))


def int_array(u):
    v = uuid.UUID(u).int
    parts = [(v >> s) & 0xFFFFFFFF for s in (96, 64, 32, 0)]
    return '[I;' + ','.join(str(p - (1 << 32) if p >= 1 << 31 else p) for p in parts) + ']'


def block_pos_long(x, y, z):
    v = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
    return v - (1 << 64) if v >= 1 << 63 else v


def jtext(text, color=None, bold=False):
    """A JSON text component inside a single-quoted SNBT string."""
    d = {'text': text}
    if color:
        d['color'] = color
    if bold:
        d['bold'] = True
    return "'" + json.dumps(d, ensure_ascii=False).replace('\\', '\\\\').replace("'", "\\'") + "'"


def label(x, y, z, text, color='gold', scale=1.5, bold=True):
    """A floating label (text display, always facing the viewer)."""
    return ('summon minecraft:text_display %.1f %.1f %.1f {Tags:["%s"],billboard:"center",alignment:"center",text:%s,'
            'background:1426063360,transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],'
            'translation:[0f,0f,0f],scale:[%sf,%sf,%sf]}}' % (x, y, z, TAG, jtext(text, color, bold), scale, scale, scale))


def show_item(x, z, item, components='', scale=0.7, pedestal='minecraft:polished_andesite', caption=None):
    """An item floating above a pedestal (item display), with an optional caption."""
    comp = ',components:{%s}' % components if components else ''
    cmds = ['setblock %d %d %d %s' % (x, Y, z, pedestal),
            ('summon minecraft:item_display %.1f %.2f %.1f {Tags:["%s"],billboard:"vertical",item:{id:"%s",count:1%s},'
             'transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],'
             'scale:[%sf,%sf,%sf]}}') % (x + 0.5, Y + 1.5, z + 0.5, TAG, item, comp, scale, scale, scale)]
    if caption:
        cmds.append(label(x + 0.5, Y + 2.3, z + 0.5, caption, 'white', 0.6, False))
    return cmds


def load_patterns():
    """Stencil patterns (id -> 256 bytes, x-major like StencilShape.index) parsed from StencilPatterns.java."""
    src = open(os.path.join(SRC, 'stencil', 'StencilPatterns.java'), encoding='utf-8').read()
    patterns = {}
    for m in re.finditer(r'add\("(\w+)",\s*Category\.\w+,((?:\s*"[^"]{16}",?){16})\s*\);', src):
        rows = re.findall(r'"([^"]{16})"', m.group(2))
        shape = [0] * 256
        for yy, row in enumerate(rows):
            for xx, ch in enumerate(row):
                if ch == '#':
                    shape[xx * 16 + yy] = 1
        patterns[m.group(1)] = shape
    return patterns


PATTERNS = load_patterns()


def shape_nbt(pattern):
    return '[B;' + ','.join('%db' % b for b in PATTERNS[pattern]) + ']'


def shape_list(pattern):
    return '[' + ','.join('%db' % b for b in PATTERNS[pattern]) + ']'


# ---------------------------------------------------------------------------------------------------- terrain

def terrain():
    x0, z0, x1, z1 = AREA
    cmds = ['# Terrain: clear the area, flat grass floor at y=99',
            'kill @e[type=!minecraft:player,x=%d,y=60,z=%d,dx=%d,dy=200,dz=%d]' % (x0, z0, x1 - x0, z1 - z0),
            'kill @e[tag=%s]' % TAG]
    for cx in range(x0, x1 + 1, 16):
        for cz in range(z0, z1 + 1, 16):
            cmds.append('fill %d 100 %d %d 179 %d air' % (cx, cz, cx + 15, cz + 15))
            cmds.append('fill %d 180 %d %d 259 %d air' % (cx, cz, cx + 15, cz + 15))
            cmds.append('fill %d 88 %d %d 98 %d dirt' % (cx, cz, cx + 15, cz + 15))
            cmds.append('fill %d 99 %d %d 99 %d grass_block' % (cx, cz, cx + 15, cz + 15))
    # Paths between the board and the stations
    cmds += ['fill 1958 99 2014 2080 99 2016 dirt_path',
             'fill 2030 99 1966 2032 99 2042 dirt_path',
             'fill 2055 99 1966 2057 99 2042 dirt_path',
             'fill 2006 99 1966 2008 99 2042 dirt_path',
             'fill 2006 99 1990 2080 99 1992 dirt_path']
    return cmds


# ---------------------------------------------------------------------------------------------------- board

X0, Z0 = 1962, 2004          # first start tile (south-west corner of the loop)
STRAIGHT, DIAG = 4, 3        # tiles are 2 blocks wide: 4 apart = 2 blocks of grass between them
N, NE, E, SE, S, SW, W, NW = (0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1)
# TileBlock.rotation8FromYaw: the rotation a tile gets when placed by a player looking that way, i.e. walking the
# path toward the next tile (0 = looking north, then clockwise every 45 degrees: 1 NE, 2 E, 3 SE, 4 S, 5 SW, 6 W, 7 NW)
ROTATION = {N: 0, NE: 1, E: 2, SE: 3, S: 4, SW: 5, W: 6, NW: 7}
LOOP = [N] * 5 + [NE] * 2 + [E] * 6 + [SE] * 2 + [S] * 5 + [SW] * 2 + [W] * 6 + [NW] * 2

CHEST_W = (1968, Y, 1990)
CHEST_E = (1992, Y, 1998)
CONTROLLER = (1970, Y, 1998)
STEP_CONTROLLER = (1974, Y, 1998)


def step(d):
    return (d[0] * (DIAG if d[0] and d[1] else STRAIGHT), d[1] * (DIAG if d[0] and d[1] else STRAIGHT))


def direction_between(a, b):
    dx, dz = b[0] - a[0], b[1] - a[1]
    return ((dx > 0) - (dx < 0), (dz > 0) - (dz < 0))


def board_layout():
    loop = [(X0, Z0)]
    for d in LOOP[:-1]:
        sx, sz = step(d)
        loop.append((loop[-1][0] + sx, loop[-1][1] + sz))
    last = step(LOOP[-1])
    assert (loop[-1][0] + last[0], loop[-1][1] + last[1]) == loop[0], 'the loop does not close'
    fork, merge = 10, 25
    assert loop[fork][0] == loop[merge][0]
    shortcut = [(loop[fork][0], z) for z in range(loop[fork][1] + STRAIGHT, loop[merge][1], STRAIGHT)]
    return loop, fork, merge, shortcut


def dest_nbt(*positions):
    return '{destinations:[%s],world:""}' % ','.join('[I;%d,%d,%d]' % (x, Y, z) for x, z in positions)


def cartridge(slot, item, destinations, extra=''):
    return ('{Slot:%db,id:"steveparty:%s",count:1,components:{"steveparty:tile-behavior-component":%s%s}}'
            % (slot, item, dest_nbt(*destinations), extra))


def color(c):
    return ',"steveparty:color":%d' % c


def inventory_cartridge(destinations, chest, negative):
    ghost = '{id:"minecraft:emerald",count:3%s}' % (',components:{"steveparty:is-negative":true}' if negative else '')
    extra = (',"steveparty:inventory-cartridge":{items:[%s]},"steveparty:inventory-pos":[I;%d,%d,%d]'
             ',"steveparty:selection-state":1' % (ghost, *chest)) + color(BAD if negative else GOOD)
    return cartridge(0, 'inventory_cartridge', destinations, extra)


def board():
    loop, fork, merge, shortcut = board_layout()
    owner = int_array(offline_uuid(PLAYER))
    starts = {0: CHEST_W, 17: CHEST_E}
    blue = {2: CHEST_W, 18: CHEST_E, 27: CHEST_W}
    red = {7: CHEST_W, 21: CHEST_E}
    simple, check_point, routed = 3, 12, 16
    router = (loop[routed][0] + 4, Y, loop[routed][1])

    cmds = ['# Board: loop of %d tiles + %d shortcut tiles' % (len(loop), len(shortcut))]
    emeralds = ','.join('{Slot:%db,id:"minecraft:emerald",count:64}' % s for s in range(9))
    for chest in (CHEST_W, CHEST_E):
        cmds.append('setblock %d %d %d minecraft:chest[facing=south]{CustomName:%s,Items:[%s]}'
                    % (*chest, jtext('Coffre des cases bleues/rouges'), emeralds))

    def place(pos, nxt_list, i=None, tint=WHITE):
        nxt = nxt_list[0]
        rot = ROTATION[direction_between(pos, nxt)]
        block, state = 'tile', 'default'
        if i is not None and i in starts:
            state, items = 'tile_start', cartridge(0, 'tile_behavior_start', nxt_list, color(WHITE))
        elif i is not None and (i in blue or i in red):
            negative = i in red
            state = 'tile_inventory_interactor'
            items = inventory_cartridge(nxt_list, (red if negative else blue)[i], negative)
        elif i == routed:
            # Router off (power 0): slot 0, a default tile; router powered (lever): slot 15, a stop tile
            items = cartridge(0, 'board_space_behavior', nxt_list, color(WHITE)) + ',' + \
                cartridge(15, 'board_space_behavior_stop', nxt_list, color(DYE_RGB['orange']))
        else:
            items = cartridge(0, 'board_space_behavior', nxt_list, color(tint))
            if i == simple:
                block = 'simple_tile'
        if i == check_point:
            return 'setblock %d %d %d steveparty:check_point[tile_type=default]{Items:[%s]}' % (pos[0], Y, pos[1], items)
        return 'setblock %d %d %d steveparty:%s[tile_type=%s,rotation_8=%d]{Items:[%s]}' % (
            pos[0], Y, pos[1], block, state, rot, items)

    for i, pos in enumerate(loop):
        nxt = [loop[(i + 1) % len(loop)]]
        if i == fork:
            nxt.append(shortcut[0])   # first destination: the main route (east); second: the shortcut (south)
        cmds.append(place(pos, nxt, i))
    for j, pos in enumerate(shortcut):
        nxt = shortcut[j + 1] if j + 1 < len(shortcut) else loop[merge]
        cmds.append(place(pos, [nxt], None, SHORTCUT_COLOR))

    # Router driving the stop tile (placed empty, then given its cartridge: the data merge marks it dirty and routes)
    cmds += ['setblock %d %d %d minecraft:stone' % (router[0], Y - 1, router[2]),
             'setblock %d %d %d steveparty:board_space_redstone_router' % router,
             'data merge block %d %d %d {Items:[%s]}' % (*router, cartridge(0, 'board_space_behavior', [loop[routed]])),
             'setblock %d %d %d minecraft:lever[face=floor,facing=east]' % (router[0] + 1, Y, router[2])]

    cmds += [
        'setblock %d %d %d steveparty:party_controller[facing=south]' % CONTROLLER,
        'setblock %d %d %d minecraft:lever[face=floor,facing=south]' % (CONTROLLER[0] - 1, Y, CONTROLLER[2]),
        'setblock %d %d %d steveparty:step_controller' % STEP_CONTROLLER,
        'setblock %d %d %d minecraft:lever[face=floor,facing=south]' % (STEP_CONTROLLER[0] + 1, Y, STEP_CONTROLLER[2]),
    ]

    for i, (name, col, mob) in zip(sorted(starts), [('Pion rouge', 'red', 'pig'), ('Pion bleu', 'aqua', 'sheep')]):
        x, z = loop[i]
        cmds.append(('summon minecraft:%s %.1f %d %.1f {Tokenized:1b,TokenOwner:%s,PersistenceRequired:1b,'
                     'CustomNameVisible:1b,CustomName:%s}') % (mob, x + 0.5, Y, z + 0.5, owner, jtext(name, col)))

    cx = (min(p[0] for p in loop) + max(p[0] for p in loop)) / 2 + 0.5
    cz = (min(p[1] for p in loop) + max(p[1] for p in loop)) / 2 + 0.5
    cmds += [
        label(cx, Y + 8, cz, 'PLATEAU', 'gold', 4),
        label(loop[0][0] + 0.5, Y + 2.5, loop[0][1] + 0.5, 'Départ 1', 'yellow', 1),
        label(loop[17][0] + 0.5, Y + 2.5, loop[17][1] + 0.5, 'Départ 2', 'yellow', 1),
        label(loop[fork][0] + 0.5, Y + 2.5, loop[fork][1] + 0.5, 'Embranchement : tout droit ou raccourci (sud)', 'green', 1),
        label(loop[merge][0] + 0.5, Y + 2.5, loop[merge][1] + 0.5, 'Fin du raccourci', 'green', 1),
        label(shortcut[3][0] + 0.5, Y + 2.5, shortcut[3][1] + 0.5, 'Raccourci', 'green', 1),
        label(loop[routed][0] + 0.5, Y + 2.5, loop[routed][1] + 0.5, 'Case stop si le routeur est alimenté', 'gold', 0.8),
        label(router[0] + 0.5, Y + 1.8, router[2] + 0.5, 'Routeur + levier', 'white', 0.7, False),
        label(loop[check_point][0] + 0.5, Y + 2, loop[check_point][1] + 0.5, 'Point de passage', 'light_purple', 0.8),
        label(loop[simple][0] + 0.5, Y + 2, loop[simple][1] + 0.5, 'Case simple', 'white', 0.8),
        label(CONTROLLER[0] + 0.5, Y + 2, CONTROLLER[2] + 0.5, 'Party Controller (levier)', 'white', 0.7, False),
        label(STEP_CONTROLLER[0] + 0.5, Y + 2, STEP_CONTROLLER[2] + 0.5, 'Step Controller (levier)', 'white', 0.7, False),
    ]
    return cmds, loop, shortcut


# ---------------------------------------------------------------------------------------------------- stations

def plot_title(x0, z0, title):
    return [label(x0 + 10, Y + 7, z0 + 10, title, 'gold', 3)]


def dice_station(x0=2010, z0=1970):
    cmds = ['# Dice station'] + plot_title(x0, z0, 'DÉS & FORGE')
    forge = (x0 + 10, Y, z0 + 9)
    cmds += ['setblock %d %d %d steveparty:dice_forge[activated=true]{ActivationTime:0L}' % forge,
             label(forge[0] + 0.5, Y + 3.2, forge[2] + 0.5, 'Dice Forge (noyau inséré)', 'white', 0.8, False)]
    cmds += ['setblock %d %d %d steveparty:gravity_core' % (x0 + 4, Y, z0 + 9),
             label(x0 + 4.5, Y + 1.8, z0 + 9.5, 'Gravity Core', 'white', 0.7, False)]
    for k, c in enumerate(['blue', 'green', 'red', 'yellow', 'purple', 'black']):
        cmds.append('setblock %d %d %d steveparty:%s_star_fragments_block' % (x0 + 3 + 3 * k, Y, z0 + 3, c))
    cmds.append(label(x0 + 10.5, Y + 2, z0 + 3.5, 'Blocs de fragments d\'étoile', 'white', 0.8, False))

    forged = '"steveparty:dice-faces":[%s],"minecraft:item_name":%s'
    normal_faces = ','.join('{kind:"normal",value:%d}' % v for v in range(1, 7))
    premium_faces = '{kind:"premium",value:10,weight:2},{kind:"premium",value:7},{kind:"normal",value:5},{kind:"blank"}'
    cursed_faces = '{kind:"cursed",value:1,weight:2},{kind:"cursed",value:2},{kind:"cursed",value:3},{kind:"normal",value:4}'
    items = [
        ('steveparty:default_dice', '', 'Dé'),
        ('steveparty:double_dice', '', 'Double dé'),
        ('steveparty:triple_dice', '', 'Triple dé'),
        ('steveparty:default_dice', forged % (normal_faces, jtext('Dé forgé 1-6')), 'Forgé 1-6'),
        ('steveparty:default_dice', forged % (premium_faces, jtext('Dé forgé premium')), 'Forgé premium'),
        ('steveparty:default_dice', forged % (cursed_faces, jtext('Dé forgé maudit')), 'Forgé maudit'),
        ('steveparty:dice_face_6', '', 'Face 6'),
        ('steveparty:premium_dice_face_10', '', 'Face premium'),
        ('steveparty:cursed_dice_face_3', '', 'Face maudite'),
        ('steveparty:blank_dice_face', '', 'Face vide'),
    ]
    for k, (item, comp, caption) in enumerate(items):
        cmds += show_item(x0 + 1 + 2 * k, z0 + 16, item, comp, caption=caption)

    # A chest to forge: faces and fragments
    faces = ['dice_face_%d' % v for v in range(1, 11)] + ['premium_dice_face_%d' % v for v in range(1, 11)] + \
            ['cursed_dice_face_%d' % v for v in range(1, 4)] + ['blank_dice_face']
    contents = ['{Slot:%db,id:"steveparty:%s",count:16}' % (s, f) for s, f in enumerate(faces)]
    contents += ['{Slot:%db,id:"steveparty:%s_star_fragment",count:16}' % (24 + s, c)
                 for s, c in enumerate(['blue', 'red', 'green'])]
    cmds += ['setblock %d %d %d minecraft:chest[facing=south]{CustomName:%s,Items:[%s]}'
             % (x0 + 14, Y, z0 + 9, jtext('Faces de dé et fragments'), ','.join(contents)),
             label(x0 + 14.5, Y + 1.8, z0 + 9.5, 'Faces & fragments', 'white', 0.7, False)]
    return cmds


def shop_station(x0=2035, z0=1970):
    cmds = ['# Shop station'] + plot_title(x0, z0, 'BOUTIQUE')
    cmds += ['setblock %d %d %d steveparty:trading_stall[facing=south,color1=14,color2=0]' % (x0 + 5, Y, z0 + 4),
             label(x0 + 5.5, Y + 3, z0 + 4.5, 'Trading Stall', 'white', 0.7, False),
             'setblock %d %d %d steveparty:cash_register[facing=south]' % (x0 + 10, Y, z0 + 4),
             label(x0 + 10.5, Y + 1.8, z0 + 4.5, 'Cash Register', 'white', 0.7, False),
             'setblock %d %d %d steveparty:looting_box' % (x0 + 15, Y, z0 + 4),
             label(x0 + 15.5, Y + 2, z0 + 4.5, 'Looting Box', 'white', 0.7, False),
             'setblock %d %d %d steveparty:villager_block' % (x0 + 15, Y, z0 + 8),
             label(x0 + 15.5, Y + 1.8, z0 + 8.5, 'Villager Block', 'white', 0.7, False)]
    cmds += show_item(x0 + 5, z0 + 8, 'steveparty:shopkeeper_key', caption='Clé de marchand')
    # The hiding traders' pen (one per bandana colour)
    px0, pz0, px1, pz1 = x0 + 2, z0 + 11, x0 + 17, z0 + 18
    cmds += ['fill %d %d %d %d %d %d oak_fence' % (px0, Y, pz0, px1, Y, pz1),
             'fill %d %d %d %d %d %d air' % (px0 + 1, Y, pz0 + 1, px1 - 1, Y, pz1 - 1),
             label((px0 + px1) / 2 + 0.5, Y + 3, (pz0 + pz1) / 2 + 0.5, 'Hiding Traders (bandanas)', 'white', 0.9, False)]
    for k in range(5):
        x, z = px0 + 2 + 3 * k, pz0 + 3
        cmds.append('summon steveparty:hiding_trader %.1f %d %.1f {PersistenceRequired:1b,BandanaColor:%d,Home:%dL}'
                    % (x + 0.5, Y, z + 0.5, k, block_pos_long(x, Y, z)))
    return cmds


def goal_pole_station(x0=2060, z0=1970):
    cmds = ['# Goal pole station'] + plot_title(x0, z0, 'GOAL POLE')
    poles = [(x0 + 6, z0 + 9, 6, [(2, 'red'), (4, 'yellow'), (6, 'blue')]),
             (x0 + 13, z0 + 9, 4, [(4, 'lime')])]
    for bx, bz, height, flags in poles:
        cmds.append('setblock %d %d %d steveparty:goal_pole_base[facing=south]' % (bx, Y, bz))
        flag_at = dict(flags)
        for h in range(1, height + 1):
            state = 'facing=south,on_base=%s,top=%s,flag=%s' % (
                'true' if h == 1 else 'false', 'true' if h == height else 'false', 'true' if h in flag_at else 'false')
            nbt = '{FlagColor:%d}' % (DYE_RGB[flag_at[h]] & 0xFFFFFF) if h in flag_at else ''
            cmds.append('setblock %d %d %d steveparty:goal_pole[%s]%s' % (bx, Y + h, bz, state, nbt))
    cmds.append(label(x0 + 10, Y + 1.5, z0 + 14, 'Base + segments + drapeaux colorés', 'white', 0.8, False))
    return cmds


def plastic_station(x0=2010, z0=1995):
    cmds = ['# Plastic station'] + plot_title(x0, z0, 'PLASTIQUE')
    rows = [('plastic_block', '', 'Blocs'), ('plastic_stud', '[face=floor,facing=north]', 'Plots'),
            ('plastic_fence', '', 'Barrières'), ('plastic_slab', '', 'Dalles'),
            ('plastic_stairs', '[facing=north]', 'Escaliers'), ('plastic_wall', '', 'Murs')]
    for r, (kind, state, name) in enumerate(rows):
        z = z0 + 1 + 2 * r
        for k, c in enumerate(COLORS):
            cmds.append('setblock %d %d %d steveparty:%s_%s%s' % (x0 + 2 + k, Y, z, c, kind, state))
        cmds.append(label(x0 + 0.5, Y + 1.2, z + 0.5, name, 'white', 0.6, False))

    # Pool: still water, a bubble column up (soul sand) and one down (magma); plastic floats up
    px0, pz0, px1, pz1 = x0 + 3, z0 + 13, x0 + 14, z0 + 18
    cmds += ['fill %d %d %d %d %d %d smooth_stone' % (px0 - 1, Y - 7, pz0 - 1, px1 + 1, Y - 1, pz1 + 1),
             'fill %d %d %d %d %d %d water' % (px0, Y - 6, pz0, px1, Y - 1, pz1),
             'setblock %d %d %d soul_sand' % (px0 + 3, Y - 7, pz0 + 2),
             'setblock %d %d %d magma_block' % (px0 + 8, Y - 7, pz0 + 2)]
    for k, c in enumerate(['red', 'yellow', 'lime', 'light_blue']):
        cmds.append('setblock %d %d %d steveparty:%s_plastic_block' % (px0 + 1 + 2 * k, Y - 5, pz0 + 4, c))
    cmds += ['setblock %d %d %d steveparty:orange_plastic_block' % (px0 + 3, Y - 4, pz0 + 2),
             'setblock %d %d %d steveparty:plastic_road_sign[plate=star,mount=floor,waterlogged=true]'
             '{PlateColor:"yellow",SymbolShape:%s,Color:"red"}' % (px0 + 6, Y - 1, pz0 + 1, shape_nbt('power_star')),
             label((px0 + px1) / 2 + 0.5, Y + 2, pz1 + 1.5, 'Plastique qui flotte + colonne de bulles', 'white', 0.8, False)]

    # Hop switch: jumping on it switches the plastic wall off for a while
    hop = (x0 + 17, Y, z0 + 15)
    wall = [(x0 + 17, Y + dy, z0 + 18) for dy in range(3)] + [(x0 + 18, Y + dy, z0 + 18) for dy in range(3)]
    cmds += ['setblock %d %d %d steveparty:red_plastic_block' % p for p in wall]
    cmds += ['setblock %d %d %d steveparty:hop_switch' % hop,
             'data merge block %d %d %d {Items:[{Slot:0b,id:"steveparty:board_space_behavior",count:1,'
             'components:{"steveparty:tile-behavior-component":{destinations:[%s],world:""}}}]}'
             % (*hop, ','.join('[I;%d,%d,%d]' % p for p in wall)),
             label(hop[0] + 0.5, Y + 2, hop[2] + 0.5, 'Hop Switch -> mur de plastique', 'white', 0.7, False)]
    return cmds


def sign_station(x0=2035, z0=1995):
    cmds = ['# Sign station'] + plot_title(x0, z0, 'PANNEAUX')
    pats = ['up_arrow', 'right_arrow', 'left_arrow', 'no_entry', 'warning', 'heart', 'star' if 'star' in PATTERNS else 'power_star',
            'creeper_face', 'coin', 'question_block', 'mushroom', 'crown', 'key', 'flag', 'house', 'sun']
    dyes = ['white', 'yellow', 'red', 'black', 'blue', 'lime', 'orange', 'magenta']

    def pattern(k):
        return pats[k % len(pats)]

    # Row 1: the 10 fixed-wood traffic signs
    z = z0 + 2
    for k, wood in enumerate(WOODS):
        cmds.append('setblock %d %d %d steveparty:%s_traffic_sign[rotation=0,mount=post]{SymbolShape:%s,Color:"%s"}'
                    % (x0 + 1 + 2 * k, Y, z, wood, shape_nbt(pattern(k)), dyes[k % len(dyes)]))
    cmds.append(label(x0 + 10, Y + 2.6, z + 0.5, 'Panneaux de signalisation (bois fixes)', 'white', 0.7, False))
    # Row 2: the material traffic sign, every planks
    z = z0 + 6
    for k, wood in enumerate(PLANKS):
        cmds.append('setblock %d %d %d steveparty:traffic_sign[rotation=0,mount=post]{Material:"minecraft:%s_planks",'
                    'SymbolShape:%s,Color:"%s"}' % (x0 - 1 + 2 * k, Y, z, wood, shape_nbt(pattern(k + 3)),
                                                     dyes[(k + 2) % len(dyes)]))
    cmds.append(label(x0 + 10, Y + 2.6, z + 0.5, 'Traffic sign (toutes les planches)', 'white', 0.7, False))
    # Row 3: rock signs, engraved
    z = z0 + 10
    rocks = ['stone', 'granite', 'diorite', 'andesite', 'deepslate', 'tuff', 'sandstone', 'blackstone', 'calcite']
    for k, rock in enumerate(rocks):
        cmds.append('setblock %d %d %d steveparty:rock_sign[rotation=0]{Material:"minecraft:%s",SymbolShape:%s,Engraved:1b}'
                    % (x0 + 1 + 2 * k, Y, z, rock, shape_nbt(pattern(k + 6))))
    cmds.append(label(x0 + 10, Y + 2, z + 0.5, 'Rock signs (gravés)', 'white', 0.7, False))
    # Row 4: plastic road signs on plastic fences, wooden panels on oak fences
    z = z0 + 14
    plates = ['round', 'square', 'diamond', 'triangle', 'star', 'heart']
    plate_colors = ['red', 'blue', 'yellow', 'orange', 'lime', 'pink']
    for k, plate in enumerate(plates):
        x = x0 + 1 + 2 * k
        cmds += ['setblock %d %d %d steveparty:white_plastic_fence' % (x, Y, z),
                 'setblock %d %d %d steveparty:plastic_road_sign[plate=%s,rotation=0,mount=post]'
                 '{PlateColor:"%s",SymbolShape:%s,Color:"white"}' % (x, Y + 1, z, plate, plate_colors[k], shape_nbt(pattern(k)))]
    for k, (block, wood) in enumerate([('wooden_panel', 'oak'), ('wooden_panel', 'cherry'),
                                       ('wooden_cutout_panel', 'spruce'), ('wooden_cutout_panel', 'birch')]):
        x = x0 + 13 + 2 * k - (1 if k == 3 else 0)
        cmds += ['setblock %d %d %d minecraft:oak_fence' % (x, Y, z),
                 'setblock %d %d %d steveparty:%s[rotation=0,mount=post]{Material:"minecraft:%s_planks",'
                 'SymbolShape:%s,Color:"black"}' % (x, Y + 1, z, block, wood, shape_nbt(pattern(k + 9)))]
    cmds.append(label(x0 + 10, Y + 3.4, z + 0.5, 'Plastic road signs + panneaux en bois', 'white', 0.7, False))
    # Row 5: stencil paint on the floor and on a wall, and the stencil maker
    z = z0 + 18
    for k, (pat, dye) in enumerate([('up_arrow', 'yellow'), ('heart', 'red'), ('creeper_face', 'lime'),
                                    ('power_star', 'orange')]):
        cmds.append('setblock %d %d %d steveparty:stencil_paint[orientation=up_north]{SymbolShape:%s,Color:"%s"}'
                    % (x0 + 1 + 2 * k, Y, z, shape_nbt(pat), dye))
    cmds += ['fill %d %d %d %d %d %d minecraft:white_concrete' % (x0 + 9, Y, z + 1, x0 + 12, Y + 1, z + 1)]
    for k, (pat, dye) in enumerate([('right_arrow', 'blue'), ('warning', 'orange')]):
        cmds.append('setblock %d %d %d steveparty:stencil_paint[orientation=north_up]{SymbolShape:%s,Color:"%s"}'
                    % (x0 + 10 + k, Y + 1, z, shape_nbt(pat), dye))
    cmds += ['setblock %d %d %d steveparty:stencil_maker' % (x0 + 16, Y, z),
             label(x0 + 16.5, Y + 1.8, z + 0.5, 'Stencil Maker', 'white', 0.7, False),
             label(x0 + 5, Y + 1.2, z + 0.5, 'Peinture au pochoir', 'white', 0.7, False)]
    return cmds


def building_station(x0=2060, z0=1995):
    cmds = ['# Building blocks showcase'] + plot_title(x0, z0, 'BLOCS DE CONSTRUCTION')
    terracotta = [('polished_%s_terracotta', ''), ('polished_%s_terracotta_bricks', ''),
                  ('%s_polished_terracotta_stairs', '[facing=north]'), ('%s_polished_terracotta_slab', ''),
                  ('%s_polished_terracotta_wall', ''), ('%s_polished_terracotta_bricks_stairs', '[facing=north]'),
                  ('%s_polished_terracotta_bricks_slab', ''), ('%s_polished_terracotta_bricks_wall', '')]
    concrete = [(p.replace('terracotta', 'concrete'), s) for p, s in terracotta]
    for r, (pattern, state) in enumerate(terracotta):
        for k, c in enumerate(COLORS_WITH_DEFAULT):
            cmds.append('setblock %d %d %d steveparty:%s%s' % (x0 + 1 + k, Y, z0 + 1 + r, pattern % c, state))
    for r, (pattern, state) in enumerate(concrete):
        for k, c in enumerate(COLORS):
            cmds.append('setblock %d %d %d steveparty:%s%s' % (x0 + 2 + k, Y, z0 + 11 + r, pattern % c, state))
    cmds += [label(x0 + 9.5, Y + 2, z0 + 0.5, 'Terracotta polie (bloc, briques, escaliers, dalles, murs)', 'white', 0.7, False),
             label(x0 + 9.5, Y + 2, z0 + 10.5, 'Béton poli (bloc, briques, escaliers, dalles, murs)', 'white', 0.7, False)]
    return cmds


def misc_station(x0=2010, z0=2020):
    cmds = ['# Misc station'] + plot_title(x0, z0, 'DIVERS')
    cmds += ['setblock %d %d %d steveparty:big_book' % (x0 + 4, Y, z0 + 4),
             label(x0 + 4.5, Y + 2.5, z0 + 4.5, 'Big Book / pad de téléportation', 'white', 0.7, False),
             'setblock %d %d %d steveparty:big_book' % (x0 + 10, Y, z0 + 4),
             label(x0 + 10.5, Y + 2.5, z0 + 4.5, 'Big Book', 'white', 0.7, False)]
    items = [('here_we_go_book', 'Here We Go'), ('here_we_come_book', 'Here We Come'),
             ('garnet_crystal_ball', 'Garnet Crystal Ball'), ('mini_games_catalogue', 'Catalogue'),
             ('mini_game_page', 'Page de mini-jeu'), ('triple_jump_shoes', 'Triple Jump Shoes'),
             ('plunger', 'Plunger'), ('power_star', 'Power Star'), ('tokenizer_wand', 'Tokenizer Wand'),
             ('token', 'Token'), ('wrench', 'Wrench'), ('stencil_gun', 'Stencil Gun'), ('flag', 'Drapeau'),
             ('bandana', 'Bandana'), ('mula_spawn_egg', 'Oeuf de Mula'), ('plastic_pellets', 'Granulés'),
             ('plastic_stick', 'Bâton en plastique')]
    for k, (item, caption) in enumerate(items):
        x = x0 + 1 + 2 * (k % 9)
        z = z0 + 10 + 5 * (k // 9)
        cmds += show_item(x, z, 'steveparty:' + item, caption=caption)
    return cmds


def mula_station(x0=2060, z0=2020):
    cmds = ['# Mula enclosure'] + plot_title(x0, z0 - 1, 'MULAS')
    gx0, gz0, gx1, gz1, top = x0 + 1, z0 + 1, x0 + 18, z0 + 18, Y + 8
    cmds += ['fill %d %d %d %d %d %d glass' % (gx0, Y, gz0, gx1, top, gz1),
             'fill %d %d %d %d %d %d air' % (gx0 + 1, Y, gz0 + 1, gx1 - 1, top - 1, gz1 - 1),
             # a door on the west side
             'setblock %d %d %d oak_door[facing=east,half=lower,hinge=left]' % (gx0, Y, gz0 + 8),
             'setblock %d %d %d oak_door[facing=east,half=upper,hinge=left]' % (gx0, Y + 1, gz0 + 8)]
    forge = (x0 + 10, Y, z0 + 10)
    cmds += ['setblock %d %d %d steveparty:dice_forge[activated=true]{ActivationTime:0L}' % forge]
    mulas = [(0, 0), (1, 10), (2, 20), (3, 30), (4, 40), (5, 0), (0, 25), (2, 5)]
    for k, (variant, hunger) in enumerate(mulas):
        a = 2 * math.pi * k / len(mulas)
        x, z = forge[0] + 0.5 + 5 * math.cos(a), forge[2] + 0.5 + 5 * math.sin(a)
        cmds.append('summon steveparty:mula %.1f %d %.1f {Variant:%d,Hunger:%d,PersistenceRequired:1b}'
                    % (x, Y, z, variant, hunger))
    cmds.append(label(gx0 - 1.5, Y + 2.5, gz0 + 8.5, 'Mulas (6 couleurs, faim 0 à 40) + Dice Forge', 'white', 0.8, False))
    return cmds


# ---------------------------------------------------------------------------------------------------- every item

def all_items():
    """Every registered steveparty item, grouped (same registration order as ModItems / ModBlocks)."""
    groups = []
    groups.append(('Plateau', ['tile', 'simple_tile', 'check_point', 'board_space_redstone_router', 'party_controller',
                               'step_controller', 'board_space_behavior', 'board_space_behavior_stop',
                               'tile_behavior_start', 'inventory_cartridge', 'wrench', 'tokenizer_wand', 'token',
                               'plunger', 'mini_games_catalogue', 'mini_game_page', 'garnet_crystal_ball',
                               'big_book', 'here_we_go_book', 'here_we_come_book']))
    faces = ['blank_dice_face'] + ['dice_face_%d' % i for i in range(1, 11)] + \
            ['premium_dice_face_%d' % i for i in range(1, 11)] + ['cursed_dice_face_%d' % i for i in range(1, 4)]
    groups.append(('Dés', ['default_dice', 'double_dice', 'triple_dice', 'dice_forge', 'gravity_core'] + faces))
    stars = ['%s_star_fragment' % c for c in ['blue', 'purple', 'red', 'yellow', 'green', 'black']]
    groups.append(('Mulas et étoiles', ['mula_spawn_egg', 'power_star'] + stars +
                   ['%s_star_fragments_block' % c for c in ['blue', 'green', 'red', 'yellow', 'purple', 'black']]))
    groups.append(('Boutique et divers', ['trading_stall', 'cash_register', 'looting_box', 'villager_block',
                                          'shopkeeper_key', 'bandana', 'goal_pole_base', 'goal_pole', 'flag',
                                          'triple_jump_shoes', 'hop_switch', 'plastic_pellets', 'plastic_stick']))
    groups.append(('Panneaux', ['%s_traffic_sign' % w for w in WOODS] +
                   ['traffic_sign', 'wooden_panel', 'wooden_cutout_panel', 'rock_sign', 'plastic_road_sign',
                    'stencil', 'stencil_gun', 'stencil_maker']))
    for kind, name in [('plastic_block', 'blocs'), ('plastic_stud', 'plots'), ('plastic_fence', 'barrières'),
                       ('plastic_slab', 'dalles'), ('plastic_stairs', 'escaliers'), ('plastic_wall', 'murs')]:
        groups.append(('Plastique : ' + name, ['%s_%s' % (c, kind) for c in COLORS]))
    ter = [('polished_%s_terracotta', 'blocs'), ('polished_%s_terracotta_bricks', 'briques'),
           ('%s_polished_terracotta_stairs', 'escaliers'), ('%s_polished_terracotta_slab', 'dalles'),
           ('%s_polished_terracotta_wall', 'murs'), ('%s_polished_terracotta_bricks_stairs', 'escaliers de briques'),
           ('%s_polished_terracotta_bricks_slab', 'dalles de briques'), ('%s_polished_terracotta_bricks_wall', 'murs de briques')]
    for p, name in ter:
        groups.append(('Terracotta polie : ' + name, [p % c for c in COLORS_WITH_DEFAULT]))
    for p, name in ter:
        q = p.replace('terracotta', 'concrete')
        groups.append(('Béton poli : ' + name, [q % c for c in COLORS]))
    return groups


def chests_station(x0=2035, z0=2020):
    """A row of chests, each labelled, holding one of every item (27 per chest)."""
    cmds = ['# Chests with one of every item'] + plot_title(x0, z0 - 1, 'UN DE CHAQUE OBJET')
    chests = []
    for name, ids in all_items():
        parts = [ids[i:i + 27] for i in range(0, len(ids), 27)]
        for n, part in enumerate(parts):
            chests.append((name + ('' if len(parts) == 1 else ' %d/%d' % (n + 1, len(parts))), part))
    # Extra chest: one stencil of each pattern, the traffic sign in every planks, the bandana in every colour
    extras = ['{id:"steveparty:stencil",count:1,components:{"steveparty:stencil-pixels":%s}}' % shape_list(p)
              for p in PATTERNS]
    extra_chests = [('Pochoirs %d/%d' % (i // 27 + 1, (len(extras) + 26) // 27), extras[i:i + 27])
                    for i in range(0, len(extras), 27)]
    variants = ['{id:"steveparty:traffic_sign",count:1,components:{"steveparty:sign-material":"minecraft:%s_planks"}}' % w
                for w in PLANKS]
    variants += ['{id:"steveparty:bandana",count:1,components:{"steveparty:bandana-color":%d}}' % c for c in range(5)]
    extra_chests.append(('Variantes (planches, bandanas)', variants))

    total = 0
    per_row = 10
    all_chests = [(name, ['{id:"steveparty:%s",count:1}' % i for i in ids]) for name, ids in chests] + extra_chests
    for k, (name, stacks) in enumerate(all_chests):
        x = x0 + 1 + 2 * (k % per_row)
        z = z0 + 2 + 4 * (k // per_row)
        items = ','.join(s.replace('{', '{Slot:%db,' % slot, 1) for slot, s in enumerate(stacks))
        cmds.append('setblock %d %d %d minecraft:chest[facing=south]{CustomName:%s,Items:[%s]}' % (x, Y, z, jtext(name), items))
        cmds.append(label(x + 0.5, Y + (1.4 if k % 2 == 0 else 2.0), z + 0.5, name, 'white', 0.45, False))
        total += len(stacks)
    return cmds, sum(len(ids) for _, ids in chests), len(all_chests)


# ---------------------------------------------------------------------------------------------------- welcome

def welcome(loop):
    sx, sz = loop[0]
    px, pz = sx + 0.5, sz + 4.5
    bx = sum(p[0] for p in loop) / len(loop)
    bz = sum(p[1] for p in loop) / len(loop)
    yaw = -math.degrees(math.atan2(bx - px, bz - pz))
    kit = ['steveparty:default_dice', 'steveparty:double_dice', 'steveparty:triple_dice',
           'steveparty:default_dice[steveparty:dice-faces=[{kind:"premium",value:10},{kind:"normal",value:5},'
           '{kind:"cursed",value:2}],item_name=\'"Dé forgé"\']',
           'steveparty:tokenizer_wand', 'steveparty:token 4', 'steveparty:wrench', 'steveparty:board_space_behavior 8',
           'steveparty:board_space_behavior_stop 2', 'steveparty:tile_behavior_start 2', 'steveparty:inventory_cartridge 2',
           'steveparty:tile 8', 'steveparty:simple_tile 4', 'steveparty:check_point 2', 'steveparty:mini_games_catalogue']
    return (['time set day', 'weather clear']
            + ['give %s %s' % (PLAYER, k) for k in kit]
            + ['tp %s %.1f %d %.1f %.1f 25' % (PLAYER, px, Y, pz, yaw),
               'tellraw %s {"text":"[test] Monde de test prêt : plateau ici (levier du Party Controller = lancer la partie), '
               'stations à l\'est (dés, boutique, goal pole, plastique, panneaux, blocs, divers, coffres, Mulas).",'
               '"color":"gold"}' % PLAYER])


# ---------------------------------------------------------------------------------------------------- main

def main():
    board_cmds, loop, shortcut = board()
    chest_cmds, n_items, n_chests = chests_station()
    functions = {
        'terrain': terrain(),
        'board': board_cmds,
        'stations': (dice_station() + shop_station() + goal_pole_station() + plastic_station() + sign_station()
                     + building_station() + misc_station() + mula_station()),
        'chests': chest_cmds,
        'welcome': welcome(loop),
    }
    functions['build'] = ['function %s:%s' % (NS, f) for f in ('terrain', 'board', 'stations', 'chests')] + \
                         ['kill @e[type=minecraft:item,x=%d,y=60,z=%d,dx=%d,dy=200,dz=%d]'
                          % (AREA[0], AREA[1], AREA[2] - AREA[0], AREA[3] - AREA[1])]

    pack = os.path.join(RUN_DIR, 'world', 'datapacks', 'steveparty-test')
    fdir = os.path.join(pack, 'data', NS, 'function')
    os.makedirs(fdir, exist_ok=True)
    with open(os.path.join(pack, 'pack.mcmeta'), 'w', encoding='utf-8') as f:
        json.dump({'pack': {'pack_format': 57, 'description': 'SteveParty test world'}}, f)
    for name, cmds in functions.items():
        with open(os.path.join(fdir, name + '.mcfunction'), 'w', encoding='utf-8') as f:
            f.write('\n'.join(cmds) + '\n')
        print('%-9s %5d commands' % (name, len(cmds)))
    print('board: %d loop tiles, %d shortcut tiles; %d items in %d chests; %d stencil patterns'
          % (len(loop), len(shortcut), n_items, n_chests, len(PATTERNS)))
    for i, p in enumerate(loop):
        print('  P%-2d %s' % (i, p), end='\n' if i % 6 == 5 else '')
    print()
    print('  shortcut', shortcut)


if __name__ == '__main__':
    main()

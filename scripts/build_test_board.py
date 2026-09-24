"""
Builds a small test board on a dev server through RCON (port = dev server port + 10, 25593 for port 25583):
a loop of 22 tiles 3 blocks apart, 2 start tiles with their tokens (owned by LordFinn), a party controller with a program,
party bells with lamps, a piggy bank and a podium.

Usage: python scripts/build_test_board.py <x> <y> <z> [rcon port]
"""
import socket
import struct
import sys

OWNER = "[I; 1622346422, -1549584000, -1677805493, -1020466992]"  # LordFinn (offline uuid)
X0, Y, Z0 = int(sys.argv[1]), int(sys.argv[2]), int(sys.argv[3])
NW, ND = 8, 5  # tiles per side of the loop
SP = 3  # distance between two tiles
W, D = (NW - 1) * SP, (ND - 1) * SP  # size of the loop in blocks


def pkt(i, t, body):
    data = struct.pack('<ii', i, t) + body.encode() + b'\x00\x00'
    return struct.pack('<i', len(data)) + data


sock = socket.create_connection(('127.0.0.1', int(sys.argv[4]) if len(sys.argv) > 4 else 25593), timeout=10)
sock.sendall(pkt(1, 3, 'steveparty-dev'))
sock.recv(4096)
counter = [10]


def cmd(c):
    counter[0] += 1
    sock.sendall(pkt(counter[0], 2, c))
    out = sock.recv(8192)[12:-2].decode(errors='ignore')
    if out and ('rror' in out or 'Unknown' in out or 'Invalid' in out or 'Expected' in out):
        print('!!', c[:120], '->', out[:200])
    return out


def pos(x, z, y=Y):
    return f"{x} {y} {z}"


# ------------------------------------------------------------------ clean area
cmd(f"fill {X0-6} {Y-1} {Z0-4} {X0+W+7} {Y-1} {Z0+D+4} minecraft:smooth_stone")
cmd(f"fill {X0-6} {Y} {Z0-4} {X0+W+7} {Y+4} {Z0+D+4} minecraft:air")
cmd(f"kill @e[type=!player,x={X0-6},y={Y-1},z={Z0-4},dx={W+14},dy=6,dz={D+9}]")

# ------------------------------------------------------------------ loop of tiles (clockwise), SP blocks apart
loop = [(X0 + i * SP, Z0) for i in range(NW)]
loop += [(X0 + W, Z0 + j * SP) for j in range(1, ND)]
loop += [(X0 + i * SP, Z0 + D) for i in range(NW - 2, -1, -1)]
loop += [(X0, Z0 + j * SP) for j in range(ND - 2, 0, -1)]
COLORS = [0x0083DF, 0xC41C24, 0x45B649, 0xF29F05]


def cartridge(item, dest, color=None):
    comps = f'"steveparty:tile-behavior-component":{{destinations:[[I;{dest[0]},{Y},{dest[1]}]]}}'
    if color is not None:
        comps += f',"steveparty:color":{color}'
    return f'{{Slot:0b,id:"{item}",count:1,components:{{{comps}}}}}'


for i, (x, z) in enumerate(loop):
    nxt = loop[(i + 1) % len(loop)]
    cmd(f"setblock {pos(x, z)} steveparty:tile{{Items:[{cartridge('steveparty:board_space_behavior', nxt, COLORS[i % 4])}]}}")

# ------------------------------------------------------------------ start tiles + tokens
starts = [(X0 - SP, Z0), (X0 - SP, Z0 + SP)]
names = ['Rosie', 'Porky']
for (x, z), name in zip(starts, names):
    cmd(f"setblock {pos(x, z)} steveparty:tile[tile_type=tile_start]{{Items:[{cartridge('steveparty:tile_behavior_start', loop[0])}]}}")
    cmd(f'summon minecraft:pig {x}.5 {Y}.2 {z}.5 {{Tokenized:1b,TokenOwner:{OWNER},TokenStatus:0,'
        f'CustomName:\'"{name}"\',CustomNameVisible:1b,NoAI:1b,Invulnerable:1b,PersistenceRequired:1b}}')

# Bind the tokens to their start tiles (a token standing still is not detected by the tile)
import re
import uuid as uuidlib


def to_uuid(ints):
    return str(uuidlib.UUID(bytes=b''.join(struct.pack('>i', i) for i in ints)))


owner_uuid = to_uuid([int(n) for n in re.findall(r'-?\d+', OWNER)])
for (x, z), name in zip(starts, names):
    out = cmd(f"data get entity @e[type=pig,name={name},limit=1] UUID")
    token_uuid = to_uuid([int(n) for n in re.findall(r'-?\d+', out.split(':', 1)[1])][-4:])
    cmd(f'data modify block {pos(x, z)} Items[0].components merge value '
        f'{{"steveparty:bound-entity":"{token_uuid}","steveparty:owner":"{owner_uuid}"}}')

# ------------------------------------------------------------------ controller (program: turns + event + repeat x3, coin/star)
cx, cz = X0 + W // 2, Z0 + D // 2
settings = ('{Items:[{Slot:0b,id:"minecraft:gold_nugget",count:1},{Slot:1b,id:"steveparty:power_star",count:1},'
            '{Slot:2b,id:"steveparty:party_card_turns",count:1},{Slot:3b,id:"steveparty:party_card_event",count:1},'
            '{Slot:4b,id:"steveparty:party_card_repeat",count:3}]}')
cmd(f"setblock {pos(cx, cz)} steveparty:party_controller[facing=south]{{PartySettings:{settings}}}")
cmd(f"setblock {pos(cx - 1, cz)} minecraft:stone_bricks")
cmd(f"setblock {pos(cx - 1, cz, Y + 1)} minecraft:stone_button[face=floor]")
# comparator reading the phase -> lamp
cmd(f"setblock {pos(cx + 1, cz)} minecraft:comparator[facing=west]")
cmd(f"setblock {pos(cx + 2, cz)} minecraft:redstone_lamp")


def sign(x, z, *lines):
    msgs = ",".join("'\"" + l + "\"'" for l in (list(lines) + ["", "", "", ""])[:4])
    cmd(f"setblock {pos(x, z)} minecraft:oak_sign[rotation=8]{{front_text:{{messages:[{msgs}]}}}}")


sign(cx - 1, cz + 1, "Lancer", "la partie")
sign(cx + 2, cz + 1, "Phase", "(comparateur)")

# ------------------------------------------------------------------ bells, one in each quarter of the loop
qx1, qx2, qz1, qz2 = X0 + SP + 2, X0 + W - SP - 2, Z0 + SP, Z0 + D - SP
# turn start (pulse) -> lamp
cmd(f"setblock {pos(qx1, qz1)} steveparty:party_bell[moment=turn_start]")
cmd(f"setblock {pos(qx1 + 1, qz1)} minecraft:redstone_lamp")
sign(qx1, qz1 + 1, "Debut de tour")
# dice rolled -> comparator -> lamp (the comparator gives the dice value)
cmd(f"setblock {pos(qx2, qz1)} steveparty:party_bell[moment=dice_rolled]")
cmd(f"setblock {pos(qx2 + 1, qz1)} minecraft:comparator[facing=west]")
cmd(f"setblock {pos(qx2 + 2, qz1)} minecraft:redstone_lamp")
sign(qx2, qz1 + 1, "De lance")
# event card (waiting) + button to go on
cmd(f"setblock {pos(qx2, qz2)} steveparty:party_bell[moment=event,waiting=true]")
cmd(f"setblock {pos(qx2 + 1, qz2)} minecraft:stone_bricks")
cmd(f"setblock {pos(qx2 + 1, qz2, Y + 1)} minecraft:stone_button[face=floor]")
sign(qx2, qz2 + 1, "Evenement", "(attente)", "bouton: suite")
# party end -> lamp
cmd(f"setblock {pos(qx1, qz2)} steveparty:party_bell[moment=party_end]")
cmd(f"setblock {pos(qx1 + 1, qz2)} minecraft:redstone_lamp")
sign(qx1, qz2 + 1, "Fin de partie")

# ------------------------------------------------------------------ piggy bank + podium showcase (outside the loop)
cmd(f"setblock {pos(X0 + W + 4, Z0 + SP)} steveparty:piggy_bank[facing=west]")
cmd(f"setblock {pos(X0 + W + 4, Z0 + D - SP)} steveparty:podium")
sign(X0 + W + 4, Z0 + SP + 1, "Tirelire")
sign(X0 + W + 4, Z0 + D - SP + 1, "Podium")

# ------------------------------------------------------------------ player kit
for item in ("steveparty:default_dice 2", "steveparty:wrench", "minecraft:gold_nugget 32"):
    cmd(f"give LordFinn {item}")
cmd(f"tp LordFinn {X0 - SP - 2} {Y} {Z0 + D // 2} -90 25")
print("board built:", len(loop), "tiles")

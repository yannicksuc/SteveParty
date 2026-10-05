"""Validate SteveParty advancements (live ones and the pending ones waiting for their custom triggers).

Checks: JSON parses, parents exist, criteria/requirements agree, triggers are vanilla 1.21.1 or known SteveParty
triggers (a live advancement may only use a custom trigger once it is registered in Java), every item/block/entity/potion id exists in vanilla 1.21.1 or in the mod, tags exist,
reward loot tables and tab backgrounds exist, and every title/description key is in en_us and fr_fr.

Usage: python scripts/validate_advancements.py [path/to/1.21.1 registries data.min.json]
Without a path, the 1.21.1 registries are downloaded from misode/mcmeta (summary branch).
"""
import json, os, re, sys, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
DATA = os.path.join(RES, "data", "steveparty")
ASSETS = os.path.join(RES, "assets", "steveparty")
PENDING = os.path.join(ROOT, "docs", "advancements-pending", "data", "steveparty")
JAVA = os.path.join(ROOT, "src", "main", "java", "fr", "lordfinn", "steveparty")

# Custom triggers specified in docs/advancements.md, implemented after the 1.21.1 port
CUSTOM_TRIGGERS = {
    "pawn_spell", "dice_rolled", "dice_coins", "dice_forged", "dice_forge_level", "entity_orbiting",
    "forge_core_exploded", "mula_burst", "mula_lifted", "ephemeride_seen", "telescope_star_found",
    "mula_site_reached", "mula_dance", "boxed_trader_created", "shop_sale", "party_started", "party_ended",
    "pawn_landed", "minigame_ended", "minigame_ready", "podium_claimed", "pole_goal_reached", "looting_box_hit",
}
VANILLA_ENTITY_TAGS = {"skeletons", "raiders", "undead", "arthropod", "zombies", "illager", "aquatic"}

errors = []


def err(msg):
    errors.append(msg)


def load_vanilla():
    if len(sys.argv) > 1:
        d = json.load(open(sys.argv[1], encoding="utf-8"))
    else:
        host = "raw." + "githubusercontent.com"
        url = f"https://{host}/misode/mcmeta/1.21.1-summary/registries/data.min.json"
        d = json.loads(urllib.request.urlopen(url, timeout=30).read().decode())
    return {k: {"minecraft:" + x for x in d[k]} for k in ("item", "block", "entity_type", "trigger_type", "potion")}


def mod_ids():
    ids = set()
    for sub in ("models/item", "blockstates"):
        p = os.path.join(ASSETS, sub)
        ids |= {"steveparty:" + f[:-5] for f in os.listdir(p) if f.endswith(".json")}
    for f in ("items/ModItems.java", "blocks/ModBlocks.java", "entities/ModEntities.java"):
        src = open(os.path.join(JAVA, f), encoding="utf-8").read()
        ids |= {"steveparty:" + m for m in re.findall(r'"([a-z0-9_]+)"', src)}
    # generated ids (loops in ModItems)
    for i in range(0, 11):
        ids.add(f"steveparty:dice_face_{i}")
    for p in ("premium_dice_face_", "coin_dice_face_", "debt_dice_face_"):
        ids |= {f"steveparty:{p}{i}" for i in range(1, 11)}
    ids |= {f"steveparty:cursed_dice_face_{i}" for i in range(1, 4)}
    for m in ("slow", "choice", "infinity", "lucky", "reroll", "reversed", "skeleton_key", "homing"):
        ids.add(f"steveparty:dice_module_{m}")
    for c in ("turns", "minigame", "event", "repeat", "sequence_start"):
        ids.add(f"steveparty:party_card_{c}")
    return ids


def registered_triggers():
    """Custom trigger ids registered in Java: the literal "steveparty:<id>" or "<id>" next to Criteria.register."""
    found = set()
    for dp, _, fs in os.walk(JAVA):
        for f in fs:
            if f.endswith(".java"):
                src = open(os.path.join(dp, f), encoding="utf-8").read()
                if "Criteria.register" not in src:
                    continue
                for t in CUSTOM_TRIGGERS:
                    if f'"steveparty:{t}"' in src or f'"{t}"' in src:
                        found.add("steveparty:" + t)
    return found


def walk_files(base):
    out = {}
    adv = os.path.join(base, "advancement")
    if not os.path.isdir(adv):
        return out
    for dp, _, fs in os.walk(adv):
        for f in fs:
            if f.endswith(".json"):
                p = os.path.join(dp, f)
                rid = "steveparty:" + os.path.relpath(p, adv)[:-5].replace(os.sep, "/")
                try:
                    out[rid] = (p, json.load(open(p, encoding="utf-8")))
                except Exception as e:
                    err(f"{p}: invalid JSON: {e}")
    return out


def check_id(where, rid, kinds, vanilla, mod):
    if rid.startswith("#"):
        ns, path = rid[1:].split(":")
        if ns == "steveparty":
            tag_dir = {"item": "item", "block": "block", "entity_type": "entity_type"}[kinds[0]]
            generated = os.path.join(DATA, "..", "..", "..", "generated", "data", "steveparty", "tags", tag_dir, path + ".json")  # datagen tags
            if not os.path.isfile(os.path.join(DATA, "tags", tag_dir, path + ".json")) and not os.path.isfile(generated):
                err(f"{where}: unknown tag {rid}")
        elif kinds[0] == "entity_type" and path not in VANILLA_ENTITY_TAGS:
            err(f"{where}: unverified vanilla entity tag {rid}")
        return
    if rid.startswith("steveparty:"):
        if rid not in mod:
            err(f"{where}: unknown mod id {rid}")
    elif not any(rid in vanilla[k] for k in kinds):
        err(f"{where}: unknown vanilla {kinds} id {rid}")


def scan(where, node, vanilla, mod):
    if isinstance(node, dict):
        for k, v in node.items():
            if k in ("items", "blocks") and not (isinstance(v, list) and v and isinstance(v[0], dict)):
                kinds = ["item"] if k == "items" else ["block"]
                for x in ([v] if isinstance(v, str) else v):
                    check_id(where, x, kinds, vanilla, mod)
            elif k == "type" and isinstance(v, str):
                check_id(where, v, ["entity_type"], vanilla, mod)
            elif k == "potion" and isinstance(v, str):
                check_id(where, v, ["potion"], vanilla, mod)
            elif k == "block" and isinstance(v, str):
                check_id(where, v, ["block"], vanilla, mod)
            else:
                scan(where, v, vanilla, mod)
    elif isinstance(node, list):
        for x in node:
            scan(where, x, vanilla, mod)


def main():
    vanilla = load_vanilla()
    mod = mod_ids()
    registered = registered_triggers()
    live = walk_files(DATA)
    pending = walk_files(PENDING)
    for rid in set(live) & set(pending):
        err(f"{rid}: both live and pending")
    langs = {}
    for l in ("en_us", "fr_fr"):
        langs[l] = json.load(open(os.path.join(ASSETS, "lang", l + ".json"), encoding="utf-8"))
        guide = os.path.join(ASSETS, "..", "steveparty_guide", "lang", l + ".json")  # the guide book's own texts
        if os.path.exists(guide):
            langs[l].update(json.load(open(guide, encoding="utf-8")))

    stats = {"live": 0, "pending": 0}
    for group, files in (("live", live), ("pending", pending)):
        for rid, (path, a) in sorted(files.items()):
            if rid.startswith("steveparty:recipes/"):
                continue
            stats[group] += 1
            where = f"[{group}] {rid}"
            parent = a.get("parent")
            if parent and not parent.startswith("minecraft:"):
                ok = parent in live if group == "live" else (parent in live or parent in pending)
                if not ok:
                    err(f"{where}: missing parent {parent}")
            crits = a.get("criteria", {})
            if not crits:
                err(f"{where}: no criteria")
            req = a.get("requirements", [[c] for c in crits])
            used = {c for r in req for c in r}
            if used != set(crits):
                err(f"{where}: requirements {sorted(used)} != criteria {sorted(crits)}")
            for name, c in crits.items():
                t = c.get("trigger", "")
                if t.startswith("steveparty:"):
                    if group == "live" and t not in registered:
                        err(f"{where}/{name}: custom trigger {t} is live but not registered in Java")
                    elif t.split(":")[1] not in CUSTOM_TRIGGERS:
                        err(f"{where}/{name}: unknown custom trigger {t}")
                elif t not in vanilla["trigger_type"]:
                    err(f"{where}/{name}: unknown vanilla trigger {t}")
                scan(f"{where}/{name}", c.get("conditions", {}), vanilla, mod)
            d = a.get("display")
            if d:
                check_id(where + "/icon", d["icon"]["id"], ["item"], vanilla, mod)
                if d.get("frame", "task") not in ("task", "goal", "challenge"):
                    err(f"{where}: bad frame")
                for part in ("title", "description"):
                    k = d[part].get("translate")
                    for l, table in langs.items():
                        if k not in table:
                            err(f"{where}: {l} misses {k}")
                bg = d.get("background")
                if bg:
                    ns, p = bg.split(":")
                    if ns == "steveparty" and not os.path.isfile(os.path.join(ASSETS, p)):
                        err(f"{where}: missing background {bg}")
                if not parent and not bg:
                    err(f"{where}: root without background")
            for lt in a.get("rewards", {}).get("loot", []):
                ns, p = lt.split(":")
                lp = os.path.join(DATA, "loot_table", p + ".json")
                if ns != "steveparty" or not os.path.isfile(lp):
                    err(f"{where}: missing reward loot table {lt}")
                else:
                    scan(where + "/reward", json.load(open(lp, encoding="utf-8")).get("pools", []), vanilla, mod)
    # loot entries use "name" for the item id
    for f in os.listdir(os.path.join(DATA, "loot_table", "advancement_reward")):
        lt = json.load(open(os.path.join(DATA, "loot_table", "advancement_reward", f), encoding="utf-8"))
        for pool in lt["pools"]:
            for e in pool["entries"]:
                check_id(f"loot {f}", e["name"], ["item"], vanilla, mod)
    print(f"{stats['live']} live, {stats['pending']} pending advancements checked (recipe unlocks skipped)")
    if errors:
        print(f"{len(errors)} error(s):")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print("OK")


main()

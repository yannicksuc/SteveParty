#!/usr/bin/env python3
"""Checks the Modonomicon guide book and its advancements (no game, no Gradle needed).

    python scripts/guide/check_guide.py

Checks: every JSON parses; book / category / entry / parent / condition references resolve; every
translation key exists in en_us and fr_fr (same key set, no empty value); icons, spotlight items and
advancement items/blocks exist in the mod (lang keys or item models) or in a small vanilla allow-list;
recipe pages point to existing recipes of a type Modonomicon can draw; advancements have no rewards and
never announce to chat; a gated child always carries its parent's gates; no two entries share a slot.
Exit code 1 on any error. Warnings (long texts) do not fail.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / "src" / "main"
RES_DIRS = [MAIN / "resources", MAIN / "generated"]
BOOK = MAIN / "resources" / "data" / "steveparty" / "modonomicon" / "books" / "guide"
ADV = MAIN / "resources" / "data" / "steveparty" / "advancement" / "guide"
LANG = MAIN / "resources" / "assets" / "steveparty_guide" / "lang"
VANILLA_OK = {"minecraft:clock", "minecraft:armor_stand", "minecraft:enchanted_book", "minecraft:barrier",
              "minecraft:glow_ink_sac", "minecraft:book"}
RECIPE_TYPES = {
    "modonomicon:crafting_recipe": {"minecraft:crafting_shaped", "minecraft:crafting_shapeless"},
    "modonomicon:smelting_recipe": {"minecraft:smelting"},
    "modonomicon:stonecutting_recipe": {"minecraft:stonecutting"},
}
PAGE_TYPES = {"modonomicon:text", "modonomicon:spotlight", *RECIPE_TYPES}
TEXT_MAX, SMALL_MAX = 420, 190  # rough characters that fit a page before Modonomicon scales text down

errors, warnings = [], []


def err(msg):
    errors.append(msg)


def load(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as e:  # noqa: BLE001
        err(f"{path.relative_to(ROOT)}: invalid JSON ({e})")
        return None


# ------------------------------------------------------------------ registries as seen in the mod
mod_lang = load(MAIN / "resources" / "assets" / "steveparty" / "lang" / "en_us.json") or {}
mod_items = set()
for k in mod_lang:
    parts = k.split(".")
    if len(parts) == 3 and parts[0] in ("item", "block") and parts[1] == "steveparty":
        mod_items.add("steveparty:" + parts[2])
mod_blocks = {"steveparty:" + k.split(".")[2] for k in mod_lang if k.startswith("block.steveparty.") and k.count(".") == 2}
for d in RES_DIRS:
    for m in (d / "assets" / "steveparty" / "models" / "item").glob("*.json"):
        mod_items.add("steveparty:" + m.stem)

recipes = {}
for d in RES_DIRS:
    for r in (d / "data" / "steveparty" / "recipe").glob("*.json"):
        data = load(r)
        if data is not None:
            recipes["steveparty:" + r.stem] = data.get("type")

item_tags = set()
for d in RES_DIRS:
    for sub in ("item", "items"):
        for t in (d / "data" / "steveparty" / "tags" / sub).glob("**/*.json"):
            item_tags.add("#steveparty:" + t.relative_to(d / "data" / "steveparty" / "tags" / sub).with_suffix("").as_posix())


def item_exists(iid):
    return iid in mod_items or iid in VANILLA_OK


# ------------------------------------------------------------------ lang
en = load(LANG / "en_us.json") or {}
fr = load(LANG / "fr_fr.json") or {}
for k in set(en) ^ set(fr):
    err(f"lang: key {k} missing in {'fr_fr' if k in en else 'en_us'}")
for name, lang in (("en_us", en), ("fr_fr", fr)):
    for k, v in lang.items():
        if not isinstance(v, str) or not v.strip():
            err(f"lang {name}: empty value for {k}")
used = set()


def key(k, where):
    used.add(k)
    if k not in en or k not in fr:
        err(f"{where}: translation key {k} missing")


# ------------------------------------------------------------------ advancements
advs = {}
for f in sorted(ADV.glob("*.json")):
    a = load(f)
    if a is None:
        continue
    aid = "steveparty:guide/" + f.stem
    advs[aid] = a
for aid, a in advs.items():
    w = f"advancement {aid}"
    if "rewards" in a:
        err(f"{w}: has rewards (rewards come from FTB Quests)")
    if a.get("parent") and a["parent"] not in advs:
        err(f"{w}: unknown parent {a['parent']}")
    disp = a.get("display")
    if not disp:
        err(f"{w}: no display")
    else:
        if disp.get("announce_to_chat", True):
            err(f"{w}: announce_to_chat must be false")
        if not item_exists(disp.get("icon", {}).get("id", "")):
            err(f"{w}: unknown icon {disp.get('icon')}")
        for part in ("title", "description"):
            key(disp.get(part, {}).get("translate", ""), w)
    crit = a.get("criteria", {})
    if not crit:
        err(f"{w}: no criteria")
    for req in a.get("requirements", []):
        for c in req:
            if c not in crit:
                err(f"{w}: requirement {c} is not a criterion")
    for cname, c in crit.items():
        trig, cond = c.get("trigger"), c.get("conditions", {})
        if trig == "minecraft:inventory_changed":
            for pred in cond.get("items", []):
                ids = pred.get("items")
                for iid in [ids] if isinstance(ids, str) else ids:
                    if iid.startswith("#"):
                        if iid not in item_tags:
                            err(f"{w}/{cname}: unknown item tag {iid}")
                    elif not item_exists(iid):
                        err(f"{w}/{cname}: unknown item {iid}")
        elif trig == "minecraft:placed_block":
            for lc in cond.get("location", []):
                if lc.get("block") not in mod_blocks:
                    err(f"{w}/{cname}: unknown block {lc.get('block')}")
        else:
            err(f"{w}/{cname}: unexpected trigger {trig}")

# ------------------------------------------------------------------ book
book = load(BOOK / "book.json") or {}
key(book.get("name", ""), "book.json")
if book.get("tooltip"):
    key(book["tooltip"], "book.json")

cats = {}
for f in sorted((BOOK / "categories").glob("*.json")):
    c = load(f)
    if c is None:
        continue
    cats[f.stem] = c
    key(c.get("name", ""), f"category {f.stem}")
    if not item_exists(c.get("icon", {}).get("item", "")):
        err(f"category {f.stem}: unknown icon {c.get('icon')}")

entries = {}
for f in sorted((BOOK / "entries").glob("*/*.json")):
    e = load(f)
    if e is not None:
        entries[f"{f.parent.name}/{f.stem}"] = e


def cond_gates(cond, where):
    """Validates a condition and returns the set of advancement ids it requires."""
    if not cond:
        return set()
    t = cond.get("type")
    if cond.get("tooltip"):
        key(cond["tooltip"], where)
    if t == "modonomicon:advancement":
        if cond.get("advancement_id") not in advs:
            err(f"{where}: unknown advancement {cond.get('advancement_id')}")
        return {cond.get("advancement_id")}
    if t == "modonomicon:entry_read":
        if cond.get("entry_id") not in entries:
            err(f"{where}: unknown entry {cond.get('entry_id')}")
        return set()
    if t in ("modonomicon:and", "modonomicon:or"):
        out = set()
        for ch in cond.get("children", []):
            out |= cond_gates(ch, where)
        return out
    if t != "modonomicon:none":
        err(f"{where}: unexpected condition type {t}")
    return set()


slots = {}
for eid, e in entries.items():
    w = f"entry {eid}"
    cat = eid.split("/")[0]
    if e.get("category") != cat or cat not in cats:
        err(f"{w}: category {e.get('category')} does not match its folder or does not exist")
    key(e.get("name", ""), w)
    if e.get("description"):
        key(e["description"], w)
    if not item_exists(e.get("icon", {}).get("item", "")):
        err(f"{w}: unknown icon {e.get('icon')}")
    slot = (cat, e.get("x"), e.get("y"))
    if slot in slots:
        err(f"{w}: same position as {slots[slot]}")
    slots[slot] = eid
    gates = cond_gates(e.get("condition"), w)
    for p in e.get("parents", []):
        pid = p.get("entry")
        if pid not in entries:
            err(f"{w}: unknown parent {pid}")
            continue
        pg = cond_gates(entries[pid].get("condition"), f"{w} (parent)")
        if not pg <= gates:
            err(f"{w}: parent {pid} needs {sorted(pg - gates)} that this entry does not, it would float alone")
    if not e.get("pages"):
        err(f"{w}: no pages")
    for i, pg in enumerate(e.get("pages", [])):
        pw = f"{w} page {i}"
        t = pg.get("type")
        if t not in PAGE_TYPES:
            err(f"{pw}: unexpected page type {t}")
        for field in ("title", "text"):
            if pg.get(field):
                key(pg[field], pw)
        if t in RECIPE_TYPES:
            for field in ("recipe_id_1", "recipe_id_2"):
                rid = pg.get(field)
                if rid is None:
                    continue
                if rid not in recipes:
                    err(f"{pw}: unknown recipe {rid}")
                elif recipes[rid] not in RECIPE_TYPES[t]:
                    err(f"{pw}: recipe {rid} is {recipes[rid]}, {t} cannot draw it")
        if t == "modonomicon:spotlight" and not item_exists(pg.get("item", {}).get("item", "")):
            err(f"{pw}: unknown spotlight item {pg.get('item')}")
        limit = TEXT_MAX if t == "modonomicon:text" else SMALL_MAX
        if pg.get("text"):
            for name, lang in (("en", en), ("fr", fr)):
                n = len(lang.get(pg["text"], ""))
                if n > limit:
                    warnings.append(f"{pw}: {name} text is {n} chars (> {limit}), it will be scaled down")

for k in set(en) - used:
    err(f"lang: unused key {k}")

# ------------------------------------------------------------------ fabric.mod.json
fmj = load(MAIN / "resources" / "fabric.mod.json") or {}
if "modonomicon" not in fmj.get("suggests", {}):
    err("fabric.mod.json: modonomicon is not in suggests")

for wmsg in warnings:
    print("WARN ", wmsg)
for emsg in errors:
    print("ERROR", emsg)
print(f"{len(cats)} categories, {len(entries)} entries, {len(advs)} advancements, {len(en)} lang keys: "
      f"{len(errors)} error(s), {len(warnings)} warning(s)")
sys.exit(1 if errors else 0)

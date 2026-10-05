"""Checks the recipe JSONs against the Minecraft 1.21.1 recipe format.

    python scripts/check_recipes.py              # hand-written recipes (src/main/resources)
    python scripts/check_recipes.py --generated  # also the datagen output (src/main/generated)

1.21.1 ingredients are objects, {"item": "ns:id"} or {"tag": "ns:tag"}, or a list of those for alternatives (the
plain strings "ns:id" / "#ns:tag" are 1.21.2+). Results are {"id": ..., "count": ...}. Also checks that the shaped
patterns and keys agree, that the mod's items exist (they have a name in en_us.json), and that every recipe an
advancement rewards exists. Exit code 1 if anything is wrong.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
MOD = "steveparty"
ID = re.compile(r"^([a-z0-9_.-]+:)?[a-z0-9_./-]+$")  # no namespace: minecraft

SHAPED = {"minecraft:crafting_shaped", "steveparty:material_shaped", "steveparty:shaped_unmirrored",
          "steveparty:flag_from_wool"}
SHAPELESS = {"minecraft:crafting_shapeless"}
COOKING = {"minecraft:smelting", "minecraft:blasting", "minecraft:smoking", "minecraft:campfire_cooking"}
SPECIAL = {"steveparty:crafting_special_stencil_copy", "steveparty:crafting_special_mini_game_page_copy",
           "steveparty:crafting_special_flag_dye", "steveparty:crafting_special_tile_size",
           "steveparty:crafting_special_multi_dice"}
CATEGORIES = {"building", "redstone", "equipment", "misc", "food", "blocks"}

errors = []
mod_items = set()


def err(path, msg):
    errors.append(f"{os.path.relpath(path, ROOT)}: {msg}")


def check_id(path, where, value):
    if not isinstance(value, str) or not ID.match(value):
        err(path, f"{where}: bad id {value!r}")
        return
    if value.startswith(MOD + ":") and mod_items and value.split(":", 1)[1] not in mod_items:
        err(path, f"{where}: no item {value} (not named in en_us.json)")


def check_ingredient(path, where, ing, allow_list=True):
    if isinstance(ing, str):
        err(path, f"{where}: string ingredient {ing!r} (1.21.2+ format; 1.21.1 wants {{\"item\"}} / {{\"tag\"}})")
    elif isinstance(ing, list) and allow_list:
        if not ing:
            err(path, f"{where}: empty ingredient list")
        for i, alt in enumerate(ing):
            check_ingredient(path, f"{where}[{i}]", alt, allow_list=False)
    elif isinstance(ing, dict):
        keys = set(ing) - {"fabric:type"}
        if keys == {"item"}:
            check_id(path, where, ing["item"])
        elif keys == {"tag"}:
            if not isinstance(ing["tag"], str) or not ID.match(ing["tag"]):
                err(path, f"{where}: bad tag {ing['tag']!r}")
        elif "fabric:type" not in ing:
            err(path, f"{where}: ingredient needs exactly one of item / tag, has {sorted(ing)}")
    else:
        err(path, f"{where}: bad ingredient {ing!r}")


def check_result(path, result, count_allowed=True):
    if not isinstance(result, dict) or "id" not in result:
        err(path, f"result: must be an object with an id, is {result!r}")
        return
    check_id(path, "result.id", result["id"])
    extra = set(result) - {"id", "count", "components"}
    if extra:
        err(path, f"result: unknown fields {sorted(extra)}")
    if "count" in result and (not isinstance(result["count"], int) or not 1 <= result["count"] <= 99 or not count_allowed):
        err(path, f"result.count: bad {result['count']!r}")


def check_recipe(path):
    try:
        d = json.load(open(path, encoding="utf-8"))
    except ValueError as e:
        err(path, f"invalid JSON: {e}")
        return
    t = d.get("type")
    if "category" in d and d["category"] not in CATEGORIES:
        err(path, f"category: unknown {d['category']!r}")
    if t in SHAPED:
        pattern, key = d.get("pattern"), d.get("key")
        if not isinstance(pattern, list) or not 1 <= len(pattern) <= 3 or not all(isinstance(r, str) for r in pattern):
            err(path, "pattern: 1 to 3 strings")
            return
        if len({len(r) for r in pattern}) != 1 or not 1 <= len(pattern[0]) <= 3:
            err(path, "pattern: rows of the same width, 1 to 3")
        if not isinstance(key, dict):
            err(path, "key: missing")
            return
        used = {c for r in pattern for c in r if c != " "}
        for c in used - set(key):
            err(path, f"pattern: symbol {c!r} not in key")
        for c in set(key) - used:
            err(path, f"key: symbol {c!r} not used in pattern")
        for c, ing in key.items():
            if len(c) != 1 or c == " ":
                err(path, f"key: bad symbol {c!r}")
            check_ingredient(path, f"key.{c}", ing)
        check_result(path, d.get("result"))
    elif t in SHAPELESS:
        ings = d.get("ingredients")
        if not isinstance(ings, list) or not 1 <= len(ings) <= 9:
            err(path, "ingredients: 1 to 9")
            return
        for i, ing in enumerate(ings):
            check_ingredient(path, f"ingredients[{i}]", ing)
        check_result(path, d.get("result"))
    elif t == "minecraft:stonecutting":
        check_ingredient(path, "ingredient", d.get("ingredient"))
        check_result(path, d.get("result"))
    elif t in COOKING:
        check_ingredient(path, "ingredient", d.get("ingredient"))
        check_result(path, d.get("result"))
        if not isinstance(d.get("cookingtime", 200), int):
            err(path, "cookingtime: int")
    elif t == "minecraft:smithing_transform":
        for f in ("template", "base", "addition"):
            check_ingredient(path, f, d.get(f))
        check_result(path, d.get("result"))
    elif t == "minecraft:smithing_trim":
        for f in ("template", "base", "addition"):
            check_ingredient(path, f, d.get(f))
    elif t == "steveparty:dice_module":
        if not isinstance(d.get("module"), str):
            err(path, "module: string")
    elif t in SPECIAL:
        extra = set(d) - {"type", "category"}
        if extra:
            err(path, f"special recipe with extra fields {sorted(extra)}")
    else:
        err(path, f"unknown recipe type {t!r}")


def recipe_ids(data_dirs):
    ids = set()
    for data in data_dirs:
        for path in glob.glob(os.path.join(data, "*", "recipe", "**", "*.json"), recursive=True):
            rel = os.path.relpath(path, data).replace("\\", "/")
            ns, _, rest = rel.split("/", 2)
            ids.add(f"{ns}:{rest[:-5]}")
    return ids


def check_advancements(data_dirs, known):
    for data in data_dirs:
        for path in glob.glob(os.path.join(data, "*", "advancement", "recipes", "**", "*.json"), recursive=True):
            d = json.load(open(path, encoding="utf-8"))
            for rid in d.get("rewards", {}).get("recipes", []):
                if rid not in known:
                    err(path, f"rewards a recipe that does not exist: {rid}")
            for name, crit in d.get("criteria", {}).items():
                if crit.get("trigger") == "minecraft:recipe_unlocked":
                    rid = crit.get("conditions", {}).get("recipe")
                    if rid not in known:
                        err(path, f"criterion {name}: unknown recipe {rid}")


def main():
    generated = "--generated" in sys.argv
    lang = os.path.join(ROOT, "src/main/resources/assets", MOD, "lang/en_us.json")
    for key in json.load(open(lang, encoding="utf-8")):
        parts = key.split(".")
        if len(parts) >= 3 and parts[0] in ("item", "block") and parts[1] == MOD:
            mod_items.add(parts[2])
    data_dirs = [os.path.join(ROOT, "src/main/resources/data")]
    all_dirs = data_dirs + [os.path.join(ROOT, "src/main/generated/data")]
    checked = data_dirs + ([all_dirs[1]] if generated else [])
    count = 0
    for data in checked:
        for path in sorted(glob.glob(os.path.join(data, "*", "recipe", "**", "*.json"), recursive=True)):
            check_recipe(path)
            count += 1
    check_advancements(checked, recipe_ids(all_dirs))
    for e in errors:
        print(e)
    print(f"{count} recipes checked, {len(errors)} problems")
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()

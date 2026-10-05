#!/usr/bin/env python3
"""Validates the FTB Quests book in modpack/config/ftbquests/quests/ (FTB Quests 2101.x, Minecraft 1.21.1).

Checks, offline and without the game:
- every .snbt file parses (FTB's SNBT dialect: unquoted keys, newline separators, typed numbers);
- object ids are 16-digit hex and unique; dependencies, reward tables and chapter groups resolve; no dependency loop;
- task / reward types, quest shapes and dependency requirements are known to FTB Quests 2101;
- every item id exists: steveparty items need an item model and a name in the mod's lang file,
  minecraft items must be in the vanilla 1.21.1 item registry (tools/vanilla_items_1.21.1.txt);
- observed entity types exist;
- lang/en_us.snbt and lang/fr_fr.snbt hold the same keys, every chapter, quest, group and reward table
  has a title in both, and no key points to a missing object;
- no two quests of a chapter overlap.

Usage: python modpack/tools/validate_quests.py   (exit code 1 on any error)
"""
import glob
import math
import os
import re
import sys

TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(TOOLS))
QUESTS = os.path.join(ROOT, "modpack", "config", "ftbquests", "quests")
ASSETS = [os.path.join(ROOT, "src", "main", d, "assets", "steveparty") for d in ("resources", "generated")]

TASK_TYPES = {"item", "checkmark", "advancement", "kill", "location", "stat", "observation", "xp", "dimension",
              "biome", "structure", "fluid", "custom", "gamestage"}
REWARD_TYPES = {"item", "xp", "xp_levels", "command", "random", "loot", "choice", "all_table", "advancement",
                "toast", "custom", "gamestage"}
SHAPES = {"", "default", "circle", "square", "rsquare", "diamond", "pentagon", "hexagon", "octagon", "heart",
          "gear", "none"}
DEP_REQS = {"all_completed", "one_completed", "all_started", "one_started"}
HEX = re.compile(r"^[0-9A-F]{16}$")

errors = []
warnings = []


def err(msg):
    errors.append(msg)


# ---------------------------------------------------------------------------------------------
# SNBT parser (FTB Library dialect)
# ---------------------------------------------------------------------------------------------

class Typed:
    def __init__(self, value, suffix):
        self.value, self.suffix = value, suffix

    def __repr__(self):
        return f"{self.value}{self.suffix}"


class Parser:
    NUM = re.compile(r"^[-+]?(\d+\.?\d*|\.\d+)([eE][-+]?\d+)?([bBsSlLfFdD])?$")

    def __init__(self, text, path):
        self.s, self.i, self.path = text, 0, path

    def fail(self, msg):
        line = self.s.count("\n", 0, self.i) + 1
        raise ValueError(f"{self.path}:{line}: {msg}")

    def ws(self):
        while self.i < len(self.s) and (self.s[self.i] in " \t\r\n,"):
            self.i += 1

    def peek(self):
        self.ws()
        return self.s[self.i] if self.i < len(self.s) else ""

    def parse(self):
        v = self.value()
        if self.peek():
            self.fail("trailing data")
        return v

    def value(self):
        c = self.peek()
        if c == "{":
            return self.compound()
        if c == "[":
            return self.list()
        if c == '"':
            return self.string()
        return self.bare_value()

    def compound(self):
        self.i += 1
        out = {}
        while True:
            c = self.peek()
            if c == "}":
                self.i += 1
                return out
            if not c:
                self.fail("unclosed compound")
            key = self.string() if c == '"' else self.word()
            if self.peek() != ":":
                self.fail(f"expected ':' after key {key!r}")
            self.i += 1
            if key in out:
                self.fail(f"duplicate key {key!r}")
            out[key] = self.value()

    def list(self):
        self.i += 1
        out = []
        while True:
            c = self.peek()
            if c == "]":
                self.i += 1
                return out
            if not c:
                self.fail("unclosed list")
            out.append(self.value())

    def string(self):
        self.i += 1
        buf = []
        while self.i < len(self.s):
            c = self.s[self.i]
            if c == "\\":
                buf.append(self.s[self.i + 1])
                self.i += 2
                continue
            if c == '"':
                self.i += 1
                return "".join(buf)
            buf.append(c)
            self.i += 1
        self.fail("unclosed string")

    def word(self):
        start = self.i
        while self.i < len(self.s) and self.s[self.i] not in " \t\r\n,:{}[]\"":
            self.i += 1
        if start == self.i:
            self.fail("expected a word")
        return self.s[start:self.i]

    def bare_value(self):
        w = self.word()
        if w in ("true", "false"):
            return w == "true"
        m = self.NUM.match(w)
        if m:
            suffix = (m.group(3) or "").lower()
            num = float(w[:-1] if suffix else w) if (suffix in ("d", "f") or "." in w or "e" in w.lower()) \
                else int(w[:-1] if suffix else w)
            return Typed(num, suffix) if suffix else num
        return w


def load(path):
    with open(path, encoding="utf-8") as fh:
        return Parser(fh.read(), os.path.relpath(path, ROOT)).parse()


def num(v):
    return v.value if isinstance(v, Typed) else v


# ---------------------------------------------------------------------------------------------
# Known ids
# ---------------------------------------------------------------------------------------------

def read_list(name):
    with open(os.path.join(TOOLS, name), encoding="utf-8") as fh:
        return {"minecraft:" + l.strip() for l in fh if l.strip()}


VANILLA_ITEMS = read_list("vanilla_items_1.21.1.txt")
VANILLA_ENTITIES = read_list("vanilla_entities_1.21.1.txt")

import json  # noqa: E402

MOD_LANG = {}
for code in ("en_us", "fr_fr"):
    with open(os.path.join(ASSETS[0], "lang", code + ".json"), encoding="utf-8") as fh:
        MOD_LANG[code] = json.load(fh)

MOD_MODELS = set()
for a in ASSETS:
    for p in glob.glob(os.path.join(a, "models", "item", "*.json")):
        MOD_MODELS.add(os.path.splitext(os.path.basename(p))[0])


def item_exists(item_id):
    ns, _, path = item_id.partition(":")
    if ns == "minecraft":
        return item_id in VANILLA_ITEMS
    if ns == "steveparty":
        named = all(f"item.steveparty.{path}" in MOD_LANG[c] or f"block.steveparty.{path}" in MOD_LANG[c]
                    for c in MOD_LANG)
        return named and path in MOD_MODELS
    return False


def entity_exists(entity_id):
    ns, _, path = entity_id.partition(":")
    if ns == "minecraft":
        return entity_id in VANILLA_ENTITIES
    if ns == "steveparty":
        return f"entity.steveparty.{path}" in MOD_LANG["en_us"]
    return False


def check_item(stack, where):
    if not isinstance(stack, dict) or "id" not in stack:
        err(f"{where}: malformed item {stack!r}")
        return
    if not item_exists(stack["id"]):
        err(f"{where}: unknown item {stack['id']}")


# ---------------------------------------------------------------------------------------------
# Validation
# ---------------------------------------------------------------------------------------------

def main():
    ids = {}  # hex id -> description

    def register(oid, what):
        if not isinstance(oid, str) or not HEX.match(oid):
            err(f"{what}: bad id {oid!r}")
            return
        if int(oid, 16) > 0x7FFFFFFFFFFFFFFF or int(oid, 16) < 2:
            err(f"{what}: id {oid} out of range")
        if oid in ids:
            err(f"duplicate id {oid}: {ids[oid]} and {what}")
        ids[oid] = what

    files = sorted(glob.glob(os.path.join(QUESTS, "**", "*.snbt"), recursive=True))
    parsed = {}
    for p in files:
        try:
            parsed[p] = load(p)
        except ValueError as e:
            err(f"parse error: {e}")
    if errors:
        return finish(files, {})

    data = parsed[os.path.join(QUESTS, "data.snbt")]
    if num(data.get("version")) != 13:
        err("data.snbt: version should be 13 (FTB Quests 2101)")

    groups = parsed[os.path.join(QUESTS, "chapter_groups.snbt")]["chapter_groups"]
    group_ids = set()
    for g in groups:
        register(g["id"], f"chapter group {g['id']}")
        group_ids.add(g["id"])
        if "icon" in g:
            check_item(g["icon"], f"chapter group {g['id']} icon")

    tables = {}
    for p in sorted(glob.glob(os.path.join(QUESTS, "reward_tables", "*.snbt"))):
        t = parsed[p]
        name = os.path.basename(p)
        register(t["id"], f"reward table {name}")
        tables[t["id"]] = t
        if "icon" in t:
            check_item(t["icon"], f"reward table {name} icon")

    def check_reward(r, where, in_table=False):
        register(r.get("id"), where)
        rtype = r.get("type", "item" if in_table else None)
        if rtype not in REWARD_TYPES:
            err(f"{where}: unknown reward type {rtype!r}")
        if rtype == "item":
            check_item(r.get("item"), where)
        if rtype in ("random", "loot", "choice", "all_table"):
            tid = "%016X" % num(r.get("table_id", 0))
            if tid not in tables:
                err(f"{where}: reward table {tid} not found")
        if rtype == "xp" and num(r.get("xp", 0)) <= 0:
            err(f"{where}: xp reward without xp")
        if rtype == "xp_levels" and num(r.get("xp_levels", 0)) <= 0:
            err(f"{where}: xp_levels reward without levels")

    for tid, t in tables.items():
        if not t.get("rewards"):
            err(f"reward table {tid} is empty")
        for r in t.get("rewards", []):
            check_reward(r, f"reward table {tid} reward {r.get('id')}", in_table=True)

    quests = {}  # id -> (chapter file, quest)
    chapters = {}
    stats = []
    for p in sorted(glob.glob(os.path.join(QUESTS, "chapters", "*.snbt"))):
        ch = parsed[p]
        name = os.path.basename(p)
        register(ch["id"], f"chapter {name}")
        chapters[ch["id"]] = ch
        if ch.get("filename") + ".snbt" != name:
            err(f"chapter {name}: filename field {ch.get('filename')!r} does not match the file")
        if ch.get("group") and ch["group"] not in group_ids:
            err(f"chapter {name}: unknown group {ch['group']}")
        if "icon" in ch:
            check_item(ch["icon"], f"chapter {name} icon")
        n_tasks = n_rewards = 0
        for qd in ch.get("quests", []):
            where = f"{name} quest {qd.get('id')}"
            register(qd.get("id"), where)
            quests[qd["id"]] = (name, qd)
            if "x" not in qd or "y" not in qd:
                err(f"{where}: no position")
            if qd.get("shape", "") not in SHAPES:
                err(f"{where}: unknown shape {qd['shape']!r}")
            if qd.get("dependency_requirement", "all_completed") not in DEP_REQS:
                err(f"{where}: unknown dependency_requirement {qd['dependency_requirement']!r}")
            if "icon" in qd:
                check_item(qd["icon"], f"{where} icon")
            if not qd.get("tasks"):
                err(f"{where}: no task")
            for t in qd.get("tasks", []):
                tw = f"{where} task {t.get('id')}"
                register(t.get("id"), tw)
                n_tasks += 1
                if t.get("type") not in TASK_TYPES:
                    err(f"{tw}: unknown task type {t.get('type')!r}")
                if t.get("type") == "item":
                    check_item(t.get("item"), tw)
                    if num(t.get("count", 1)) < 1:
                        err(f"{tw}: count < 1")
                if t.get("type") == "observation":
                    if t.get("observation_type") == "entity_type" and not entity_exists(t.get("to_observe", "")):
                        err(f"{tw}: unknown entity {t.get('to_observe')}")
            for r in qd.get("rewards", []):
                check_reward(r, f"{where} reward {r.get('id')}")
                n_rewards += 1
        stats.append((name, len(ch.get("quests", [])), n_tasks, n_rewards))

    # dependencies
    for qid, (name, qd) in quests.items():
        for dep in qd.get("dependencies", []):
            if dep == qid:
                err(f"{name} quest {qid}: depends on itself")
            elif dep not in quests:
                err(f"{name} quest {qid}: dependency {dep} not found")

    state = {}

    def visit(qid, stack):
        if state.get(qid) == 1:
            err("dependency loop: " + " -> ".join(stack + [qid]))
            return
        if state.get(qid) == 2:
            return
        state[qid] = 1
        for dep in quests[qid][1].get("dependencies", []):
            if dep in quests:
                visit(dep, stack + [qid])
        state[qid] = 2

    for qid in quests:
        visit(qid, [])

    # layout: quests of a chapter must not overlap
    for cid, ch in chapters.items():
        qs = ch.get("quests", [])
        for a in range(len(qs)):
            for b in range(a + 1, len(qs)):
                qa, qb = qs[a], qs[b]
                sa, sb = num(qa.get("size", 1.0)) or 1.0, num(qb.get("size", 1.0)) or 1.0
                dist = math.hypot(num(qa["x"]) - num(qb["x"]), num(qa["y"]) - num(qb["y"]))
                if dist < (sa + sb) / 2:
                    err(f"chapter {ch['filename']}: quests {qa['id']} and {qb['id']} overlap")

    # lang
    lang = {}
    for code in ("en_us", "fr_fr"):
        p = os.path.join(QUESTS, "lang", code + ".snbt")
        if p not in parsed:
            err(f"missing lang/{code}.snbt")
            continue
        lang[code] = parsed[p]
    if len(lang) == 2:
        en, fr = lang["en_us"], lang["fr_fr"]
        for k in sorted(set(en) ^ set(fr)):
            err(f"lang key only in {'en_us' if k in en else 'fr_fr'}: {k}")
        known = set(ids) | {"0000000000000001"}
        for code, table in lang.items():
            for k, v in table.items():
                parts = k.split(".")
                if len(parts) != 3:
                    err(f"{code}: malformed key {k}")
                    continue
                kind, oid, sub = parts
                if oid not in known:
                    err(f"{code}: key {k} points to no object")
                if sub in ("quest_desc", "chapter_subtitle"):
                    if not isinstance(v, list):
                        err(f"{code}: {k} should be a list")
                elif not isinstance(v, str) or not v:
                    err(f"{code}: {k} should be a non-empty string")
                if sub == "title" and isinstance(v, str) and re.search(r"&[0-9a-fk-or]", v):
                    warnings.append(f"{code}: {k} contains a formatting code: {v}")
        needed = [("chapter", c) for c in chapters] + [("quest", q) for q in quests] + \
                 [("chapter_group", g) for g in group_ids] + [("reward_table", t) for t in tables]
        for kind, oid in needed:
            for code, table in lang.items():
                if f"{kind}.{oid}.title" not in table:
                    err(f"{code}: no title for {kind} {oid}")
        same = 0
        for qid in quests:
            k = f"quest.{qid}.quest_desc"
            if k not in en:
                warnings.append(f"quest {qid} has no description")
            elif en.get(k) == fr.get(k):
                same += 1
        if same:
            warnings.append(f"{same} quest descriptions are identical in English and French")

    return finish(files, {"chapters": stats, "quests": len(quests), "ids": len(ids), "tables": len(tables),
                          "lang": {c: len(t) for c, t in lang.items()}})


def finish(files, info):
    print(f"{len(files)} SNBT files parsed")
    if info:
        print(f"{len(info['chapters'])} chapters, {info['quests']} quests, {info['tables']} reward tables, "
              f"{info['ids']} unique ids, lang keys {info['lang']}")
        for name, nq, nt, nr in info["chapters"]:
            print(f"  {name:<16} {nq:>3} quests {nt:>3} tasks {nr:>3} rewards")
    for w in warnings:
        print("WARNING:", w)
    for e in errors:
        print("ERROR:", e)
    print("OK" if not errors else f"{len(errors)} error(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

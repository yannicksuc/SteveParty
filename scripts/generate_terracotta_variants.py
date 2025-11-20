import os
import json
from pathlib import Path

# === CONFIGURATION ===
modid = "steveparty"
project_root = Path(__file__).resolve().parents[1]  # remonte à la racine du projet
resources_root = project_root / "src" / "main" / "resources" / "assets" / modid

# Liste des couleurs vanilla
colors = [
    "white", "orange", "magenta", "light_blue", "yellow", "lime",
    "pink", "gray", "light_gray", "cyan", "purple", "blue",
    "brown", "green", "red", "black"
]

# Types de blocs de base
base_types = ["polished_terracotta", "polished_terracotta_bricks"]

# Dossiers de sortie
paths = {
    "blockstates": resources_root / "blockstates",
    "block_models": resources_root / "models" / "block",
    "item_models": resources_root / "models" / "item",
}

# === Templates JSON ===
def make_blockstate(name, block_type):
    if block_type == "stairs":
        return {
            "variants": {
                "facing=north,half=bottom,shape=straight": {"model": f"{modid}:block/{name}"},
                "facing=south,half=bottom,shape=straight": {"model": f"{modid}:block/{name}", "y": 180},
                "facing=west,half=bottom,shape=straight": {"model": f"{modid}:block/{name}", "y": 270},
                "facing=east,half=bottom,shape=straight": {"model": f"{modid}:block/{name}", "y": 90}
            }
        }
    elif block_type == "slab":
        return {
            "variants": {
                "type=bottom": {"model": f"{modid}:block/{name}"},
                "type=top": {"model": f"{modid}:block/{name}_top"},
                "type=double": {"model": f"{modid}:block/{name}_double"}
            }
        }
    elif block_type == "wall":
        return {
            "multipart": [
                {"apply": {"model": f"{modid}:block/{name}_post"}},
                {"when": {"north": "low"}, "apply": {"model": f"{modid}:block/{name}_side", "uvlock": True}},
                {"when": {"south": "low"}, "apply": {"model": f"{modid}:block/{name}_side", "y": 180, "uvlock": True}},
                {"when": {"east": "low"}, "apply": {"model": f"{modid}:block/{name}_side", "y": 90, "uvlock": True}},
                {"when": {"west": "low"}, "apply": {"model": f"{modid}:block/{name}_side", "y": 270, "uvlock": True}}
            ]
        }

def make_item_model(name):
    return {"parent": f"{modid}:block/{name}"}

def make_block_model(name, texture):
    return {
        "parent": "block/cube_all",
        "textures": {"all": f"{modid}:block/{texture}"}
    }

# === GÉNÉRATION ===
for color in colors:
    for base in base_types:
        texture_base = f"{base}/{color}"
        block_variants = {
            "stairs": f"{color}_{base}_stairs",
            "slab": f"{color}_{base}_slab",
            "wall": f"{color}_{base}_wall"
        }

        for variant_type, block_name in block_variants.items():
            for path in paths.values():
                path.mkdir(parents=True, exist_ok=True)

            # Blockstate
            with open(paths["blockstates"] / f"{block_name}.json", "w") as f:
                json.dump(make_blockstate(block_name, variant_type), f, indent=4)

            # Block model
            with open(paths["block_models"] / f"{block_name}.json", "w") as f:
                json.dump(make_block_model(block_name, texture_base), f, indent=4)

            # Item model
            with open(paths["item_models"] / f"{block_name}.json", "w") as f:
                json.dump(make_item_model(block_name), f, indent=4)

            print(f"✅ Généré : {block_name}")

print("\n🎉 Tous les fichiers ont été générés avec succès !")

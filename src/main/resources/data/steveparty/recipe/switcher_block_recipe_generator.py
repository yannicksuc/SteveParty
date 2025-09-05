import json

# List of wool colors
COLORS = [
    "white", "orange", "magenta", "light_blue",
    "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue",
    "brown", "green", "red", "black"
]

# Loop over colors and generate recipe JSON for each
for color in COLORS:
    recipe = {
        "type": "minecraft:crafting_shaped",
        "fabric:type": "minecraft:crafting_shaped",
        "pattern": [
            "WFW",
            "FAF",
            "WFW"
        ],
        "key": {
            "W": f"minecraft:{color}_wool",
            "F": "minecraft:feather",
            "A": "minecraft:amethyst_shard"
        },
        "result": {
            "id": f"steveparty:{color}_switcher_block"
        }
    }

    # Write each recipe to a separate file
    filename = f"{color}_switcher_block.json"
    with open(filename, "w") as f:
        json.dump(recipe, f, indent=2)
    print(f"Generated recipe: {filename}")
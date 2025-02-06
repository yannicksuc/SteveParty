import os
import json
from PIL import Image

# Base paths
textures_dir = "textures/block"
models_dir = "models/block"
item_models_dir = "models/item"
blockstates_dir = "blockstates"
java_dir = "../../../java/fr/lordfinn/steveparty/blocks/custom"
mod_blocks_file = "../../../java/fr/lordfinn/steveparty/blocks/ModBlocks.java"
lang_file = "lang/en_us.json"

# Template for block model JSON
block_model_template = {
    "parent": "block/cube_all",
    "textures": {
        "all": "steveparty:block/{block_name}"
    }
}

# Template for item model JSON
item_model_template = {
    "parent": "steveparty:block/{block_name}"
}

# Template for Java class
java_template = """package fr.lordfinn.steveparty.blocks.custom;

import net.minecraft.block.Block;

public class {class_name} extends Block {
    public {class_name}(Settings settings) {
        super(settings);
    }
}
"""

# Template for blockstate JSON
blockstate_template = {
    "variants": {
        "": { "model": "steveparty:block/{block_name}" }
    }
}

# Template for ModBlocks registration
mod_blocks_registration_template = """    public static final Block {BLOCK_NAME} = register({class_name}::new,
            Block.Settings.create()
                    .strength({strength}f, {hardness}f)
                    .sounds(BlockSoundGroup.{sound_group}){additional_settings}
            "{block_name}", true);
"""

def to_class_name(block_name):
    return "".join(word.capitalize() for word in block_name.split("_")) + "Block"

def generate_additional_settings(settings):
    additional_settings = ""
    if settings.get("luminance"):
        additional_settings += f"                    .luminance(state -> {settings['luminance']})"
    if settings.get("non_opaque"):
        additional_settings += "                    .nonOpaque()"
    if settings.get("requires_tool"):
        additional_settings += "                    .requiresTool()"
    if not additional_settings:
        return ","
    return "\n" + additional_settings + ","

def create_empty_texture(file_path):
    if not os.path.exists(file_path):
        image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        image.save(file_path)
        print(f"Created texture: {file_path}")

def create_model_file(file_path, block_name, is_item_model=False):
    if not os.path.exists(file_path):
        template = item_model_template if is_item_model else block_model_template
        content = json.dumps(template, indent=2).replace("{block_name}", block_name)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Created model: {file_path}")

def create_java_class(file_path, class_name, block_name):
    if not os.path.exists(file_path):
        content = java_template.replace("{class_name}", class_name).replace("{block_name}", block_name)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Created Java class: {file_path}")

def add_to_mod_blocks(block_name, class_name, block_settings):
    if os.path.exists(mod_blocks_file):
        with open(mod_blocks_file, "r", encoding="utf-8") as f:
            content = f.read()

        additional_settings = generate_additional_settings(block_settings)
        block_declaration = mod_blocks_registration_template.replace("{block_name}", block_name) \
            .replace("{BLOCK_NAME}", block_name.upper()) \
            .replace("{class_name}", class_name) \
            .replace("{strength}", str(block_settings["strength"])) \
            .replace("{hardness}", str(block_settings["hardness"])) \
            .replace("{sound_group}", block_settings["sound_group"]) \
            .replace("{additional_settings}", additional_settings)

        if block_declaration.strip() not in content:
            content = content.replace("public class ModBlocks {", f"public class ModBlocks {{\n{block_declaration}")
            with open(mod_blocks_file, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Added {block_name} to ModBlocks.java")

def add_to_lang_file(block_name):
    if os.path.exists(lang_file):
        with open(lang_file, "r", encoding="utf-8") as f:
            lang_data = json.load(f)
    else:
        lang_data = {}

    key = f"block.steveparty.{block_name}"
    value = " ".join(word.capitalize() for word in block_name.split("_"))

    if key not in lang_data:
        lang_data[key] = value
        with open(lang_file, "w", encoding="utf-8") as f:
            json.dump(lang_data, f, indent=2)
        print(f"Added {key} to {lang_file}")

def generate_block_files(block_name, block_settings):
    os.makedirs(textures_dir, exist_ok=True)
    os.makedirs(models_dir, exist_ok=True)
    os.makedirs(item_models_dir, exist_ok=True)
    os.makedirs(blockstates_dir, exist_ok=True)
    os.makedirs(java_dir, exist_ok=True)

    texture_path = os.path.join(textures_dir, f"{block_name}.png")
    model_path = os.path.join(models_dir, f"{block_name}.json")
    item_model_path = os.path.join(item_models_dir, f"{block_name}.json")
    class_name = to_class_name(block_name)
    java_path = os.path.join(java_dir, f"{class_name}.java")
    blockstate_path = os.path.join(blockstates_dir, f"{block_name}.json")

    create_empty_texture(texture_path)
    create_model_file(model_path, block_name)
    create_model_file(item_model_path, block_name, is_item_model=True)
    create_java_class(java_path, class_name, block_name)
    add_to_mod_blocks(block_name, class_name, block_settings)
    add_to_lang_file(block_name)

    # Create blockstate file
    if not os.path.exists(blockstate_path):
        content = json.dumps(blockstate_template, indent=2).replace("{block_name}", block_name)
        with open(blockstate_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Created blockstate: {blockstate_path}")

if __name__ == "__main__":
    block_name = input("Enter the block name: ")

    # Define block settings (example)
    block_settings = {
        "strength": 3.0,
        "hardness": 9.0,
        "sound_group": "METAL",  # Could be different like WOOD, STONE, etc.
        "non_opaque": False,
        "requires_tool": True
    }

    generate_block_files(block_name, block_settings)

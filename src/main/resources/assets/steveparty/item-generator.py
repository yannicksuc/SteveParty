import os
import json
from PIL import Image

# Base paths
textures_dir = "textures/item"
models_dir = "models/item"
java_dir = "../../../java/fr/lordfinn/steveparty/items/custom"
mod_items_file = "../../../java/fr/lordfinn/steveparty/items/ModItems.java"
lang_file = "lang/en_us.json"

# Template for model JSON
model_template = {
    "parent": "item/handheld",
    "textures": {
        "layer0": "steveparty:item/{item_name}"
    }
}

# Template for Java class
java_template = """package fr.lordfinn.steveparty.items.custom;

import net.minecraft.item.Item;

public class {class_name} extends Item {
    public {class_name}(Settings settings) {
        super(settings);
    }
}
"""

def to_class_name(item_name):
    return "".join(word.capitalize() for word in item_name.split("_")) + "Item"

def create_empty_texture(file_path):
    if not os.path.exists(file_path):
        image = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        image.save(file_path)
        print(f"Created texture: {file_path}")

def create_model_file(file_path, item_name):
    if not os.path.exists(file_path):
        content = json.dumps(model_template, indent=2).replace("{item_name}", item_name)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Created model: {file_path}")

def create_java_class(file_path, class_name):
    if not os.path.exists(file_path):
        content = java_template.replace("{class_name}", class_name)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Created Java class: {file_path}")

def add_to_mod_items(item_name, class_name):
    if os.path.exists(mod_items_file):
        print("oui")
        with open(mod_items_file, "r", encoding="utf-8") as f:
            content = f.read()

        item_declaration = f"    public static final Item {item_name.upper()} = register({class_name}.class, \"{item_name}\");\n"
        if item_declaration.strip() not in content:
            content = content.replace("public class ModItems {", f"public class ModItems {{\n{item_declaration}")
            with open(mod_items_file, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"Added {item_name} to ModItems.java")

def add_to_lang_file(item_name):
    if os.path.exists(lang_file):
        with open(lang_file, "r", encoding="utf-8") as f:
            lang_data = json.load(f)
    else:
        lang_data = {}

    key = f"item.steveparty.{item_name}"
    value = " ".join(word.capitalize() for word in item_name.split("_"))

    if key not in lang_data:
        lang_data[key] = value
        with open(lang_file, "w", encoding="utf-8") as f:
            json.dump(lang_data, f, indent=2)
        print(f"Added {key} to {lang_file}")

def generate_item_files(item_name):
    os.makedirs(textures_dir, exist_ok=True)
    os.makedirs(models_dir, exist_ok=True)
    os.makedirs(java_dir, exist_ok=True)

    texture_path = os.path.join(textures_dir, f"{item_name}.png")
    model_path = os.path.join(models_dir, f"{item_name}.json")
    class_name = to_class_name(item_name)
    java_path = os.path.join(java_dir, f"{class_name}.java")

    create_empty_texture(texture_path)
    create_model_file(model_path, item_name)
    create_java_class(java_path, class_name)
    add_to_mod_items(item_name, class_name)
    add_to_lang_file(item_name)

if __name__ == "__main__":
    item_name = input("Enter the item name: ")
    generate_item_files(item_name)

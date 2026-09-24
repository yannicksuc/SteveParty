import os
import shutil

# List of wood types to replace 'spruce' with
wood_types = [
    'oak',
    'spruce',
    'birch',
    'jungle',
    'acacia',
    'dark_oak',
    'mangrove',
    'cherry',
    'crimson',
    'warped'
]

# Paths to directories where the files are located
directories = ['models/block', 'blockstates', 'models/item']

# Function to process and copy the file
def process_file(file_path, wood_type):
    # Read the file content
    with open(file_path, 'r', encoding='utf-8') as file:
        content = file.read()

    # Replace 'spruce' with the current wood type
    content = content.replace('spruce', wood_type)

    # Create new file name
    new_file_name = file_path.replace('spruce', wood_type)

    # Create the parent directories if they do not exist
    os.makedirs(os.path.dirname(new_file_name), exist_ok=True)

    # Write the new content to the new file
    with open(new_file_name, 'w', encoding='utf-8') as new_file:
        new_file.write(content)

    print(f"Created {new_file_name}.")

# Iterate over each directory and each file
for directory in directories:
    for wood_type in wood_types:
        # Walk through all files in the directory
        for root, _, files in os.walk(directory):
            print("oui")
            for file_name in files:
                print("oui")
                # Check if file name contains 'spruce' and needs to be copied
                if 'spruce' in file_name:
                    # Construct the full path of the current file
                    current_file_path = os.path.join(root, file_name)
                    process_file(current_file_path, wood_type)

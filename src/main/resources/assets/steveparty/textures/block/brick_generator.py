import os
from PIL import Image
import numpy as np
from sklearn.cluster import KMeans

# --- CONFIG ---
colors = [
    "white", "orange", "magenta", "light_blue",
    "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue",
    "brown", "green", "red", "black"
]

src_polished_dir = "polished_terracotta"
src_bricks_dir = "polished_terracotta_bricks"
ref_color = "yellow"  # Reference color pair

os.makedirs(src_bricks_dir, exist_ok=True)

# --- Helper functions ---
def extract_palette(image_path, n_colors=6):
    """Extract dominant colors from an image using KMeans."""
    img = Image.open(image_path).convert("RGB")
    pixels = np.array(img).reshape(-1, 3)
    kmeans = KMeans(n_clusters=n_colors, random_state=42).fit(pixels)
    palette = np.array(kmeans.cluster_centers_, dtype=int)
    return palette

def recolor_image(base_image_path, src_palette, target_palette, output_path):
    """Map colors from one palette to another (closest match)."""
    img = Image.open(base_image_path).convert("RGB")
    pixels = np.array(img)
    flat_pixels = pixels.reshape(-1, 3)

    # For each pixel, find nearest color in src_palette and replace with target_palette
    new_pixels = np.zeros_like(flat_pixels)
    for i, p in enumerate(flat_pixels):
        distances = np.linalg.norm(src_palette - p, axis=1)
        idx = np.argmin(distances)
        new_pixels[i] = target_palette[idx % len(target_palette)]

    new_pixels = new_pixels.reshape(pixels.shape)
    new_img = Image.fromarray(new_pixels.astype("uint8"))
    new_img.save(output_path)
    print(f"✅ Saved {output_path}")

# --- Extract reference palettes ---
ref_polished_path = f"{src_polished_dir}/polished_{ref_color}_terracotta.png"
ref_bricks_path = f"{src_bricks_dir}/polished_{ref_color}_terracotta_bricks.png"

ref_polished_palette = extract_palette(ref_polished_path)
ref_bricks_palette = extract_palette(ref_bricks_path)

# --- Generate recolored brick textures ---
for color in colors:
    if color == ref_color:
        continue  # Skip the reference
    target_polished_path = f"{src_polished_dir}/polished_{color}_terracotta.png"
    output_path = f"{src_bricks_dir}/polished_{color}_terracotta_bricks.png"

    if not os.path.exists(target_polished_path):
        print(f"⚠️ Missing {target_polished_path}, skipping...")
        continue

    target_palette = extract_palette(target_polished_path)

    # Recolor base bricks (yellow) -> to match this color's palette
    recolor_image(ref_bricks_path, ref_polished_palette, target_palette, output_path)

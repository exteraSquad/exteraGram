import os
import re

BASE_ICON_PACKS_PATH = 'TMessagesProj/src/main/java/com/exteragram/messenger/icons/BaseIconPacks.kt'
RES_REMIX_PATH = 'TMessagesProj/src/main/res-remix/drawable'
RES_SOLAR_PATH = 'TMessagesProj/src/main/res-solar/drawable'

def get_used_icons():
    if not os.path.exists(BASE_ICON_PACKS_PATH):
        print(f"Error: {BASE_ICON_PACKS_PATH} not found.")
        return set()

    with open(BASE_ICON_PACKS_PATH, 'r') as f:
        content = f.read()

    matches = re.findall(r'put\s*\([^,]+,\s*R\.drawable\.([a-zA-Z0-9_]+)\)', content)

    return set(matches)

def clean_directory(dir_path, used_icons):
    if not os.path.exists(dir_path):
        print(f"Warning: Directory {dir_path} not found.")
        return

    print(f"Cleaning {dir_path}...")
    removed_count = 0
    total_count = 0

    for filename in os.listdir(dir_path):
        if filename.endswith('.xml') or filename.endswith('.png'):
            total_count += 1
            icon_name = os.path.splitext(filename)[0]
            
            if icon_name not in used_icons:
                file_path = os.path.join(dir_path, filename)
                try:
                    os.remove(file_path)
                    # print(f"Removed: {filename}")
                    removed_count += 1
                except Exception as e:
                    print(f"Failed to remove {filename}: {e}")

    print(f"Finished cleaning {dir_path}. Removed {removed_count} out of {total_count} files.")

if __name__ == "__main__":
    used = get_used_icons()
    if used:
        print(f"Found {len(used)} unique used icons in BaseIconPacks.kt")
        clean_directory(RES_REMIX_PATH, used)
        clean_directory(RES_SOLAR_PATH, used)
    else:
        print("No icons found in BaseIconPacks.kt or file not found.")

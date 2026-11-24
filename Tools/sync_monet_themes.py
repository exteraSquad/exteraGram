#!/usr/bin/env python3
import os
import re

THEME_COLORS_PATH = 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ThemeColors.java'
ASSETS_PATH = 'TMessagesProj/src/main/assets'

def get_project_root():
    return os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))

def extract_valid_keys(file_path):
    valid_keys = set()
    if not os.path.exists(file_path):
        print(f"Error: {file_path} not found")
        return valid_keys
    
    pattern = re.compile(r'colorKeysMap\.put\(.*,\s*"([^"]+)"\)')
    
    with open(file_path, 'r', encoding='utf-8') as f:
        for line in f:
            match = pattern.search(line)
            if match:
                valid_keys.add(match.group(1))
    return valid_keys

def sync_theme(file_path, valid_keys):
    if not os.path.exists(file_path):
        return

    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    new_lines = []
    current_keys = set()
    removed_count = 0
    
    for line in lines:
        stripped = line.strip()
        
        if not stripped or stripped.startswith('//') or stripped == 'end':
            if stripped.startswith("// Missing keys"):
                break
            new_lines.append(line)
            continue
        
        if '=' in stripped:
            key = stripped.split('=')[0].strip()
            if key in valid_keys:
                new_lines.append(line)
                current_keys.add(key)
            else:
                removed_count += 1
        else:
            removed_count += 1

    while new_lines and not new_lines[-1].strip():
        new_lines.pop()

    missing_keys = sorted([k for k in valid_keys if k not in current_keys])
    
    if missing_keys:
        new_lines.append("\n\n// Missing keys:\n")
        for k in missing_keys:
            new_lines.append(f"//{k}=\n")

    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)
    
    print(f"Synced {os.path.basename(file_path)}: removed {removed_count}, added {len(missing_keys)} as comments")

def main():
    root = get_project_root()
    theme_colors = os.path.join(root, THEME_COLORS_PATH)
    assets = os.path.join(root, ASSETS_PATH)
    
    print("Extracting keys from ThemeColors.java...")
    valid_keys = extract_valid_keys(theme_colors)
    print(f"Found {len(valid_keys)} valid keys.")
    
    if not valid_keys:
        return

    for filename in os.listdir(assets):
        if filename.startswith('monet_') and filename.endswith('.attheme'):
            sync_theme(os.path.join(assets, filename), valid_keys)

if __name__ == "__main__":
    main()

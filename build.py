#!/usr/bin/env python3
"""
build.py - Bundle and compile ChalSmooth frontend into standalone distribution artifacts
"""
import os
import re

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))
FRONTEND_DIR = os.path.join(ROOT_DIR, "frontend")
DIST_DIR = os.path.join(ROOT_DIR, "dist")

os.makedirs(DIST_DIR, exist_ok=True)

# 1. Read index.html
with open(os.path.join(FRONTEND_DIR, "index.html"), "r", encoding="utf-8") as f:
    html_content = f.read()

# 2. Read all CSS files and combine them
css_files = [
    "main.css",
    "dashboard.css",
    "map.css",
    "modals.css",
    "navigation.css",
    "studio.css",
    "hud.css",
    "profile.css"
]

combined_css = "/* ChalSmooth Compiled Bundle Styles */\n"
for css_file in css_files:
    css_path = os.path.join(FRONTEND_DIR, "css", css_file)
    if os.path.exists(css_path):
        with open(css_path, "r", encoding="utf-8") as cf:
            combined_css += f"\n/* --- {css_file} --- */\n" + cf.read() + "\n"

# Write compiled CSS to dist
with open(os.path.join(DIST_DIR, "chalsmooth.min.css"), "w", encoding="utf-8") as f:
    f.write(combined_css)

# Create standalone bundled HTML
standalone_html = html_content

# Replace external CSS links with inlined combined CSS
standalone_html = re.sub(r'<link\s+rel="stylesheet"\s+href="css/[^"]+"\s*/>', '', standalone_html)
standalone_html = standalone_html.replace('</head>', f'<style>\n{combined_css}\n</style>\n</head>')

with open(os.path.join(DIST_DIR, "index.html"), "w", encoding="utf-8") as f:
    f.write(standalone_html)

# Also copy js and assets to dist
import shutil
if os.path.exists(os.path.join(DIST_DIR, "js")):
    shutil.rmtree(os.path.join(DIST_DIR, "js"))
shutil.copytree(os.path.join(FRONTEND_DIR, "js"), os.path.join(DIST_DIR, "js"))

print(f"Compilation Successful!")
print(f"Distribution Directory: {DIST_DIR}")
print(f"Distribution HTML: {os.path.join(DIST_DIR, 'index.html')}")
print(f"Frontend Source: {os.path.join(FRONTEND_DIR, 'index.html')}")

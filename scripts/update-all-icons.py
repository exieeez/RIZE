#!/usr/bin/env python3
import os
import sys
import numpy as np
from PIL import Image, ImageDraw

def main():
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    src_png_path = '/home/exieeez/.gemini/antigravity/brain/7cbb7d3a-6abd-4e03-88d1-4817271ed558/.user_uploaded/media_1792564719750_100d5a4d.png'
    
    if not os.path.exists(src_png_path):
        print(f"Error: {src_png_path} not found")
        sys.exit(1)

    print("Loading source image...")
    src_img = Image.open(src_png_path).convert('RGBA')

    # 1. Master Square 1024x1024 centered
    canvas_size = 1024
    blot_canvas = Image.new('RGBA', (canvas_size, canvas_size), (0, 0, 0, 0))
    y_offset = (canvas_size - src_img.height) // 2
    blot_canvas.paste(src_img, (0, y_offset))

    # 2. Master Squircle 1024x1024
    # Create smooth 4x supersampled rounded rectangle mask (iOS / Android squircle)
    scale = 4
    hi_size = canvas_size * scale
    hi_mask = Image.new('L', (hi_size, hi_size), 0)
    draw = ImageDraw.Draw(hi_mask)
    # Apple squircle corner radius is ~22.37% of size
    radius = int(hi_size * 0.2237)
    draw.rounded_rectangle((0, 0, hi_size - 1, hi_size - 1), radius=radius, fill=255)
    squircle_mask = hi_mask.resize((canvas_size, canvas_size), Image.Resampling.LANCZOS)

    # Master white squircle
    white_bg = Image.new('RGBA', (canvas_size, canvas_size), (255, 255, 255, 255))
    white_bg.putalpha(squircle_mask)

    # Composite blot over white squircle
    # Note: inner cutout of blot in src_img is transparent, so it reveals the white_bg!
    master_squircle = Image.alpha_composite(white_bg, blot_canvas)

    # Also transparent blot master (for mono/adaptive foregrounds)
    master_transparent = blot_canvas

    # Helper function to save resized images
    def save_resized(img, path, size, mode='RGBA'):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        res = img.resize((size, size), Image.Resampling.LANCZOS)
        if mode != 'RGBA':
            res = res.convert(mode)
        res.save(path)
        print(f"Saved: {path} ({size}x{size} {mode})")

    # Helper function for adaptive foreground (centered in 432x432 safe zone, approx 66% = 288px)
    def make_adaptive_foreground(size=432, icon_size=288, mono=False):
        fg = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        # icon inside safe zone
        icon = master_squircle.resize((icon_size, icon_size), Image.Resampling.LANCZOS)
        if mono:
            # Monochrome icon: pure white or tinted
            r, g, b, a = icon.split()
            # Invert black blot to white on dark or preserve alpha
            icon_arr = np.array(icon)
            # Make dark parts transparent or inverted
            mono_im = Image.fromarray(np.full((icon_size, icon_size, 4), [255, 255, 255, 255], dtype=np.uint8))
            mono_im.putalpha(a)
            icon = mono_im
        offset = (size - icon_size) // 2
        fg.paste(icon, (offset, offset), icon)
        return fg

    # Generate core icons
    targets = [
        (master_squircle, os.path.join(root, 'src/icon.png'), 512, 'RGBA'),
        (master_squircle, os.path.join(root, 'src/assets/logo.png'), 512, 'RGBA'),
        (master_squircle, os.path.join(root, 'public/logo.png'), 512, 'RGBA'),
        (master_squircle, os.path.join(root, 'assets/icon.png'), 512, 'RGBA'),
        (master_squircle, os.path.join(root, 'assets/icon-mobile.png'), 512, 'RGBA'),
        (master_squircle, os.path.join(root, 'assets/icon-new.png'), 512, 'RGBA'),
        (master_squircle, os.path.join(root, 'assets/logo.png'), 512, 'RGBA'),
        # Web / PWA
        (master_squircle, os.path.join(root, 'src/icons/icon-512.png'), 512, 'RGBA'),
        (master_squircle, os.path.join(root, 'src/icons/icon-192.png'), 192, 'RGBA'),
        (master_squircle, os.path.join(root, 'src/icons/apple-touch-icon.png'), 180, 'RGB'),
        # Tauri
        (master_squircle, os.path.join(root, 'src-tauri/icons/icon.png'), 512, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/128x128.png'), 128, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/128x128@2x.png'), 256, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/32x32.png'), 32, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/64x64.png'), 64, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/Square30x30Logo.png'), 30, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/Square44x44Logo.png'), 44, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/Square71x71Logo.png'), 71, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/Square89x89Logo.png'), 89, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/Square107x107Logo.png'), 107, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/Square142x142Logo.png'), 142, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/Square150x150Logo.png'), 150, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/Square284x284Logo.png'), 284, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/Square310x310Logo.png'), 310, 'RGBA'),
        (master_squircle, os.path.join(root, 'src-tauri/icons/StoreLogo.png'), 50, 'RGBA'),
        # Android drawables
        (master_squircle, os.path.join(root, 'android/app/src/main/res/drawable/ic_votify_logo.png'), 256, 'RGBA'),
        # Android mipmaps (standard launcher)
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-mdpi/ic_launcher.png'), 48, 'RGBA'),
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-mdpi/ic_launcher_round.png'), 48, 'RGBA'),
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-hdpi/ic_launcher.png'), 72, 'RGBA'),
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-hdpi/ic_launcher_round.png'), 72, 'RGBA'),
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-xhdpi/ic_launcher.png'), 96, 'RGBA'),
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-xhdpi/ic_launcher_round.png'), 96, 'RGBA'),
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-xxhdpi/ic_launcher.png'), 144, 'RGBA'),
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-xxhdpi/ic_launcher_round.png'), 144, 'RGBA'),
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png'), 192, 'RGBA'),
        (master_squircle, os.path.join(root, 'android/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png'), 192, 'RGBA'),
    ]

    for img, path, sz, mode in targets:
        save_resized(img, path, sz, mode)

    # Maskable PWA icon (icon within 80% safe zone, black background)
    maskable = Image.new('RGBA', (512, 512), (20, 20, 20, 255))
    safe_icon = master_squircle.resize((384, 384), Image.Resampling.LANCZOS)
    maskable.paste(safe_icon, (64, 64), safe_icon)
    maskable.save(os.path.join(root, 'src/icons/icon-maskable-512.png'))
    print("Saved: src/icons/icon-maskable-512.png")

    # Android adaptive layers (432x432)
    res_dir = os.path.join(root, 'android/app/src/main/res/drawable-nodpi')
    fg_visual = make_adaptive_foreground(432, 280, mono=False)
    fg_visual.save(os.path.join(res_dir, 'ic_launcher_visual.png'))
    fg_visual.save(os.path.join(res_dir, 'ic_launcher_visual_bw.png'))
    fg_visual.save(os.path.join(res_dir, 'ic_launcher_visual_classic.png'))
    fg_visual.save(os.path.join(res_dir, 'ic_launcher_visual_glass.png'))
    
    fg_mono = make_adaptive_foreground(432, 280, mono=True)
    fg_mono.save(os.path.join(res_dir, 'ic_launcher_mono.png'))
    print("Saved Android adaptive layers in drawable-nodpi")

    # App icon variants preview
    variants_preview = Image.new('RGBA', (800, 200), (18, 18, 18, 255))
    v_icon = master_squircle.resize((160, 160), Image.Resampling.LANCZOS)
    for i in range(4):
        variants_preview.paste(v_icon, (30 + i * 190, 20), v_icon)
    variants_preview.save(os.path.join(root, 'assets/app-icon-variants.png'))
    print("Saved assets/app-icon-variants.png")

    # Tauri ICO file (contains 16, 32, 48, 64, 128, 256)
    ico_sizes = [(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    master_squircle.save(os.path.join(root, 'src-tauri/icons/icon.ico'), sizes=ico_sizes)
    print("Saved src-tauri/icons/icon.ico")

    print("All icons successfully generated!")

if __name__ == '__main__':
    main()

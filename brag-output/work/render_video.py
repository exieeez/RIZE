import math
import subprocess
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

WIDTH, HEIGHT = 1920, 1080
FPS = 30
TOTAL_FRAMES = 600  # 20.0 seconds
BG_COLOR = (9, 11, 15)

FONT_DIR = "/usr/share/fonts/TTF"
def get_font(name, size):
    path = os.path.join(FONT_DIR, name)
    if os.path.exists(path):
        return ImageFont.truetype(path, size)
    return ImageFont.load_default()

font_title_huge = get_font("Roboto-Black.ttf", 82)
font_title_large = get_font("Roboto-Bold.ttf", 52)
font_title_med = get_font("Roboto-Bold.ttf", 38)
font_body_large = get_font("Roboto-Medium.ttf", 28)
font_body = get_font("Roboto-Regular.ttf", 22)
font_caption = get_font("Roboto-Medium.ttf", 18)
font_code = get_font("RobotoMono-Bold.ttf", 24)

def load_img(path, max_w=None, max_h=None):
    if not os.path.exists(path):
        return None
    im = Image.open(path).convert("RGBA")
    if max_w and max_h:
        im.thumbnail((max_w, max_h), Image.Resampling.LANCZOS)
    return im

icon_img = load_img("assets/icon.png", 200, 200)
icon_small = load_img("assets/icon.png", 64, 64)
home_screen = load_img("assets/screenshots/01-home.png", 960, 560)
player_screen = load_img("assets/screenshots/03-player.png", 880, 510)

# Pre-render a soft radial gradient glow disk (550x550)
glow_size = 550
glow_im = Image.new("RGBA", (glow_size, glow_size), (0, 0, 0, 0))
glow_draw = ImageDraw.Draw(glow_im)
for r in range(glow_size // 2, 0, -4):
    a = int(35 * (1.0 - (r / (glow_size / 2.0))))
    glow_draw.ellipse([glow_size//2 - r, glow_size//2 - r, glow_size//2 + r, glow_size//2 + r],
                      fill=(29, 185, 84, a))
glow_im = glow_im.filter(ImageFilter.GaussianBlur(18))

# Floating particles
np.random.seed(42)
NUM_PARTICLES = 50
particles = []
for _ in range(NUM_PARTICLES):
    particles.append({
        'x': np.random.uniform(40, WIDTH - 40),
        'y': np.random.uniform(40, HEIGHT - 40),
        'speed_y': np.random.uniform(0.5, 2.0),
        'speed_x': np.random.uniform(-0.4, 0.4),
        'size': np.random.uniform(2, 5),
        'alpha': np.random.uniform(50, 160),
    })

def ease_out_cubic(x):
    return 1 - pow(1 - x, 3)

def blend(fg, alpha, bg=BG_COLOR):
    a = max(0.0, min(1.0, alpha / 255.0))
    return (
        int(fg[0] * a + bg[0] * (1.0 - a)),
        int(fg[1] * a + bg[1] * (1.0 - a)),
        int(fg[2] * a + bg[2] * (1.0 - a)),
        255
    )

def draw_rounded_rect(draw, bbox, radius, fill_fg=None, fill_alpha=255, outline_fg=None, outline_alpha=255, width=1):
    fill_c = blend(fill_fg, fill_alpha) if fill_fg else None
    outline_c = blend(outline_fg, outline_alpha) if outline_fg else None
    draw.rounded_rectangle(bbox, radius=radius, fill=fill_c, outline=outline_c, width=width)

def render_frame(frame_idx):
    t = frame_idx / float(FPS)
    
    # 1. Base Dark OLED Background
    base = Image.new("RGBA", (WIDTH, HEIGHT), (BG_COLOR[0], BG_COLOR[1], BG_COLOR[2], 255))
    draw = ImageDraw.Draw(base)
    
    # Soft ambient glow behind content
    gx = int(WIDTH * 0.5 - glow_size // 2)
    gy = int(HEIGHT * 0.45 - glow_size // 2 + 25 * math.sin(t * 1.5))
    base.paste(glow_im, (gx, gy), glow_im)
    
    # Floating ambient dust particles
    for p in particles:
        py = (p['y'] - t * p['speed_y'] * 35.0) % HEIGHT
        px = (p['x'] + t * p['speed_x'] * 18.0) % WIDTH
        p_sz = p['size']
        p_color = blend((29, 185, 84), p['alpha'])
        draw.ellipse([px - p_sz, py - p_sz, px + p_sz, py + p_sz], fill=p_color)

    # Top Brand Ribbon
    draw.rectangle([0, 0, WIDTH, 54], fill=blend((255, 255, 255), 14))
    draw.line([0, 54, WIDTH, 54], fill=blend((255, 255, 255), 26), width=1)
    if icon_small:
        base.paste(icon_small, (36, 11), icon_small)
    draw.text((82, 14), "VOTIFY", font=font_body_large, fill=(255, 255, 255, 255))
    draw.text((210, 20), "•  Free Open-Source Music Streaming", font=font_caption, fill=(160, 160, 165, 255))
    draw.text((WIDTH - 250, 18), "v1.1.5  |  ZERO ADS  •  100% FREE", font=font_caption, fill=(29, 185, 84, 255))

    # =========================================================================
    # SCENE 1: PAIN & HOOK (0.0s - 3.8s, Frames 0 - 114)
    # =========================================================================
    if frame_idx < 114:
        scene_fade = 1.0 if frame_idx < 100 else (114 - frame_idx) / 14.0
        alpha_mult = max(0.0, min(1.0, scene_fade))
        
        badge_y = 170
        draw_rounded_rect(draw, [WIDTH//2 - 150, badge_y, WIDTH//2 + 150, badge_y + 42], radius=21,
                          fill_fg=(255, 59, 48), fill_alpha=int(25 * alpha_mult),
                          outline_fg=(255, 59, 48), outline_alpha=int(120 * alpha_mult), width=1)
        draw.text((WIDTH//2 - 120, badge_y + 10), "ЗНАКОМАЯ ПРОБЛЕМА?", font=font_caption, fill=blend((255, 69, 58), int(255 * alpha_mult)))

        pains = [
            ("Spotify заблокирован в РФ?", 0.2, 270),
            ("Подписки требуют по 400 ₽ каждый месяц?", 1.2, 380),
            ("Назойливая аудиореклама между треками?", 2.2, 490),
        ]
        
        for text, start_t, y_pos in pains:
            if t >= start_t:
                progress = min(1.0, (t - start_t) / 0.3)
                anim = ease_out_cubic(progress)
                x_offset = int((1.0 - anim) * 50)
                
                draw.text((180 - x_offset + 2, y_pos + 2), text, font=font_title_large, fill=(0, 0, 0, 255))
                draw.text((180 - x_offset, y_pos), text, font=font_title_large, fill=blend((255, 255, 255), int(250 * alpha_mult)))
                
                if t >= start_t + 0.35:
                    strike_prog = min(1.0, (t - (start_t + 0.35)) / 0.25)
                    bbox = font_title_large.getbbox(text)
                    t_w = bbox[2] - bbox[0]
                    strike_w = int(t_w * ease_out_cubic(strike_prog))
                    line_y = y_pos + (bbox[3] - bbox[1]) // 2 + 10
                    draw.line([180, line_y, 180 + strike_w, line_y], fill=blend((255, 59, 48), int(240 * alpha_mult)), width=5)

        if t >= 3.0:
            punch_prog = min(1.0, (t - 3.0) / 0.3)
            p_alpha = int(255 * ease_out_cubic(punch_prog) * alpha_mult)
            draw.text((180, 640), "Хватит терпеть ограничения.", font=font_title_med, fill=blend((29, 185, 84), p_alpha))

    # =========================================================================
    # SCENE 2: THE REVEAL — VOTIFY (3.8s - 8.0s, Frames 114 - 240)
    # =========================================================================
    elif frame_idx < 240:
        scene_t = (frame_idx - 114) / float(FPS)
        scene_fade = 1.0 if frame_idx < 225 else (240 - frame_idx) / 15.0
        center_x = WIDTH // 2
        
        icon_scale = min(1.0, scene_t / 0.4)
        icon_scale = ease_out_cubic(icon_scale)
        
        # Logo Icon
        if icon_img:
            target_sz = int(140 * icon_scale)
            if target_sz > 10:
                cur_icon = icon_img.resize((target_sz, target_sz), Image.Resampling.BILINEAR)
                ix = center_x - target_sz // 2
                iy = 85
                base.paste(cur_icon, (ix, iy), cur_icon)

        # Title VOTIFY
        if scene_t >= 0.15:
            t_prog = min(1.0, (scene_t - 0.15) / 0.3)
            t_anim = ease_out_cubic(t_prog)
            y_shift = int((1.0 - t_anim) * 20)
            
            title_text = "VOTIFY"
            tb = font_title_huge.getbbox(title_text)
            draw.text((center_x - (tb[2] - tb[0]) // 2, 235 + y_shift), title_text, font=font_title_huge, fill=blend((29, 185, 84), int(255 * scene_fade)))
            
            sub_text = "Свободный стриминг музыки нового поколения"
            sb = font_body_large.getbbox(sub_text)
            draw.text((center_x - (sb[2] - sb[0]) // 2, 330 + y_shift), sub_text, font=font_body_large, fill=blend((255, 255, 255), int(240 * scene_fade)))

        # Platforms Pills
        if scene_t >= 0.4:
            pill_y = 390
            platforms = ["Android (APK)", "Windows (PC)", "Linux", "Web Player"]
            tot_w = len(platforms) * 165
            px_start = center_x - tot_w // 2
            cur_px = px_start
            for p_name in platforms:
                draw_rounded_rect(draw, [cur_px, pill_y, cur_px + 155, pill_y + 38], radius=19,
                                  fill_fg=(255, 255, 255), fill_alpha=int(22 * scene_fade),
                                  outline_fg=(255, 255, 255), outline_alpha=int(50 * scene_fade), width=1)
                pb = font_caption.getbbox(p_name)
                draw.text((cur_px + (155 - (pb[2] - pb[0])) // 2, pill_y + 9), p_name, font=font_caption, fill=blend((255, 255, 255), int(240 * scene_fade)))
                cur_px += 165

        # Floating mockup
        if scene_t >= 0.6 and home_screen:
            mock_prog = min(1.0, (scene_t - 0.6) / 0.5)
            mock_anim = ease_out_cubic(mock_prog)
            mock_y = int(HEIGHT - (mock_anim * 460))
            mock_x = center_x - home_screen.width // 2
            
            draw_rounded_rect(draw, [mock_x - 6, mock_y - 6, mock_x + home_screen.width + 6, mock_y + home_screen.height + 6],
                              radius=16, fill_fg=(29, 185, 84), fill_alpha=int(25 * scene_fade),
                              outline_fg=(29, 185, 84), outline_alpha=int(120 * scene_fade), width=2)
            base.paste(home_screen, (mock_x, mock_y))

    # =========================================================================
    # SCENE 3: KILLER FEATURES (8.0s - 12.5s, Frames 240 - 375)
    # =========================================================================
    elif frame_idx < 375:
        scene_t = (frame_idx - 240) / float(FPS)
        scene_fade = 1.0 if frame_idx < 360 else (375 - frame_idx) / 15.0
        
        draw_rounded_rect(draw, [110, 85, 330, 122], radius=18,
                          fill_fg=(29, 185, 84), fill_alpha=int(30 * scene_fade),
                          outline_fg=(29, 185, 84), outline_alpha=int(100 * scene_fade), width=1)
        draw.text((126, 94), "ТОПОВЫЕ ФИЧИ", font=font_caption, fill=blend((29, 185, 84), int(255 * scene_fade)))
        draw.text((110, 140), "ВСЁ, ЧТО ТЫ ЛЮБИШЬ. БЕЗ ПОДПИСКИ.", font=font_title_large, fill=blend((255, 255, 255), int(255 * scene_fade)))

        cards = [
            ("01", "Миллионы треков без сервера", "Встроенный движок YouTube & SoundCloud.\nЛюбые треки, редкие релизы, ремиксы и лайвы.", 0.2, 240),
            ("02", "Караоке-тексты в реальном времени", "Синхронизированные построчные тексты песен\nс плавной подсветкой строчки прямо во время проигрывания.", 1.2, 450),
            ("03", "Полный оффлайн-режим", "Сохраняй любимые альбомы и плейлисты в память\nи слушай без доступа к интернету.", 2.2, 660),
        ]
        
        for num, heading, desc, start_t, card_y in cards:
            if scene_t >= start_t:
                c_prog = min(1.0, (scene_t - start_t) / 0.35)
                c_anim = ease_out_cubic(c_prog)
                c_x = int(110 + (1.0 - c_anim) * -80)
                card_w = 820
                card_h = 175
                
                draw_rounded_rect(draw, [c_x, card_y, c_x + card_w, card_y + card_h], radius=16,
                                  fill_fg=(255, 255, 255), fill_alpha=int(16 * scene_fade),
                                  outline_fg=(255, 255, 255), outline_alpha=int(35 * scene_fade), width=1)
                
                draw_rounded_rect(draw, [c_x + 24, card_y + 24, c_x + 76, card_y + 66], radius=12,
                                  fill_fg=(29, 185, 84), fill_alpha=int(40 * scene_fade),
                                  outline_fg=(29, 185, 84), outline_alpha=int(140 * scene_fade), width=1)
                draw.text((c_x + 36, card_y + 32), num, font=font_caption, fill=blend((29, 185, 84), int(255 * scene_fade)))
                
                draw.text((c_x + 94, card_y + 28), heading, font=font_body_large, fill=blend((255, 255, 255), int(250 * scene_fade)))
                draw.text((c_x + 94, card_y + 80), desc, font=font_body, fill=blend((160, 165, 175), int(230 * scene_fade)))

        # Right Side Mockup
        if scene_t >= 0.4 and player_screen:
            p_prog = min(1.0, (scene_t - 0.4) / 0.4)
            p_anim = ease_out_cubic(p_prog)
            p_x = int(980 + (1.0 - p_anim) * 100)
            p_y = 250
            
            draw_rounded_rect(draw, [p_x - 6, p_y - 6, p_x + player_screen.width + 6, p_y + player_screen.height + 6],
                              radius=16, fill_fg=(29, 185, 84), fill_alpha=int(25 * scene_fade),
                              outline_fg=(29, 185, 84), outline_alpha=int(110 * scene_fade), width=2)
            base.paste(player_screen, (p_x, p_y))

    # =========================================================================
    # SCENE 4: FREEDOM, THEMES & MOBILE INTEGRATION (12.5s - 16.5s, Frames 375 - 495)
    # =========================================================================
    elif frame_idx < 495:
        scene_t = (frame_idx - 375) / float(FPS)
        scene_fade = 1.0 if frame_idx < 480 else (495 - frame_idx) / 15.0
        center_x = WIDTH // 2
        
        hb = font_title_large.getbbox("ПОЛНАЯ СВОБОДА И КАСТОМИЗАЦИЯ")
        draw.text((center_x - (hb[2] - hb[0]) // 2, 100), "ПОЛНАЯ СВОБОДА И КАСТОМИЗАЦИЯ", font=font_title_large, fill=blend((255, 255, 255), int(255 * scene_fade)))
        
        sub = "Приложение, которое подстраивается под тебя, а не наоборот"
        subb = font_body.getbbox(sub)
        draw.text((center_x - (subb[2] - subb[0]) // 2, 185), sub, font=font_body, fill=blend((160, 165, 175), int(220 * scene_fade)))

        columns = [
            ("ТЕМЫ ОФОРМЛЕНИЯ", "Dotify 23, iOS Glass, Cyberpunk и поддержка пользовательских тем через воркшоп.", 0.2, center_x - 560),
            ("DYNAMIC ISLAND", "Интеграция с островком уведомлений на смартфонах и компактным виджетом на ПК.", 0.6, center_x - 170),
            ("ОБЛАЧНЫЙ СИНК", "Резервное копирование плейлистов и треков через Firebase без третьих лиц.", 1.0, center_x + 220),
        ]
        
        for title, desc, start_t, col_x in columns:
            if scene_t >= start_t:
                c_prog = min(1.0, (scene_t - start_t) / 0.3)
                c_anim = ease_out_cubic(c_prog)
                col_y = int(280 + (1.0 - c_anim) * 50)
                col_w = 340
                col_h = 290
                
                draw_rounded_rect(draw, [col_x, col_y, col_x + col_w, col_y + col_h], radius=20,
                                  fill_fg=(255, 255, 255), fill_alpha=int(18 * scene_fade),
                                  outline_fg=(255, 255, 255), outline_alpha=int(40 * scene_fade), width=1)
                
                draw_rounded_rect(draw, [col_x + 26, col_y + 30, col_x + 60, col_y + 36], radius=3, fill_fg=(29, 185, 84), fill_alpha=int(255 * scene_fade))
                draw.text((col_x + 26, col_y + 55), title, font=font_body_large, fill=blend((255, 255, 255), int(250 * scene_fade)))
                draw.text((col_x + 26, col_y + 115), desc, font=font_body, fill=blend((160, 165, 175), int(220 * scene_fade)))

        if scene_t >= 1.4:
            b_prog = min(1.0, (scene_t - 1.4) / 0.35)
            b_anim = ease_out_cubic(b_prog)
            b_y = int(670 + (1.0 - b_anim) * 40)
            
            draw_rounded_rect(draw, [center_x - 620, b_y, center_x + 620, b_y + 120], radius=24,
                              fill_fg=(29, 185, 84), fill_alpha=int(30 * scene_fade),
                              outline_fg=(29, 185, 84), outline_alpha=int(160 * scene_fade), width=2)
            
            banner_text = "0 ₽ ПОДПИСОК   •   0 РЕКЛАМЫ   •   100% OPEN-SOURCE"
            bb = font_title_med.getbbox(banner_text)
            draw.text((center_x - (bb[2] - bb[0]) // 2, b_y + 36), banner_text, font=font_title_med, fill=blend((29, 185, 84), int(255 * scene_fade)))

    # =========================================================================
    # SCENE 5: OUTRO & CALL TO ACTION (16.5s - 20.0s, Frames 495 - 600)
    # =========================================================================
    else:
        scene_t = (frame_idx - 495) / float(FPS)
        scene_fade = 1.0 if frame_idx < 585 else max(0.0, (600 - frame_idx) / 15.0)
        center_x = WIDTH // 2
        
        pulse_scale = 1.0 + 0.03 * math.sin(scene_t * 5.0)
        if icon_img:
            sz = int(160 * pulse_scale)
            cur_icon = icon_img.resize((sz, sz), Image.Resampling.BILINEAR)
            ix = center_x - sz // 2
            iy = 160
            base.paste(cur_icon, (ix, iy), cur_icon)

        title_text = "VOTIFY"
        tb = font_title_huge.getbbox(title_text)
        draw.text((center_x - (tb[2] - tb[0]) // 2, 350), title_text, font=font_title_huge, fill=blend((29, 185, 84), int(255 * scene_fade)))
        
        claim_text = "Твоя музыка. Без границ и подписок."
        cb = font_title_med.getbbox(claim_text)
        draw.text((center_x - (cb[2] - cb[0]) // 2, 455), claim_text, font=font_title_med, fill=blend((255, 255, 255), int(250 * scene_fade)))

        # Big CTA Button
        btn_y = 540
        btn_w = 460
        btn_h = 76
        bx = center_x - btn_w // 2
        draw_rounded_rect(draw, [bx, btn_y, bx + btn_w, btn_y + btn_h], radius=38,
                          fill_fg=(29, 185, 84), fill_alpha=int(255 * scene_fade))
        btn_str = "СКАЧАТЬ БЕСПЛАТНО"
        btn_b = font_body_large.getbbox(btn_str)
        draw.text((bx + (btn_w - (btn_b[2] - btn_b[0])) // 2, btn_y + 20), btn_str, font=font_body_large, fill=(10, 12, 16, 255))

        # GitHub Repo Link Card with dark glass background
        link_y = 660
        link_w = 540
        lx = center_x - link_w // 2
        draw_rounded_rect(draw, [lx, link_y, lx + link_w, link_y + 56], radius=16,
                          fill_fg=(255, 255, 255), fill_alpha=int(22 * scene_fade),
                          outline_fg=(255, 255, 255), outline_alpha=int(50 * scene_fade), width=1)
        draw.text((lx + 42, link_y + 14), "GitHub: github.com/exieeez/Votify", font=font_code, fill=blend((255, 255, 255), int(230 * scene_fade)))

        sub_str = "Доступно для Android (APK), Windows и Linux"
        subb = font_body.getbbox(sub_str)
        draw.text((center_x - (subb[2] - subb[0]) // 2, 755), sub_str, font=font_body, fill=blend((160, 165, 175), int(210 * scene_fade)))

    return base.convert("RGB")

if __name__ == "__main__":
    print("Rendering video directly to FFmpeg...")
    
    poster_frame = render_frame(540)
    poster_frame.save("brag-output/brag.jpg", quality=95)
    print("Saved poster: brag-output/brag.jpg")

    ffmpeg_cmd = [
        "ffmpeg", "-y",
        "-f", "rawvideo",
        "-vcodec", "rawvideo",
        "-s", f"{WIDTH}x{HEIGHT}",
        "-pix_fmt", "rgb24",
        "-r", str(FPS),
        "-i", "-",
        "-i", "brag-output/work/soundtrack.wav",
        "-c:v", "libx264",
        "-preset", "fast",
        "-crf", "18",
        "-pix_fmt", "yuv420p",
        "-c:a", "aac",
        "-b:a", "256k",
        "-shortest",
        "brag-output/brag.mp4"
    ]

    proc = subprocess.Popen(ffmpeg_cmd, stdin=subprocess.PIPE)

    for f_idx in range(TOTAL_FRAMES):
        if f_idx == 0:
            img = poster_frame
        else:
            img = render_frame(f_idx)
            
        proc.stdin.write(img.tobytes())
        if f_idx % 60 == 0:
            print(f"Rendered frame {f_idx}/{TOTAL_FRAMES} ({(f_idx/TOTAL_FRAMES)*100:.1f}%)")

    proc.stdin.close()
    proc.wait()

    print("Video successfully rendered: brag-output/brag.mp4")

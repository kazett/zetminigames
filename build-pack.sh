#!/usr/bin/env bash
# Rakit resource pack dari file flat di root repo, lalu zip.
set -euo pipefail
OUT=ZetMinigames-Pack.zip
rm -rf pack-build "$OUT"
FONT=pack-build/assets/zetminigames/textures/font
SPR=pack-build/assets/minecraft/textures/gui/sprites
mkdir -p pack-build/assets/zetminigames/font "$FONT" "$SPR/boss_bar" "$SPR/widget"
cp pack.mcmeta pack-build/pack.mcmeta
cp hud.json pack-build/assets/zetminigames/font/hud.json
cp hud_low.json pack-build/assets/zetminigames/font/hud_low.json
for f in hud_panel hud_frame hud_skull hud_item hud_alive hud_logo hud_border hud_banner hud_banner_round hud_banner_draw hud_winframe hud_icons hud_bar hud_count hud_tpwarn hud_banner_go hud_banner_swap hud_banner_elim; do
  cp "$f.png" "$FONT/$f.png"
done

# bossbar bawaan (warna WHITE) transparan, HUD digambar lewat font
cp bar_clear.png "$SPR/boss_bar/white_background.png"
cp bar_clear.png "$SPR/boss_bar/white_progress.png"

# skin widget (tombol / kolom teks / checkbox) buat semua dialog /panel
cp ui_button.png     "$SPR/widget/button.png"
cp ui_button_hl.png  "$SPR/widget/button_highlighted.png"
cp ui_button_off.png "$SPR/widget/button_disabled.png"
cp ui_field.png      "$SPR/widget/text_field.png"
cp ui_field_hl.png   "$SPR/widget/text_field_highlighted.png"
cp ui_check.png        "$SPR/widget/checkbox.png"
cp ui_check_hl.png     "$SPR/widget/checkbox_highlighted.png"
cp ui_check_on.png     "$SPR/widget/checkbox_selected.png"
cp ui_check_on_hl.png  "$SPR/widget/checkbox_selected_highlighted.png"
NINE='{"gui":{"scaling":{"type":"nine_slice","width":200,"height":20,"border":3}}}'
for n in button button_highlighted button_disabled; do echo "$NINE" > "$SPR/widget/$n.png.mcmeta"; done
FLD='{"gui":{"scaling":{"type":"nine_slice","width":200,"height":20,"border":2}}}'
for n in text_field text_field_highlighted; do echo "$FLD" > "$SPR/widget/$n.png.mcmeta"; done

(cd pack-build && zip -qr "../$OUT" .)
sha1sum "$OUT"

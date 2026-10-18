#!/usr/bin/env python3
"""
Fusionne les traductions du front web (apps/web/messages/<langue>/<namespace>.json) et celles
propres à l'app Android (localization/android.<langue>.json) en un fichier par langue :
app/src/main/assets/messages.<langue>.json (lu par L10n.kt).

Même mécanique que le script de l'app iOS, et pour la même raison : une clé visible sur les
deux apps ne doit exister qu'une fois, dans le dépôt web.

Usage : python3 scripts/sync-messages.py [chemin/vers/ygo-deckbuilder/apps/web/messages]
Par défaut, le dépôt web est cherché à côté de celui-ci (../ygo-deckbuilder).
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LOCALES = ["en", "fr", "de", "it", "pt"]
web = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / "ygo-deckbuilder" / "apps" / "web" / "messages"
out_dir = ROOT / "app" / "src" / "main" / "assets"

if not web.is_dir():
    sys.exit(f"Messages du web introuvables : {web}")

out_dir.mkdir(parents=True, exist_ok=True)
for locale in LOCALES:
    merged = {}
    for file in sorted((web / locale).glob("*.json")):
        merged[file.stem] = json.loads(file.read_text(encoding="utf-8"))
    local = ROOT / "localization" / f"android.{locale}.json"
    merged["android"] = json.loads(local.read_text(encoding="utf-8")) if local.exists() else {}
    target = out_dir / f"messages.{locale}.json"
    target.write_text(json.dumps(merged, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"{target.relative_to(ROOT)} : {len(merged)} namespaces")

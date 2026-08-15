"""Build Android HAT-first offline pack assets (gzip JSON)."""
from __future__ import annotations

import gzip
import json
import sqlite3
from pathlib import Path

ROOT = Path(r"c:\Users\adani\Desktop\YGOChecker")
DB = ROOT / "ygo-card-checker" / "data" / "card-knowledge" / "cards.db"
LEGALITY = ROOT / "ygo-card-checker" / "src" / "assets" / "data" / "card-knowledge" / "format-legality.json"
SCRIPTS = ROOT / "ygo-card-checker" / "src" / "assets" / "data" / "effect-scripts" / "scripts.json"
RELATED = ROOT / "ygo-card-checker" / "src" / "assets" / "data" / "card-knowledge" / "related.json"
OUT = ROOT / "android" / "data" / "cards" / "src" / "main" / "assets" / "offline-pack"


def write_gz(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    raw = json.dumps(obj, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    with gzip.open(path, "wb", compresslevel=9) as f:
        f.write(raw)
    print(path.name, "bytes", path.stat().st_size, "raw", len(raw))


def main() -> None:
    legality = json.loads(LEGALITY.read_text(encoding="utf-8"))
    playable = legality.get("playable") or {}
    hat_ids = {int(cid) for cid, fmts in playable.items() if "hat" in (fmts or [])}
    print("HAT ids", len(hat_ids))

    con = sqlite3.connect(DB)
    con.row_factory = sqlite3.Row
    cols = [r[1] for r in con.execute("PRAGMA table_info(cards)")]
    print("card cols", cols)

    # Adapt to schema
    name_col = "name"
    type_col = "type"
    desc_col = "desc_en" if "desc_en" in cols else "description"
    atk_col = "atk"
    def_col = "def"
    level_col = "level"
    race_col = "race"
    attr_col = "attribute"
    year_col = "tcg_date" if "tcg_date" in cols else None

    cards = []
    for row in con.execute(f"SELECT * FROM cards WHERE id IN ({','.join(map(str, hat_ids))})"):
        year = None
        if year_col and row[year_col]:
            try:
                year = int(str(row[year_col])[:4])
            except ValueError:
                year = None
        cards.append(
            {
                "id": row["id"],
                "name": row[name_col] or "",
                "type": row[type_col] or "",
                "race": row[race_col],
                "attribute": row[attr_col],
                "attack": row[atk_col],
                "defense": row[def_col],
                "level": row[level_col],
                "description": row[desc_col] or "",
                "year": year,
            }
        )
    print("cards exported", len(cards))
    write_gz(OUT / "cards-hat.json.gz", cards)

    scripts_root = json.loads(SCRIPTS.read_text(encoding="utf-8"))
    scripts = scripts_root.get("scripts") or {}
    hat_scripts = {str(cid): scripts[str(cid)] for cid in hat_ids if str(cid) in scripts}
    print("scripts", len(hat_scripts))
    write_gz(
        OUT / "scripts-hat.json.gz",
        {
            "version": scripts_root.get("version", 1),
            "generatedAt": scripts_root.get("generatedAt", ""),
            "cardCount": len(hat_scripts),
            "scripts": hat_scripts,
        },
    )

    related_root = json.loads(RELATED.read_text(encoding="utf-8"))
    entries = related_root.get("entries") or {}
    hat_entries = {str(cid): entries[str(cid)] for cid in hat_ids if str(cid) in entries}
    # Keep related targets even if outside HAT (Elegant Egotist is HAT legal anyway)
    print("related entries", len(hat_entries))
    write_gz(
        OUT / "related-hat.json.gz",
        {
            "version": related_root.get("version", 1),
            "cardCount": len(hat_entries),
            "entries": hat_entries,
        },
    )

    # Manual synergies (HAT / archetype staples) — merge on top of related
    synergies = {
        "76812113": [  # Harpie Lady
            {"id": 90219263, "name": "Elegant Egotist", "relation": "hat_engine", "score": 2.0},
            {"id": 90238142, "name": "Harpie Channeler", "relation": "archetype", "score": 1.8},
            {"id": 68815132, "name": "Harpie Dancer", "relation": "archetype", "score": 1.7},
            {"id": 75064463, "name": "Harpie Queen", "relation": "archetype", "score": 1.6},
            {"id": 70095154, "name": "Cyber Dragon", "relation": "hat_tech", "score": 1.1},
            {"id": 91812341, "name": "Traptrix Myrmeleo", "relation": "hat_engine", "score": 1.2},
            {"id": 1475311, "name": "Allure of Darkness", "relation": "hat_draw", "score": 1.0},
        ],
        "90219263": [  # Elegant Egotist
            {"id": 76812113, "name": "Harpie Lady", "relation": "hat_engine", "score": 2.0},
            {"id": 68815132, "name": "Harpie Dancer", "relation": "archetype", "score": 1.5},
            {"id": 90238142, "name": "Harpie Channeler", "relation": "archetype", "score": 1.7},
            {"id": 75064463, "name": "Harpie Queen", "relation": "archetype", "score": 1.5},
        ],
        "17259470": [  # Zombie Master
            {"id": 92826944, "name": "Mezuki", "relation": "gy_synergy", "score": 2.0},
            {"id": 63665875, "name": "Goblin Zombie", "relation": "gy_synergy", "score": 1.8},
            {"id": 33420078, "name": "Plaguespreader Zombie", "relation": "gy_synergy", "score": 1.6},
            {"id": 81439173, "name": "Foolish Burial", "relation": "gy_synergy", "score": 1.7},
        ],
    }
    write_gz(OUT / "synergies-manual.json.gz", {"version": 1, "entries": synergies})
    print("done", OUT)


if __name__ == "__main__":
    main()

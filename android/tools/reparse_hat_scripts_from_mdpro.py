"""Re-parse MDPro LUA for HAT scripts and rewrite scripts-hat.json.gz produces/tags.

Uses the same structural rules as mdpro-lua-parser.ts (kept in sync manually).
Requires extracted LUA at ygo-card-checker/tools/card-knowledge-db/mdpro-scripts/
or falls back to C:\\Games\\MDPro3\\Data\\script.zip extraction for missing cards.
"""
from __future__ import annotations

import gzip
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(r"c:\Users\adani\Desktop\YGOChecker")
SCRIPTS_GZ = ROOT / "android" / "data" / "cards" / "src" / "main" / "assets" / "offline-pack" / "scripts-hat.json.gz"
LUA_CACHE = ROOT / "ygo-card-checker" / "tools" / "card-knowledge-db" / "mdpro-scripts"
MDPRO_ZIP = Path(r"C:\Games\MDPro3\Data\script.zip")
SAMPLES = ROOT / "android" / "tools" / "_lua_samples"


def extract_race_filter(lua: str) -> str | None:
    m = re.search(r"IsRace\(\s*RACE_([A-Z0-9_]+)\s*\)", lua)
    if not m:
        return None
    label = " ".join(p.capitalize() for p in m.group(1).lower().split("_"))
    return f"{label} monster"


def extract_gy_cost(lua: str) -> list[str]:
    if re.search(r"bfgcost|Remove\(", lua):
        return ["banish_self_from_gy"]
    if "SendtoDeck" in lua and "LOCATION_HAND" in lua:
        return ["send_hand_to_deck"]
    return []


def parse_mdpro_lua(lua: str) -> dict:
    has_hand_to_grave = "LOCATION_HAND" in lua and (
        "IsAbleToGraveAsCost" in lua
        or "DiscardHand" in lua
        or bool(re.search(r"SendtoGrave\([^)]*LOCATION_HAND", lua))
    )
    has_grave_range = bool(re.search(r"SetRange\(\s*LOCATION_GRAVE\s*\)", lua))
    has_ss = bool(re.search(r"SpecialSummon|CATEGORY_SPECIAL_SUMMON", lua))
    summons_from_grave = has_ss and (
        "LOCATION_GRAVE" in lua or bool(re.search(r"IsExistingTarget\([^)]*LOCATION_GRAVE", lua))
    )
    summons_self = has_ss and (
        bool(re.search(r"SetOperationInfo\(\s*0\s*,\s*CATEGORY_SPECIAL_SUMMON\s*,\s*e:GetHandler\(\)", lua))
        or bool(re.search(r"SpecialSummon\(\s*e:GetHandler\(\)", lua))
        or bool(re.search(r"SpecialSummon\(\s*c\b", lua))
    )
    discards = bool(re.search(r"DiscardHand|SendtoGrave\([^)]*LOCATION_HAND", lua))

    timings: set[str] = set()
    roles: set[str] = set()
    steps: list[dict] = []

    if has_hand_to_grave or discards:
        roles.add("extender")
        timings.add("activate")
    if has_grave_range:
        roles.add("extender")
        timings.add("gy")
    if has_grave_range and summons_self:
        roles.add("extender")
        timings.add("special_summon")
    elif summons_from_grave:
        roles.add("extender")
        timings.add("special_summon")

    if has_hand_to_grave and summons_from_grave and not summons_self:
        roles.add("starter")
        steps.append(
            {
                "id": "mdpro-hand-cost-ss-gy",
                "when": "ignition",
                "cost": ["send_monster_hand_to_gy"],
                "actions": [
                    {"op": "discard", "from": "hand", "to": "gy", "filter": "monster", "qty": 1, "note": "cost"},
                    {
                        "op": "ss",
                        "from": "gy",
                        "to": "monster",
                        "filter": extract_race_filter(lua) or "monster in GY",
                        "qty": 1,
                    },
                ],
                "produces": ["gy_body", "ss_from_gy"],
            }
        )
    elif has_grave_range and summons_self:
        steps.append(
            {
                "id": "mdpro-gy-self-ss",
                "when": "gy",
                "cost": extract_gy_cost(lua),
                "actions": [{"op": "ss", "from": "gy", "to": "monster", "filter": "this card", "qty": 1}],
                "produces": ["revives_from_gy", "gy_effect"],
            }
        )
    elif has_grave_range and summons_from_grave:
        steps.append(
            {
                "id": "mdpro-gy-ss",
                "when": "gy",
                "cost": extract_gy_cost(lua),
                "actions": [
                    {
                        "op": "ss",
                        "from": "gy",
                        "to": "monster",
                        "filter": extract_race_filter(lua) or "monster in GY",
                        "qty": 1,
                    }
                ],
                "produces": ["ss_from_gy", "gy_effect"],
            }
        )
    elif summons_from_grave:
        steps.append(
            {
                "id": "mdpro-ss-gy",
                "when": "activate",
                "actions": [
                    {
                        "op": "ss",
                        "from": "gy",
                        "to": "monster",
                        "filter": extract_race_filter(lua) or "monster in GY",
                        "qty": 1,
                    }
                ],
                "produces": ["ss_from_gy"],
            }
        )
    elif has_hand_to_grave:
        steps.append(
            {
                "id": "mdpro-hand-to-gy",
                "when": "cost",
                "actions": [{"op": "discard", "from": "hand", "to": "gy", "qty": 1}],
                "produces": ["hand_to_gy"],
            }
        )

    if not roles:
        roles.add("tech")

    return {"roles": sorted(roles), "timings": sorted(timings), "steps": steps}


def load_lua(card_id: int) -> str | None:
    for base in (LUA_CACHE, SAMPLES):
        p = base / f"c{card_id}.lua"
        if p.exists():
            return p.read_text(encoding="utf-8", errors="replace")
    if MDPRO_ZIP.exists():
        with zipfile.ZipFile(MDPRO_ZIP) as z:
            name = f"script/c{card_id}.lua"
            try:
                return z.read(name).decode("utf-8", errors="replace")
            except KeyError:
                return None
    return None


def main() -> None:
    with gzip.open(SCRIPTS_GZ, "rt", encoding="utf-8") as f:
        root = json.load(f)
    scripts = root.get("scripts") or {}
    updated = 0
    checked = 0
    for cid_str, script in list(scripts.items()):
        try:
            cid = int(cid_str)
        except ValueError:
            continue
        lua = load_lua(cid)
        if not lua:
            continue
        checked += 1
        existing_steps = script.get("steps") or []
        if existing_steps and not all(str(s.get("id", "")).startswith("mdpro-") for s in existing_steps):
            # Keep richer hand/HAT-authored ASTs; only refresh MDPro-derived stubs.
            continue
        parsed = parse_mdpro_lua(lua)
        if not parsed["steps"]:
            continue
        script["timings"] = parsed["timings"] or script.get("timings") or []
        script["roles"] = parsed["roles"] or script.get("roles") or ["tech"]
        script["steps"] = parsed["steps"]
        script["source"] = "manual"
        script["confidence"] = 0.95
        scripts[cid_str] = script
        updated += 1

    root["scripts"] = scripts
    root["generatedAt"] = "2026-08-15Tmdpro-tag-fix"
    raw = json.dumps(root, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    with gzip.open(SCRIPTS_GZ, "wb", compresslevel=9) as f:
        f.write(raw)
    print("checked", checked, "updated", updated, "bytes", SCRIPTS_GZ.stat().st_size)

    # Spot-check the three reference cards
    for cid in (11260714, 33420078, 92826944):
        s = scripts.get(str(cid), {})
        print(cid, s.get("name"), "timings", s.get("timings"), "steps", s.get("steps"))


if __name__ == "__main__":
    main()

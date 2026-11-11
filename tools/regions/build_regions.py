"""Build the 230 stamp-region boundary files from the vuski/admdongkor 행정동 GeoJSON.

Usage (from the repo root, Node.js required for mapshaper via npx):
    python -I tools/regions/build_regions.py data/raw/HangJeongDong_ver20261001.geojson

Outputs:
    app/src/main/assets/regions/sgg230.geojson   (on-device lookup)
    design/sgg230_preview.geojson                (drawing / design work)
"""
import json
import os
import re
import subprocess
import sys
import tempfile
from collections import defaultdict

MAPSHAPER = "mapshaper@0.7.99"
EXPECTED_REGIONS = 230

# 일반구 (non-autonomous district inside a city): sggnm looks like "성남시수정구".
GENERAL_GU = re.compile(r"^(?P<city>\S+시)(?P<gu>\S+구)$")
# Sejong has no si/gun/gu level; the whole city is one region.
SEJONG_CODE = "36110"


def region_of(props):
    """Return (code, sido, name, merged_gu_or_None) for one 행정동 feature."""
    code = props["adm_cd2"][:5]
    sido = props["adm_nm"].split()[0]
    sggnm = props["sggnm"]
    if code == SEJONG_CODE:
        return code, sido, sido, None
    m = GENERAL_GU.match(sggnm)
    if m:
        # Parent city code: last digit set to 0 (e.g. 41131 -> 41130).
        return code[:4] + "0", sido, m.group("city"), (code, sggnm)
    return code, sido, sggnm, None


def run_mapshaper(args):
    npx = "npx.cmd" if os.name == "nt" else "npx"
    cmd = [npx, "-y", MAPSHAPER] + args
    print("$ mapshaper " + " ".join(args), flush=True)
    subprocess.run(cmd, check=True)


def point_in_ring(x, y, ring):
    inside = False
    j = len(ring) - 1
    for i in range(len(ring)):
        xi, yi = ring[i][0], ring[i][1]
        xj, yj = ring[j][0], ring[j][1]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


def point_in_geometry(x, y, geom):
    polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
    for poly in polys:
        if point_in_ring(x, y, poly[0]) and not any(point_in_ring(x, y, h) for h in poly[1:]):
            return True
    return False


def lookup(features, lat, lon):
    return [f["properties"] for f in features if point_in_geometry(lon, lat, f["geometry"])]


POINT_CHECKS = [
    ((37.41272, 127.09468), "경기도", "성남시"),
    ((35.8562, 129.2247), "경상북도", "경주시"),
    ((33.4996, 126.5312), "제주특별자치도", "제주시"),
    ((37.4602, 126.4407), "인천광역시", "영종구"),
]


def verify(path):
    fc = json.load(open(path, encoding="utf-8"))
    feats = fc["features"]
    ok = len(feats) == EXPECTED_REGIONS
    print(f"{path}: {len(feats)} features, {os.path.getsize(path) / 1024:.0f} KB")
    for (lat, lon), sido, name in POINT_CHECKS:
        hits = lookup(feats, lat, lon)
        got = ", ".join(f"{p['sido']} {p['name']} ({p['code']})" for p in hits) or "none"
        good = len(hits) == 1 and hits[0]["sido"] == sido and hits[0]["name"] == name
        ok &= good
        print(f"  ({lat}, {lon}) -> {got}  [{'OK' if good else 'FAIL'}]")
    for f in feats:
        p = f["properties"]
        if not point_in_geometry(p["label_lon"], p["label_lat"], f["geometry"]):
            ok = False
            print(f"  label point outside polygon: {p['code']} {p['name']}")
    return ok


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    src = sys.argv[1]
    root = os.getcwd()
    asset_out = os.path.join(root, "app", "src", "main", "assets", "regions", "sgg230.geojson")
    preview_out = os.path.join(root, "design", "sgg230_preview.geojson")
    os.makedirs(os.path.dirname(asset_out), exist_ok=True)
    os.makedirs(os.path.dirname(preview_out), exist_ok=True)

    fc = json.load(open(src, encoding="utf-8"))
    print("Sample feature properties:", json.dumps(fc["features"][0]["properties"], ensure_ascii=False))

    regions = {}
    merged = defaultdict(set)
    for f in fc["features"]:
        code, sido, name, gu = region_of(f["properties"])
        prev = regions.setdefault(code, (sido, name))
        if prev != (sido, name):
            sys.exit(f"Conflicting names for {code}: {prev} vs {(sido, name)}")
        if gu:
            merged[code].add(gu)
        f["properties"] = {"code": code, "sido": sido, "name": name}

    print(f"\nRegions: {len(regions)} (expected {EXPECTED_REGIONS})")
    print(f"\nMerged 일반구 ({sum(len(v) for v in merged.values())} gu into {len(merged)} cities):")
    for code in sorted(merged):
        gus = ", ".join(f"{n} ({c})" for c, n in sorted(merged[code]))
        print(f"  {code} {regions[code][0]} {regions[code][1]} <- {gus}")
    if len(regions) != EXPECTED_REGIONS:
        for code in sorted(regions):
            print("  ", code, *regions[code])
        sys.exit("Region count mismatch; stopping.")

    with tempfile.TemporaryDirectory() as tmp:
        tagged = os.path.join(tmp, "tagged.geojson")
        dissolved = os.path.join(tmp, "dissolved.json")
        json.dump(fc, open(tagged, "w", encoding="utf-8"), ensure_ascii=False)
        del fc
        run_mapshaper(["-i", tagged, "snap",
                       "-dissolve", "code", "copy-fields=sido,name",
                       "-o", dissolved, "format=topojson"])
        labels = ("-each", "label_lon=+this.innerX.toFixed(5), label_lat=+this.innerY.toFixed(5)")
        # Labels are computed after simplification so they stay inside the drawn shape.
        run_mapshaper(["-i", dissolved,
                       "-simplify", "weighted", "keep-shapes", "interval=25",
                       *labels, "-sort", "code",
                       "-o", asset_out, "format=geojson", "precision=0.00001"])
        run_mapshaper(["-i", dissolved,
                       "-simplify", "weighted", "keep-shapes", "interval=250",
                       *labels, "-sort", "code",
                       "-o", preview_out, "format=geojson", "precision=0.0001"])

    print()
    ok = verify(asset_out) & verify(preview_out)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()

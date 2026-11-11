# Region boundary data

Chagok Chagok stamps one of **230 regions**: the 227 기초자치단체 (시·군·자치구, after the
July 2026 Incheon reorganization) plus 세종특별자치시, 제주시 and 서귀포시.

| File | Use | Simplification | Size |
|---|---|---|---|
| `app/src/main/assets/regions/sgg230.geojson` | on-device point-in-polygon lookup | Visvalingam weighted, 25 m interval, 1e-5° precision | ~2.9 MB |
| `design/sgg230_preview.geojson` | drawing / design work | Visvalingam weighted, 250 m interval, 1e-4° precision | ~0.56 MB |

Both files are WGS84 (EPSG:4326) FeatureCollections of 230 features, sorted by `code`:

| Property | Example | Meaning |
|---|---|---|
| `code` | `41130` | 5-digit 시군구 code (일반구 rolled up to the parent city) |
| `sido` | `경기도` | 시도 name |
| `name` | `성남시` | region name (세종 is `세종특별자치시`) |
| `label_lon`, `label_lat` | `127.1…`, `37.4…` | interior point for a label (always inside the polygon) |

## Source

`vuski/admdongkor` `ver20261001/HangJeongDong_ver20261001.geojson` (3,559 행정동, WGS84),
built from 통계청 SGIS boundaries. License: CC BY 4.0 (data from SGIS, 공공누리 제1유형). See `NOTICE.md`.

## How it was built

```bash
mkdir -p data/raw   # git-ignored
curl -L -o data/raw/HangJeongDong_ver20261001.geojson \
  https://raw.githubusercontent.com/vuski/admdongkor/master/ver20261001/HangJeongDong_ver20261001.geojson
python -I tools/regions/build_regions.py data/raw/HangJeongDong_ver20261001.geojson
```

`tools/regions/build_regions.py` (Python stdlib + mapshaper 0.7.99 via `npx`):

1. **Region code** per 행정동: first 5 digits of `adm_cd2`; `sido` from the first token of `adm_nm`.
2. **일반구 roll-up**: if `sggnm` looks like `<city>시<gu>구` (e.g. `성남시수정구`), the feature belongs to
   the parent city; its code gets the last digit set to 0 (41131 → 41130) and `name` = the city.
   The rule "5th digit ≠ 0 means 일반구" is *not* used, because several autonomous districts also have a
   non-zero 5th digit (서울 광진구 11215, 강북구 11305, 금천구 11545, 인천 제물포구 28125, 영종구 28155, …).
   39 일반구 are merged into 13 cities: 수원, 성남, 안양, 부천, 안산, 고양, 용인, 화성, 청주, 천안, 포항, 창원, 전주.
3. **Dissolve** with mapshaper (`-i snap`, `-dissolve code`) → exactly 230 polygons.
4. **Simplify** each output separately with `keep-shapes` (no region or island group disappears),
   then compute `label_lon/label_lat` with mapshaper's `innerX/innerY` on the simplified shape.
5. **Verify** (script exits non-zero on failure): 230 features; every label point inside its polygon; point checks
   - (37.41272, 127.09468) → 경기도 성남시 (the NaviHelper emulator position, "수정구 시흥동")
   - (35.8562, 129.2247) → 경상북도 경주시
   - (33.4996, 126.5312) → 제주특별자치도 제주시
   - (37.4602, 126.4407) → 인천광역시 영종구

## Notes on this data version

- 광주광역시 and 전라남도 appear as one 시도, `전남광주통합특별시` (code prefix `12`), in ver20261001.
  Its 27 regions (5 자치구, 5 시, 17 군) are still separate stamps.
- Regions per 시도: 경기 31, 전남광주 27, 서울 25, 경북 22, 경남 18, 강원 18, 부산 16, 충남 15, 전북 14,
  인천 11, 충북 11, 대구 9, 대전 5, 울산 5, 제주 2, 세종 1.
- Simplification moves borders by up to tens of meters, so lookups very close to a border can pick the
  neighbor. Region detection code should tolerate this (not implemented yet).

# NOTICE

## Third-party data

- 행정구역 경계: 통계청 SGIS(공공누리 제1유형) 가공, vuski/admdongkor (CC BY 4.0)
  - Source: https://github.com/vuski/admdongkor (`ver20261001/HangJeongDong_ver20261001.geojson`)
  - Modified: 행정동 polygons dissolved into 230 si/gun/gu regions and simplified
    (see `design/README.md`). Used in `app/src/main/assets/regions/sgg230.geojson`
    and `design/sgg230_preview.geojson`.
- 고속도로 휴게소: 한국도로공사, 「전국휴게소정보표준데이터」 (공공데이터포털, 데이터 기준일 2026-09-17,
  이용허락범위 제한 없음)
  - Source: https://www.data.go.kr/data/15025446/standard.do (file data 「한국도로공사_휴게소정보」, CSV, 215 rows,
    downloaded without an API key)
  - Modified: only name, route number, route name, direction, latitude and longitude are kept, in
    `app/src/main/assets/restareas/rest_areas.csv`. Up and down sides of the same rest area are merged in code
    (`restarea/RestAreas.kt`).

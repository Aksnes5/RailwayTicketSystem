"""Build a small railway-first cartographic base from the supplied OSM China extract.

The route map deliberately does not use a generic web-map tile layer as its default.
This script retains only real major waterways and metropolitan labels, leaving the
application to render a calm, low-contrast transport base around them.
"""
import argparse
import json
import os
from build_railway_network import read_dbf, read_points, read_polylines, simplify, merge_group

# Exact national-scale waterways plus a few name variants used by OSM.
RIVER_NAMES = {
    "长江", "长江 - Chang Jiang", "金沙江 (长江 - Chang Jiang)", "黄河", "珠江", "淮河",
    "海河", "辽河", "松花江", "嫩江", "汉江", "湘江", "赣江", "闽江", "钱塘江",
    "京杭运河", "大运河", "澜沧江", "怒江", "雅鲁藏布江", "塔里木河", "伊犁河",
}

CITY_NAMES = {
    "北京", "天津", "上海", "重庆", "广州", "深圳", "武汉", "成都", "西安", "郑州",
    "南京", "杭州", "宁波", "合肥", "南昌", "福州", "厦门", "长沙", "贵阳", "昆明",
    "南宁", "济南", "青岛", "石家庄", "太原", "沈阳", "大连", "长春", "哈尔滨",
    "呼和浩特", "兰州", "西宁", "银川", "乌鲁木齐", "拉萨", "海口", "三亚",
}

# The free OSM point extract is inconsistent about whether a prefecture-level
# city is represented by a point, a polygon, or an administrative-name variant.
# Keep a compact vetted fallback list so the national transport base keeps
# meaningful anchors without inventing local railway stations.
CITY_FALLBACKS = {
    "北京": (116.4074, 39.9042), "天津": (117.2010, 39.0842), "上海": (121.4737, 31.2304),
    "重庆": (106.5516, 29.5630), "广州": (113.2644, 23.1291), "深圳": (114.0579, 22.5431),
    "武汉": (114.3054, 30.5931), "成都": (104.0665, 30.5723), "西安": (108.9398, 34.3416),
    "郑州": (113.6254, 34.7466), "南京": (118.7969, 32.0603), "杭州": (120.1551, 30.2741),
    "宁波": (121.5440, 29.8683), "合肥": (117.2272, 31.8206), "南昌": (115.8582, 28.6829),
    "福州": (119.2965, 26.0745), "厦门": (118.0894, 24.4798), "长沙": (112.9388, 28.2282),
    "贵阳": (106.6302, 26.6470), "昆明": (102.8329, 24.8801), "南宁": (108.3669, 22.8170),
    "济南": (116.9972, 36.6512), "青岛": (120.3826, 36.0671), "石家庄": (114.5149, 38.0428),
    "太原": (112.5489, 37.8706), "沈阳": (123.4315, 41.8057), "大连": (121.6147, 38.9140),
    "长春": (125.3235, 43.8171), "哈尔滨": (126.5350, 45.8038), "呼和浩特": (111.7492, 40.8426),
    "兰州": (103.8343, 36.0611), "西宁": (101.7782, 36.6171), "银川": (106.2309, 38.4872),
    "乌鲁木齐": (87.6168, 43.8256), "拉萨": (91.1322, 29.6604), "海口": (110.1983, 20.0440),
    "三亚": (109.5121, 18.2528),
}


# Compact provincial anchors for satellite imagery. Administrative borders are not
# bundled, so names are deliberately a readable overlay rather than a fake boundary.
PROVINCE_LABELS = [
    ("北京", 116.40, 39.90), ("天津", 117.20, 39.08), ("河北", 114.50, 38.04),
    ("山西", 112.55, 37.87), ("内蒙古", 111.75, 40.84), ("辽宁", 123.43, 41.81),
    ("吉林", 125.32, 43.82), ("黑龙江", 126.54, 45.80), ("上海", 121.47, 31.23),
    ("江苏", 118.80, 32.06), ("浙江", 120.16, 30.27), ("安徽", 117.23, 31.82),
    ("福建", 119.30, 26.07), ("江西", 115.86, 28.68), ("山东", 116.99, 36.65),
    ("河南", 113.63, 34.75), ("湖北", 114.31, 30.59), ("湖南", 112.94, 28.23),
    ("广东", 113.26, 23.13), ("广西", 108.37, 22.82), ("海南", 110.20, 20.04),
    ("重庆", 106.55, 29.56), ("四川", 104.07, 30.57), ("贵州", 106.63, 26.65),
    ("云南", 102.83, 24.88), ("西藏", 91.13, 29.66), ("陕西", 108.94, 34.34),
    ("甘肃", 103.83, 36.06), ("青海", 101.78, 36.62), ("宁夏", 106.23, 38.49),
    ("新疆", 87.62, 43.83), ("香港", 114.17, 22.32), ("澳门", 113.54, 22.20),
    ("台湾", 121.57, 25.04),
]
def is_major_river(name):
    return name in RIVER_NAMES or name.startswith("长江") or name.startswith("黄河")


def build(source_root):
    water = os.path.join(source_root, "gis_osm_waterways_free_1")
    places = os.path.join(source_root, "gis_osm_places_free_1")
    water_columns = read_dbf(water + ".dbf", ["name", "fclass"])
    river_segments = {}
    for index, segment in read_polylines(water + ".shp"):
        if index >= len(water_columns["name"]):
            continue
        name = water_columns["name"][index].strip()
        kind = water_columns["fclass"][index]
        if kind not in {"river", "canal"} or not is_major_river(name):
            continue
        river_segments.setdefault(name, []).append(segment)

    rivers = []
    for name, segments in sorted(river_segments.items()):
        for chain in merge_group(segments):
            line = simplify(chain, 0.006)
            if len(line) >= 2:
                rivers.append({"name": name, "points": [[round(x, 4), round(y, 4)] for x, y in line]})

    place_columns = read_dbf(places + ".dbf", ["name", "fclass"])
    cities = []
    counties = []
    city_seen = set()
    county_seen = set()
    for index, (longitude, latitude) in read_points(places + ".shp"):
        if index >= len(place_columns["name"]):
            continue
        raw_name = place_columns["name"][index].strip()
        kind = place_columns["fclass"][index]
        # Prefer the first local-language form of multilingual OSM place names.
        name = raw_name.split(" /")[0].strip()
        if not name or len(name) > 18:
            continue
        record = {"name": name, "longitude": round(longitude, 5), "latitude": round(latitude, 5)}
        if kind in {"city", "national_capital"} and name not in city_seen:
            city_seen.add(name)
            cities.append(record)
        elif (kind == "county" or (kind == "suburb" and name.endswith(("区", "县", "旗")))) and name not in county_seen:
            county_seen.add(name)
            counties.append(record)

    # The free point export omits or renames some provincial capitals; retain
    # compact vetted anchors for readable nationwide satellite labels.
    for name, (longitude, latitude) in CITY_FALLBACKS.items():
        if name not in city_seen:
            cities.append({"name": name, "longitude": longitude, "latitude": latitude})
    cities.sort(key=lambda city: city["name"])
    counties.sort(key=lambda county: county["name"])
    provinces = [{"name": name, "longitude": longitude, "latitude": latitude}
                 for name, longitude, latitude in PROVINCE_LABELS]
    return {"rivers": rivers, "cities": cities, "counties": counties, "provinces": provinces}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source_root", help="directory containing the Geofabrik OSM shapefiles")
    parser.add_argument("-o", "--output", default="app/src/main/assets/railway_map_base.js")
    args = parser.parse_args()
    data = build(args.source_root)
    with open(args.output, "w", encoding="utf-8") as fh:
        fh.write("window.railwayMapBase = ")
        json.dump(data, fh, ensure_ascii=False, separators=(",", ":"))
        fh.write(";\n")
    print("wrote %d river chains and %d city labels to %s" % (len(data["rivers"]), len(data["cities"]), args.output))


if __name__ == "__main__":
    main()
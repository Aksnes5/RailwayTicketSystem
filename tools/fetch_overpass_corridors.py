"""Fetch rail alignment + station nodes for corridors the 2024 extract does not cover.

The Geofabrik China extract is frozen at 2024-01-01, so corridors opened since then
(or incompletely tagged at the extract cut-off) can lack alignment and station data.
This pulls one tightly scoped corridor at a time from live OpenStreetMap via Overpass,
writing a file per corridor that `build_railway_network.py --extra` can consume.

Usage:
    python tools/fetch_overpass_corridors.py [--only 广湛高铁] [--out .geodata]

NOTE: this machine's shell exports a proxy that is not running, so requests are made
with proxies explicitly disabled. Direct access works.
"""
import argparse
import json
import os
import time
import urllib.parse
import urllib.request

ENDPOINTS = (
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
)

# (key, OSM name pattern, (south, west, north, east), include unnamed construction)
#
# Newer corridors are sometimes mapped before their final route name is added, so those
# retain the construction fallback. For established lines that were merely missing from
# the frozen extract, querying unnamed construction would pull unrelated projects in the
# same province; those use exact-name queries only.
CORRIDORS = [
    ("shenbai", "沈白|沈佳", (41.0, 123.0, 42.5, 127.0), True),
    ("guangzhan", "广湛", (21.0, 110.1, 23.4, 113.4), True),
    ("yuxia", "渝厦|渝湘|渝黔", (28.4, 106.4, 29.9, 109.1), True),
    ("hangwen", "杭温", (28.0, 119.5, 30.3, 120.8), True),
    ("meilong", "梅龙", (23.8, 115.5, 24.5, 116.3), True),
    ("chihuang", "池黄", (29.9, 117.4, 30.6, 118.4), True),
    ("xuanji", "宣绩", (30.3, 118.4, 31.0, 119.2), True),
    ("xiangjing", "襄荆", (30.8, 111.8, 32.2, 112.7), True),
    ("wuyi", "武宜|沪渝蓉|沿江", (30.0, 110.8, 31.5, 114.8), True),

    # Established passenger corridors absent or incompletely named in the local extract.
    ("nanyu", "南玉|南宁至玉林", (22.1, 108.5, 23.4, 110.5), True),
    ("chengziyi", "成自宜|成宜|自宜", (28.4, 103.0, 31.1, 105.5), False),
    ("changjinghuang", "昌景黄|昌景|景黄|南昌至黄山", (28.8, 116.3, 30.7, 119.3), False),
    ("lanzhang", "兰张|兰州至张掖", (35.0, 100.5, 37.2, 104.3), False),
    ("lixiang", "丽香|丽江至香格里拉|香格里拉至丽江", (26.8, 99.5, 27.8, 100.7), False),
    ("dazhang", "大张|大同至张家口", (39.5, 113.0, 41.1, 115.6), False),
    ("hean", "合安|合肥至安庆", (30.0, 116.8, 32.2, 118.1), False),
    ("huanghuang", "黄黄|黄冈至黄梅", (29.7, 114.6, 30.2, 116.2), False),
    ("qianzhangchang", "黔张常|黔江至张家界至常德", (28.7, 108.0, 30.9, 112.4), False),
    ("huaishaoheng", "怀邵衡|怀化至衡阳", (26.5, 109.5, 28.6, 113.3), False),
    ("heyari", "菏兖日|荷兖日", (34.2, 115.0, 36.5, 119.2), False),
    ("qingfu", "青阜|青龙山至阜阳", (32.0, 115.4, 34.1, 117.8), False),
]

QUERY = """[out:json][timeout:180];
(
  way["railway"~"rail|construction|proposed"]["name"~"%s"](%s);
  way["railway"~"construction|proposed"](%s);
  node["railway"~"station|halt"](%s);
);
out geom;"""

NAMED_QUERY = """[out:json][timeout:180];
(
  way["railway"~"rail|construction|proposed"]["name"~"%s"](%s);
  node["railway"~"station|halt"](%s);
);
out geom;"""


def fetch(pattern, box, include_unnamed_construction):
    south, west, north, east = box
    bounds = "%s,%s,%s,%s" % (south, west, north, east)
    if include_unnamed_construction:
        query = QUERY % (pattern, bounds, bounds, bounds)
    else:
        query = NAMED_QUERY % (pattern, bounds, bounds)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    errors = []
    for endpoint in ENDPOINTS:
        request = urllib.request.Request(
            endpoint,
            data=urllib.parse.urlencode({"data": query}).encode(),
            headers={"User-Agent": "railway-ticket-app/1.0 (corridor patch)"},
        )
        try:
            with opener.open(request, timeout=240) as response:
                return json.loads(response.read().decode("utf-8"))
        except Exception as error:  # noqa: BLE001 - try a public mirror first
            errors.append("%s: %s" % (endpoint, error))
    raise RuntimeError("; ".join(errors))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=".geodata")
    parser.add_argument("--only", default=None, help="comma-separated corridor keys")
    parser.add_argument("--force", action="store_true", help="refresh an existing corridor patch")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    for key, pattern, box, include_unnamed_construction in CORRIDORS:
        if args.only and key not in {value.strip() for value in args.only.split(",")}:
            continue
        target = os.path.join(args.out, key + ".json")
        if os.path.exists(target) and not args.force:
            print("skip %-16s (already fetched)" % key)
            continue
        try:
            document = fetch(pattern, box, include_unnamed_construction)
        except Exception as error:  # noqa: BLE001 - report and continue
            print("FAIL %-16s %s" % (key, error))
            continue
        elements = document.get("elements", [])
        ways = sum(1 for element in elements if element.get("type") == "way")
        nodes = sum(1 for element in elements if element.get("type") == "node")
        with open(target, "w", encoding="utf-8") as fh:
            json.dump(document, fh, ensure_ascii=False, separators=(",", ":"))
        size_kb = os.path.getsize(target) / 1024
        print("ok   %-16s %5d ways %5d stations %7.0f KB" % (key, ways, nodes, size_kb))
        time.sleep(2)


if __name__ == "__main__":
    main()
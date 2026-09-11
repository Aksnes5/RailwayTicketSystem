"""Fetch rail alignment + station nodes for corridors the 2024 extract does not cover.

The Geofabrik China extract is frozen at 2024-01-01, so corridors opened since then
(沈白, 广湛, 杭温, 梅龙, 池黄, 襄荆, 武宜 …) have neither geometry nor stations in it.
This pulls each one from the live OpenStreetMap via Overpass, writing a file per
corridor that `build_railway_network.py --extra` can consume.

Each query asks for three things:
  * ways whose name matches the corridor's OSM naming,
  * any way still tagged construction/proposed inside the box (new lines are often
    mapped before they open, and not always under the final name),
  * station/halt nodes, which is what supplies coordinates for stations that postdate
    the extract.

Usage:
    python tools/fetch_overpass_corridors.py [--only 广湛高铁] [--out .geodata]

NOTE: this machine's shell exports a proxy that is not running, so the request is made
with proxies explicitly disabled.  Direct access works.
"""
import argparse
import json
import os
import time
import urllib.parse
import urllib.request

ENDPOINT = "https://overpass-api.de/api/interpreter"

# (key, osm name pattern, (south, west, north, east))
CORRIDORS = [
    ("shenbai",   "沈白|沈佳",           (41.0, 123.0, 42.5, 127.0)),
    ("guangzhan", "广湛",                (21.0, 110.1, 23.4, 113.4)),
    ("yuxia",     "渝厦|渝湘|渝黔",       (28.4, 106.4, 29.9, 109.1)),
    ("hangwen",   "杭温",                (28.0, 119.5, 30.3, 120.8)),
    ("meilong",   "梅龙",                (23.8, 115.5, 24.5, 116.3)),
    ("chihuang",  "池黄",                (29.9, 117.4, 30.6, 118.4)),
    ("xuanji",    "宣绩",                (30.3, 118.4, 31.0, 119.2)),
    ("xiangjing", "襄荆",                (30.8, 111.8, 32.2, 112.7)),
    ("wuyi",      "武宜|沪渝蓉|沿江",     (30.0, 110.8, 31.5, 114.8)),
]

QUERY = """[out:json][timeout:180];
(
  way["railway"~"rail|construction|proposed"]["name"~"%s"](%s);
  way["railway"~"construction|proposed"](%s);
  node["railway"~"station|halt"](%s);
);
out geom;"""


def fetch(pattern, box):
    south, west, north, east = box
    bounds = "%s,%s,%s,%s" % (south, west, north, east)
    query = QUERY % (pattern, bounds, bounds, bounds)
    request = urllib.request.Request(
        ENDPOINT,
        data=urllib.parse.urlencode({"data": query}).encode(),
        headers={"User-Agent": "railway-ticket-app/1.0 (corridor patch)"},
    )
    # The shell exports a proxy that is not listening; go direct.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(request, timeout=240) as response:
        return json.loads(response.read().decode("utf-8"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=".geodata")
    parser.add_argument("--only", default=None, help="substring of the corridor key")
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)

    for key, pattern, box in CORRIDORS:
        if args.only and args.only not in key:
            continue
        target = os.path.join(args.out, key + ".json")
        if os.path.exists(target):
            print("skip %-10s (already fetched)" % key)
            continue
        try:
            document = fetch(pattern, box)
        except Exception as error:                      # noqa: BLE001 - report and continue
            print("FAIL %-10s %s" % (key, error))
            continue
        elements = document.get("elements", [])
        ways = sum(1 for e in elements if e.get("type") == "way")
        nodes = sum(1 for e in elements if e.get("type") == "node")
        with open(target, "w", encoding="utf-8") as fh:
            json.dump(document, fh, ensure_ascii=False, separators=(",", ":"))
        size_kb = os.path.getsize(target) / 1024
        print("ok   %-10s %5d ways %5d stations  %7.0f KB" % (key, ways, nodes, size_kb))
        time.sleep(2)   # be polite to the public Overpass instance


if __name__ == "__main__":
    main()

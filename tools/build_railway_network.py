"""Build app/src/main/assets/railway_network.json from an OpenStreetMap railway extract.

Why this exists
---------------
The journey map used to join consecutive stations with straight lines, which only
approximates the real alignment.  Bundling the whole country is not viable: the
Geofabrik extract holds ~315k `rail` segments / 2.1M vertices for China, and because
ways are split at every node, simplification barely shrinks it (13-15 MB as GeoJSON).
Selecting just the line names the app's corridors use cuts that to ~25k segments /
~109k points, which fits an APK asset.

Input
-----
`gis_osm_railways_free_1.shp` + `.dbf` from a Geofabrik China extract, e.g.
https://download.geofabrik.de/asia/free/china-latest-free.shp.zip
(the China extract has been frozen at 2024-01-01, so lines opened after that are
absent — those corridors fall back to straight lines at runtime).

Usage
-----
    python tools/build_railway_network.py <path-to-shp-without-extension> [-o out.json]

Pure standard library: no fiona/shapely/pyshp required.
"""
import argparse
import json
import math
import struct
import sys

# OSM names the app's 29 corridors map onto.  OSM does not always use the official
# short name (厦深铁路 is tagged 杭深线, 安九高铁 is part of 京港高速线), so each
# corridor carries the substrings that actually appear in the extract.
CORRIDOR_ALIASES = {
    "宁蓉铁路": ["沪蓉"],
    "京港高铁": ["京港高速", "商合杭"],
    "宣绩高铁": ["宣绩"],
    "杭昌高铁": ["杭昌"],
    "宁安高铁": ["宁安客专", "宁安客运专线"],
    "安九高铁": ["京港高速"],
    "昌福铁路": ["昌福"],
    "厦深铁路": ["杭深"],
    "沪宁城际铁路": ["沪宁城际"],
    "武广高铁": ["京广高速"],
    "京沪高铁": ["京沪高铁", "京沪高速铁路"],
    "成渝高铁": ["成渝高速", "成渝客专"],
    "广深港高铁": ["广深港", "西九龙", "香港段"],
    "京广高铁": ["京广高速"],
    "石济客专": ["石济"],
    "郑渝高铁": ["郑万"],
    "渝万城际铁路": ["渝万"],
    "津秦高铁": ["津秦"],
    # In the 2024 OSM extract, almost the whole Qinhuangdao—Shenyang passenger
    # corridor is named 京哈线 / 沈山线; the literal 秦沈客运专线 name only occurs
    # in the Shenyang approach.  Include the operational names so the northeast
    # corridor is a continuous alignment rather than falling back to a chord.
    "秦沈客专": ["秦沈", "沈山线", "京哈线"],
    "京哈高铁京沈段": ["京哈高速", "京沈", "朝凌"],
    "沪昆高铁": ["沪昆高速"],
    # 哈大高铁 is tagged as 京哈高速线 (北京—哈尔滨) plus 沈大 for the southern half;
    # the literal name 哈大 only exists on a few depot sidings.
    "哈大高铁": ["哈大", "沈大", "京哈高速"],
    "西成高铁": ["西成"],
    "兰新高铁": ["兰新客专"],
    "郑西高铁": ["郑西客专"],
    "合福高铁": ["合福高速"],
    # The app's 贵广 corridor is one long chain 贵阳北—桂林—南宁—百色—兴义—广州南,
    # so it also needs the 柳南 and 南昆 alignments, not just 贵广 proper.
    "贵广高铁": ["贵广", "南昆", "柳南", "湘桂", "南广"],
    "武宜高铁": ["武宜", "沪渝蓉"],
    "武九客专": ["武九"],
    "昌九城际": ["昌九城际"],
    "汉十高铁": ["汉十", "武西高速"],
    "徐兰高铁": ["徐兰", "郑徐", "宝兰"],
    "襄荆高铁": ["襄荆"],
    "武汉枢纽连接线": ["汉口联络", "武昌南环"],
    "南昌枢纽联络线": ["南昌枢纽", "南昌西联络"],
    # Corridors from LatestRailwayNetwork that opened after the extract's 2024-01-01 cut-off.
    # None of these exist in the extract at all, so they need an --extra patch; the aliases
    # are here so a patched export files itself under the right corridor.
    "沈白高铁": ["沈白", "沈佳"],
    "广湛高铁": ["广湛"],
    "渝厦高铁重庆东—黔江段": ["渝厦", "渝湘", "渝黔"],
    "杭温高铁": ["杭温"],
    # OSM carries the Meizhou—Longchuan section as 龙龙高速线, and the neighbouring
    # 瑞金—梅州 line as 瑞梅铁路; the literal 梅龙 name is not used.
    "梅龙高铁": ["梅龙", "龙龙高速", "瑞梅"],
    "池黄高铁": ["池黄"],
}

# NationalPassengerRailCatalog and the conventional graph already expose these
# passenger corridors to search. They must be selected from the supplied OSM
# extract too; otherwise their detail maps degrade to city-to-city chords.
# Keys mirror routeName exactly so PhysicalRailCorridorResolver can prefer the
# same named geometry. Values are the OSM operational-name substrings.
NATIONAL_CORRIDOR_ALIASES = {
    # High-speed and intercity passenger corridors.
    "京津城际铁路": ["京津城际"], "京张高铁": ["京张", "京包客专"], "京雄城际铁路": ["京雄"],
    "京唐城际铁路": ["京唐"], "津兴城际铁路": ["津兴"], "大张高铁": ["大张"],
    "石太客专": ["石太"], "大西高铁": ["大西"], "郑太高铁": ["郑太"],
    "郑济高铁": ["郑济", "济郑高速", "济郑高铁"], "郑阜高铁": ["郑阜"], "商杭高铁": ["商合杭", "商杭"],
    "合蚌高铁": ["合蚌"], "合安高铁": ["合安"], "宁杭高铁": ["宁杭"],
    "杭甬高铁": ["杭甬"], "甬台温铁路": ["甬台温", "杭深线"], "温福铁路": ["温福", "杭深线"],
    "福厦高铁": ["福厦"], "金温铁路": ["金温"], "金台铁路": ["金台"],
    "衢宁铁路": ["衢宁"], "昌景黄高铁": ["昌景黄"], "赣瑞龙铁路": ["赣瑞龙"],
    "南龙铁路": ["南龙"], "济青高铁": ["济青"], "青荣城际铁路": ["青荣"],
    "潍莱高铁": ["潍莱"], "日兰高铁": ["日兰"], "青盐铁路": ["青盐"],
    "连镇高铁": ["连镇"], "徐盐高铁": ["徐盐"], "盐通高铁": ["盐通"],
    "沪苏通铁路": ["沪苏通"], "沪苏湖高铁": ["沪苏湖"], "南沿江城际铁路": ["南沿江"],
    "宁启铁路": ["宁启"], "张吉怀高铁": ["张吉怀"], "黔张常铁路": ["黔张常"],
    "怀邵衡铁路": ["怀邵衡"], "长株潭城际铁路": ["长株潭"], "荆荆高铁": ["荆荆"],
    "黄黄高铁": ["黄黄"], "郑开城际铁路": ["郑开"], "武咸城际铁路": ["武咸"],
    "广汕高铁": ["广汕"], "汕汕高铁": ["汕汕"], "梅汕铁路": ["梅汕"],
    "深湛铁路江茂段": ["深湛", "江茂"], "南广铁路": ["南广"], "贵南高铁": ["贵南"],
    "南凭高铁": ["南凭"], "南玉高铁": ["南玉"], "渝贵铁路": ["渝贵"],
    "成贵高铁": ["成贵"], "成绵乐客专": ["成绵乐"], "成自宜高铁": ["成自宜"],
    "川南城际铁路": ["川南城际"], "成灌铁路": ["成灌"], "银西高铁": ["银西"],
    "兰张高铁": ["兰张"], "兰中城际铁路": ["兰中", "中川线", "中川铁路"], "川青铁路": ["川青"],
    "丽香铁路": ["丽香"], "楚大铁路": ["楚大"], "弥蒙高铁": ["弥蒙"],
    "哈齐高铁": ["哈齐"], "哈牡高铁": ["哈牡"], "牡佳客专": ["牡佳"],
    "长珲城际铁路": ["长珲"], "沈丹客专": ["沈丹"], "盘营高铁": ["盘营"],
    "丹大快速铁路": ["丹大"],

    # Conventional passenger trunk and regional railways.
    "京广铁路": ["京广线"], "京沪铁路": ["京沪线"], "沪昆铁路": ["沪昆线"],
    "陇海铁路": ["陇海线"], "京包铁路": ["京包线"], "包兰铁路": ["包兰线"],
    "沈山铁路": ["沈山线"], "京通铁路": ["京通线"], "滨洲铁路": ["滨洲线"],
    "滨绥铁路": ["滨绥线"], "图佳铁路": ["图佳线"], "长图铁路": ["长图线", "长图铁路"],
    "梅集铁路": ["梅集线", "梅集铁路"], "胶济铁路": ["胶济线"], "蓝烟铁路": ["蓝烟线"],
    "菏兖日铁路": ["菏兖日"], "石德铁路": ["石德线"], "邯长铁路": ["邯长线"],
    "太焦铁路": ["太焦线"], "侯月铁路": ["侯月线"], "宁西铁路": ["宁西线"],
    "皖赣铁路": ["皖赣线"], "宣杭铁路": ["宣杭线"], "鹰厦铁路": ["鹰厦线"],
    "漳龙铁路": ["漳龙线"], "赣龙铁路": ["赣龙线"], "合九铁路": ["合九线"],
    "青阜铁路": ["青阜线"], "洛湛铁路": ["洛湛线"], "益湛铁路": ["益湛线", "益湛铁路"],
    "湘桂铁路": ["湘桂线"], "黔桂铁路": ["黔桂线", "黔桂铁路"], "南昆铁路": ["南昆线"],
    "贵昆铁路": ["贵昆线"], "川黔铁路": ["川黔线"], "渝怀铁路": ["渝怀线"],
    "宝成铁路": ["宝成线"], "成昆铁路": ["成昆线"], "阳安铁路": ["阳安线"],
    "达成铁路": ["达成线"], "内六铁路": ["内六线", "内六铁路"], "兰青铁路": ["兰青线"],
    "干武铁路": ["干武线"], "临哈铁路": ["临哈线"], "南疆铁路": ["南疆线"],
    "喀和铁路": ["喀和线", "喀什至和田铁路"],
}

# Route names alone are not a transport-type signal: several high-speed
# corridors are officially named “铁路”. Keep this list explicit.
CONVENTIONAL_CORRIDORS = {
    "京广铁路", "京沪铁路", "沪昆铁路", "陇海铁路", "京包铁路", "包兰铁路",
    "沈山铁路", "京通铁路", "滨洲铁路", "滨绥铁路", "图佳铁路", "长图铁路",
    "梅集铁路", "胶济铁路", "蓝烟铁路", "菏兖日铁路", "石德铁路", "邯长铁路",
    "太焦铁路", "侯月铁路", "宁西铁路", "皖赣铁路", "宣杭铁路", "鹰厦铁路",
    "漳龙铁路", "赣龙铁路", "合九铁路", "青阜铁路", "洛湛铁路", "益湛铁路",
    "湘桂铁路", "黔桂铁路", "南昆铁路", "贵昆铁路", "川黔铁路", "渝怀铁路",
    "宝成铁路", "成昆铁路", "阳安铁路", "达成铁路", "内六铁路", "兰青铁路",
    "干武铁路", "临哈铁路", "南疆铁路", "喀和铁路",
}

CORRIDOR_ALIASES.update(NATIONAL_CORRIDOR_ALIASES)
TOLERANCE_DEG = 0.0002   # ~22 m; the bundle stays well under 1 MB even at this fidelity
ROUND_DP = 5             # endpoint key precision for chain merging
MAX_ALTITUDE = 3000      # sanity bound on latitude
THIN_CORRIDOR_SEGMENTS = 60  # below this a corridor is treated as effectively un-mapped


def read_dbf(path, wanted):
    """Return {field: [values]} for the requested DBF columns."""
    with open(path, "rb") as fh:
        data = fh.read()
    num_records, header_len, record_len = struct.unpack_from("<IHH", data, 4)
    fields, pos = [], 32
    while data[pos] != 0x0D:
        fields.append((data[pos:pos + 11].split(b"\x00")[0].decode("ascii", "ignore"),
                       chr(data[pos + 11]), data[pos + 16]))
        pos += 32
    columns = {name: [] for name in wanted}
    offsets = {}
    for name in wanted:
        i = next((k for k, (nm, _, _) in enumerate(fields) if nm == name), None)
        if i is None:
            raise SystemExit("DBF has no field %r" % name)
        offsets[name] = 1 + sum(fields[k][2] for k in range(i))
    pos = header_len
    for _ in range(num_records):
        rec = data[pos:pos + record_len]
        for name in wanted:
            raw = rec[offsets[name]:offsets[name] + next(f[2] for f in fields if f[0] == name)]
            columns[name].append(raw.decode("utf-8", "ignore").strip())
        pos += record_len
    return columns


def read_polylines(path):
    """Yield (record_index, [(lon, lat), ...]) for every PolyLine record."""
    with open(path, "rb") as fh:
        fh.seek(100)
        index = 0
        while True:
            head = fh.read(8)
            if len(head) < 8:
                break
            _, content_len = struct.unpack(">ii", head)
            content = fh.read(content_len * 2)
            if len(content) < content_len * 2:
                break
            if struct.unpack_from("<i", content, 0)[0] == 3:
                num_parts, num_points = struct.unpack_from("<ii", content, 36)
                starts = list(struct.unpack_from("<%di" % num_parts, content, 44)) + [num_points]
                base = 44 + 4 * num_parts
                pts = struct.unpack_from("<%dd" % (num_points * 2), content, base)
                for part in range(num_parts):
                    seg = [(pts[2 * j], pts[2 * j + 1]) for j in range(starts[part], starts[part + 1])]
                    if len(seg) >= 2:
                        yield index, seg
            index += 1


def read_points(path):
    """Yield (record_index, (lon, lat)) for Point records in an OSM shapefile."""
    with open(path, "rb") as fh:
        fh.seek(100)
        index = 0
        while True:
            head = fh.read(8)
            if len(head) < 8:
                break
            _, content_len = struct.unpack(">ii", head)
            content = fh.read(content_len * 2)
            if len(content) < content_len * 2:
                break
            if len(content) >= 20 and struct.unpack_from("<i", content, 0)[0] == 1:
                yield index, struct.unpack_from("<dd", content, 4)
            index += 1


def station_points(railway_shapefile):
    """Read exact railway station pins from the sibling OSM transport layer.

    The railways layer has the physical alignment, while `transport_free_1` has
    the named station points.  Keeping the two together is what lets the map
    snap 汉川/孝感东等中间站 to the track rather than to their municipal centres.
    """
    import os
    transport = os.path.join(os.path.dirname(railway_shapefile), "gis_osm_transport_free_1")
    if not (os.path.exists(transport + ".shp") and os.path.exists(transport + ".dbf")):
        print("station point layer not found; using app catalog only")
        return {}
    columns = read_dbf(transport + ".dbf", ["name", "fclass"])
    points = {}
    for index, (lon, lat) in read_points(transport + ".shp"):
        if index >= len(columns["name"]) or columns["fclass"][index] not in {"railway_station", "railway_halt"}:
            continue
        name = columns["name"][index].strip()
        if not name or not (-90 <= lat <= 90 and -180 <= lon <= 180):
            continue
        # The app stores station names without the optional final "站", while OSM
        # has both conventions.  Export both spellings; retain the first point for
        # duplicated names so we never silently move a pin on rebuild.
        for key_name in {name, name[:-1] if name.endswith("站") else name}:
            points.setdefault(key_name, [round(lon, 6), round(lat, 6)])
    print("loaded %d railway station points" % len(points))
    return points


def read_extra_layers(path):
    """Read a supplementary export into (lines, stations).

    The Geofabrik China extract is frozen at 2024-01-01, so a corridor that opened after
    that date has neither an alignment nor its stations in the extract — both have to come
    from a current source.  Rather than re-downloading a country-sized file, one small
    Overpass query per corridor is layered in.  Both Overpass "out geom" JSON and GeoJSON
    are accepted, because overpass-turbo offers either.

    lines:    [(line_name, [(lon, lat), ...]), ...]
    stations: {station_name: [lon, lat]}
    """
    with open(path, encoding="utf-8") as fh:
        document = json.load(fh)
    lines = []
    stations = {}

    def add_line(name, coordinates):
        points = [(float(x), float(y)) for x, y in coordinates]
        if len(points) >= 2:
            lines.append((name, points))

    def add_station(name, longitude, latitude):
        if not name or not (-90 <= latitude <= 90 and -180 <= longitude <= 180):
            return
        # The app stores station names without the optional final "站"; keep both spellings.
        for key in {name, name[:-1] if name.endswith("站") else name}:
            stations.setdefault(key, [round(longitude, 6), round(latitude, 6)])

    if document.get("type") == "FeatureCollection":
        for feature in document.get("features", []):
            properties = feature.get("properties") or {}
            name = properties.get("name") or properties.get("ref") or ""
            geometry = feature.get("geometry") or {}
            kind = geometry.get("type")
            if kind == "LineString":
                add_line(name, geometry.get("coordinates") or [])
            elif kind == "MultiLineString":
                for part in geometry.get("coordinates") or []:
                    add_line(name, part)
            elif kind == "Point":
                coordinates = geometry.get("coordinates") or []
                if len(coordinates) >= 2:
                    add_station(name, float(coordinates[0]), float(coordinates[1]))
    else:
        for element in document.get("elements", []):
            tags = element.get("tags") or {}
            name = tags.get("name", "")
            kind = element.get("type")
            if kind == "node" and element.get("lat") is not None:
                # Keep anything the query tagged as a station, plus named rail features.
                if tags.get("railway") in {"station", "halt"} or tags.get("public_transport") == "station":
                    add_station(name, element["lon"], element["lat"])
                continue
            add_line(name, [(node["lon"], node["lat"]) for node in element.get("geometry") or []
                            if "lon" in node and "lat" in node])
    return lines, stations


def perp_distance(p, a, b):
    if a == b:
        return math.hypot(p[0] - a[0], p[1] - a[1])
    dx, dy = b[0] - a[0], b[1] - a[1]
    t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy)
    t = max(0.0, min(1.0, t))
    return math.hypot(p[0] - a[0] - t * dx, p[1] - a[1] - t * dy)


def simplify(points, tolerance):
    """Iterative Douglas-Peucker (recursion would blow the stack on long chains)."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        lo, hi = stack.pop()
        a, b = points[lo], points[hi]
        worst, worst_at = tolerance, -1
        for i in range(lo + 1, hi):
            d = perp_distance(points[i], a, b)
            if d > worst:
                worst, worst_at = d, i
        if worst_at > 0:
            keep[worst_at] = True
            stack.append((lo, worst_at))
            stack.append((worst_at, hi))
    return [p for i, p in enumerate(points) if keep[i]]


def key(point):
    return (round(point[0], ROUND_DP), round(point[1], ROUND_DP))


def merge_group(segments):
    """Join same-named segments that share endpoints into continuous lines.

    The extract splits a railway at every node, so one line arrives as hundreds of two- and
    three-point pieces.  Merging is done *within a single OSM line name*, which keeps
    connecting tracks out of the result: a junction line carries its own name, so it can
    never be spliced into the main line and break it in half.
    """
    from collections import defaultdict
    incidents = defaultdict(list)
    for idx, seg in enumerate(segments):
        incidents[key(seg[0])].append((idx, 0))
        incidents[key(seg[-1])].append((idx, 1))

    used = [False] * len(segments)
    chains = []

    def oriented(seg_idx, entry_end):
        pieces = segments[seg_idx]
        return list(pieces) if entry_end == 0 else list(reversed(pieces))

    def walk(seg_idx, entry_end):
        points = oriented(seg_idx, entry_end)
        used[seg_idx] = True
        while True:
            tail = key(points[-1])
            nxt = None
            for candidate, candidate_end in incidents.get(tail, ()):
                if not used[candidate]:
                    nxt = (candidate, candidate_end)
                    break
            if nxt is None:
                return points
            seg_idx, entry_end = nxt
            piece = oriented(seg_idx, entry_end)
            used[seg_idx] = True
            points.extend(piece[1:])

    # Start at loose ends first so a chain spans a whole line, then sweep leftovers.
    for idx, seg in enumerate(segments):
        if used[idx]:
            continue
        for node, entry_end in ((key(seg[0]), 0), (key(seg[-1]), 1)):
            if len(incidents[node]) == 1:
                chains.append(walk(idx, entry_end))
                break
    for idx in range(len(segments)):
        if not used[idx]:
            chains.append(walk(idx, 0))
    return chains


def merge_into_chains(named_segments):
    """Merge every app corridor / OSM name pair and retain both on its chains.

    Keeping the source name is important at runtime: a national bundle contains
    many corridors that cross or run close together at hubs.  The map must be
    able to prefer a path on one named railway instead of treating every close
    parallel track as a free interchange.
    """
    from collections import defaultdict
    by_name = defaultdict(list)
    for corridor, name, seg in named_segments:
        by_name[(corridor, name)].append(seg)
    chains = []
    for (corridor, name), group in by_name.items():
        chains.extend((corridor, name, chain) for chain in merge_group(group))
    return chains


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("shapefile", help="path to gis_osm_railways_free_1 without extension")
    parser.add_argument("-o", "--output", default="app/src/main/assets/railway_network.js")
    parser.add_argument("-e", "--extra", action="append", default=[], metavar="PATH",
                        help="supplementary Overpass/GeoJSON export(s) layered over the extract; "
                             "use this to patch a corridor opened after the extract's cut-off date")
    args = parser.parse_args()

    print("reading %s.dbf ..." % args.shapefile)
    columns = read_dbf(args.shapefile + ".dbf", ["name", "fclass"])
    names, classes = columns["name"], columns["fclass"]

    wanted = {name for aliases in CORRIDOR_ALIASES.values() for name in aliases}
    print("corridor aliases: %d" % len(wanted))

    selected = []
    hit_names = {}
    for index, seg in read_polylines(args.shapefile + ".shp"):
        if index >= len(names) or classes[index] != "rail":
            continue
        line_name = names[index]
        if not line_name:
            continue
        matched_corridors = [
            corridor for corridor, aliases in CORRIDOR_ALIASES.items()
            if any(alias in line_name for alias in aliases)
        ]
        for corridor in matched_corridors:
            selected.append((corridor, line_name, seg))
        for alias in wanted:
            if alias in line_name:
                hit_names.setdefault(alias, set()).add(line_name)

    extra_stations = {}
    for path in args.extra:
        lines, stations = read_extra_layers(path)
        for name, segment in lines:
            corridors = [corridor for corridor, aliases in CORRIDOR_ALIASES.items()
                         if any(alias in name for alias in aliases)]
            if not corridors:
                # Unnamed or unrecognised geometry still belongs in the bundle; give each
                # segment its own group so it cannot be merged with an unrelated line.
                selected.append(("extra:%d" % len(selected), name, segment))
                continue
            for corridor in corridors:
                selected.append((corridor, name, segment))
        extra_stations.update(stations)
        print("  + %d segments, %d stations from %s" % (len(lines), len(stations), path))

    raw_points = sum(len(segment) for _, _, segment in selected)
    print("selected %d segments / %d points" % (len(selected), raw_points))

    # Surface gaps instead of letting them degrade silently into straight chords.  Counting
    # segments, not just "did anything match", matters: 武宜高铁's alias also matches the
    # Jiangsu section of 沪渝蓉, so a boolean check would call a corridor covered when its
    # actual alignment is still absent.
    counts = {}
    for corridor, _, _ in selected:
        counts[corridor] = counts.get(corridor, 0) + 1
    thin = sorted((c for c in CORRIDOR_ALIASES if counts.get(c, 0) < THIN_CORRIDOR_SEGMENTS),
                  key=lambda c: counts.get(c, 0))
    print("corridors with little or no geometry (straight-line fallback):")
    for corridor in thin:
        print("   %-14s %5d segments" % (corridor, counts.get(corridor, 0)))
    if thin:
        print("   patch with: --extra <overpass-export.json>")
    for alias in sorted(hit_names):
        print("   %-12s %s" % (alias, " / ".join(sorted(hit_names[alias])[:3])))

    print("merging into chains ...")
    chains = merge_into_chains(selected)
    print("   %d chains" % len(chains))

    print("simplifying (tolerance %s deg) ..." % TOLERANCE_DEG)
    simplified = [(corridor, name, simplify(chain, TOLERANCE_DEG)) for corridor, name, chain in chains]
    simplified = [(corridor, name, chain) for corridor, name, chain in simplified if len(chain) >= 2]
    kept_points = sum(len(chain) for _, _, chain in simplified)

    features = [
        {"type": "Feature", "properties": {"name": name, "corridor": corridor, "network": ("CONVENTIONAL" if corridor in CONVENTIONAL_CORRIDORS else "HIGH_SPEED")},
         "geometry": {"type": "LineString",
                      "coordinates": [[round(x, 5), round(y, 5)] for x, y in chain]}}
        for corridor, name, chain in simplified
    ]
    payload = {"type": "FeatureCollection", "features": features}
    points = station_points(args.shapefile)
    # A patched corridor's stations are newer than the extract's, so they win.
    if extra_stations:
        points = {**points, **extra_stations}
        print("station points: %d from extract + %d patched" % (len(points) - len(extra_stations), len(extra_stations)))

    # Emitted as a JS file the map loads with <script src>: a >500 KB payload pushed through
    # WebView.evaluateJavascript is unreliable, and that silently left the map on straight lines.
    body = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    with open(args.output, "w", encoding="utf-8") as fh:
        fh.write("window.railwayNetwork = %s;\n" % body)
        fh.write("window.railwayStationPoints = %s;\n" % json.dumps(points, ensure_ascii=False, separators=(",", ":")))

    import os
    size_mb = os.path.getsize(args.output) / 1024 / 1024
    print("%d chains, %d points (from %d) -> %s  (%.2f MB)"
          % (len(simplified), kept_points, raw_points, args.output, size_mb))


if __name__ == "__main__":
    sys.exit(main())

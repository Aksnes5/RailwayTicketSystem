(function () {
  let canvas, context, payload, base = 'standard', resizeBound = false;

  function initialize(message) {
    if (canvas) return;
    const map = document.getElementById('map');
    canvas = document.getElementById('fallback-map');
    if (!canvas) return;
    map.style.display = 'none';
    canvas.style.display = 'block';
    context = canvas.getContext('2d');
    if (!resizeBound) {
      resizeBound = true;
      window.addEventListener('resize', () => render());
    }
    if (message) message.style.display = 'none';
    resize();
  }

  function resize() {
    if (!canvas) return;
    const ratio = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    const width = Math.max(1, canvas.clientWidth);
    const height = Math.max(1, canvas.clientHeight);
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
  }

  function frameFor(stations) {
    if (!stations.length) return { west: 73, east: 135, south: 18, north: 54 };
    let west = Infinity, east = -Infinity, south = Infinity, north = -Infinity;
    stations.forEach(s => {
      west = Math.min(west, s.longitude); east = Math.max(east, s.longitude);
      south = Math.min(south, s.latitude); north = Math.max(north, s.latitude);
    });
    const dx = Math.max(.2, east - west), dy = Math.max(.2, north - south);
    return { west: west - dx * .2, east: east + dx * .2, south: south - dy * .25, north: north + dy * .25 };
  }

  function point(location, frame, width, height) {
    return [
      ((location.longitude - frame.west) / (frame.east - frame.west)) * width,
      height - ((location.latitude - frame.south) / (frame.north - frame.south)) * height
    ];
  }

  function visible(point, width, height) {
    return point[0] > -100 && point[0] < width + 100 && point[1] > -100 && point[1] < height + 100;
  }

  function paintRailwayBase(frame, width, height) {
    const gradient = context.createLinearGradient(0, 0, width, height);
    if (base === 'satellite') {
      gradient.addColorStop(0, '#1d3945'); gradient.addColorStop(.54, '#254c58'); gradient.addColorStop(1, '#173641');
    } else {
      gradient.addColorStop(0, '#eff8fc'); gradient.addColorStop(.5, '#d9edf7'); gradient.addColorStop(1, '#eaf4fa');
    }
    context.fillStyle = gradient;
    context.fillRect(0, 0, width, height);
    const glow = context.createRadialGradient(width * .18, height * .08, 0, width * .18, height * .08, Math.max(width, height) * .72);
    glow.addColorStop(0, base === 'satellite' ? 'rgba(223,246,247,.18)' : 'rgba(255,255,255,.76)');
    glow.addColorStop(1, 'rgba(255,255,255,0)');
    context.fillStyle = glow; context.fillRect(0, 0, width, height);

    context.lineCap = 'round'; context.lineJoin = 'round';
    context.strokeStyle = base === 'satellite' ? 'rgba(196,229,236,.13)' : 'rgba(137,181,205,.18)';
    context.lineWidth = 1;
    for (let index = 0; index < 4; index++) {
      const y = height * (.18 + index * .23);
      context.beginPath(); context.moveTo(-40, y + 20);
      context.bezierCurveTo(width * .22, y - 42, width * .62, y + 52, width + 50, y - 18);
      context.stroke();
    }

    const mapBase = window.railwayMapBase || { rivers: [], cities: [] };
    context.strokeStyle = base === 'satellite' ? 'rgba(116,207,230,.50)' : 'rgba(111,174,206,.50)';
    context.lineWidth = 1.45;
    (mapBase.rivers || []).forEach(river => {
      const projected = (river.points || []).map(pair => point({ longitude: pair[0], latitude: pair[1] }, frame, width, height));
      if (!projected.some(candidate => visible(candidate, width, height))) return;
      context.beginPath(); projected.forEach((candidate, index) => index ? context.lineTo(candidate[0], candidate[1]) : context.moveTo(candidate[0], candidate[1]));
      context.stroke();
    });
    context.font = '11px -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif';
    context.textBaseline = 'middle';
    (mapBase.cities || []).forEach(city => {
      const candidate = point(city, frame, width, height);
      if (!visible(candidate, width, height)) return;
      context.beginPath(); context.arc(candidate[0], candidate[1], 2.4, 0, Math.PI * 2);
      context.fillStyle = base === 'satellite' ? 'rgba(190,233,245,.78)' : 'rgba(60,127,164,.72)'; context.fill();
      context.fillStyle = base === 'satellite' ? 'rgba(235,250,255,.9)' : 'rgba(56,89,111,.88)';
      context.fillText(city.name, candidate[0] + 6, candidate[1] - 1);
    });
  }

  function paintRoute(stations, calling, frame) {
    const width = canvas.clientWidth, height = canvas.clientHeight;
    const points = stations.map(s => point(s, frame, width, height));
    context.lineCap = 'round'; context.lineJoin = 'round';
    context.beginPath(); points.forEach((p, i) => i ? context.lineTo(p[0], p[1]) : context.moveTo(p[0], p[1]));
    context.strokeStyle = 'rgba(255,255,255,.9)'; context.lineWidth = 9; context.stroke();
    context.beginPath(); points.forEach((p, i) => i ? context.lineTo(p[0], p[1]) : context.moveTo(p[0], p[1]));
    context.strokeStyle = base === 'satellite' ? '#69d7ff' : '#1976d2'; context.lineWidth = 4; context.stroke();

    const calls = new Set(calling || []);
    context.font = '500 12px -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif';
    context.textBaseline = 'middle';
    stations.forEach((station, index) => {
      if (!calls.has(station.name)) return;
      const candidate = points[index];
      const isStart = station.name === payload.boardingStation;
      const isEnd = station.name === payload.alightingStation;
      context.beginPath(); context.arc(candidate[0], candidate[1], isStart || isEnd ? 7 : 5, 0, Math.PI * 2);
      context.fillStyle = isEnd ? '#f06438' : '#2588de'; context.fill();
      context.lineWidth = 2; context.strokeStyle = '#ffffff'; context.stroke();
      context.fillStyle = base === 'satellite' ? '#ffffff' : '#19324a';
      const labelX = candidate[0] > width * .72 ? candidate[0] - 9 : candidate[0] + 9;
      context.textAlign = candidate[0] > width * .72 ? 'right' : 'left';
      context.fillText(station.name, labelX, candidate[1] - 13);
    });
    context.textAlign = 'left'; context.textBaseline = 'alphabetic';
    context.fillStyle = base === 'satellite' ? 'rgba(231,250,255,.76)' : 'rgba(31,77,111,.72)';
    context.font = '12px -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif';
    context.fillText('离线铁路运行图 · 路线与经停站已本地加载', 16, height - 20);
  }

  function render(nextPayload) {
    if (nextPayload) payload = nextPayload;
    if (!payload || !canvas) return;
    resize();
    const stations = (payload.routeStations || []).filter(s => Number.isFinite(s.latitude) && Number.isFinite(s.longitude));
    const width = canvas.clientWidth, height = canvas.clientHeight;
    const frame = frameFor(stations);
    paintRailwayBase(frame, width, height);
    if (stations.length < 2) {
      context.fillStyle = base === 'satellite' ? '#ffffff' : '#344054';
      context.font = '15px -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif';
      context.textAlign = 'center'; context.fillText('暂未收录该线路足够的站点坐标', width / 2, height / 2);
      return;
    }
    paintRoute(stations, payload.callingStations, frame);
  }

  function switchBase(next) { base = next === 'satellite' ? 'satellite' : 'standard'; render(); return base; }
  window.RailwayMapFallback = { initialize, render, switchBase, refresh: render };
}());
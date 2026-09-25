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
    let west = Infinity, east = -Infinity, south = Infinity, north = -Infinity;
    stations.forEach(s => {
      west = Math.min(west, s.longitude); east = Math.max(east, s.longitude);
      south = Math.min(south, s.latitude); north = Math.max(north, s.latitude);
    });
    const dx = Math.max(.2, east - west), dy = Math.max(.2, north - south);
    return { west: west - dx * .2, east: east + dx * .2, south: south - dy * .25, north: north + dy * .25 };
  }

  function point(station, frame, width, height) {
    return [
      ((station.longitude - frame.west) / (frame.east - frame.west)) * width,
      height - ((station.latitude - frame.south) / (frame.north - frame.south)) * height
    ];
  }

  function paintGrid(width, height) {
    const gradient = context.createLinearGradient(0, 0, width, height);
    if (base === 'satellite') {
      gradient.addColorStop(0, '#0f2c37'); gradient.addColorStop(.55, '#1e4350'); gradient.addColorStop(1, '#15313c');
    } else {
      gradient.addColorStop(0, '#eaf7ff'); gradient.addColorStop(.55, '#d8edf9'); gradient.addColorStop(1, '#eef9ff');
    }
    context.fillStyle = gradient;
    context.fillRect(0, 0, width, height);
    context.strokeStyle = base === 'satellite' ? 'rgba(200,239,235,.12)' : 'rgba(41,122,183,.12)';
    context.lineWidth = 1;
    for (let x = 22; x < width; x += 44) { context.beginPath(); context.moveTo(x, 0); context.lineTo(x, height); context.stroke(); }
    for (let y = 18; y < height; y += 44) { context.beginPath(); context.moveTo(0, y); context.lineTo(width, y); context.stroke(); }
  }

  function paintRoute(stations, calling) {
    const width = canvas.clientWidth, height = canvas.clientHeight;
    const frame = frameFor(stations);
    const points = stations.map(s => point(s, frame, width, height));
    context.lineCap = 'round'; context.lineJoin = 'round';
    context.beginPath();
    points.forEach((p, i) => i ? context.lineTo(p[0], p[1]) : context.moveTo(p[0], p[1]));
    context.strokeStyle = 'rgba(255,255,255,.9)'; context.lineWidth = 9; context.stroke();
    context.beginPath();
    points.forEach((p, i) => i ? context.lineTo(p[0], p[1]) : context.moveTo(p[0], p[1]));
    context.strokeStyle = base === 'satellite' ? '#69d7ff' : '#1976d2'; context.lineWidth = 4; context.stroke();

    const calls = new Set(calling || []);
    context.font = '500 12px -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif';
    context.textBaseline = 'middle';
    stations.forEach((station, index) => {
      if (!calls.has(station.name)) return;
      const p = points[index];
      const isStart = station.name === payload.boardingStation;
      const isEnd = station.name === payload.alightingStation;
      context.beginPath(); context.arc(p[0], p[1], isStart || isEnd ? 7 : 5, 0, Math.PI * 2);
      context.fillStyle = isEnd ? '#f06438' : '#2588de'; context.fill();
      context.lineWidth = 2; context.strokeStyle = '#ffffff'; context.stroke();
      context.fillStyle = base === 'satellite' ? '#ffffff' : '#19324a';
      const labelX = p[0] > width * .72 ? p[0] - 9 : p[0] + 9;
      context.textAlign = p[0] > width * .72 ? 'right' : 'left';
      context.fillText(station.name, labelX, p[1] - 13);
    });
    context.textAlign = 'left'; context.textBaseline = 'alphabetic';
    context.fillStyle = base === 'satellite' ? 'rgba(231,250,255,.76)' : 'rgba(31,77,111,.72)';
    context.font = '12px -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif';
    context.fillText('离线铁路底图 · 路线与经停站已本地加载', 16, height - 20);
  }

  function render(nextPayload) {
    if (nextPayload) payload = nextPayload;
    if (!payload || !canvas) return;
    resize();
    const stations = (payload.routeStations || []).filter(s => Number.isFinite(s.latitude) && Number.isFinite(s.longitude));
    const width = canvas.clientWidth, height = canvas.clientHeight;
    paintGrid(width, height);
    if (stations.length < 2) {
      context.fillStyle = base === 'satellite' ? '#ffffff' : '#344054';
      context.font = '15px -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif';
      context.textAlign = 'center'; context.fillText('暂未收录该线路足够的站点坐标', width / 2, height / 2);
      return;
    }
    paintRoute(stations, payload.callingStations);
  }

  function switchBase(next) { base = next === 'satellite' ? 'satellite' : 'standard'; render(); return base; }
  window.RailwayMapFallback = { initialize, render, switchBase, refresh: render };
}());

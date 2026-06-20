/**
 * Marine Weather — AIS watchlist prototype (Digitraffic MQTT + REST).
 * Data stays in localStorage; no server-side account.
 */

const STORAGE_KEY = 'mw_ais_watchlist_v1';
const STALE_MS = 30 * 60 * 1000;
const MQTT_URL = 'wss://meri.digitraffic.fi:443/mqtt';
const MQTT_USER = 'digitraffic';
const MQTT_PASS = 'digitrafficPassword';
const API_BASE = 'https://meri.digitraffic.fi/api/ais/v1/';
const DIGITRAFFIC_USER = 'MarineWeather/web-track (safelight.fi)';
const MAP_STYLE = 'https://tiles.openfreemap.org/styles/liberty';
const DEFAULT_CENTER = [21.0, 60.5];
const DEFAULT_ZOOM = 5.5;
const COURSE_VECTOR_MINUTES = 5;
const MIN_SOG_FOR_VECTOR_KN = 0.4;
/** Short heading tick for moored vessels (~185 m). */
const HEADING_VECTOR_NM = 0.1;
/** Extrapolate at most this long without a fresh AIS fix (avoids runaway drift). */
const MAX_DRIFT_MS = 120_000;
const MAP_ANIMATION_MS = 1000;
const GLOBAL_SEARCH_MIN_CHARS = 2;
const GLOBAL_SEARCH_MAX = 40;
const TRAFICOM_STORAGE_KEY = 'mw_traficom_chart_v1';
const TRAFICOM_SOURCE_ID = 'traficom-nautical';
const TRAFICOM_LAYER_ID = 'traficom-nautical';
const TRAFICOM_TILE_URL =
  'https://julkinen.traficom.fi/rasteripalvelu/wmts/rest/' +
  'Traficom:Merikarttasarjat%20public/default/WGS84_Pseudo-Mercator/' +
  'WGS84_Pseudo-Mercator:{z}/{y}/{x}?format=image/png';

/** @typedef {{ mmsi: number, nickname?: string, name?: string, callSign?: string, destination?: string, lat?: number, lon?: number, sog?: number, cog?: number, heading?: number, navStat?: number, lastSeenMs?: number }} WatchEntry */

/** @type {Map<number, WatchEntry>} */
const vessels = new Map();
let filter = 'all';
let searchQuery = '';
let selectedMmsi = null;
/** @type {import('maplibre-gl').Map | null} */
let map = null;
/** @type {import('mqtt').MqttClient | null} */
let mqttClient = null;
let subscribedMmsis = new Set();
let mapAnimTimer = null;
/** @type {'watchlist' | 'browse'} */
let panelMode = 'watchlist';
let browseSearchQuery = '';
/** @type {Array<{ mmsi: number, lat?: number, lon?: number, sog?: number, name?: string, callSign?: string, lastSeenMs?: number, source: 'nearby' | 'global' }>} */
let browseResults = [];
/** @type {Map<number, { name?: string, callSign?: string }> | null} */
let vesselMetaCache = null;
let metaCachePromise = null;
let nearbyLoading = false;
let nearbyDebounce = null;
let browseSelectedMmsi = null;
let traficomEnabled = false;

const $ = (id) => document.getElementById(id);

function loadWatchlist() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return;
    const list = JSON.parse(raw);
    if (!Array.isArray(list)) return;
    for (const item of list) {
      if (item && typeof item.mmsi === 'number') {
        vessels.set(item.mmsi, normalizeEntry(item));
      }
    }
  } catch {
    /* ignore corrupt storage */
  }
}

function saveWatchlist() {
  const list = [...vessels.values()].map((v) => ({
    mmsi: v.mmsi,
    nickname: v.nickname || undefined,
    name: v.name,
    callSign: v.callSign,
    destination: v.destination,
  }));
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
}

/** @param {Partial<WatchEntry> & { mmsi: number }} e */
function normalizeEntry(e) {
  return {
    mmsi: e.mmsi,
    nickname: e.nickname?.trim() || undefined,
    name: e.name?.trim() || undefined,
    callSign: e.callSign?.trim() || undefined,
    destination: e.destination?.trim() || undefined,
    lat: typeof e.lat === 'number' ? e.lat : undefined,
    lon: typeof e.lon === 'number' ? e.lon : undefined,
    sog: typeof e.sog === 'number' ? e.sog : undefined,
    cog: typeof e.cog === 'number' ? e.cog : undefined,
    heading: typeof e.heading === 'number' ? e.heading : undefined,
    navStat: typeof e.navStat === 'number' ? e.navStat : undefined,
    lastSeenMs: typeof e.lastSeenMs === 'number' ? e.lastSeenMs : undefined,
  };
}

function isActive(v) {
  return v.lastSeenMs != null && Date.now() - v.lastSeenMs < STALE_MS;
}

function displayName(v) {
  return v.nickname || v.name || `MMSI ${v.mmsi}`;
}

function formatSog(kn) {
  if (kn == null || Number.isNaN(kn)) return '—';
  return `${kn.toFixed(1)} kn`;
}

function formatDeg(d) {
  if (d == null || Number.isNaN(d)) return '—';
  return `${Math.round(d)}°`;
}

function formatLastSeen(ms) {
  if (ms == null) return 'Ei signaalia';
  const delta = Date.now() - ms;
  if (delta < 60_000) return 'Juuri nyt';
  if (delta < 3600_000) return `${Math.floor(delta / 60_000)} min sitten`;
  if (delta < 86400_000) return `${Math.floor(delta / 3600_000)} h sitten`;
  return `${Math.floor(delta / 86400_000)} pv sitten`;
}

/** @param {WatchEntry} v */
function isMovingVessel(v) {
  return v.sog != null && v.sog >= MIN_SOG_FOR_VECTOR_KN;
}

function validCog(cog) {
  return cog != null && Number.isFinite(cog) && cog >= 0 && cog < 360;
}

function validHeading(heading) {
  return heading != null && heading >= 0 && heading <= 359 && heading !== 511;
}

/** @param {WatchEntry} v */
function courseBearingDeg(v) {
  if (isMovingVessel(v)) {
    if (validCog(v.cog)) return v.cog;
    if (validHeading(v.heading)) return v.heading;
    return null;
  }
  if (validHeading(v.heading)) return v.heading;
  return null;
}

/** @param {WatchEntry} v */
function showsCourseVector(v) {
  if (!isActive(v) || courseBearingDeg(v) == null) return false;
  return isMovingVessel(v) || validHeading(v.heading);
}

/** @param {WatchEntry} v */
function vectorDistanceNm(v, minutes = COURSE_VECTOR_MINUTES) {
  if (isMovingVessel(v)) return v.sog * minutes / 60;
  if (validHeading(v.heading)) return HEADING_VECTOR_NM;
  return null;
}

/** Great-circle destination: bearing ° true, distance nautical miles. */
function destinationPoint(lat, lon, bearingDeg, distanceNm) {
  if (distanceNm <= 0 || !Number.isFinite(distanceNm) || !Number.isFinite(bearingDeg)) {
    return [lat, lon];
  }
  const earthRadiusM = 6_371_000;
  const distanceM = distanceNm * 1852;
  const bearing = (bearingDeg * Math.PI) / 180;
  const lat1 = (lat * Math.PI) / 180;
  const lon1 = (lon * Math.PI) / 180;
  const lat2 = Math.asin(
    Math.sin(lat1) * Math.cos(distanceM / earthRadiusM)
      + Math.cos(lat1) * Math.sin(distanceM / earthRadiusM) * Math.cos(bearing),
  );
  const lon2 = lon1 + Math.atan2(
    Math.sin(bearing) * Math.sin(distanceM / earthRadiusM) * Math.cos(lat1),
    Math.cos(distanceM / earthRadiusM) - Math.sin(lat1) * Math.sin(lat2),
  );
  return [(lat2 * 180) / Math.PI, (lon2 * 180) / Math.PI];
}

/** @param {WatchEntry} v */
function courseVectorEnd(v, minutes = COURSE_VECTOR_MINUTES, nowMs = Date.now()) {
  const pos = getDisplayPosition(v, nowMs);
  const bearing = courseBearingDeg(v);
  const distanceNm = vectorDistanceNm(v, minutes);
  if (!pos || bearing == null || distanceNm == null || distanceNm <= 0.0001) return null;
  return destinationPoint(pos.lat, pos.lon, bearing, distanceNm);
}

/** Dead-reckoned map position from last AIS report + SOG/COG. */
function getDisplayPosition(v, nowMs = Date.now()) {
  if (v.lat == null || v.lon == null) return null;
  if (!isActive(v) || !isMovingVessel(v) || v.lastSeenMs == null) {
    return { lat: v.lat, lon: v.lon };
  }
  const elapsedMs = Math.min(Math.max(0, nowMs - v.lastSeenMs), MAX_DRIFT_MS);
  if (elapsedMs <= 0) return { lat: v.lat, lon: v.lon };
  const bearing = courseBearingDeg(v);
  if (bearing == null || v.sog == null) return { lat: v.lat, lon: v.lon };
  const distanceNm = v.sog * (elapsedMs / 3_600_000);
  if (distanceNm <= 0.0001) return { lat: v.lat, lon: v.lon };
  const [lat, lon] = destinationPoint(v.lat, v.lon, bearing, distanceNm);
  return { lat, lon };
}

function needsLiveMapUpdates() {
  for (const v of vessels.values()) {
    if (isActive(v) && isMovingVessel(v)) return true;
  }
  return false;
}

async function apiFetch(path) {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      Accept: 'application/json',
      'Digitraffic-User': DIGITRAFFIC_USER,
    },
  });
  if (!res.ok) throw new Error(`API ${res.status}`);
  return res.json();
}

async function fetchVesselMeta(mmsi) {
  try {
    return await apiFetch(`vessels/${mmsi}`);
  } catch {
    return null;
  }
}

async function fetchVesselLocation(mmsi) {
  try {
    const data = await apiFetch(`locations?mmsi=${mmsi}`);
    const feature = data?.features?.[0];
    if (!feature) return null;
    const [lon, lat] = feature.geometry?.coordinates ?? [];
    const p = feature.properties ?? {};
    return {
      lat,
      lon,
      sog: p.sog,
      cog: p.cog,
      heading: p.heading,
      navStat: p.navStat,
      lastSeenMs: p.timestampExternal ?? Date.now(),
    };
  } catch {
    return null;
  }
}

async function ensureVesselMetaCache() {
  if (vesselMetaCache) return vesselMetaCache;
  if (metaCachePromise) return metaCachePromise;
  metaCachePromise = (async () => {
    const list = await apiFetch('vessels');
    const cache = new Map();
    if (Array.isArray(list)) {
      for (const v of list) {
        if (v?.mmsi != null) {
          cache.set(v.mmsi, { name: v.name, callSign: v.callSign });
        }
      }
    }
    vesselMetaCache = cache;
    return cache;
  })().catch((err) => {
    metaCachePromise = null;
    throw err;
  });
  return metaCachePromise;
}

function metaForMmsi(mmsi) {
  return vesselMetaCache?.get(mmsi);
}

function browseRadiusKm() {
  if (!map) return 20;
  const z = map.getZoom();
  if (z >= 11) return 8;
  if (z >= 9) return 15;
  if (z >= 7) return 30;
  return 50;
}

function parseLocationFeature(feature) {
  const mmsi = feature.mmsi ?? feature.properties?.mmsi;
  const [lon, lat] = feature.geometry?.coordinates ?? [];
  const p = feature.properties ?? {};
  if (mmsi == null || lat == null || lon == null) return null;
  let lastSeenMs = p.timestampExternal ?? p.timestamp ?? Date.now();
  if (typeof lastSeenMs === 'number' && lastSeenMs < 1e12) lastSeenMs *= 1000;
  return {
    mmsi: Number(mmsi),
    lat,
    lon,
    sog: p.sog,
    cog: p.cog,
    heading: p.heading,
    lastSeenMs,
  };
}

async function fetchNearbyVessels() {
  if (!map) return;
  nearbyLoading = true;
  renderBrowseList();
  try {
    const c = map.getCenter();
    const radius = browseRadiusKm();
    const data = await apiFetch(
      `locations?latitude=${c.lat.toFixed(5)}&longitude=${c.lng.toFixed(5)}&radius=${radius}`,
    );
    await ensureVesselMetaCache();
    const items = (data?.features ?? [])
      .map(parseLocationFeature)
      .filter(Boolean);
    browseResults = items.map((item) => {
      const meta = metaForMmsi(item.mmsi);
      return {
        ...item,
        name: meta?.name,
        callSign: meta?.callSign,
        source: 'nearby',
      };
    });
    browseResults.sort((a, b) => browseDisplayName(a).localeCompare(browseDisplayName(b), 'fi'));
    updateNearbyMapSource();
  } catch {
    browseResults = [];
  } finally {
    nearbyLoading = false;
    renderBrowseList();
    renderStats();
  }
}

function searchGlobalVessels(query) {
  if (!vesselMetaCache || query.length < GLOBAL_SEARCH_MIN_CHARS) return [];
  const hits = [];
  for (const [mmsi, meta] of vesselMetaCache) {
    const hay = [String(mmsi), meta.name, meta.callSign].filter(Boolean).join(' ').toLowerCase();
    if (!hay.includes(query)) continue;
    hits.push({
      mmsi,
      name: meta.name,
      callSign: meta.callSign,
      source: 'global',
    });
    if (hits.length >= GLOBAL_SEARCH_MAX) break;
  }
  hits.sort((a, b) => browseDisplayName(a).localeCompare(browseDisplayName(b), 'fi'));
  return hits;
}

function browseDisplayName(item) {
  const name = item.name?.trim();
  return name || `MMSI ${item.mmsi}`;
}

function filterBrowseByQuery(items, query) {
  if (!query) return items;
  return items.filter((item) => {
    const hay = [String(item.mmsi), item.name, item.callSign].filter(Boolean).join(' ').toLowerCase();
    return hay.includes(query);
  });
}

function mergedBrowseResults() {
  const q = browseSearchQuery.trim().toLowerCase();
  const byMmsi = new Map();
  for (const item of filterBrowseByQuery(browseResults, q)) {
    byMmsi.set(item.mmsi, item);
  }
  if (q.length >= GLOBAL_SEARCH_MIN_CHARS && vesselMetaCache) {
    for (const item of searchGlobalVessels(q)) {
      if (!byMmsi.has(item.mmsi)) byMmsi.set(item.mmsi, item);
    }
  }
  return [...byMmsi.values()].sort((a, b) =>
    browseDisplayName(a).localeCompare(browseDisplayName(b), 'fi'),
  );
}

function scheduleNearbyRefresh() {
  if (nearbyDebounce != null) clearTimeout(nearbyDebounce);
  nearbyDebounce = window.setTimeout(() => {
    nearbyDebounce = null;
    if (panelMode === 'browse') fetchNearbyVessels();
  }, 700);
}

async function openBrowsePanel() {
  panelMode = 'browse';
  $('watchlist-tools').hidden = true;
  $('browse-tools').hidden = false;
  document.querySelectorAll('.panel-tab').forEach((btn) => {
    const on = btn.dataset.panel === 'browse';
    btn.classList.toggle('is-active', on);
    btn.setAttribute('aria-selected', on ? 'true' : 'false');
  });
  setNearbyLayerVisible(true);
  renderStats();
  try {
    await ensureVesselMetaCache();
  } catch {
    /* global name search unavailable */
  }
  await fetchNearbyVessels();
}

function openWatchlistPanel() {
  panelMode = 'watchlist';
  $('watchlist-tools').hidden = false;
  $('browse-tools').hidden = true;
  document.querySelectorAll('.panel-tab').forEach((btn) => {
    const on = btn.dataset.panel === 'watchlist';
    btn.classList.toggle('is-active', on);
    btn.setAttribute('aria-selected', on ? 'true' : 'false');
  });
  setNearbyLayerVisible(false);
  renderList();
  renderStats();
}

async function addFromBrowse(mmsi) {
  if (vessels.has(mmsi)) {
    openWatchlistPanel();
    selectVessel(mmsi, true);
    return;
  }
  await addVessel(mmsi, undefined);
  const meta = metaForMmsi(mmsi);
  if (meta?.name) {
    mergeVessel(mmsi, { name: meta.name, callSign: meta.callSign });
    saveWatchlist();
    scheduleUiRefresh();
  }
  browseSelectedMmsi = null;
  openWatchlistPanel();
}

function findBrowseItem(mmsi) {
  return mergedBrowseResults().find((item) => item.mmsi === mmsi)
    ?? browseResults.find((item) => item.mmsi === mmsi);
}

function previewBrowseVessel(mmsi) {
  browseSelectedMmsi = mmsi;
  selectedMmsi = null;
  const item = findBrowseItem(mmsi);
  renderBrowseList();
  renderBrowseDetail(mmsi);
  if (item?.lat != null && item?.lon != null && map) {
    map.flyTo({
      center: [item.lon, item.lat],
      zoom: Math.max(map.getZoom(), 11),
      duration: 600,
    });
  }
}

function renderBrowseDetail(mmsi) {
  const panel = $('detail');
  const item = findBrowseItem(mmsi);
  if (!item) {
    panel.hidden = true;
    return;
  }

  panel.hidden = false;
  const watched = vessels.has(mmsi);
  const title = browseDisplayName(item);
  const subtitle = [
    item.callSign,
    `MMSI ${item.mmsi}`,
    item.source === 'global' && item.lat == null ? 'ei sijaintia nyt' : null,
  ]
    .filter(Boolean)
    .join(' · ');

  $('detail-content').innerHTML = `
    <h2 class="detail-title">${escapeHtml(title)}</h2>
    <p class="detail-sub">${escapeHtml(subtitle)}</p>
    <dl class="detail-grid">
      <dt>Nopeus</dt><dd>${escapeHtml(item.sog != null ? formatSog(item.sog) : '—')}</dd>
      ${item.lat != null ? `<dt>Sijainti</dt><dd>${item.lat.toFixed(4)}°, ${item.lon.toFixed(4)}°</dd>` : ''}
    </dl>
    <div class="detail-actions">
      ${
        watched
          ? '<p class="detail-sub" style="margin:0">Jo seurannassa.</p>'
          : '<button type="button" class="btn btn-primary" id="detail-add-watch">Lisää seurantaan</button>'
      }
    </div>
  `;

  if (!watched) {
    $('detail-add-watch').addEventListener('click', () => addFromBrowse(mmsi));
  }
}

function mergeVessel(mmsi, patch) {
  const prev = vessels.get(mmsi) ?? { mmsi };
  const next = normalizeEntry({ ...prev, ...patch, mmsi });
  vessels.set(mmsi, next);
  return next;
}

function parseMmsiInput(raw) {
  const digits = raw.replace(/\D/g, '');
  if (digits.length !== 9) return null;
  const n = Number(digits);
  return Number.isSafeInteger(n) ? n : null;
}

function topicMmsi(topic) {
  const m = /^vessels-v2\/(\d+)\//.exec(topic);
  return m ? Number(m[1]) : null;
}

function handleMqttMessage(topic, payload) {
  const mmsi = topicMmsi(topic);
  if (mmsi == null || !vessels.has(mmsi)) return;

  let json;
  try {
    json = JSON.parse(payload.toString());
  } catch {
    return;
  }

  if (topic.endsWith('/metadata')) {
    mergeVessel(mmsi, {
      name: json.name ?? json.properties?.name,
      callSign: json.callSign ?? json.properties?.callSign,
      destination: json.destination ?? json.properties?.destination,
    });
    saveWatchlist();
    scheduleUiRefresh();
    return;
  }

  if (!topic.includes('/location')) return;

  let lat;
  let lon;
  let sog;
  let cog;
  let heading;
  let navStat;
  let lastSeenMs = Date.now();

  if (json.lat != null && json.lon != null) {
    lat = json.lat;
    lon = json.lon;
    sog = json.sog;
    cog = json.cog;
    heading = json.heading;
    navStat = json.navStat;
    if (json.time) lastSeenMs = json.time < 1e12 ? json.time * 1000 : json.time;
  } else if (json.geometry?.coordinates) {
    [lon, lat] = json.geometry.coordinates;
    const p = json.properties ?? {};
    sog = p.sog;
    cog = p.cog;
    heading = p.heading;
    navStat = p.navStat;
    lastSeenMs = p.timestampExternal ?? p.timestamp ?? lastSeenMs;
    if (typeof lastSeenMs === 'number' && lastSeenMs < 1e12) lastSeenMs *= 1000;
  }

  if (lat == null || lon == null) return;

  mergeVessel(mmsi, { lat, lon, sog, cog, heading, navStat, lastSeenMs });
  scheduleUiRefresh();
}

function setMqttStatus(state) {
  const el = $('mqtt-status');
  el.className = 'mqtt-pill';
  if (state === 'on') {
    el.textContent = 'Live';
    el.classList.add('mqtt-pill--on');
  } else if (state === 'off') {
    el.textContent = 'Yhteys katkennut';
    el.classList.add('mqtt-pill--off');
  } else {
    el.textContent = 'Yhdistetään…';
    el.classList.add('mqtt-pill--off');
  }
}

function subscribeMmsi(mmsi) {
  if (!mqttClient?.connected || subscribedMmsis.has(mmsi)) return;
  mqttClient.subscribe(`vessels-v2/${mmsi}/location`);
  mqttClient.subscribe(`vessels-v2/${mmsi}/metadata`);
  subscribedMmsis.add(mmsi);
}

function unsubscribeMmsi(mmsi) {
  if (!mqttClient?.connected || !subscribedMmsis.has(mmsi)) return;
  mqttClient.unsubscribe(`vessels-v2/${mmsi}/location`);
  mqttClient.unsubscribe(`vessels-v2/${mmsi}/metadata`);
  subscribedMmsis.delete(mmsi);
}

function syncMqttSubscriptions() {
  const wanted = new Set(vessels.keys());
  for (const mmsi of subscribedMmsis) {
    if (!wanted.has(mmsi)) unsubscribeMmsi(mmsi);
  }
  for (const mmsi of wanted) {
    subscribeMmsi(mmsi);
  }
}

function connectMqtt() {
  if (typeof mqtt === 'undefined') {
    setMqttStatus('off');
    return;
  }

  setMqttStatus('connecting');
  const clientId = `marine-weather-web-${Math.random().toString(16).slice(2, 10)}`;

  mqttClient = mqtt.connect(MQTT_URL, {
    username: MQTT_USER,
    password: MQTT_PASS,
    clientId,
    reconnectPeriod: 5000,
    connectTimeout: 15000,
  });

  mqttClient.on('connect', () => {
    setMqttStatus('on');
    syncMqttSubscriptions();
  });

  mqttClient.on('reconnect', () => setMqttStatus('connecting'));

  mqttClient.on('close', () => {
    setMqttStatus('off');
    subscribedMmsis.clear();
  });

  mqttClient.on('error', () => setMqttStatus('off'));

  mqttClient.on('message', (topic, payload) => handleMqttMessage(topic, payload));
}

function filteredVessels() {
  const q = searchQuery.trim().toLowerCase();
  let list = [...vessels.values()];

  if (filter === 'active') list = list.filter(isActive);
  else if (filter === 'stale') list = list.filter((v) => !isActive(v));

  if (q) {
    list = list.filter((v) => {
      const hay = [
        String(v.mmsi),
        v.nickname,
        v.name,
        v.callSign,
        v.destination,
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return hay.includes(q);
    });
  }

  list.sort((a, b) => displayName(a).localeCompare(displayName(b), 'fi'));
  return list;
}

let uiRefreshTimer = null;
function scheduleUiRefresh() {
  if (uiRefreshTimer != null) return;
  uiRefreshTimer = requestAnimationFrame(() => {
    uiRefreshTimer = null;
    if (panelMode === 'browse') {
      renderBrowseList();
    } else {
      renderList();
    }
    renderStats();
    updateMapSource();
    if (selectedMmsi != null) renderDetail(selectedMmsi);
  });
}

function renderStats() {
  const el = $('stats');
  if (panelMode === 'browse') {
    if (nearbyLoading) {
      el.textContent = 'Haetaan…';
      return;
    }
    const items = mergedBrowseResults();
    const radius = browseRadiusKm();
    const extra = browseSearchQuery.trim().length >= GLOBAL_SEARCH_MIN_CHARS ? ' + haku' : '';
    el.textContent = `${items.length} alusta (${radius} km${extra})`;
    return;
  }
  const all = [...vessels.values()];
  const active = all.filter(isActive).length;
  const stale = all.length - active;
  if (all.length === 0) {
    el.textContent = '0 alusta';
    return;
  }
  el.textContent = `${active} aktiivista · ${stale} ei signaalia`;
}

function renderList() {
  if (panelMode === 'browse') {
    renderBrowseList();
    return;
  }
  const listEl = $('vessel-list');
  const items = filteredVessels();

  if (items.length === 0) {
    const hint =
      vessels.size === 0
        ? 'Ei aluksia — lisää MMSI seurantaan.'
        : 'Ei hakutuloksia tällä suodattimella.';
    listEl.innerHTML = `<li class="vessel-empty">${hint}</li>`;
    return;
  }

  const frag = document.createDocumentFragment();
  for (const v of items) {
    const li = document.createElement('li');
    li.className = 'vessel-item';
    li.setAttribute('role', 'option');
    if (!isActive(v)) li.classList.add('is-stale');
    if (v.mmsi === selectedMmsi) li.classList.add('is-selected');

    const dot = document.createElement('span');
    dot.className = 'vessel-dot';
    dot.setAttribute('aria-hidden', 'true');

    const name = document.createElement('div');
    name.className = 'vessel-name';
    name.textContent = displayName(v);

    const meta = document.createElement('div');
    meta.className = 'vessel-meta';
    const parts = [`MMSI ${v.mmsi}`];
    if (v.name && v.nickname) parts.push(v.name);
    parts.push(formatLastSeen(v.lastSeenMs));
    meta.textContent = parts.join(' · ');

    const sog = document.createElement('div');
    sog.className = 'vessel-sog';
    sog.textContent = isActive(v) ? formatSog(v.sog) : '—';

    li.append(dot, name, sog, meta);
    li.addEventListener('click', () => selectVessel(v.mmsi, true));
    frag.appendChild(li);
  }

  listEl.replaceChildren(frag);
}

function renderBrowseList() {
  const listEl = $('vessel-list');
  if (nearbyLoading && browseResults.length === 0 && !vesselMetaCache) {
    listEl.innerHTML = '<li class="vessel-empty">Ladataan alusrekisteriä…</li>';
    return;
  }
  if (nearbyLoading && browseResults.length === 0) {
    listEl.innerHTML = '<li class="vessel-empty">Haetaan lähellä olevia aluksia…</li>';
    return;
  }

  const items = mergedBrowseResults();
  if (items.length === 0) {
    listEl.innerHTML = '<li class="vessel-empty">Ei tuloksia. Siirrä karttaa tai kokeile toista hakusanaa.</li>';
    return;
  }

  const frag = document.createDocumentFragment();
  for (const item of items) {
    const watched = vessels.has(item.mmsi);
    const li = document.createElement('li');
    li.className = 'vessel-item';
    if (watched) li.classList.add('is-watched');
    if (browseSelectedMmsi === item.mmsi) li.classList.add('is-selected');

    const dot = document.createElement('span');
    dot.className = 'vessel-dot';
    dot.style.background = item.source === 'global' && item.lat == null ? '#6b7a94' : '#7dcea0';

    const name = document.createElement('div');
    name.className = 'vessel-name';
    name.textContent = browseDisplayName(item);

    const action = document.createElement('div');
    if (watched) {
      action.className = 'vessel-badge';
      action.textContent = 'Seurannassa';
    } else {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'vessel-add-btn';
      btn.textContent = 'Lisää seurantaan';
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        addFromBrowse(item.mmsi);
      });
      action.appendChild(btn);
    }

    const meta = document.createElement('div');
    meta.className = 'vessel-meta';
    const parts = [`MMSI ${item.mmsi}`];
    if (item.callSign) parts.push(item.callSign);
    if (item.source === 'global' && item.lat == null) {
      parts.push('ei sijaintia nyt');
    } else if (item.sog != null) {
      parts.push(formatSog(item.sog));
    }
    meta.textContent = parts.join(' · ');

    const sog = document.createElement('div');
    sog.className = 'vessel-sog';
    sog.textContent = item.lat != null && item.sog != null ? formatSog(item.sog) : '';

    li.append(dot, name, action, sog, meta);
    li.addEventListener('click', () => {
      if (watched) {
        openWatchlistPanel();
        selectVessel(item.mmsi, true);
      } else {
        previewBrowseVessel(item.mmsi);
      }
    });
    frag.appendChild(li);
  }

  listEl.replaceChildren(frag);
}

function buildNearbyGeoJson() {
  const features = [];
  for (const item of browseResults) {
    if (item.lat == null || item.lon == null) continue;
    if (vessels.has(item.mmsi)) continue;
    features.push({
      type: 'Feature',
      id: item.mmsi,
      geometry: { type: 'Point', coordinates: [item.lon, item.lat] },
      properties: { mmsi: item.mmsi, name: browseDisplayName(item) },
    });
  }
  return { type: 'FeatureCollection', features };
}

function updateNearbyMapSource() {
  if (!map?.isStyleLoaded()) return;
  const src = map.getSource('nearby-vessels');
  if (src) src.setData(buildNearbyGeoJson());
}

function setNearbyLayerVisible(visible) {
  if (!map?.isStyleLoaded()) return;
  const vis = visible ? 'visible' : 'none';
  if (map.getLayer('nearby-points')) map.setLayoutProperty('nearby-points', 'visibility', vis);
}

function selectVessel(mmsi, fly) {
  selectedMmsi = mmsi;
  browseSelectedMmsi = null;
  const v = vessels.get(mmsi);
  if (panelMode === 'browse') renderBrowseList();
  else renderList();
  renderDetail(mmsi);

  if (v?.lat != null && v?.lon != null && map) {
    const pos = getDisplayPosition(v);
    if (fly && pos) {
      map.flyTo({ center: [pos.lon, pos.lat], zoom: Math.max(map.getZoom(), 9), duration: 800 });
    }
    updateMapSource();
  }
}

function renderDetail(mmsi) {
  const panel = $('detail');
  const v = vessels.get(mmsi);
  if (!v) {
    panel.hidden = true;
    return;
  }

  panel.hidden = false;
  const title = displayName(v);
  const subtitle = [
    v.name && v.nickname ? v.name : null,
    v.callSign,
    `MMSI ${v.mmsi}`,
  ]
    .filter(Boolean)
    .join(' · ');

  $('detail-content').innerHTML = `
    <h2 class="detail-title">${escapeHtml(title)}</h2>
    <p class="detail-sub">${escapeHtml(subtitle)}</p>
    <dl class="detail-grid">
      <dt>Nopeus</dt><dd>${escapeHtml(formatSog(v.sog))}</dd>
      <dt>Kurssi</dt><dd>${escapeHtml(formatDeg(v.cog))}</dd>
      <dt>Suunta</dt><dd>${escapeHtml(formatDeg(v.heading))}</dd>
      <dt>Viimeisin</dt><dd>${escapeHtml(formatLastSeen(v.lastSeenMs))}</dd>
      ${v.destination ? `<dt>Määränpää</dt><dd>${escapeHtml(v.destination)}</dd>` : ''}
      ${v.lat != null ? `<dt>Sijainti</dt><dd id="detail-position">${(() => {
        const p = getDisplayPosition(v);
        return p ? `${p.lat.toFixed(4)}°, ${p.lon.toFixed(4)}°` : '—';
      })()}</dd>` : ''}
    </dl>
    <div class="detail-actions">
      <button type="button" class="btn" id="detail-remove">Poista listalta</button>
    </div>
  `;

  $('detail-remove').addEventListener('click', () => removeVessel(mmsi));
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function removeVessel(mmsi) {
  unsubscribeMmsi(mmsi);
  vessels.delete(mmsi);
  saveWatchlist();
  if (selectedMmsi === mmsi) {
    selectedMmsi = null;
    $('detail').hidden = true;
  }
  scheduleUiRefresh();
}

async function addVessel(mmsi, nickname) {
  if (vessels.has(mmsi)) throw new Error('Alus on jo listalla.');

  mergeVessel(mmsi, { mmsi, nickname });
  saveWatchlist();
  subscribeMmsi(mmsi);

  const [meta, loc] = await Promise.all([
    fetchVesselMeta(mmsi),
    fetchVesselLocation(mmsi),
  ]);

  const patch = {};
  if (meta) {
    patch.name = meta.name;
    patch.callSign = meta.callSign;
    patch.destination = meta.destination;
  }
  if (loc) Object.assign(patch, loc);
  mergeVessel(mmsi, patch);
  saveWatchlist();
  scheduleUiRefresh();
  selectVessel(mmsi, loc != null);
}

function buildGeoJson(nowMs = Date.now()) {
  const features = [];
  for (const v of vessels.values()) {
    const pos = getDisplayPosition(v, nowMs);
    if (!pos) continue;
    features.push({
      type: 'Feature',
      id: v.mmsi,
      geometry: { type: 'Point', coordinates: [pos.lon, pos.lat] },
      properties: {
        mmsi: v.mmsi,
        name: displayName(v),
        sog: v.sog,
        cog: v.cog ?? v.heading,
        active: isActive(v),
        selected: v.mmsi === selectedMmsi,
      },
    });
  }
  return { type: 'FeatureCollection', features };
}

function buildCourseVectorsGeoJson(nowMs = Date.now()) {
  const features = [];
  for (const v of vessels.values()) {
    if (!isActive(v) || !showsCourseVector(v)) continue;
    const pos = getDisplayPosition(v, nowMs);
    const end = courseVectorEnd(v, COURSE_VECTOR_MINUTES, nowMs);
    if (!pos || !end) continue;
    const [endLat, endLon] = end;
    features.push({
      type: 'Feature',
      id: `vec-${v.mmsi}`,
      geometry: {
        type: 'LineString',
        coordinates: [[pos.lon, pos.lat], [endLon, endLat]],
      },
      properties: {
        mmsi: v.mmsi,
        active: true,
        selected: v.mmsi === selectedMmsi,
        moored: !isMovingVessel(v),
      },
    });
  }
  return { type: 'FeatureCollection', features };
}

function loadTraficomPref() {
  try {
    traficomEnabled = localStorage.getItem(TRAFICOM_STORAGE_KEY) === '1';
  } catch {
    traficomEnabled = false;
  }
}

function saveTraficomPref() {
  try {
    localStorage.setItem(TRAFICOM_STORAGE_KEY, traficomEnabled ? '1' : '0');
  } catch {
    /* ignore */
  }
}

function findRasterInsertBeforeLayer() {
  if (!map) return undefined;
  const layers = map.getStyle()?.layers ?? [];
  for (const layer of layers) {
    if (layer.type === 'symbol' && layer.layout?.['text-field']) {
      return layer.id;
    }
  }
  for (let i = layers.length - 1; i >= 0; i -= 1) {
    if (layers[i].type === 'fill' || layers[i].type === 'background') {
      return layers[i + 1]?.id;
    }
  }
  return undefined;
}

function ensureTraficomLayer(enabled) {
  if (!map?.isStyleLoaded()) return;
  if (!enabled) {
    if (map.getLayer(TRAFICOM_LAYER_ID)) {
      map.setLayoutProperty(TRAFICOM_LAYER_ID, 'visibility', 'none');
    }
    return;
  }

  const existing = map.getSource(TRAFICOM_SOURCE_ID);
  if (existing) {
    // Recreate if URL template was wrong (older deploy).
    const tiles = existing.tiles?.[0] ?? '';
    if (!tiles.includes('WGS84_Pseudo-Mercator:{z}')) {
      if (map.getLayer(TRAFICOM_LAYER_ID)) map.removeLayer(TRAFICOM_LAYER_ID);
      map.removeSource(TRAFICOM_SOURCE_ID);
    }
  }

  if (!map.getSource(TRAFICOM_SOURCE_ID)) {
    map.addSource(TRAFICOM_SOURCE_ID, {
      type: 'raster',
      tiles: [TRAFICOM_TILE_URL],
      tileSize: 256,
      minzoom: 5,
      maxzoom: 15,
      bounds: [17, 58, 32, 71],
      attribution: '© Traficom (CC BY 4.0)',
    });
    map.addLayer(
      {
        id: TRAFICOM_LAYER_ID,
        type: 'raster',
        source: TRAFICOM_SOURCE_ID,
        paint: { 'raster-opacity': 1 },
      },
      findRasterInsertBeforeLayer(),
    );
    return;
  }

  if (map.getLayer(TRAFICOM_LAYER_ID)) {
    map.setLayoutProperty(TRAFICOM_LAYER_ID, 'visibility', 'visible');
  }
}

function initMap() {
  map = new maplibregl.Map({
    container: 'map',
    style: MAP_STYLE,
    center: DEFAULT_CENTER,
    zoom: DEFAULT_ZOOM,
    attributionControl: { compact: true },
  });

  map.addControl(new maplibregl.NavigationControl({ showCompass: true }), 'top-right');

  map.on('load', () => {
    ensureTraficomLayer(traficomEnabled);

    map.addSource('vessels', {
      type: 'geojson',
      data: buildGeoJson(),
      cluster: true,
      clusterMaxZoom: 11,
      clusterRadius: 45,
    });

    map.addLayer({
      id: 'clusters',
      type: 'circle',
      source: 'vessels',
      filter: ['has', 'point_count'],
      paint: {
        'circle-color': '#3d6f9a',
        'circle-radius': ['step', ['get', 'point_count'], 16, 10, 20, 30, 26],
        'circle-stroke-width': 2,
        'circle-stroke-color': '#0c1220',
      },
    });

    map.addLayer({
      id: 'cluster-count',
      type: 'symbol',
      source: 'vessels',
      filter: ['has', 'point_count'],
      layout: {
        'text-field': '{point_count_abbreviated}',
        'text-size': 12,
        'text-font': ['Open Sans Bold'],
      },
      paint: { 'text-color': '#e8eef8' },
    });

    map.addSource('vessel-vectors', {
      type: 'geojson',
      data: buildCourseVectorsGeoJson(),
    });

    map.addLayer({
      id: 'vessel-vectors',
      type: 'line',
      source: 'vessel-vectors',
      layout: {
        'line-cap': 'round',
        'line-join': 'round',
      },
      paint: {
        'line-width': ['case', ['get', 'selected'], 4, 3],
        'line-color': [
          'case',
          ['get', 'moored'],
          '#9aa8c4',
          ['case', ['get', 'selected'], '#8ec5f0', '#40b8ff'],
        ],
        'line-opacity': 0.92,
      },
    });

    map.addLayer({
      id: 'vessel-points',
      type: 'circle',
      source: 'vessels',
      filter: ['!', ['has', 'point_count']],
      paint: {
        'circle-radius': ['case', ['get', 'selected'], 9, 7],
        'circle-color': [
          'case',
          ['get', 'selected'],
          '#8ec5f0',
          ['case', ['get', 'active'], '#5b9fd4', '#6b7a94'],
        ],
        'circle-stroke-width': 2,
        'circle-stroke-color': '#0c1220',
      },
    });

    map.addSource('nearby-vessels', {
      type: 'geojson',
      data: { type: 'FeatureCollection', features: [] },
    });

    map.addLayer({
      id: 'nearby-points',
      type: 'circle',
      source: 'nearby-vessels',
      layout: { visibility: 'none' },
      paint: {
        'circle-radius': 5,
        'circle-color': '#7dcea0',
        'circle-opacity': 0.75,
        'circle-stroke-width': 1.5,
        'circle-stroke-color': '#0c1220',
      },
    });

    map.on('moveend', () => {
      if (panelMode === 'browse') scheduleNearbyRefresh();
    });

    map.on('click', 'nearby-points', (e) => {
      const mmsi = e.features?.[0]?.properties?.mmsi ?? e.features?.[0]?.id;
      if (mmsi != null) previewBrowseVessel(Number(mmsi));
    });

    map.on('mouseenter', 'nearby-points', () => { map.getCanvas().style.cursor = 'pointer'; });
    map.on('mouseleave', 'nearby-points', () => { map.getCanvas().style.cursor = ''; });

    map.on('click', 'clusters', (e) => {
      const features = map.queryRenderedFeatures(e.point, { layers: ['clusters'] });
      const clusterId = features[0]?.properties?.cluster_id;
      if (clusterId == null) return;
      const source = map.getSource('vessels');
      source.getClusterExpansionZoom(clusterId, (err, zoom) => {
        if (err) return;
        map.easeTo({ center: features[0].geometry.coordinates, zoom });
      });
    });

    map.on('click', 'vessel-points', (e) => {
      const f = e.features?.[0];
      const mmsi = f?.properties?.mmsi ?? f?.id;
      if (mmsi != null) selectVessel(Number(mmsi), false);
    });

    // Lines sit below dots; also accept clicks on the vector near a vessel.
    map.on('click', 'vessel-vectors', (e) => {
      const hits = map.queryRenderedFeatures(e.point, { layers: ['vessel-points'] });
      const mmsi = hits[0]?.properties?.mmsi ?? hits[0]?.id;
      if (mmsi != null) selectVessel(Number(mmsi), false);
    });

    ['clusters', 'vessel-points', 'vessel-vectors'].forEach((id) => {
      map.on('mouseenter', id, () => { map.getCanvas().style.cursor = 'pointer'; });
      map.on('mouseleave', id, () => { map.getCanvas().style.cursor = ''; });
    });

    updateMapSource();
    fitMapToVessels();
    startMapAnimation();
  });
}

function updateMapSource(nowMs = Date.now()) {
  if (!map?.isStyleLoaded()) return;
  const src = map.getSource('vessels');
  if (src) src.setData(buildGeoJson(nowMs));
  const vec = map.getSource('vessel-vectors');
  if (vec) vec.setData(buildCourseVectorsGeoJson(nowMs));
}

function updateDetailPosition(mmsi) {
  const v = vessels.get(mmsi);
  const el = $('detail-position');
  if (!v || !el) return;
  const pos = getDisplayPosition(v);
  if (pos) el.textContent = `${pos.lat.toFixed(4)}°, ${pos.lon.toFixed(4)}°`;
}

function startMapAnimation() {
  if (mapAnimTimer != null) return;
  const tick = () => {
    if (!document.hidden && map?.isStyleLoaded() && needsLiveMapUpdates()) {
      updateMapSource();
      if (selectedMmsi != null) updateDetailPosition(selectedMmsi);
    }
    mapAnimTimer = window.setTimeout(tick, MAP_ANIMATION_MS);
  };
  tick();
}

function fitMapToVessels() {
  if (!map || vessels.size === 0) return;
  const coords = [...vessels.values()]
    .filter((v) => v.lat != null && v.lon != null)
    .map((v) => [v.lon, v.lat]);
  if (coords.length === 0) return;
  if (coords.length === 1) {
    map.flyTo({ center: coords[0], zoom: 9 });
    return;
  }
  const bounds = coords.reduce(
    (b, c) => b.extend(c),
    new maplibregl.LngLatBounds(coords[0], coords[0]),
  );
  map.fitBounds(bounds, { padding: 60, maxZoom: 10, duration: 0 });
}

function wireUi() {
  $('btn-add').addEventListener('click', () => {
    $('add-error').hidden = true;
    $('add-mmsi').value = '';
    $('add-nickname').value = '';
    $('add-dialog').showModal();
    $('add-mmsi').focus();
  });

  $('add-cancel').addEventListener('click', () => $('add-dialog').close());

  $('add-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const errEl = $('add-error');
    errEl.hidden = true;

    const mmsi = parseMmsiInput($('add-mmsi').value);
    if (mmsi == null) {
      errEl.textContent = 'MMSI on 9 numeroa.';
      errEl.hidden = false;
      return;
    }

    const nickname = $('add-nickname').value.trim() || undefined;
    const btn = $('add-submit');
    btn.disabled = true;

    try {
      await addVessel(mmsi, nickname);
      $('add-dialog').close();
    } catch (err) {
      errEl.textContent = err.message || 'Lisäys epäonnistui.';
      errEl.hidden = false;
    } finally {
      btn.disabled = false;
    }
  });

  $('search').addEventListener('input', (e) => {
    searchQuery = e.target.value;
    renderList();
  });

  $('browse-search').addEventListener('input', (e) => {
    browseSearchQuery = e.target.value;
    if (panelMode === 'browse') {
      renderBrowseList();
      renderStats();
    }
  });

  $('btn-refresh-nearby').addEventListener('click', () => fetchNearbyVessels());

  $('traficom-toggle')?.addEventListener('change', (e) => {
    traficomEnabled = e.target.checked;
    saveTraficomPref();
    ensureTraficomLayer(traficomEnabled);
  });

  document.querySelectorAll('.panel-tab').forEach((btn) => {
    btn.addEventListener('click', () => {
      if (btn.dataset.panel === 'browse') openBrowsePanel();
      else openWatchlistPanel();
    });
  });

  document.querySelectorAll('.filter-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      filter = btn.dataset.filter ?? 'all';
      document.querySelectorAll('.filter-btn').forEach((b) => {
        const on = b === btn;
        b.classList.toggle('is-active', on);
        b.setAttribute('aria-selected', on ? 'true' : 'false');
      });
      renderList();
    });
  });

  $('detail-close').addEventListener('click', () => {
    selectedMmsi = null;
    browseSelectedMmsi = null;
    $('detail').hidden = true;
    if (panelMode === 'browse') renderBrowseList();
    else renderList();
    updateMapSource();
  });

  setInterval(() => {
    renderStats();
    renderList();
    if (selectedMmsi != null) renderDetail(selectedMmsi);
  }, 60_000);
}

async function bootstrap() {
  loadWatchlist();
  loadTraficomPref();
  wireUi();
  initMap();
  const traficomToggle = $('traficom-toggle');
  if (traficomToggle) traficomToggle.checked = traficomEnabled;
  connectMqtt();
  renderList();
  renderStats();

  for (const mmsi of vessels.keys()) {
    fetchVesselLocation(mmsi).then((loc) => {
      if (!loc || !vessels.has(mmsi)) return;
      mergeVessel(mmsi, loc);
      scheduleUiRefresh();
    });
  }
}

bootstrap();

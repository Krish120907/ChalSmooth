/**
 * map_view.js - Interactive Map, Markers, Search & Filters
 * (Parameters 2, 3, 4, 11, 12)
 */

import { appState } from '../state.js';
import { SEVERITY_CONFIG, ROAD_CONDITION_RATINGS } from '../data/sample_potholes.js';
import { showToast } from './notifications.js';

export class MapView {
  constructor(container) {
    this.container = container;
    this.map = null;
    this.markersLayer = null;
    this.userPuckMarker = null;
    this.currentTileLayer = null;
    this.tileStyle = "dark"; // dark | streets | satellite
  }

  render() {
    this.container.innerHTML = `
      <div class="map-view-layout">
        <!-- Leaflet Map Container (Parameter 2) -->
        <div id="leaflet-map"></div>

        <!-- Floating Search Bar (Parameter 11) -->
        <div class="map-floating-search-container">
          <div class="map-search-bar">
            <span class="map-search-icon">🔍</span>
            <input
              type="text"
              class="map-search-input"
              id="map-search-input"
              placeholder="Search roads, areas, or landmarks (e.g. Baner, FC Road)..."
              value="${appState.filters.searchQuery}"
            />
            <button class="map-search-clear" id="map-search-clear">✕</button>
          </div>
          <div class="map-search-results-dropdown" id="map-search-results"></div>
        </div>

        <!-- Floating Filter Toolbar (Parameter 12) -->
        <div class="map-floating-filters">
          <div class="filter-pill-group">
            <button class="filter-btn ${appState.filters.severity === 'all' ? 'active' : ''}" data-severity="all">
              All Hazards
            </button>
            <button class="filter-btn ${appState.filters.severity === 'low' ? 'active-low' : ''}" data-severity="low">
              🟡 Low
            </button>
            <button class="filter-btn ${appState.filters.severity === 'medium' ? 'active-medium' : ''}" data-severity="medium">
              🟠 Medium
            </button>
            <button class="filter-btn ${appState.filters.severity === 'high' ? 'active-high' : ''}" data-severity="high">
              🔴 High
            </button>
            <button class="filter-btn ${appState.filters.severity === 'critical' ? 'active-critical' : ''}" data-severity="critical">
              🟣 Critical
            </button>
          </div>
        </div>

        <!-- Map Control Action Dock (Parameter 4 & Tile Switcher) -->
        <div class="map-actions-dock">
          <button class="map-icon-btn" id="btn-recenter-location" title="Recenter to Current GPS Location">
            🎯
          </button>
          <button class="map-icon-btn" id="btn-toggle-tiles" title="Toggle Satellite / Dark Map Mode">
            🛰️
          </button>
          <button class="map-icon-btn" id="btn-map-add-pothole" title="Report Pothole at Map Center">
            ➕
          </button>
        </div>

        <!-- Map Legend (Parameter 3) -->
        <div class="map-legend-panel">
          <div class="legend-title">Hazard Severity Index</div>
          <div class="legend-items">
            <div class="legend-item"><span class="legend-dot legend-dot-low"></span> Low (&lt;4cm)</div>
            <div class="legend-item"><span class="legend-dot legend-dot-medium"></span> Medium (4-8cm)</div>
            <div class="legend-item"><span class="legend-dot legend-dot-high"></span> High (8-12cm)</div>
            <div class="legend-item"><span class="legend-dot legend-dot-critical"></span> Critical (&gt;12cm)</div>
          </div>
        </div>
      </div>
    `;

    this.initLeaflet();
    this.bindEvents();
  }

  initLeaflet() {
    const mapElement = this.container.querySelector('#leaflet-map');
    if (!mapElement || typeof L === 'undefined') return;

    if (this.map) {
      this.map.remove();
    }

    const { lat, lng } = appState.currentLocation;

    this.map = L.map(mapElement, {
      center: [lat, lng],
      zoom: 14,
      zoomControl: true
    });

    this.setTileLayer(this.tileStyle);
    this.markersLayer = L.layerGroup().addTo(this.map);

    this.renderUserLocationPuck();
    this.renderPotholeMarkers();

    // Map click to report pothole at exact position
    this.map.on('click', (e) => {
      // Optional: prompt user to report at tapped spot
    });

    this.map.on('contextmenu', (e) => {
      appState.openReportModal({
        lat: Number(e.latlng.lat.toFixed(5)),
        lng: Number(e.latlng.lng.toFixed(5)),
        address: `Selected GPS Pin (${e.latlng.lat.toFixed(4)}, ${e.latlng.lng.toFixed(4)})`
      });
      showToast('📍 Selected custom pin location for reporting!', 'info');
    });
  }

  setTileLayer(style) {
    if (this.currentTileLayer) {
      this.map.removeLayer(this.currentTileLayer);
    }
    this.tileStyle = style;
    if (style === 'satellite') {
      this.currentTileLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
        attribution: '&copy; Esri &mdash; Earthstar Geographics',
        maxZoom: 19
      });
    } else if (style === 'streets') {
      this.currentTileLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors',
        maxZoom: 19
      });
    } else {
      // Default: CartoDB Dark Matter
      this.currentTileLayer = L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
        attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
        subdomains: 'abcd',
        maxZoom: 20
      });
    }
    this.currentTileLayer.addTo(this.map);
  }

  renderUserLocationPuck() {
    if (!this.map) return;
    const { lat, lng } = appState.currentLocation;

    const puckIcon = L.divIcon({
      className: 'user-location-puck-wrapper',
      html: `
        <div class="user-location-puck">
          <div class="puck-core"></div>
          <div class="puck-pulse-halo"></div>
        </div>
      `,
      iconSize: [24, 24],
      iconAnchor: [12, 12]
    });

    if (this.userPuckMarker) {
      this.userPuckMarker.setLatLng([lat, lng]);
    } else {
      this.userPuckMarker = L.marker([lat, lng], { icon: puckIcon, zIndexOffset: 1000 }).addTo(this.map);
    }
  }

  renderPotholeMarkers() {
    if (!this.map || !this.markersLayer) return;
    this.markersLayer.clearLayers();

    const potholes = appState.getFilteredPotholes();

    potholes.forEach(ph => {
      const config = SEVERITY_CONFIG[ph.severity] || SEVERITY_CONFIG.medium;
      const isCritical = ph.severity === 'critical';

      const customIcon = L.divIcon({
        className: 'custom-pothole-marker',
        html: `
          <div style="position: relative; display: flex; align-items: center; justify-content: center;">
            ${isCritical ? '<div class="critical-pulse-ring"></div>' : ''}
            <svg class="pothole-pin-svg" width="30" height="30" viewBox="0 0 30 30" fill="none">
              <circle cx="15" cy="15" r="13" fill="${config.color}" fill-opacity="0.25" stroke="${config.color}" stroke-width="2.5"/>
              <circle cx="15" cy="15" r="6" fill="${config.color}"/>
            </svg>
          </div>
        `,
        iconSize: [30, 30],
        iconAnchor: [15, 15]
      });

      const marker = L.marker([ph.location.lat, ph.location.lng], { icon: customIcon });

      marker.on('click', () => {
        appState.selectPothole(ph.id);
      });

      marker.bindTooltip(`
        <div style="padding: 4px; font-family: 'Outfit', sans-serif;">
          <strong>${ph.title}</strong><br/>
          <span style="color:${config.color}; font-weight:700;">${ph.severity.toUpperCase()}</span> &bull; ${ph.location.roadName}
        </div>
      `, {
        direction: 'top',
        className: 'glass-tooltip'
      });

      this.markersLayer.addLayer(marker);
    });
  }

  bindEvents() {
    // Recenter Location (Parameter 4)
    this.container.querySelector('#btn-recenter-location')?.addEventListener('click', () => {
      if (this.map) {
        const { lat, lng } = appState.currentLocation;
        this.map.flyTo([lat, lng], 15, { animate: true, duration: 1 });
        showToast('🎯 Centered on your current GPS location', 'info');
      }
    });

    // Toggle Tiles
    this.container.querySelector('#btn-toggle-tiles')?.addEventListener('click', () => {
      const nextStyle = this.tileStyle === 'dark' ? 'satellite' : (this.tileStyle === 'satellite' ? 'streets' : 'dark');
      this.setTileLayer(nextStyle);
      showToast(`🗺️ Map style changed to ${nextStyle.toUpperCase()}`, 'info');
    });

    // Add Pothole FAB
    this.container.querySelector('#btn-map-add-pothole')?.addEventListener('click', () => {
      if (this.map) {
        const center = this.map.getCenter();
        appState.openReportModal({
          lat: Number(center.lat.toFixed(5)),
          lng: Number(center.lng.toFixed(5)),
          address: `Map Center Location (${center.lat.toFixed(4)}, ${center.lng.toFixed(4)})`
        });
      }
    });

    // Severity Filters (Parameter 12)
    this.container.querySelectorAll('.filter-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const severity = btn.getAttribute('data-severity');
        this.container.querySelectorAll('.filter-btn').forEach(b => {
          b.className = 'filter-btn';
        });
        btn.classList.add(severity === 'all' ? 'active' : `active-${severity}`);
        appState.setFilters({ severity });
        this.renderPotholeMarkers();
      });
    });

    // Search Bar (Parameter 11)
    const searchInput = this.container.querySelector('#map-search-input');
    const searchClear = this.container.querySelector('#map-search-clear');
    const searchDropdown = this.container.querySelector('#map-search-results');

    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        const query = e.target.value.trim();
        appState.setFilters({ searchQuery: query });
        this.renderPotholeMarkers();

        if (query.length > 1) {
          searchClear.style.display = 'block';
          const matches = appState.potholes.filter(p => 
            p.title.toLowerCase().includes(query.toLowerCase()) ||
            p.location.address.toLowerCase().includes(query.toLowerCase()) ||
            p.location.roadName.toLowerCase().includes(query.toLowerCase())
          );

          if (matches.length > 0) {
            searchDropdown.style.display = 'block';
            searchDropdown.innerHTML = matches.map(m => `
              <div class="search-result-item" data-lat="${m.location.lat}" data-lng="${m.location.lng}" data-id="${m.id}">
                <div>${SEVERITY_CONFIG[m.severity]?.icon || '📍'}</div>
                <div>
                  <div class="search-result-name">${m.title}</div>
                  <div class="search-result-type">${m.location.address}</div>
                </div>
              </div>
            `).join('');

            searchDropdown.querySelectorAll('.search-result-item').forEach(item => {
              item.addEventListener('click', () => {
                const lat = parseFloat(item.getAttribute('data-lat'));
                const lng = parseFloat(item.getAttribute('data-lng'));
                const id = item.getAttribute('data-id');
                this.map.flyTo([lat, lng], 16, { animate: true, duration: 1 });
                appState.selectPothole(id);
                searchDropdown.style.display = 'none';
              });
            });
          } else {
            searchDropdown.style.display = 'none';
          }
        } else {
          searchClear.style.display = 'none';
          searchDropdown.style.display = 'none';
        }
      });

      searchClear?.addEventListener('click', () => {
        searchInput.value = '';
        searchClear.style.display = 'none';
        searchDropdown.style.display = 'none';
        appState.setFilters({ searchQuery: '' });
        this.renderPotholeMarkers();
      });
    }
  }

  resize() {
    if (this.map) {
      setTimeout(() => this.map.invalidateSize(), 150);
    }
  }
}

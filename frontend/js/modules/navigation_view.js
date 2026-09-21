/**
 * navigation_view.js - Comfort-Prioritized Navigation & Lagrangian Lambda-Sweep
 * (Parameters 15, 16)
 */

import { appState } from '../state.js';
import { PRESET_ROUTES } from '../data/road_graph.js';
import { SEVERITY_CONFIG } from '../data/sample_potholes.js';
import { soundSynth } from '../utils/audio.js';
import { getComfortGrade, getRoughnessColor } from '../utils/helpers.js';
import { showToast } from './notifications.js';

export class NavigationView {
  constructor(container) {
    this.container = container;
    this.map = null;
    this.routePolylines = [];
    this.simCarMarker = null;
    this.animationTimer = null;
    this.currentPreset = PRESET_ROUTES[0];
  }

  render() {
    this.currentPreset = PRESET_ROUTES.find(r => r.id === appState.navigation.selectedPresetId) || PRESET_ROUTES[0];
    const lambda = appState.navigation.lambda; // 0 to 1

    // Interpolate metrics based on lambda
    const fastest = this.currentPreset.fastestRoute;
    const smoothest = this.currentPreset.smoothestRoute;

    const currentDuration = Math.round(fastest.durationMin + (smoothest.durationMin - fastest.durationMin) * lambda);
    const currentComfort = Math.round(fastest.comfortScore + (smoothest.comfortScore - fastest.comfortScore) * lambda);
    const currentRoughSegments = Math.round(fastest.roughSegmentsCount - (fastest.roughSegmentsCount - smoothest.roughSegmentsCount) * lambda);
    const currentPotholes = Math.round(fastest.potholesEncountered - (fastest.potholesEncountered - smoothest.potholesEncountered) * lambda);

    const deltaMin = currentDuration - fastest.durationMin;
    const avoidedShocksPercent = Math.round(((fastest.roughSegmentsCount - currentRoughSegments) / fastest.roughSegmentsCount) * 100);
    const comfortGrade = getComfortGrade(currentComfort);

    this.container.innerHTML = `
      <div class="navigation-view-layout">
        <!-- Navigation Map -->
        <div id="nav-map"></div>

        <!-- Turn-by-Turn Instruction Banner (Top Center) -->
        <div class="tbt-instruction-banner ${appState.navigation.isSimulating ? 'active' : ''}" id="tbt-banner">
          <div class="tbt-icon-bubble" id="tbt-icon">↱</div>
          <div class="tbt-info">
            <div class="tbt-distance" id="tbt-distance">In 250m</div>
            <div class="tbt-instruction" id="tbt-text">Keep Right on Pashan Bypass to avoid rough craters</div>
          </div>
        </div>

        <!-- Hazard Proximity Alert (Parameter 16) -->
        <div class="hazard-proximity-alert" id="hazard-proximity-alert">
          <span style="font-size: 26px;">⚠️</span>
          <div>
            <div style="font-family: var(--font-heading); font-weight: 800; color: #fff; font-size: 15px;">
              Caution: Severe Road Crater Ahead!
            </div>
            <div style="font-size: 12px; color: #fca5a5;">
              80m ahead in Center Lane &bull; Suggested speed: &le; 20 km/h
            </div>
          </div>
        </div>

        <!-- Navigation Control Panel (Bottom Sheet) -->
        <div class="nav-control-panel">
          <!-- Preset Route Selector -->
          <div class="route-selector-row">
            <div class="route-selector-label">Corridor Route</div>
            <select class="route-select-dropdown" id="select-nav-route">
              ${PRESET_ROUTES.map(r => `
                <option value="${r.id}" ${r.id === this.currentPreset.id ? 'selected' : ''}>
                  ${r.name} (${r.city})
                </option>
              `).join('')}
            </select>
          </div>

          <!-- Metrics Row -->
          <div class="nav-metrics-row">
            <div class="nav-metric-card">
              <div class="nav-metric-val">
                ${currentDuration} <span class="nav-metric-unit">min</span>
              </div>
              <div class="nav-metric-label">Estimated Time (${this.currentPreset.fastestRoute.distanceKm} km)</div>
            </div>
            <div class="nav-metric-card">
              <div class="nav-metric-val" style="color: ${comfortGrade.class.includes('emerald') ? '#10b981' : '#38bdf8'};">
                ${currentComfort} <span class="nav-metric-unit">/ 100</span>
              </div>
              <div class="nav-metric-label">Ride Comfort (${comfortGrade.label})</div>
            </div>
          </div>

          <!-- Trade-off Delta Chip (Lagrangian Comparison) -->
          <div class="tradeoff-delta-banner">
            <span class="delta-badge">${deltaMin === 0 ? '⚡ Fastest' : `+${deltaMin} min`}</span>
            <span>
              ${deltaMin === 0 
                ? 'Direct path with 11 road bumps/potholes' 
                : `${avoidedShocksPercent}% fewer pothole shocks (${currentPotholes} remaining)`}
            </span>
          </div>

          <!-- Lagrangian Time vs Comfort Slider (Parameter 15) -->
          <div class="comfort-slider-box">
            <div class="slider-labels-row">
              <span class="label-fastest">⚡ Fastest (${fastest.durationMin}m)</span>
              <span style="color: var(--text-muted); font-size: 10.5px;">&lambda;-sweep trade-off</span>
              <span class="label-smoothest">🧈 Smoothest (${smoothest.durationMin}m)</span>
            </div>
            <input
              type="range"
              class="comfort-range-slider"
              id="comfort-lambda-slider"
              min="0"
              max="1"
              step="0.05"
              value="${lambda}"
            />
          </div>

          <!-- Start Navigation & In-Car Actions -->
          <div class="nav-actions-row">
            <button class="btn-start-nav ${appState.navigation.isSimulating ? 'navigating' : ''}" id="btn-toggle-sim-nav">
              <span>${appState.navigation.isSimulating ? '⏹️' : '🚀'}</span>
              <span>${appState.navigation.isSimulating ? 'Stop Navigation Simulation' : 'Start Comfort Drive Simulation'}</span>
            </button>
            <button class="btn-secondary" id="btn-switch-hud" title="Switch to In-Car Driver HUD">
              <span>🚗</span> HUD
            </button>
          </div>
        </div>
      </div>
    `;

    this.initMap();
    this.bindEvents();
  }

  initMap() {
    const mapElement = this.container.querySelector('#nav-map');
    if (!mapElement || typeof L === 'undefined') return;

    if (this.map) {
      this.map.remove();
    }

    const startPoint = this.currentPreset.fastestRoute.waypoints[0];

    this.map = L.map(mapElement, {
      center: startPoint,
      zoom: 12,
      zoomControl: true
    });

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      attribution: '&copy; CARTO',
      subdomains: 'abcd',
      maxZoom: 20
    }).addTo(this.map);

    this.drawGradientRoute();
  }

  drawGradientRoute() {
    if (!this.map) return;
    this.routePolylines.forEach(layer => this.map.removeLayer(layer));
    this.routePolylines = [];

    const lambda = appState.navigation.lambda;
    const route = lambda > 0.5 ? this.currentPreset.smoothestRoute : this.currentPreset.fastestRoute;

    // Draw segment by segment with color gradient based on segment roughness
    const waypoints = route.waypoints;

    for (let i = 0; i < waypoints.length - 1; i++) {
      const p1 = waypoints[i];
      const p2 = waypoints[i + 1];
      const segmentInfo = route.segments[i] || { roughness: 20 };
      const color = getRoughnessColor(segmentInfo.roughness);

      const segmentPoly = L.polyline([p1, p2], {
        color: color,
        weight: 6,
        opacity: 0.85,
        lineCap: 'round',
        lineJoin: 'round'
      }).addTo(this.map);

      segmentPoly.bindTooltip(`
        <strong>${segmentInfo.from || 'Road'} ➔ ${segmentInfo.to || 'Next'}</strong><br/>
        Roughness: <span style="color:${color}; font-weight:700;">${segmentInfo.roughness}/100</span> (${segmentInfo.condition || 'good'})
      `, { direction: 'top', className: 'glass-tooltip' });

      this.routePolylines.push(segmentPoly);
    }

    // Origin & Destination Markers
    const startIcon = L.divIcon({
      html: `<div style="background:#10b981; color:#fff; border-radius:50%; width:24px; height:24px; display:flex; align-items:center; justify-content:center; font-size:12px; font-weight:800; border:2px solid #fff; box-shadow:0 0 10px #10b981;">A</div>`,
      iconSize: [24, 24],
      iconAnchor: [12, 12]
    });

    const endIcon = L.divIcon({
      html: `<div style="background:#ef4444; color:#fff; border-radius:50%; width:24px; height:24px; display:flex; align-items:center; justify-content:center; font-size:12px; font-weight:800; border:2px solid #fff; box-shadow:0 0 10px #ef4444;">B</div>`,
      iconSize: [24, 24],
      iconAnchor: [12, 12]
    });

    const mStart = L.marker(waypoints[0], { icon: startIcon }).addTo(this.map);
    const mEnd = L.marker(waypoints[waypoints.length - 1], { icon: endIcon }).addTo(this.map);
    this.routePolylines.push(mStart, mEnd);

    this.map.fitBounds(L.polyline(waypoints).getBounds(), { padding: [60, 60] });
  }

  startDriveSimulation() {
    appState.navigation.isSimulating = true;
    soundSynth.init();
    soundSynth.speak("Starting comfort-prioritized navigation. Route optimized for minimum road roughness.");
    showToast("🚀 Drive simulation started along optimal path!", "info");

    const lambda = appState.navigation.lambda;
    const route = lambda > 0.5 ? this.currentPreset.smoothestRoute : this.currentPreset.fastestRoute;
    const points = route.waypoints;

    let step = 0;
    const totalSteps = 100;

    const carIcon = L.divIcon({
      html: `
        <div style="background:#06b6d4; border:2.5px solid #fff; width:26px; height:26px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:14px; box-shadow:0 0 15px #06b6d4;">
          🚗
        </div>
      `,
      iconSize: [26, 26],
      iconAnchor: [13, 13]
    });

    if (this.simCarMarker) {
      this.map.removeLayer(this.simCarMarker);
    }
    this.simCarMarker = L.marker(points[0], { icon: carIcon, zIndexOffset: 2000 }).addTo(this.map);

    const hazardBanner = this.container.querySelector('#hazard-proximity-alert');
    const tbtBanner = this.container.querySelector('#tbt-banner');
    const tbtText = this.container.querySelector('#tbt-text');
    const tbtDist = this.container.querySelector('#tbt-distance');

    if (this.animationTimer) clearInterval(this.animationTimer);

    this.animationTimer = setInterval(() => {
      step++;
      if (step > totalSteps) {
        clearInterval(this.animationTimer);
        appState.navigation.isSimulating = false;
        soundSynth.speak("You have reached your destination safely on smooth road.");
        showToast("🏁 Destination reached! Ride comfort score: 92/100", "success");
        this.render();
        return;
      }

      // Interpolate position along polyline
      const progress = step / totalSteps;
      const pointIndex = Math.floor(progress * (points.length - 1));
      const nextIndex = Math.min(pointIndex + 1, points.length - 1);
      const subProgress = (progress * (points.length - 1)) - pointIndex;

      const lat = points[pointIndex][0] + (points[nextIndex][0] - points[pointIndex][0]) * subProgress;
      const lng = points[pointIndex][1] + (points[nextIndex][1] - points[pointIndex][1]) * subProgress;

      this.simCarMarker.setLatLng([lat, lng]);

      // Trigger Hazard Alert near 35% of route (University circle / Baner area)
      if (step === 35 && lambda < 0.6) {
        if (hazardBanner) hazardBanner.classList.add('active');
        soundSynth.playHazardAlert("critical");
        soundSynth.speak("Warning: Deep pothole crater in 80 meters. Reduce speed.");
      } else if (step === 45) {
        if (hazardBanner) hazardBanner.classList.remove('active');
      }

      // Update Turn by turn text
      if (tbtText && tbtDist) {
        if (step < 30) {
          tbtText.textContent = "Continue straight on Main Corridor";
          tbtDist.textContent = `In ${Math.round((30 - step) * 20)}m`;
        } else if (step < 70) {
          tbtText.textContent = "Take Pashan-Sus Resurfaced Bypass";
          tbtDist.textContent = `In ${Math.round((70 - step) * 20)}m`;
        } else {
          tbtText.textContent = "Arriving at Hinjewadi Phase 1 Circle";
          tbtDist.textContent = `In ${Math.round((100 - step) * 15)}m`;
        }
      }
    }, 200);
  }

  stopDriveSimulation() {
    if (this.animationTimer) clearInterval(this.animationTimer);
    appState.navigation.isSimulating = false;
    if (this.simCarMarker && this.map) {
      this.map.removeLayer(this.simCarMarker);
    }
    const hazardBanner = this.container.querySelector('#hazard-proximity-alert');
    if (hazardBanner) hazardBanner.classList.remove('active');
    this.render();
  }

  bindEvents() {
    // Route Selection Dropdown
    this.container.querySelector('#select-nav-route')?.addEventListener('change', (e) => {
      appState.navigation.selectedPresetId = e.target.value;
      this.currentPreset = PRESET_ROUTES.find(r => r.id === e.target.value) || PRESET_ROUTES[0];
      this.render();
    });

    // Comfort Slider
    const slider = this.container.querySelector('#comfort-lambda-slider');
    slider?.addEventListener('input', (e) => {
      const val = parseFloat(e.target.value);
      appState.setLambda(val);
      this.render();
    });

    // Toggle Simulation
    this.container.querySelector('#btn-toggle-sim-nav')?.addEventListener('click', () => {
      if (appState.navigation.isSimulating) {
        this.stopDriveSimulation();
      } else {
        this.startDriveSimulation();
      }
    });

    // Switch to HUD
    this.container.querySelector('#btn-switch-hud')?.addEventListener('click', () => {
      appState.setActiveTab('hud');
    });
  }

  resize() {
    if (this.map) {
      setTimeout(() => this.map.invalidateSize(), 150);
    }
  }
}

/**
 * dashboard_view.js - Home Dashboard & Road Condition metrics (Parameters 1, 10)
 */

import { appState } from '../state.js';
import { SEVERITY_CONFIG, ROAD_CONDITION_RATINGS } from '../data/sample_potholes.js';
import { CITY_HEALTH_STATS } from '../data/road_graph.js';
import { formatDate, getRoughnessColor } from '../utils/helpers.js';

export class DashboardView {
  constructor(container) {
    this.container = container;
  }

  render() {
    const stats = CITY_HEALTH_STATS.pune;
    const totalPotholes = appState.potholes.length;
    const activeHazards = appState.potholes.filter(p => p.status !== 'fixed').length;
    const fixedPotholes = appState.potholes.filter(p => p.status === 'fixed').length;
    const recentHazards = [...appState.potholes].slice(0, 5);

    this.container.innerHTML = `
      <div class="dashboard-view">
        <!-- Hero Section -->
        <div class="dashboard-hero">
          <div class="hero-banner">
            <div class="hero-content">
              <div class="hero-tag">✨ AI-Powered Comfort Navigation</div>
              <h1 class="hero-title">Navigate Smoothly. <span>Avoid Craters.</span></h1>
              <p class="hero-desc">
                ChalSmooth uses real-time 50Hz vehicle IMU vibrations and community telemetry to route you through the smoothest roads, saving your vehicle suspension and travel comfort.
              </p>
              <div class="hero-actions">
                <button class="btn-primary" id="btn-hero-nav">
                  <span>🗺️</span> Start Comfort Navigation
                </button>
                <button class="btn-secondary" id="btn-hero-report">
                  <span>📸</span> Report Pothole
                </button>
                <button class="btn-secondary" id="btn-hero-hud">
                  <span>🚗</span> In-Car HUD Mode
                </button>
              </div>
            </div>
          </div>

          <!-- Road Quality Index Gauge (Parameter 1 & 10) -->
          <div class="city-condition-card">
            <div class="form-label" style="font-size: 13px; text-transform: uppercase;">Pune Road Quality Index</div>
            <div class="condition-gauge-wrap">
              <svg class="condition-gauge-svg" viewBox="0 0 140 140">
                <defs>
                  <linearGradient id="gaugeGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                    <stop offset="0%" stop-color="#10b981" />
                    <stop offset="60%" stop-color="#06b6d4" />
                    <stop offset="100%" stop-color="#34d399" />
                  </linearGradient>
                </defs>
                <circle class="gauge-bg" cx="70" cy="70" r="58"></circle>
                <circle class="gauge-progress" cx="70" cy="70" r="58" style="stroke-dashoffset: ${(1 - stats.overallScore / 100) * 364};"></circle>
              </svg>
              <div class="gauge-center-text">
                <div class="gauge-score">${stats.overallScore}</div>
                <div class="gauge-label">/ 100 Index</div>
              </div>
            </div>
            <div class="city-condition-status">
              <span>🟢</span> Overall Condition: <strong>Good / Stable</strong>
            </div>
          </div>
        </div>

        <!-- Quick Stats Grid (Parameter 1) -->
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-icon-wrap stat-icon-cyan">📡</div>
            <div class="stat-info">
              <div class="stat-value">${(stats.totalMonitoredKm).toLocaleString()} km</div>
              <div class="stat-title">Roads Scanned</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap stat-icon-red">🕳️</div>
            <div class="stat-info">
              <div class="stat-value">${activeHazards}</div>
              <div class="stat-title">Active Potholes</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap stat-icon-green">✨</div>
            <div class="stat-info">
              <div class="stat-value">${fixedPotholes + stats.fixedThisMonth}</div>
              <div class="stat-title">Potholes Repaired</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap stat-icon-amber">🚙</div>
            <div class="stat-info">
              <div class="stat-value">${(stats.distinctPassesRecorded).toLocaleString()}</div>
              <div class="stat-title">Crowdsource IMU Passes</div>
            </div>
          </div>
        </div>

        <!-- 2-Column Split: Nearby Pothole Feed & Road Condition (Parameter 10) -->
        <div class="dashboard-content-split">
          <!-- Nearby Potholes Feed -->
          <div class="section-card">
            <div class="section-header">
              <div class="section-title">
                <span>📍</span> Nearby Pothole Reports
              </div>
              <a class="section-link" id="link-view-all-map">View on Map ➔</a>
            </div>
            <div class="hazard-feed-list">
              ${recentHazards.map(ph => `
                <div class="hazard-feed-item" data-id="${ph.id}">
                  <img src="${ph.photoUrl}" alt="pothole" class="hazard-thumb" />
                  <div class="hazard-item-info">
                    <div class="hazard-item-title">${ph.title}</div>
                    <div class="hazard-item-road">${ph.location.address}</div>
                  </div>
                  <div class="hazard-item-meta">
                    <span class="badge ${SEVERITY_CONFIG[ph.severity]?.badgeClass || 'badge-medium'}">
                      ${ph.severity}
                    </span>
                    <span class="hazard-time">${formatDate(ph.reportedDate)}</span>
                  </div>
                </div>
              `).join('')}
            </div>
          </div>

          <!-- Road Condition Status (Parameter 10: Good / Average / Poor / Dangerous) -->
          <div class="section-card">
            <div class="section-header">
              <div class="section-title">
                <span>🛣️</span> Key Road Condition Corridors
              </div>
              <span class="hero-tag" style="margin-bottom:0">Live Telemetry</span>
            </div>
            <div class="road-condition-list">
              ${stats.zones.map(zone => {
                const conditionInfo = ROAD_CONDITION_RATINGS[zone.condition] || ROAD_CONDITION_RATINGS.average;
                const barColor = getRoughnessColor(100 - zone.score);
                return `
                  <div class="road-condition-item">
                    <div class="road-condition-top">
                      <div class="road-condition-name">${zone.name}</div>
                      <span class="badge" style="background: ${conditionInfo.color}25; color: ${conditionInfo.color}; border: 1px solid ${conditionInfo.color}60;">
                        ${conditionInfo.icon} ${conditionInfo.label} (${zone.score}/100)
                      </span>
                    </div>
                    <div class="roughness-bar-track">
                      <div class="roughness-bar-fill" style="width: ${zone.score}%; background: ${barColor};"></div>
                    </div>
                  </div>
                `;
              }).join('')}
            </div>
          </div>
        </div>
      </div>
    `;

    this.bindEvents();
  }

  bindEvents() {
    this.container.querySelector('#btn-hero-nav')?.addEventListener('click', () => {
      appState.setActiveTab('navigation');
    });
    this.container.querySelector('#btn-hero-report')?.addEventListener('click', () => {
      appState.openReportModal();
    });
    this.container.querySelector('#btn-hero-hud')?.addEventListener('click', () => {
      appState.setActiveTab('hud');
    });
    this.container.querySelector('#link-view-all-map')?.addEventListener('click', () => {
      appState.setActiveTab('map');
    });

    this.container.querySelectorAll('.hazard-feed-item').forEach(item => {
      item.addEventListener('click', () => {
        const id = item.getAttribute('data-id');
        appState.selectPothole(id);
      });
    });
  }
}

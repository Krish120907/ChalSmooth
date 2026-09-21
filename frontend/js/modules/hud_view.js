/**
 * hud_view.js - In-Car HUD Fullscreen Night Driver Mode
 */

import { appState } from '../state.js';
import { soundSynth } from '../utils/audio.js';
import { showToast } from './notifications.js';

export class HudView {
  constructor(container) {
    this.container = container;
    this.speed = 48;
    this.speedTimer = null;
  }

  render() {
    this.container.innerHTML = `
      <div class="hud-view">
        <div class="hud-glow-orb"></div>

        <!-- HUD Top Bar -->
        <div class="hud-top-bar">
          <div style="display: flex; align-items: center; gap: 10px;">
            <span style="font-size: 20px;">🛡️</span>
            <span style="font-family: var(--font-heading); font-size: 16px; font-weight: 800;">CHALSMOOTH HUD</span>
          </div>
          <div class="hud-time-wrap" id="hud-clock">19:30</div>
          <button class="btn-secondary" id="btn-exit-hud" style="padding: 6px 14px; font-size: 12px;">
            Exit HUD
          </button>
        </div>

        <!-- HUD Center Cluster: Speedometer & Road Roughness Meter -->
        <div class="hud-center-cluster">
          <div class="speedometer-big" id="hud-speed-val">48</div>
          <div class="speed-unit-label">KM / H</div>

          <div class="hud-roughness-gauge">
            <div style="display: flex; align-items: center; justify-content: space-between; width: 100%; font-size: 11px; font-weight: 700; color: var(--text-muted);">
              <span>SMOOTH</span>
              <span>ROUGHNESS GAUGE</span>
              <span>CRATERS</span>
            </div>
            <div class="hud-roughness-bar">
              <div class="hud-roughness-fill" id="hud-roughness-fill"></div>
            </div>
            <div class="hud-roughness-status" id="hud-roughness-status">🟢 Smooth Asphalt Ahead</div>
          </div>
        </div>

        <!-- Upcoming Hazard Warning Card (Parameter 16) -->
        <div class="hud-hazard-alert-card">
          <div class="hud-hazard-icon">⚠️</div>
          <div>
            <div class="hud-hazard-title">Caution: Deep Crater in Center Lane</div>
            <div class="hud-hazard-dist">120m ahead &bull; University Circle Flyover Ramp</div>
          </div>
        </div>

        <!-- HUD Bottom Bar: One-Tap Instant Report Button -->
        <div class="hud-bottom-bar">
          <div style="font-size: 13px; color: var(--text-muted);">
            GPS Accuracy: <span style="color: #38bdf8;">&plusmn;3.2m</span> &bull; 50Hz IMU Active
          </div>
          <button class="btn-hud-instant-mark" id="btn-hud-quick-mark">
            <span>🔴</span> ONE-TAP MARK POTHOLE
          </button>
        </div>
      </div>
    `;

    this.startClock();
    this.startSpeedSim();
    this.bindEvents();
  }

  startClock() {
    const updateTime = () => {
      const clock = this.container.querySelector('#hud-clock');
      if (clock) {
        const now = new Date();
        clock.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      }
    };
    updateTime();
    setInterval(updateTime, 1000);
  }

  startSpeedSim() {
    if (this.speedTimer) clearInterval(this.speedTimer);
    this.speedTimer = setInterval(() => {
      this.speed = Math.min(80, Math.max(20, this.speed + (Math.random() - 0.48) * 4));
      const speedEl = this.container.querySelector('#hud-speed-val');
      if (speedEl) speedEl.textContent = Math.round(this.speed);
    }, 800);
  }

  bindEvents() {
    this.container.querySelector('#btn-exit-hud')?.addEventListener('click', () => {
      appState.setActiveTab('dashboard');
    });

    this.container.querySelector('#btn-hud-quick-mark')?.addEventListener('click', () => {
      soundSynth.playHazardAlert('high');
      appState.openReportModal({
        lat: appState.currentLocation.lat,
        lng: appState.currentLocation.lng,
        address: "Immediate HUD Marked Coordinate"
      });
      showToast('📍 Pothole location pinned instantly from HUD!', 'info');
    });
  }

  destroy() {
    if (this.speedTimer) clearInterval(this.speedTimer);
  }
}

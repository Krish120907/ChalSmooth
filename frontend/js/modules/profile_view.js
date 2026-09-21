/**
 * profile_view.js - User Profile & Report History (Parameters 17, 18)
 */

import { appState } from '../state.js';
import { SEVERITY_CONFIG, STATUS_CONFIG } from '../data/sample_potholes.js';
import { formatDate } from '../utils/helpers.js';
import { showToast } from './notifications.js';

export class ProfileView {
  constructor(container) {
    this.container = container;
  }

  render() {
    const user = appState.userProfile;
    const potholes = appState.potholes; // User can see all reports with filter for their own

    this.container.innerHTML = `
      <div class="profile-view">
        <!-- Profile Hero Card (Parameter 17) -->
        <div class="profile-hero-card">
          <div class="profile-main-info">
            <div class="profile-avatar-large">${user.avatar}</div>
            <div class="profile-name-group">
              <h1 class="profile-user-name">${user.name}</h1>
              <div class="profile-user-role">${user.role} &bull; ${user.level}</div>
              <div style="font-size: 12px; color: var(--text-muted);">${user.email}</div>
            </div>
          </div>
          <div style="display: flex; align-items: center; gap: 20px;">
            <div style="text-align: right;">
              <div style="font-family: var(--font-heading); font-size: 32px; font-weight: 800; color: var(--accent-amber);">
                ${user.points} XP
              </div>
              <div style="font-size: 11px; color: var(--text-muted); text-transform: uppercase;">Community Karma Score</div>
            </div>
            <button class="btn-primary" id="btn-profile-report">
              <span>📸</span> Report New Hazard
            </button>
          </div>
        </div>

        <!-- Badges Showcase (Parameter 17) -->
        <div class="glass-card">
          <div class="form-label" style="margin-bottom: 12px;">🏆 Contributor Achievements & Badges</div>
          <div class="badges-container">
            ${user.badges.map(b => `
              <div class="badge-card">
                <span class="badge-icon-lg">${b.icon}</span>
                <div class="badge-card-info">
                  <div class="badge-card-title">${b.title}</div>
                  <div class="badge-card-desc">${b.desc}</div>
                </div>
              </div>
            `).join('')}
          </div>
        </div>

        <!-- Lifetime Contribution Stats (Parameter 17) -->
        <div class="stats-grid">
          <div class="stat-card">
            <div class="stat-icon-wrap stat-icon-cyan">📍</div>
            <div class="stat-info">
              <div class="stat-value">${user.totalReports}</div>
              <div class="stat-title">Potholes Reported</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap stat-icon-green">🛡️</div>
            <div class="stat-info">
              <div class="stat-value">${user.verifiedReports}</div>
              <div class="stat-title">Verified by Sensor</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap stat-icon-amber">🛣️</div>
            <div class="stat-info">
              <div class="stat-value">${user.distanceScannedKm} km</div>
              <div class="stat-title">Road Scanned with IMU</div>
            </div>
          </div>
          <div class="stat-card">
            <div class="stat-icon-wrap stat-icon-red">🧈</div>
            <div class="stat-info">
              <div class="stat-value">${user.shocksAvoided}</div>
              <div class="stat-title">Vehicle Shocks Saved</div>
            </div>
          </div>
        </div>

        <!-- Report History Table (Parameter 18) -->
        <div class="history-section">
          <div class="history-header">
            <div class="section-title">
              <span>📋</span> Pothole Report History (${potholes.length})
            </div>
            <span class="hero-tag" style="margin-bottom: 0;">Live Community Feed</span>
          </div>

          <div class="history-table-card">
            <div style="display: grid; grid-template-columns: 80px 1.5fr 1fr 120px 120px 80px; gap: 16px; padding: 12px 20px; background: rgba(30, 41, 59, 0.7); font-size: 11px; font-weight: 700; text-transform: uppercase; color: var(--text-muted); border-bottom: 1px solid var(--border-subtle);">
              <div>Photo</div>
              <div>Title & Details</div>
              <div>Location</div>
              <div>Severity</div>
              <div>Status</div>
              <div>Action</div>
            </div>

            ${potholes.map(ph => {
              const sev = SEVERITY_CONFIG[ph.severity] || SEVERITY_CONFIG.medium;
              const statusObj = STATUS_CONFIG[ph.status] || STATUS_CONFIG.reported;
              return `
                <div class="history-list-item" data-id="${ph.id}">
                  <img src="${ph.photoUrl}" alt="thumbnail" class="history-thumb" />
                  <div class="history-title-col">
                    <div class="history-item-title">${ph.title}</div>
                    <div class="history-item-address">${ph.location.lane} &bull; Depth: ${ph.depthCm}cm</div>
                  </div>
                  <div style="font-size: 12.5px; color: var(--text-secondary);">
                    ${ph.location.roadName}
                  </div>
                  <div>
                    <span class="badge ${sev.badgeClass}">${ph.severity}</span>
                  </div>
                  <div>
                    <span class="badge badge-status-${ph.status}">${statusObj.label}</span>
                  </div>
                  <div>
                    <button class="btn-secondary btn-view-report-detail" data-id="${ph.id}" style="padding: 4px 10px; font-size: 11px;">
                      View
                    </button>
                  </div>
                </div>
              `;
            }).join('')}
          </div>
        </div>
      </div>
    `;

    this.bindEvents();
  }

  bindEvents() {
    this.container.querySelector('#btn-profile-report')?.addEventListener('click', () => {
      appState.openReportModal();
    });

    this.container.querySelectorAll('.btn-view-report-detail').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.stopPropagation();
        const id = btn.getAttribute('data-id');
        appState.selectPothole(id);
      });
    });

    this.container.querySelectorAll('.history-list-item').forEach(row => {
      row.addEventListener('click', () => {
        const id = row.getAttribute('data-id');
        appState.selectPothole(id);
      });
    });
  }
}

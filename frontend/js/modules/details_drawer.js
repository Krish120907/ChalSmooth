/**
 * details_drawer.js - Pothole Details & Status Tracking (Parameters 13, 14)
 */

import { appState } from '../state.js';
import { SEVERITY_CONFIG, STATUS_CONFIG } from '../data/sample_potholes.js';
import { formatDate } from '../utils/helpers.js';
import { showToast } from './notifications.js';

export class DetailsDrawer {
  constructor(container) {
    this.container = container;
  }

  render() {
    const pothole = appState.potholes.find(p => p.id === appState.selectedPotholeId);
    if (!pothole) {
      this.container.innerHTML = `
        <div class="details-drawer ${appState.isDetailsDrawerOpen ? 'open' : ''}" id="pothole-details-drawer"></div>
      `;
      return;
    }

    const sevConfig = SEVERITY_CONFIG[pothole.severity] || SEVERITY_CONFIG.medium;
    const currentStatus = pothole.status || 'reported';
    const statusObj = STATUS_CONFIG[currentStatus] || STATUS_CONFIG.reported;
    const currentStep = statusObj.stepIndex;

    this.container.innerHTML = `
      <div class="details-drawer ${appState.isDetailsDrawerOpen ? 'open' : ''}" id="pothole-details-drawer">
        <!-- Header -->
        <div class="drawer-header">
          <div style="display: flex; align-items: center; gap: 8px;">
            <span class="badge ${sevConfig.badgeClass}">${pothole.severity}</span>
            <span class="badge badge-status-${currentStatus}">${statusObj.label}</span>
          </div>
          <button class="drawer-close-btn" id="btn-close-details-drawer">✕</button>
        </div>

        <!-- Pothole Photo (Parameter 13) -->
        <div class="drawer-photo-wrap">
          <img src="${pothole.photoUrl}" alt="Pothole Photo" class="drawer-photo" />
          <div class="drawer-photo-badge">
            <span class="badge" style="background: rgba(0,0,0,0.7); backdrop-filter: blur(8px); color: #fff;">
              📸 Ground-Truth Photo
            </span>
          </div>
        </div>

        <!-- Main Info -->
        <div class="drawer-content">
          <h2 class="drawer-title">${pothole.title}</h2>
          <div class="drawer-address">
            <span>📍</span> ${pothole.location.address}
          </div>

          <!-- Parameter 14: Status Tracking Stepper -->
          <div class="status-tracker-box">
            <div class="status-tracker-title">Civic Repair Lifecycle</div>
            <div class="stepper-progress-track">
              <div class="stepper-step ${currentStep >= 1 ? (currentStep > 1 ? 'completed' : 'active') : ''}">
                <div class="step-bubble">${currentStep > 1 ? '✓' : '1'}</div>
                <div class="step-label">Reported</div>
              </div>
              <div class="stepper-step ${currentStep >= 2 ? (currentStep > 2 ? 'completed' : 'active') : ''}">
                <div class="step-bubble">${currentStep > 2 ? '✓' : '2'}</div>
                <div class="step-label">Verified</div>
              </div>
              <div class="stepper-step ${currentStep >= 3 ? (currentStep > 3 ? 'completed' : 'active') : ''}">
                <div class="step-bubble">${currentStep > 3 ? '✓' : '3'}</div>
                <div class="step-label">In Progress</div>
              </div>
              <div class="stepper-step ${currentStep >= 4 ? 'completed' : ''}">
                <div class="step-bubble">${currentStep >= 4 ? '✓' : '4'}</div>
                <div class="step-label">Fixed</div>
              </div>
            </div>

            <!-- Advance Status Action Buttons -->
            <div class="status-action-btns">
              ${currentStatus !== 'verified' && currentStatus !== 'fixed' ? `
                <button class="btn-status-step" id="btn-advance-verify">
                  <span>🛡️</span> Mark Verified
                </button>
              ` : ''}
              ${currentStatus !== 'in_progress' && currentStatus !== 'fixed' ? `
                <button class="btn-status-step" id="btn-advance-progress">
                  <span>🚧</span> Start Repair
                </button>
              ` : ''}
              ${currentStatus !== 'fixed' ? `
                <button class="btn-status-step" id="btn-advance-fixed" style="border-color: rgba(52, 211, 153, 0.4); color: #34d399;">
                  <span>✨</span> Mark Fixed
                </button>
              ` : `
                <div style="font-size: 12px; color: #34d399; font-weight: 700; text-align: center; width: 100%;">
                  🎉 Pothole Resolved & Road Surface Restored
                </div>
              `}
            </div>
          </div>

          <!-- Description (Parameter 13) -->
          <div class="glass-card">
            <div class="form-label" style="margin-bottom: 6px;">Hazard Analysis & Description</div>
            <p style="font-size: 13.5px; color: var(--text-secondary); line-height: 1.6;">
              ${pothole.description}
            </p>
          </div>

          <!-- Telemetry Specs Grid -->
          <div class="sensor-telemetry-grid">
            <div class="telemetry-item">
              <div class="telemetry-val">${pothole.depthCm} cm</div>
              <div class="telemetry-label">Estimated Depth</div>
            </div>
            <div class="telemetry-item">
              <div class="telemetry-val">${pothole.sensorPeakJerk || '4.2'} m/s³</div>
              <div class="telemetry-label">Peak Jerk Shock</div>
            </div>
            <div class="telemetry-item">
              <div class="telemetry-val">${pothole.passesCount}</div>
              <div class="telemetry-label">IMU Sensor Passes</div>
            </div>
          </div>

          <!-- Reporter & Upvotes -->
          <div style="display: flex; align-items: center; justify-content: space-between; padding-top: 8px;">
            <div style="display: flex; align-items: center; gap: 8px;">
              <span style="font-size: 20px;">${pothole.reportedBy?.avatar || '👤'}</span>
              <div>
                <div style="font-size: 13px; font-weight: 700;">${pothole.reportedBy?.name || 'Anonymous Rider'}</div>
                <div style="font-size: 11px; color: var(--text-muted);">${formatDate(pothole.reportedDate)}</div>
              </div>
            </div>
            <button class="btn-secondary" id="btn-upvote-pothole" style="padding: 6px 14px; font-size: 13px;">
              <span>👍</span> Upvote (${pothole.upvotes || 0})
            </button>
          </div>

          <!-- Action buttons: Navigate / Share -->
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 8px;">
            <button class="btn-primary" id="btn-avoid-route">
              <span>🗺️</span> Navigate Around
            </button>
            <button class="btn-secondary" id="btn-share-report">
              <span>📋</span> Copy Link
            </button>
          </div>
        </div>
      </div>
    `;

    this.bindEvents(pothole);
  }

  bindEvents(pothole) {
    this.container.querySelector('#btn-close-details-drawer')?.addEventListener('click', () => {
      appState.closeDetailsDrawer();
    });

    this.container.querySelector('#btn-upvote-pothole')?.addEventListener('click', () => {
      appState.upvotePothole(pothole.id);
      showToast('👍 Upvoted! Increased hazard priority in road routing engine.', 'info');
    });

    this.container.querySelector('#btn-advance-verify')?.addEventListener('click', () => {
      appState.updatePotholeStatus(pothole.id, 'verified', 'Verified by user community inspection');
      showToast('🛡️ Status updated to Verified!', 'success');
      this.render();
    });

    this.container.querySelector('#btn-advance-progress')?.addEventListener('click', () => {
      appState.updatePotholeStatus(pothole.id, 'in_progress', 'Repair crew assigned by municipal authority');
      showToast('🚧 Status updated to Repair in Progress!', 'info');
      this.render();
    });

    this.container.querySelector('#btn-advance-fixed')?.addEventListener('click', () => {
      appState.updatePotholeStatus(pothole.id, 'fixed', 'Pothole patched and resurfaced');
      showToast('✨ Status marked as Fixed & Smooth! +100 XP awarded', 'success');
      this.render();
    });

    this.container.querySelector('#btn-avoid-route')?.addEventListener('click', () => {
      appState.closeDetailsDrawer();
      appState.setActiveTab('navigation');
      showToast('🗺️ Routing initialized with pothole avoidance weights', 'info');
    });

    this.container.querySelector('#btn-share-report')?.addEventListener('click', () => {
      if (navigator.clipboard) {
        navigator.clipboard.writeText(`${window.location.origin}/#pothole=${pothole.id}`);
        showToast('📋 Report link copied to clipboard!', 'info');
      }
    });
  }
}

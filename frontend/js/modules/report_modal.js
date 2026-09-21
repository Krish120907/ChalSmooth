/**
 * report_modal.js - Report Pothole Modal & Form (Parameters 5, 6, 7, 8, 9)
 */

import { appState } from '../state.js';
import { generateId } from '../utils/helpers.js';
import { showToast } from './notifications.js';

export class ReportModal {
  constructor(container) {
    this.container = container;
    this.selectedSeverity = "medium";
    this.uploadedPhotoUrl = "https://images.unsplash.com/photo-1515162816999-a0c47dc192f7?auto=format&fit=crop&w=800&q=80";
  }

  render() {
    const prefill = appState.reportPrefillLocation || appState.currentLocation;

    this.container.innerHTML = `
      <div class="modal-backdrop ${appState.isReportModalOpen ? 'open' : ''}" id="report-modal-backdrop">
        <div class="report-modal-card">
          <!-- Modal Header -->
          <div class="modal-header">
            <div class="modal-title-wrap">
              <span class="modal-icon">📸</span>
              <div class="modal-title">Report Road Pothole / Hazard</div>
            </div>
            <button class="modal-close-btn" id="btn-close-report-modal">✕</button>
          </div>

          <!-- Modal Body Form -->
          <form id="form-report-pothole" class="modal-body">
            <!-- Parameter 6: Upload Photo -->
            <div class="form-group">
              <label class="form-label">
                <span>Upload Pothole Photo</span>
                <span class="form-hint">AI verifies depth & width</span>
              </label>
              <div class="photo-upload-container">
                <div class="photo-dropzone" id="photo-dropzone">
                  <div class="dropzone-icon">📷</div>
                  <div class="dropzone-text">Click or drag & drop photo of pothole</div>
                  <img id="photo-preview" src="${this.uploadedPhotoUrl}" class="photo-preview-image" style="display: block;" />
                </div>
                <input type="file" id="file-photo-input" accept="image/*" style="display: none;" />
                <div class="photo-quick-presets">
                  <span style="font-size: 11px; color: var(--text-muted);">Quick Sample Presets:</span>
                  <button type="button" class="preset-chip" data-photo="https://images.unsplash.com/photo-1515162816999-a0c47dc192f7?auto=format&fit=crop&w=800&q=80">Deep Crater</button>
                  <button type="button" class="preset-chip" data-photo="https://images.unsplash.com/photo-1541888946425-d0fbb18086f6?auto=format&fit=crop&w=800&q=80">Trench</button>
                  <button type="button" class="preset-chip" data-photo="https://images.unsplash.com/photo-1578844251758-2f71da64c96f?auto=format&fit=crop&w=800&q=80">Manhole</button>
                </div>
              </div>
            </div>

            <!-- Parameter 8: Severity Selection -->
            <div class="form-group">
              <label class="form-label">
                <span>Severity Rating</span>
                <span class="form-hint">Impact on ride safety</span>
              </label>
              <div class="severity-selection-grid">
                <div class="severity-card ${this.selectedSeverity === 'low' ? 'selected-low' : ''}" data-sev="low">
                  <div class="severity-card-icon">🟡</div>
                  <div class="severity-card-title">Low</div>
                  <div class="severity-card-depth">&lt; 4 cm depth</div>
                </div>
                <div class="severity-card ${this.selectedSeverity === 'medium' ? 'selected-medium' : ''}" data-sev="medium">
                  <div class="severity-card-icon">🟠</div>
                  <div class="severity-card-title">Medium</div>
                  <div class="severity-card-depth">4 – 8 cm depth</div>
                </div>
                <div class="severity-card ${this.selectedSeverity === 'high' ? 'selected-high' : ''}" data-sev="high">
                  <div class="severity-card-icon">🔴</div>
                  <div class="severity-card-title">High</div>
                  <div class="severity-card-depth">8 – 12 cm depth</div>
                </div>
                <div class="severity-card ${this.selectedSeverity === 'critical' ? 'selected-critical' : ''}" data-sev="critical">
                  <div class="severity-card-icon">🟣</div>
                  <div class="severity-card-title">Critical</div>
                  <div class="severity-card-depth">&gt; 12 cm depth</div>
                </div>
              </div>
            </div>

            <!-- Parameter 7: GPS Location -->
            <div class="form-group">
              <label class="form-label">
                <span>GPS Location</span>
                <span class="form-hint">Auto-tagged via GNSS</span>
              </label>
              <div class="gps-location-card">
                <div class="gps-info-wrap">
                  <div class="gps-address" id="report-gps-address">${prefill.address || 'Pune Road Segment'}</div>
                  <div class="gps-coords" id="report-gps-coords">${prefill.lat}, ${prefill.lng}</div>
                </div>
                <button type="button" class="btn-gps-locate" id="btn-re-geolocate">
                  <span>📍</span> Refresh GPS
                </button>
              </div>
            </div>

            <!-- Parameter 9: Title & Road Details -->
            <div class="form-group">
              <label class="form-label">Pothole Title / Landmark</label>
              <input
                type="text"
                id="input-pothole-title"
                class="form-input"
                required
                placeholder="e.g. Deep crater near University Circle Flyover descent"
                value="Deep crater on main road corridor"
              />
            </div>

            <div class="form-group">
              <label class="form-label">Road Lane & Dimensions</label>
              <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                <select id="select-pothole-lane" class="form-select">
                  <option value="Center Lane">Center Lane</option>
                  <option value="Left Lane (Curbside)">Left Lane (Curbside)</option>
                  <option value="Right Lane (Fast Lane)">Right Lane (Fast Lane)</option>
                  <option value="Full Road Width">Full Road Width</option>
                </select>
                <input
                  type="text"
                  id="input-pothole-depth"
                  class="form-input"
                  placeholder="Estimated depth (e.g. 10 cm)"
                  value="8.5 cm"
                />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">
                <span>Detailed Description (Optional)</span>
                <span class="form-hint">Hazard context & vehicle risk</span>
              </label>
              <textarea
                id="textarea-pothole-desc"
                class="form-textarea"
                placeholder="Describe dangerous edges, standing water, suspension impact or swerve risks..."
              >Severe impact for two-wheelers and small cars. Water collects during rain.</textarea>
            </div>

            <!-- Modal Footer -->
            <div class="modal-footer" style="padding: 16px 0 0 0; margin-top: 10px;">
              <button type="button" class="btn-secondary" id="btn-cancel-report">Cancel</button>
              <button type="submit" class="btn-primary">
                <span>🚀</span> Submit Pothole Report (+50 XP)
              </button>
            </div>
          </form>
        </div>
      </div>
    `;

    this.bindEvents();
  }

  bindEvents() {
    const backdrop = this.container.querySelector('#report-modal-backdrop');
    const closeBtn = this.container.querySelector('#btn-close-report-modal');
    const cancelBtn = this.container.querySelector('#btn-cancel-report');

    const handleClose = () => {
      appState.closeReportModal();
    };

    closeBtn?.addEventListener('click', handleClose);
    cancelBtn?.addEventListener('click', handleClose);
    backdrop?.addEventListener('click', (e) => {
      if (e.target === backdrop) handleClose();
    });

    // Severity Card Selection (Parameter 8)
    this.container.querySelectorAll('.severity-card').forEach(card => {
      card.addEventListener('click', () => {
        const sev = card.getAttribute('data-sev');
        this.selectedSeverity = sev;
        this.container.querySelectorAll('.severity-card').forEach(c => {
          c.className = 'severity-card';
        });
        card.classList.add(`selected-${sev}`);
      });
    });

    // Preset Photo Selection (Parameter 6)
    this.container.querySelectorAll('.preset-chip').forEach(btn => {
      btn.addEventListener('click', () => {
        const url = btn.getAttribute('data-photo');
        this.uploadedPhotoUrl = url;
        const preview = this.container.querySelector('#photo-preview');
        if (preview) {
          preview.src = url;
          preview.style.display = 'block';
        }
      });
    });

    // File input trigger
    const dropzone = this.container.querySelector('#photo-dropzone');
    const fileInput = this.container.querySelector('#file-photo-input');
    dropzone?.addEventListener('click', () => fileInput?.click());

    fileInput?.addEventListener('change', (e) => {
      const file = e.target.files?.[0];
      if (file) {
        const reader = new FileReader();
        reader.onload = (event) => {
          this.uploadedPhotoUrl = event.target.result;
          const preview = this.container.querySelector('#photo-preview');
          if (preview) {
            preview.src = this.uploadedPhotoUrl;
            preview.style.display = 'block';
          }
        };
        reader.readAsDataURL(file);
      }
    });

    // Form Submit (Parameter 5)
    const form = this.container.querySelector('#form-report-pothole');
    form?.addEventListener('submit', (e) => {
      e.preventDefault();

      const title = this.container.querySelector('#input-pothole-title').value.trim();
      const lane = this.container.querySelector('#select-pothole-lane').value;
      const depthVal = parseFloat(this.container.querySelector('#input-pothole-depth').value) || 8.0;
      const desc = this.container.querySelector('#textarea-pothole-desc').value.trim();
      const prefill = appState.reportPrefillLocation || appState.currentLocation;

      const newPothole = {
        id: generateId("ph"),
        title: title || "Reported Road Crater",
        description: desc || "Reported by community driver.",
        severity: this.selectedSeverity,
        depthCm: depthVal,
        widthCm: depthVal * 6,
        location: {
          lat: prefill.lat,
          lng: prefill.lng,
          address: prefill.address || "Road Segment, Pune",
          roadName: prefill.address ? prefill.address.split(',')[0] : "Main Road",
          city: "Pune",
          lane: lane
        },
        status: "reported",
        reportedDate: new Date().toISOString(),
        reportedBy: {
          name: appState.userProfile.name,
          avatar: appState.userProfile.avatar,
          karma: appState.userProfile.points
        },
        passesCount: 1,
        sensorPeakJerk: this.selectedSeverity === 'critical' ? 5.1 : (this.selectedSeverity === 'high' ? 3.8 : 2.2),
        avgComfortImpact: this.selectedSeverity === 'critical' ? -42 : -20,
        upvotes: 1,
        photoUrl: this.uploadedPhotoUrl,
        workOrderNumber: null,
        history: [
          { step: "reported", date: new Date().toISOString(), note: "Reported with photographic evidence." }
        ]
      };

      appState.addPothole(newPothole);
      appState.closeReportModal();
      showToast('🎉 Pothole reported successfully! +50 XP added to your profile', 'success');
    });
  }
}

/**
 * sensor_studio.js - 50 Hz IMU Sensor Stream & ML Detection Classifier Studio
 */

import { soundSynth } from '../utils/audio.js';
import { showToast } from './notifications.js';

export class SensorStudio {
  constructor(container) {
    this.container = container;
    this.canvas = null;
    this.ctx = null;
    this.animFrame = null;
    this.scenario = "smooth"; // smooth | pothole | bump | rough | brake
    this.historyData = {
      accelX: new Array(128).fill(0),
      accelY: new Array(128).fill(0),
      accelZ: new Array(128).fill(9.8),
      gyroZ: new Array(128).fill(0)
    };
    this.time = 0;
    this.detectedClass = "Uneven Road";
    this.confidence = 88;
  }

  render() {
    this.container.innerHTML = `
      <div class="studio-view">
        <!-- Top Scenario Controls -->
        <div class="studio-top-controls">
          <div>
            <div style="font-family: var(--font-heading); font-size: 16px; font-weight: 800;">
              📡 Real-Time 50 Hz IMU Telemetry & ML Classifier Studio
            </div>
            <div style="font-size: 12px; color: var(--text-muted);">
              Sampling Accelerometer (X, Y, Z) & Gyroscope at 20ms intervals with 128-sample sliding window.
            </div>
          </div>
          <div class="scenario-buttons-group">
            <button class="scenario-btn ${this.scenario === 'smooth' ? 'active' : ''}" data-scenario="smooth">
              🛣️ Smooth Highway
            </button>
            <button class="scenario-btn ${this.scenario === 'pothole' ? 'active' : ''}" data-scenario="pothole">
              💥 Pothole Impact (4.8g)
            </button>
            <button class="scenario-btn ${this.scenario === 'bump' ? 'active' : ''}" data-scenario="bump">
              ⚡ Speed Bump Hit
            </button>
            <button class="scenario-btn ${this.scenario === 'rough' ? 'active' : ''}" data-scenario="rough">
              🧱 Cobblestone Rough
            </button>
          </div>
        </div>

        <!-- Studio 2-Column Grid -->
        <div class="studio-grid">
          <!-- Oscilloscope Card -->
          <div class="oscilloscope-card">
            <div style="display: flex; align-items: center; justify-content: space-between;">
              <div class="form-label" style="margin: 0;">Multi-Axis Waveform Oscilloscope (50 Hz)</div>
              <div style="display: flex; align-items: center; gap: 12px; font-size: 11px; font-weight: 700;">
                <span style="color: #10b981;">&bull; Accel Z (Vertical)</span>
                <span style="color: #06b6d4;">&bull; Accel Y (Longitudinal)</span>
                <span style="color: #f59e0b;">&bull; Accel X (Lateral)</span>
              </div>
            </div>
            <div class="oscilloscope-canvas-wrap">
              <canvas id="oscilloscope-canvas"></canvas>
            </div>

            <!-- Feature Extraction 128-Sample Window Breakdown -->
            <div class="form-label">Windowed Feature Extraction (128 Samples @ 2.56s)</div>
            <div class="features-grid">
              <div class="feature-box">
                <div class="feature-val" id="feat-rms">9.84 m/s²</div>
                <div class="feature-lbl">RMS Energy</div>
              </div>
              <div class="feature-box">
                <div class="feature-val" id="feat-jerk" style="color: #ef4444;">0.42 m/s³</div>
                <div class="feature-lbl">Peak Vertical Jerk</div>
              </div>
              <div class="feature-box">
                <div class="feature-val" id="feat-zcr">14 Hz</div>
                <div class="feature-lbl">Zero-Crossing Rate</div>
              </div>
              <div class="feature-box">
                <div class="feature-val" id="feat-fft-low">0.12 W</div>
                <div class="feature-lbl">FFT 0-5Hz (Slope)</div>
              </div>
              <div class="feature-box">
                <div class="feature-val" id="feat-fft-mid">0.34 W</div>
                <div class="feature-lbl">FFT 5-15Hz (Bounce)</div>
              </div>
              <div class="feature-box">
                <div class="feature-val" id="feat-fft-high">0.08 W</div>
                <div class="feature-lbl">FFT 15-25Hz (Impact)</div>
              </div>
            </div>
          </div>

          <!-- ML Inference Output & Buffer Card -->
          <div class="inference-output-card">
            <div class="form-label" style="margin: 0;">Random Forest / TFLite Inference</div>
            
            <div class="ml-prediction-display">
              <div style="font-size: 12px; color: var(--text-muted); text-transform: uppercase;">Class Output</div>
              <div class="ml-class-detected" id="ml-predicted-class">Smooth Surface</div>
              <div style="font-size: 13px; font-weight: 700; color: #fff; margin-top: 4px;">
                Confidence: <span id="ml-confidence-num">96%</span>
              </div>
              <div class="ml-confidence-bar">
                <div class="ml-confidence-fill" id="ml-confidence-fill" style="width: 96%;"></div>
              </div>
            </div>

            <!-- Preprocessing Pipeline Specs -->
            <div class="glass-card" style="padding: 12px; font-size: 12px; color: var(--text-secondary); line-height: 1.6;">
              <div style="font-weight: 700; color: #fff; margin-bottom: 4px;">📐 Preprocessing Pipeline:</div>
              &bull; Gravity Alignment: <span style="color: #34d399;">Active (Rotation Vector)</span><br/>
              &bull; Resampling: <span style="color: #38bdf8;">Fixed 50 Hz Grid</span><br/>
              &bull; Debounce Filter: <span style="color: #fbbf24;">15m / 3.0s Suppression</span><br/>
              &bull; Batch Queue: <span style="color: #a78bfa;">32 Detections Buffered</span>
            </div>

            <button class="btn-primary" id="btn-trigger-detection-test" style="width: 100%;">
              <span>💥</span> Trigger Sudden Pothole Impact
            </button>
          </div>
        </div>
      </div>
    `;

    this.initCanvas();
    this.bindEvents();
  }

  initCanvas() {
    this.canvas = this.container.querySelector('#oscilloscope-canvas');
    if (!this.canvas) return;
    this.ctx = this.canvas.getContext('2d');

    const rect = this.canvas.parentElement.getBoundingClientRect();
    this.canvas.width = rect.width;
    this.canvas.height = rect.height;

    this.startOscilloscopeLoop();
  }

  startOscilloscopeLoop() {
    if (this.animFrame) cancelAnimationFrame(this.animFrame);

    const loop = () => {
      this.time += 0.05;
      this.generateSensorSample();
      this.drawCanvas();
      this.updateFeatureValues();
      this.animFrame = requestAnimationFrame(loop);
    };

    this.animFrame = requestAnimationFrame(loop);
  }

  generateSensorSample() {
    let az = 9.8 + (Math.random() - 0.5) * 0.4;
    let ay = (Math.random() - 0.5) * 0.2;
    let ax = (Math.random() - 0.5) * 0.2;

    if (this.scenario === "pothole") {
      // Sudden sharp negative dip followed by massive rebound spike
      const shock = Math.sin(this.time * 8) * Math.exp(-Math.abs(Math.sin(this.time * 0.8)) * 3);
      az = 9.8 + shock * 18.0;
      ay += (Math.random() - 0.5) * 4.0;
      ax += (Math.random() - 0.5) * 3.0;
      this.detectedClass = "Pothole (Class 0)";
      this.confidence = 94;
    } else if (this.scenario === "bump") {
      // Smooth sinusoidal rise and drop
      const bump = Math.sin(this.time * 4) * 6.5;
      az = 9.8 + bump;
      this.detectedClass = "Speed Bump (Class 1)";
      this.confidence = 91;
    } else if (this.scenario === "rough") {
      az = 9.8 + (Math.random() - 0.5) * 4.2;
      ay += (Math.random() - 0.5) * 1.8;
      this.detectedClass = "Uneven Road (Class 2)";
      this.confidence = 87;
    } else {
      this.detectedClass = "Smooth Surface";
      this.confidence = 97;
    }

    this.historyData.accelZ.push(az);
    this.historyData.accelZ.shift();

    this.historyData.accelY.push(ay);
    this.historyData.accelY.shift();

    this.historyData.accelX.push(ax);
    this.historyData.accelX.shift();
  }

  drawCanvas() {
    if (!this.ctx || !this.canvas) return;
    const w = this.canvas.width;
    const h = this.canvas.height;
    const ctx = this.ctx;

    ctx.clearRect(0, 0, w, h);

    // Grid lines
    ctx.strokeStyle = "rgba(255, 255, 255, 0.05)";
    ctx.lineWidth = 1;
    for (let x = 0; x < w; x += 40) {
      ctx.beginPath();
      ctx.moveTo(x, 0);
      ctx.lineTo(x, h);
      ctx.stroke();
    }
    for (let y = 0; y < h; y += 30) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    }

    // Zero-G & 1G Center Line
    ctx.strokeStyle = "rgba(16, 185, 129, 0.2)";
    ctx.beginPath();
    ctx.moveTo(0, h / 2);
    ctx.lineTo(w, h / 2);
    ctx.stroke();

    // Plot Accel Z (Green)
    this.plotWaveform(this.historyData.accelZ, "#10b981", 9.8, 25);
    // Plot Accel Y (Cyan)
    this.plotWaveform(this.historyData.accelY, "#06b6d4", 0, 15);
    // Plot Accel X (Amber)
    this.plotWaveform(this.historyData.accelX, "#f59e0b", 0, 15);
  }

  plotWaveform(dataArray, color, baseline, scale) {
    const ctx = this.ctx;
    const w = this.canvas.width;
    const h = this.canvas.height;
    const step = w / (dataArray.length - 1);
    const midY = h / 2;

    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    ctx.beginPath();

    for (let i = 0; i < dataArray.length; i++) {
      const val = dataArray[i] - baseline;
      const x = i * step;
      const y = midY - (val * (h / (2 * scale)));
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  }

  updateFeatureValues() {
    const classEl = this.container.querySelector('#ml-predicted-class');
    const confNum = this.container.querySelector('#ml-confidence-num');
    const confFill = this.container.querySelector('#ml-confidence-fill');
    const featJerk = this.container.querySelector('#feat-jerk');

    if (classEl) {
      classEl.textContent = this.detectedClass;
      if (this.detectedClass.includes("Pothole")) classEl.style.color = "#ef4444";
      else if (this.detectedClass.includes("Speed Bump")) classEl.style.color = "#f59e0b";
      else if (this.detectedClass.includes("Uneven")) classEl.style.color = "#38bdf8";
      else classEl.style.color = "#10b981";
    }

    if (confNum && confFill) {
      confNum.textContent = `${this.confidence}%`;
      confFill.style.width = `${this.confidence}%`;
      confFill.style.background = this.detectedClass.includes("Pothole") ? "#ef4444" : "#10b981";
    }

    if (featJerk) {
      const jerkVal = this.scenario === 'pothole' ? '4.85 m/s³' : (this.scenario === 'bump' ? '2.40 m/s³' : '0.45 m/s³');
      featJerk.textContent = jerkVal;
    }
  }

  bindEvents() {
    this.container.querySelectorAll('.scenario-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const sc = btn.getAttribute('data-scenario');
        this.scenario = sc;
        this.container.querySelectorAll('.scenario-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        if (sc === 'pothole') {
          soundSynth.playHazardAlert('high');
        }
      });
    });

    this.container.querySelector('#btn-trigger-detection-test')?.addEventListener('click', () => {
      this.scenario = 'pothole';
      this.container.querySelectorAll('.scenario-btn').forEach(b => b.classList.remove('active'));
      this.container.querySelector('[data-scenario="pothole"]')?.classList.add('active');
      soundSynth.playHazardAlert('critical');
      showToast('💥 High-G vertical shock detected! Triggered Pothole Event window.', 'hazard');
    });
  }

  destroy() {
    if (this.animFrame) cancelAnimationFrame(this.animFrame);
  }
}

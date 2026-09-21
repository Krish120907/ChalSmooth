/**
 * notifications.js - Toast Notifications & Proximity Hazard Alerts (Parameter 16)
 */

import { soundSynth } from '../utils/audio.js';

export function showToast(message, type = "info", duration = 3500) {
  const container = document.getElementById('toast-container');
  if (!container) return;

  const toast = document.createElement('div');
  toast.className = `toast ${type === 'hazard' ? 'toast-hazard' : ''}`;

  let icon = 'ℹ️';
  if (type === 'success') icon = '✅';
  if (type === 'hazard' || type === 'error') icon = '⚠️';
  if (type === 'pothole') icon = '🕳️';

  toast.innerHTML = `
    <span style="font-size: 18px;">${icon}</span>
    <span style="flex: 1;">${message}</span>
  `;

  container.appendChild(toast);

  if (type === 'hazard') {
    soundSynth.playHazardAlert('high');
  }

  setTimeout(() => {
    toast.style.transition = 'opacity 0.3s, transform 0.3s';
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(40px)';
    setTimeout(() => {
      if (toast.parentNode) {
        toast.parentNode.removeChild(toast);
      }
    }, 300);
  }, duration);
}

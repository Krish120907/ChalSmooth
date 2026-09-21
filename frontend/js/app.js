/**
 * app.js - Master application controller for ChalSmooth
 */

import { appState } from './state.js';
import { DashboardView } from './modules/dashboard_view.js';
import { MapView } from './modules/map_view.js';
import { NavigationView } from './modules/navigation_view.js';
import { SensorStudio } from './modules/sensor_studio.js';
import { HudView } from './modules/hud_view.js';
import { ProfileView } from './modules/profile_view.js';
import { ReportModal } from './modules/report_modal.js';
import { DetailsDrawer } from './modules/details_drawer.js';
import { showToast } from './modules/notifications.js';

class App {
  constructor() {
    this.views = {};
    this.reportModal = null;
    this.detailsDrawer = null;
  }

  init() {
    // Instantiate view managers
    this.views.dashboard = new DashboardView(document.getElementById('view-dashboard'));
    this.views.map = new MapView(document.getElementById('view-map'));
    this.views.navigation = new NavigationView(document.getElementById('view-navigation'));
    this.views.studio = new SensorStudio(document.getElementById('view-studio'));
    this.views.hud = new HudView(document.getElementById('view-hud'));
    this.views.profile = new ProfileView(document.getElementById('view-profile'));

    // Instantiate modals and drawers
    this.reportModal = new ReportModal(document.getElementById('report-modal-root'));
    this.detailsDrawer = new DetailsDrawer(document.getElementById('details-drawer-root'));

    // Render initial views
    this.views.dashboard.render();
    this.views.map.render();
    this.views.navigation.render();
    this.views.studio.render();
    this.views.hud.render();
    this.views.profile.render();

    this.reportModal.render();
    this.detailsDrawer.render();

    this.bindHeaderEvents();
    this.subscribeToState();

    // Welcome Toast
    setTimeout(() => {
      showToast('✨ ChalSmooth loaded with real-time IMU sensing & comfort routing!', 'success');
    }, 500);
  }

  bindHeaderEvents() {
    // Nav Tabs
    document.querySelectorAll('.nav-tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const tab = btn.getAttribute('data-tab');
        appState.setActiveTab(tab);
      });
    });

    // Brand Logo Click -> Dashboard
    document.querySelector('.brand-section')?.addEventListener('click', () => {
      appState.setActiveTab('dashboard');
    });

    // Top Header Report Button
    document.querySelector('#btn-header-report')?.addEventListener('click', () => {
      appState.openReportModal();
    });

    // User Profile Pill Click
    document.querySelector('#btn-header-profile')?.addEventListener('click', () => {
      appState.setActiveTab('profile');
    });

    // Vehicle Type Selector
    document.querySelector('#header-vehicle-select')?.addEventListener('change', (e) => {
      appState.setVehicleType(e.target.value);
      showToast(`🚗 Vehicle profile set to ${e.target.value.toUpperCase()}`, 'info');
    });
  }

  subscribeToState() {
    appState.subscribe((changeType, payload) => {
      if (changeType === 'tab_change') {
        this.switchTabUI(payload.tab);
      } else if (changeType === 'pothole_selected' || changeType === 'drawer_closed' || changeType === 'pothole_updated') {
        this.detailsDrawer.render();
      } else if (changeType === 'report_modal_opened' || changeType === 'report_modal_closed') {
        this.reportModal.render();
      } else if (changeType === 'pothole_added') {
        this.views.dashboard.render();
        this.views.map.renderPotholeMarkers();
        this.views.profile.render();
      }
    });
  }

  switchTabUI(activeTab) {
    // Update header tabs active class
    document.querySelectorAll('.nav-tab-btn').forEach(btn => {
      if (btn.getAttribute('data-tab') === activeTab) {
        btn.classList.add('active');
      } else {
        btn.classList.remove('active');
      }
    });

    // Switch view panels
    document.querySelectorAll('.view-panel').forEach(panel => {
      panel.classList.remove('active');
    });

    const activePanel = document.getElementById(`view-${activeTab}`);
    if (activePanel) {
      activePanel.classList.add('active');
    }

    // Trigger map resizing when map or nav tab is shown
    if (activeTab === 'map' && this.views.map) {
      this.views.map.resize();
    } else if (activeTab === 'navigation' && this.views.navigation) {
      this.views.navigation.resize();
    } else if (activeTab === 'studio' && this.views.studio) {
      this.views.studio.initCanvas();
    }
  }
}

// Bootstrap application on DOM ready
document.addEventListener('DOMContentLoaded', () => {
  const app = new App();
  app.init();
});

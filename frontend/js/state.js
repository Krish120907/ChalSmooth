/**
 * state.js - Reactive state store for ChalSmooth
 */

import { INITIAL_POTHOLES } from './data/sample_potholes.js';

const STORAGE_KEYS = {
  POTHOLES: "chalsmooth_potholes_v1",
  USER_PROFILE: "chalsmooth_user_profile_v1",
  SETTINGS: "chalsmooth_settings_v1",
  SAVED_ROUTES: "chalsmooth_saved_routes_v1"
};

class AppState {
  constructor() {
    this.listeners = new Set();
    
    // Load persisted or seed
    this.potholes = this.loadPotholes();
    this.userProfile = this.loadUserProfile();
    this.settings = this.loadSettings();

    // Runtime state
    this.currentLocation = {
      lat: 18.5284,
      lng: 73.8350,
      accuracy: 12,
      heading: 45,
      speedKmh: 0,
      address: "Senapati Bapat Rd, Pune"
    };

    this.activeTab = "dashboard"; // dashboard | map | navigation | studio | hud | analytics | profile | history
    this.selectedPotholeId = null;
    this.isReportModalOpen = false;
    this.isDetailsDrawerOpen = false;

    // Filters
    this.filters = {
      severity: "all", // all | low | medium | high | critical
      status: "all",   // all | reported | verified | in_progress | fixed
      searchQuery: "",
      radiusKm: 15,
      hideFixed: false
    };

    // Navigation & Comfort
    this.navigation = {
      selectedPresetId: "route-pune-1",
      lambda: 0.5, // 0 = fastest, 1 = smoothest
      isNavigating: false,
      isSimulating: false,
      simulationProgress: 0, // 0 to 1
      activeRoute: null,
      currentSpeed: 42,
      nearbyAlert: null
    };

    // Vehicle profile
    this.vehicle = {
      type: "car", // car | sedan | suv | scooter | auto
      suspensionStiffness: "medium"
    };
  }

  loadPotholes() {
    try {
      const stored = localStorage.getItem(STORAGE_KEYS.POTHOLES);
      if (stored) {
        return JSON.parse(stored);
      }
    } catch (e) {
      console.warn("Could not load stored potholes, using seed dataset", e);
    }
    return [...INITIAL_POTHOLES];
  }

  savePotholes() {
    try {
      localStorage.setItem(STORAGE_KEYS.POTHOLES, JSON.stringify(this.potholes));
    } catch (e) {
      console.warn("Could not persist potholes", e);
    }
  }

  loadUserProfile() {
    const defaultProfile = {
      name: "Anjali Kulkarni",
      role: "Road Guardian 🛡️",
      avatar: "👩‍💻",
      email: "anjali@chalsmooth.io",
      points: 840,
      level: "Level 4 Contributor",
      totalReports: 12,
      verifiedReports: 9,
      distanceScannedKm: 340.5,
      shocksAvoided: 184,
      badges: [
        { id: "b1", title: "Pothole Hunter", desc: "Reported 10+ validated road craters", icon: "🎯" },
        { id: "b2", title: "Smooth Navigator", desc: "Chose 50+ comfort routes", icon: "🧈" },
        { id: "b3", title: "Sensor Contributor", desc: "Streamed >100km IMU telemetry", icon: "📡" },
        { id: "b4", title: "Civic Hero", desc: "5 reported potholes repaired", icon: "🏆" }
      ]
    };
    try {
      const stored = localStorage.getItem(STORAGE_KEYS.USER_PROFILE);
      if (stored) return { ...defaultProfile, ...JSON.parse(stored) };
    } catch (e) {}
    return defaultProfile;
  }

  saveUserProfile() {
    try {
      localStorage.setItem(STORAGE_KEYS.USER_PROFILE, JSON.stringify(this.userProfile));
    } catch (e) {}
  }

  loadSettings() {
    const defaultSettings = {
      audioAlerts: true,
      voiceGuidance: true,
      mapTheme: "dark", // dark | streets | satellite
      autoRecordIMU: true,
      dpdpConsentGiven: true
    };
    try {
      const stored = localStorage.getItem(STORAGE_KEYS.SETTINGS);
      if (stored) return { ...defaultSettings, ...JSON.parse(stored) };
    } catch (e) {}
    return defaultSettings;
  }

  saveSettings() {
    try {
      localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(this.settings));
    } catch (e) {}
  }

  subscribe(listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  notify(changeType, payload = {}) {
    this.listeners.forEach(fn => {
      try {
        fn(changeType, payload, this);
      } catch (e) {
        console.error("State listener error:", e);
      }
    });
  }

  // State actions
  setActiveTab(tab) {
    this.activeTab = tab;
    this.notify("tab_change", { tab });
  }

  setFilters(newFilters) {
    this.filters = { ...this.filters, ...newFilters };
    this.notify("filters_change", { filters: this.filters });
  }

  addPothole(pothole) {
    this.potholes.unshift(pothole);
    this.userProfile.totalReports += 1;
    this.userProfile.points += 50;
    this.savePotholes();
    this.saveUserProfile();
    this.notify("pothole_added", { pothole });
  }

  updatePotholeStatus(id, newStatus, note = "") {
    const ph = this.potholes.find(p => p.id === id);
    if (ph) {
      ph.status = newStatus;
      if (!ph.history) ph.history = [];
      ph.history.push({
        step: newStatus,
        date: new Date().toISOString(),
        note: note || `Status updated to ${newStatus}`
      });
      if (newStatus === "fixed") {
        ph.passesCount += 50;
        this.userProfile.points += 100;
        this.saveUserProfile();
      }
      this.savePotholes();
      this.notify("pothole_updated", { pothole: ph });
    }
  }

  upvotePothole(id) {
    const ph = this.potholes.find(p => p.id === id);
    if (ph) {
      ph.upvotes = (ph.upvotes || 0) + 1;
      this.savePotholes();
      this.notify("pothole_updated", { pothole: ph });
    }
  }

  deletePothole(id) {
    this.potholes = this.potholes.filter(p => p.id !== id);
    this.savePotholes();
    this.notify("pothole_deleted", { id });
  }

  selectPothole(id) {
    this.selectedPotholeId = id;
    this.isDetailsDrawerOpen = !!id;
    this.notify("pothole_selected", { id });
  }

  closeDetailsDrawer() {
    this.isDetailsDrawerOpen = false;
    this.selectedPotholeId = null;
    this.notify("drawer_closed");
  }

  openReportModal(prefillLocation = null) {
    this.isReportModalOpen = true;
    this.reportPrefillLocation = prefillLocation;
    this.notify("report_modal_opened", { prefillLocation });
  }

  closeReportModal() {
    this.isReportModalOpen = false;
    this.reportPrefillLocation = null;
    this.notify("report_modal_closed");
  }

  setLambda(val) {
    this.navigation.lambda = Math.max(0, Math.min(1, val));
    this.notify("lambda_changed", { lambda: this.navigation.lambda });
  }

  setCurrentLocation(coords) {
    this.currentLocation = { ...this.currentLocation, ...coords };
    this.notify("location_changed", { location: this.currentLocation });
  }

  setVehicleType(type) {
    this.vehicle.type = type;
    this.notify("vehicle_changed", { vehicle: this.vehicle });
  }

  getFilteredPotholes() {
    return this.potholes.filter(ph => {
      if (this.filters.severity !== "all" && ph.severity !== this.filters.severity) {
        return false;
      }
      if (this.filters.status !== "all" && ph.status !== this.filters.status) {
        return false;
      }
      if (this.filters.hideFixed && ph.status === "fixed") {
        return false;
      }
      if (this.filters.searchQuery.trim() !== "") {
        const query = this.filters.searchQuery.toLowerCase();
        const matchTitle = ph.title.toLowerCase().includes(query);
        const matchRoad = ph.location.roadName.toLowerCase().includes(query);
        const matchAddress = ph.location.address.toLowerCase().includes(query);
        if (!matchTitle && !matchRoad && !matchAddress) {
          return false;
        }
      }
      return true;
    });
  }
}

export const appState = new AppState();

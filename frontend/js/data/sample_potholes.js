/**
 * sample_potholes.js - Rich dataset for ChalSmooth
 * Contains realistic road hazards, potholes, speed bumps, and road conditions.
 */

export const INITIAL_POTHOLES = [
  {
    id: "ph-101",
    title: "Deep Crater near University Circle Flyover",
    description: "Severe pothole right in the middle lane after the flyover descent. Hard impact for low clearance sedans and two-wheelers.",
    severity: "critical", // low | medium | high | critical
    depthCm: 14.5,
    widthCm: 85,
    location: {
      lat: 18.5362,
      lng: 73.8298,
      address: "Ganeshkhind Rd, near Pune University Circle, Shivajinagar",
      roadName: "Ganeshkhind Road",
      city: "Pune",
      lane: "Center Lane"
    },
    status: "verified", // reported | verified | in_progress | fixed
    reportedDate: "2026-09-18T08:30:00Z",
    reportedBy: {
      name: "Rohan Deshmukh",
      avatar: "👨‍💻",
      karma: 420
    },
    passesCount: 87,
    sensorPeakJerk: 4.8, // m/s^3
    avgComfortImpact: -38,
    upvotes: 42,
    photoUrl: "https://images.unsplash.com/photo-1515162816999-a0c47dc192f7?auto=format&fit=crop&w=800&q=80",
    workOrderNumber: "PMC-RD-2026-8812",
    history: [
      { step: "reported", date: "2026-09-18T08:30:00Z", note: "Reported by rider Rohan D." },
      { step: "verified", date: "2026-09-18T14:15:00Z", note: "Confirmed by 14 autonomous vehicle IMU passes (>3.5g shock)." }
    ]
  },
  {
    id: "ph-102",
    title: "Successive Asphalt Trench on Baner High Street",
    description: "Repeated road surface erosion along storm drain junction. Dangerous during rain and low visibility.",
    severity: "high",
    depthCm: 9.8,
    widthCm: 60,
    location: {
      lat: 18.5590,
      lng: 73.7795,
      address: "Baner Rd, opposite Balewadi Phata, Baner",
      roadName: "Baner Road",
      city: "Pune",
      lane: "Left Lane"
    },
    status: "in_progress",
    reportedDate: "2026-09-15T11:20:00Z",
    reportedBy: {
      name: "Priya Sharma",
      avatar: "👩‍🔬",
      karma: 650
    },
    passesCount: 142,
    sensorPeakJerk: 3.9,
    avgComfortImpact: -25,
    upvotes: 68,
    photoUrl: "https://images.unsplash.com/photo-1541888946425-d0fbb18086f6?auto=format&fit=crop&w=800&q=80",
    workOrderNumber: "PMC-RD-2026-7901",
    history: [
      { step: "reported", date: "2026-09-15T11:20:00Z", note: "Initial crowd report submitted." },
      { step: "verified", date: "2026-09-15T16:00:00Z", note: "Auto-verified by 50+ ChalSmooth sensing trips." },
      { step: "in_progress", date: "2026-09-20T09:00:00Z", note: "PMC Road Maintenance contractor dispatched." }
    ]
  },
  {
    id: "ph-103",
    title: "Sunken Utility Manhole on Senapati Bapat Road",
    description: "Manhole cover is sunken 7cm below asphalt grade. Creates severe vertical jar when driven over at >30km/h.",
    severity: "medium",
    depthCm: 6.8,
    widthCm: 70,
    location: {
      lat: 18.5284,
      lng: 73.8320,
      address: "Senapati Bapat Rd, near Chatushrungi Temple",
      roadName: "Senapati Bapat Road",
      city: "Pune",
      lane: "Right Lane"
    },
    status: "verified",
    reportedDate: "2026-09-19T14:45:00Z",
    reportedBy: {
      name: "Aditya Patil",
      avatar: "🚗",
      karma: 210
    },
    passesCount: 94,
    sensorPeakJerk: 2.6,
    avgComfortImpact: -18,
    upvotes: 29,
    photoUrl: "https://images.unsplash.com/photo-1578844251758-2f71da64c96f?auto=format&fit=crop&w=800&q=80",
    workOrderNumber: "PMC-RD-2026-9043",
    history: [
      { step: "reported", date: "2026-09-19T14:45:00Z", note: "Reported with photo." },
      { step: "verified", date: "2026-09-19T18:30:00Z", note: "IMU variance verified over 30 passes." }
    ]
  },
  {
    id: "ph-104",
    title: "Surface Edge Pothole on FC Road",
    description: "Small but sharp edge pothole near bus stop curb. Minor bump for cars, high slip risk for two-wheelers.",
    severity: "low",
    depthCm: 3.5,
    widthCm: 30,
    location: {
      lat: 18.5222,
      lng: 73.8415,
      address: "Fergusson College Rd, Deccan Gymkhana",
      roadName: "FC Road",
      city: "Pune",
      lane: "Left Lane"
    },
    status: "reported",
    reportedDate: "2026-09-21T09:10:00Z",
    reportedBy: {
      name: "Anjali Kulkarni",
      avatar: "🛵",
      karma: 150
    },
    passesCount: 19,
    sensorPeakJerk: 1.4,
    avgComfortImpact: -8,
    upvotes: 9,
    photoUrl: "https://images.unsplash.com/photo-1590486803833-1c5dc8ddd4c8?auto=format&fit=crop&w=800&q=80",
    workOrderNumber: null,
    history: [
      { step: "reported", date: "2026-09-21T09:10:00Z", note: "Reported via mobile app." }
    ]
  },
  {
    id: "ph-105",
    title: "Repaired Patch - Hinjewadi Phase 1 Circle",
    description: "Former large pothole cluster near Wipro Circle. Successfully resurfaced and re-leveled.",
    severity: "low",
    depthCm: 1.0,
    widthCm: 150,
    location: {
      lat: 18.5912,
      lng: 73.7389,
      address: "Hinjewadi Main Rd, Phase 1 Circle, Hinjewadi",
      roadName: "Hinjewadi Main Road",
      city: "Pune",
      lane: "All Lanes"
    },
    status: "fixed",
    reportedDate: "2026-08-10T10:00:00Z",
    reportedBy: {
      name: "Karan Mehta",
      avatar: "🚙",
      karma: 890
    },
    passesCount: 310,
    sensorPeakJerk: 0.4,
    avgComfortImpact: +2,
    upvotes: 112,
    photoUrl: "https://images.unsplash.com/photo-1621905251189-08b45d6a269e?auto=format&fit=crop&w=800&q=80",
    workOrderNumber: "MIDC-RD-2026-4421",
    history: [
      { step: "reported", date: "2026-08-10T10:00:00Z", note: "Reported as Critical." },
      { step: "verified", date: "2026-08-11T12:00:00Z", note: "Verified with 100+ passes." },
      { step: "in_progress", date: "2026-08-25T08:00:00Z", note: "Resurfacing underway." },
      { step: "fixed", date: "2026-09-02T16:00:00Z", note: "Fixed! Sensor readings now show smooth 92/100 score." }
    ]
  },
  {
    id: "ph-106",
    title: "Severe Road Dip near Swargate Underpass",
    description: "Sharp asphalt collapse before the underpass entry. Causes bottoming out on standard vehicles.",
    severity: "critical",
    depthCm: 16.0,
    widthCm: 95,
    location: {
      lat: 18.5018,
      lng: 73.8585,
      address: "Satara Rd, near Swargate Flyover, Swargate",
      roadName: "Satara Road",
      city: "Pune",
      lane: "Center Lane"
    },
    status: "verified",
    reportedDate: "2026-09-17T17:05:00Z",
    reportedBy: {
      name: "Sameer Joshi",
      avatar: "🏍️",
      karma: 340
    },
    passesCount: 160,
    sensorPeakJerk: 5.2,
    avgComfortImpact: -45,
    upvotes: 84,
    photoUrl: "https://images.unsplash.com/photo-1584463699026-664448550181?auto=format&fit=crop&w=800&q=80",
    workOrderNumber: "PMC-RD-2026-8910",
    history: [
      { step: "reported", date: "2026-09-17T17:05:00Z", note: "Reported by 3 commuters." },
      { step: "verified", date: "2026-09-17T19:20:00Z", note: "Verified by 45 vehicle shock triggers." }
    ]
  },
  {
    id: "ph-107",
    title: "Rough Patch & Speed Bump Erosion - Viman Nagar",
    description: "Unmarked deformed speed bump with adjoining potholes near Symbiosis campus.",
    severity: "medium",
    depthCm: 7.2,
    widthCm: 110,
    location: {
      lat: 18.5679,
      lng: 73.9143,
      address: "Viman Nagar Rd, near Symbiosis Rd, Viman Nagar",
      roadName: "Viman Nagar Road",
      city: "Pune",
      lane: "Both Lanes"
    },
    status: "reported",
    reportedDate: "2026-09-20T16:15:00Z",
    reportedBy: {
      name: "Sneha Nair",
      avatar: "👩‍💼",
      karma: 190
    },
    passesCount: 41,
    sensorPeakJerk: 2.9,
    avgComfortImpact: -21,
    upvotes: 18,
    photoUrl: "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?auto=format&fit=crop&w=800&q=80",
    workOrderNumber: null,
    history: [
      { step: "reported", date: "2026-09-20T16:15:00Z", note: "Reported with photos." }
    ]
  },
  {
    id: "ph-108",
    title: "Deep Pothole on Paud Road Kothrud",
    description: "Large water-filled crater opposite Vanaz Metro Station.",
    severity: "high",
    depthCm: 11.2,
    widthCm: 75,
    location: {
      lat: 18.5074,
      lng: 73.8062,
      address: "Paud Rd, near Vanaz Metro Station, Kothrud",
      roadName: "Paud Road",
      city: "Pune",
      lane: "Right Lane"
    },
    status: "in_progress",
    reportedDate: "2026-09-14T07:45:00Z",
    reportedBy: {
      name: "Vikram Rane",
      avatar: "🚗",
      karma: 510
    },
    passesCount: 118,
    sensorPeakJerk: 4.1,
    avgComfortImpact: -32,
    upvotes: 53,
    photoUrl: "https://images.unsplash.com/photo-1541888946425-d0fbb18086f6?auto=format&fit=crop&w=800&q=80",
    workOrderNumber: "PMC-RD-2026-7788",
    history: [
      { step: "reported", date: "2026-09-14T07:45:00Z", note: "Reported." },
      { step: "verified", date: "2026-09-14T11:00:00Z", note: "Verified by 80 passes." },
      { step: "in_progress", date: "2026-09-21T07:00:00Z", note: "Patching crew assigned." }
    ]
  }
];

export const SEVERITY_CONFIG = {
  low: {
    label: "Low Severity",
    badgeClass: "badge-low",
    color: "#eab308", // Yellow
    icon: "🟡",
    impact: "Minor surface roughness / shallow pothole (<4cm)",
    carAdvice: "Minimal impact; slight steering adjustment"
  },
  medium: {
    label: "Medium Severity",
    badgeClass: "badge-medium",
    color: "#f97316", // Orange
    icon: "🟠",
    impact: "Noticeable vertical jar (4–8cm depth)",
    carAdvice: "Slow down to 20-30 km/h; watch wheel rims"
  },
  high: {
    label: "High Severity",
    badgeClass: "badge-high",
    color: "#ef4444", // Red
    icon: "🔴",
    impact: "Severe road crater (8–12cm depth) with sharp edge",
    carAdvice: "Avoid lane or brake smoothly; high risk of tire cut"
  },
  critical: {
    label: "Critical Hazard",
    badgeClass: "badge-critical",
    color: "#d946ef", // Neon Magenta
    icon: "🟣",
    impact: "Dangerous deep collapse (>12cm), suspension damage risk",
    carAdvice: "DANGER: Sudden swerve hazard. Full bypass recommended!"
  }
};

export const STATUS_CONFIG = {
  reported: {
    label: "Reported",
    stepIndex: 1,
    color: "#94a3b8",
    description: "Submitted by citizen / sensor trigger. Awaiting multi-pass validation."
  },
  verified: {
    label: "Verified",
    stepIndex: 2,
    color: "#38bdf8",
    description: "Confirmed by ≥3 independent vehicle IMU passes with >2.0g impact."
  },
  in_progress: {
    label: "Repair In Progress",
    stepIndex: 3,
    color: "#fbbf24",
    description: "Work order issued to municipal road contractor; crew on site."
  },
  fixed: {
    label: "Fixed & Smooth",
    stepIndex: 4,
    color: "#34d399",
    description: "Road restored; sensor readings confirmed smooth driving condition."
  }
};

export const ROAD_CONDITION_RATINGS = {
  good: {
    label: "Good Condition",
    color: "#10b981", // Emerald Green
    description: "Smooth asphalt, minimal vibrations (Roughness score 0–30)",
    icon: "🟢"
  },
  average: {
    label: "Average Condition",
    color: "#eab308", // Yellow
    description: "Minor irregularities, occasional rumble (Roughness score 31–55)",
    icon: "🟡"
  },
  poor: {
    label: "Poor Condition",
    color: "#f97316", // Orange
    description: "Frequent dips, moderate potholes (Roughness score 56–75)",
    icon: "🟠"
  },
  dangerous: {
    label: "Dangerous",
    color: "#ef4444", // Red
    description: "Severe craters, broken pavement (Roughness score 76–100)",
    icon: "🔴"
  }
};

/**
 * road_graph.js - Road network graph for Lagrangian lambda-sweep routing
 * Contains metropolitan nodes, directed edges, segment roughness, and pre-computed routes.
 */

export const PRESET_ROUTES = [
  {
    id: "route-pune-1",
    name: "Pune Station ➔ Hinjewadi IT Park",
    city: "Pune",
    origin: { name: "Pune Railway Station", lat: 18.5289, lng: 73.8744 },
    destination: { name: "Hinjewadi Phase 1 Circle", lat: 18.5912, lng: 73.7389 },
    fastestRoute: {
      id: "fastest",
      name: "Via Old Mumbai-Pune Hwy & Wakad Flyover",
      distanceKm: 18.4,
      durationMin: 38,
      comfortScore: 58,
      roughSegmentsCount: 9,
      potholesEncountered: 11,
      roughnessAvg: 62,
      waypoints: [
        [18.5289, 73.8744],
        [18.5305, 73.8650],
        [18.5332, 73.8505],
        [18.5365, 73.8340], // University Circle (Pothole area)
        [18.5440, 73.8180],
        [18.5580, 73.7850], // Baner Highway
        [18.5720, 73.7600], // Wakad Bridge
        [18.5850, 73.7460], // Hinjewadi Flyover
        [18.5912, 73.7389]
      ],
      segments: [
        { from: "Station", to: "Sangam Bridge", roughness: 22, condition: "good", lengthKm: 2.1 },
        { from: "Sangam Bridge", to: "Shivajinagar", roughness: 45, condition: "average", lengthKm: 2.3 },
        { from: "Shivajinagar", to: "Univ Circle", roughness: 82, condition: "dangerous", lengthKm: 2.8 },
        { from: "Univ Circle", to: "Aundh Chest Hosp", roughness: 68, condition: "poor", lengthKm: 3.2 },
        { from: "Aundh Chest Hosp", to: "Wakad Bridge", roughness: 78, condition: "dangerous", lengthKm: 4.0 },
        { from: "Wakad Bridge", to: "Hinjewadi Phase 1", roughness: 55, condition: "average", lengthKm: 4.0 }
      ]
    },
    smoothestRoute: {
      id: "smoothest",
      name: "Via Pashan-Sus Expressway Bypass",
      distanceKm: 20.8,
      durationMin: 42,
      comfortScore: 92,
      roughSegmentsCount: 2,
      potholesEncountered: 2,
      roughnessAvg: 18,
      waypoints: [
        [18.5289, 73.8744],
        [18.5220, 73.8520], // FC Road
        [18.5310, 73.8260], // SB Road North
        [18.5350, 73.7920], // Pashan Circle Bypass
        [18.5520, 73.7650], // Sus Road Resurfaced
        [18.5780, 73.7480], // Hinjewadi Phase 1 Backgate
        [18.5912, 73.7389]
      ],
      segments: [
        { from: "Station", to: "FC Road", roughness: 18, condition: "good", lengthKm: 3.5 },
        { from: "FC Road", to: "SB Road", roughness: 24, condition: "good", lengthKm: 2.8 },
        { from: "SB Road", to: "Pashan Circle", roughness: 15, condition: "good", lengthKm: 4.2 },
        { from: "Pashan Circle", to: "Sus Expressway", roughness: 12, condition: "good", lengthKm: 5.1 },
        { from: "Sus Expressway", to: "Hinjewadi Phase 1", roughness: 20, condition: "good", lengthKm: 5.2 }
      ]
    }
  },
  {
    id: "route-pune-2",
    name: "Kothrud ➔ Viman Nagar (Airport Corridor)",
    city: "Pune",
    origin: { name: "Kothrud Stand (Paud Rd)", lat: 18.5074, lng: 73.8062 },
    destination: { name: "Viman Nagar Phoenix Mall", lat: 18.5679, lng: 73.9143 },
    fastestRoute: {
      id: "fastest",
      name: "Via Karve Rd, Deccan & Bund Garden Rd",
      distanceKm: 15.2,
      durationMin: 34,
      comfortScore: 61,
      roughSegmentsCount: 7,
      potholesEncountered: 8,
      roughnessAvg: 58,
      waypoints: [
        [18.5074, 73.8062],
        [18.5130, 73.8280], // Karve Statue
        [18.5190, 73.8420], // Deccan Gymkhana
        [18.5280, 73.8740], // Pune Station
        [18.5420, 73.8900], // Bund Garden
        [18.5550, 73.9050], // Yerwada Flyover
        [18.5679, 73.9143]
      ],
      segments: [
        { from: "Kothrud", to: "Karve Statue", roughness: 74, condition: "poor", lengthKm: 2.8 },
        { from: "Karve Statue", to: "Deccan", roughness: 42, condition: "average", lengthKm: 2.0 },
        { from: "Deccan", to: "Bund Garden", roughness: 65, condition: "poor", lengthKm: 4.8 },
        { from: "Bund Garden", to: "Yerwada", roughness: 70, condition: "poor", lengthKm: 2.6 },
        { from: "Yerwada", to: "Viman Nagar", roughness: 38, condition: "average", lengthKm: 3.0 }
      ]
    },
    smoothestRoute: {
      id: "smoothest",
      name: "Via Riverside Road & Koregaon Park North",
      distanceKm: 16.9,
      durationMin: 37,
      comfortScore: 89,
      roughSegmentsCount: 1,
      potholesEncountered: 1,
      roughnessAvg: 22,
      waypoints: [
        [18.5074, 73.8062],
        [18.5160, 73.8180],
        [18.5270, 73.8370], // River Bed Road
        [18.5360, 73.8820], // Koregaon Park Lane 1
        [18.5480, 73.9010], // Kalyani Nagar Bridge
        [18.5679, 73.9143]
      ],
      segments: [
        { from: "Kothrud", to: "Riverbed Link", roughness: 20, condition: "good", lengthKm: 3.2 },
        { from: "Riverbed Link", to: "Koregaon Park", roughness: 18, condition: "good", lengthKm: 5.5 },
        { from: "Koregaon Park", to: "Kalyani Nagar Bridge", roughness: 25, condition: "good", lengthKm: 3.8 },
        { from: "Kalyani Nagar Bridge", to: "Viman Nagar", roughness: 26, condition: "good", lengthKm: 4.4 }
      ]
    }
  },
  {
    id: "route-pune-3",
    name: "Swargate ➔ Baner (West Pune Corridor)",
    city: "Pune",
    origin: { name: "Swargate Bus Terminal", lat: 18.5018, lng: 73.8585 },
    destination: { name: "Baner Balewadi Phata", lat: 18.5590, lng: 73.7795 },
    fastestRoute: {
      id: "fastest",
      name: "Via Bajirao Rd & Shivajinagar",
      distanceKm: 12.8,
      durationMin: 29,
      comfortScore: 54,
      roughSegmentsCount: 8,
      potholesEncountered: 10,
      roughnessAvg: 66,
      waypoints: [
        [18.5018, 73.8585],
        [18.5150, 73.8540], // Appa Balwant Chowk
        [18.5310, 73.8440], // Shivajinagar
        [18.5450, 73.8150], // Aundh
        [18.5590, 73.7795]
      ],
      segments: [
        { from: "Swargate", to: "Appa Balwant", roughness: 80, condition: "dangerous", lengthKm: 2.2 },
        { from: "Appa Balwant", to: "Shivajinagar", roughness: 60, condition: "poor", lengthKm: 3.1 },
        { from: "Shivajinagar", to: "Aundh", roughness: 70, condition: "poor", lengthKm: 4.0 },
        { from: "Aundh", to: "Baner", roughness: 55, condition: "average", lengthKm: 3.5 }
      ]
    },
    smoothestRoute: {
      id: "smoothest",
      name: "Via Sinhagad Rd & Western Bypass Expressway",
      distanceKm: 14.5,
      durationMin: 32,
      comfortScore: 94,
      roughSegmentsCount: 1,
      potholesEncountered: 1,
      roughnessAvg: 14,
      waypoints: [
        [18.5018, 73.8585],
        [18.4920, 73.8390], // Sinhagad Road
        [18.5120, 73.7950], // Warje Bypass
        [18.5380, 73.7820], // Chandani Chowk Express
        [18.5590, 73.7795]
      ],
      segments: [
        { from: "Swargate", to: "Sinhagad Link", roughness: 18, condition: "good", lengthKm: 2.9 },
        { from: "Sinhagad Link", to: "Warje Bypass", roughness: 14, condition: "good", lengthKm: 4.1 },
        { from: "Warje Bypass", to: "Chandani Chowk", roughness: 12, condition: "good", lengthKm: 4.5 },
        { from: "Chandani Chowk", to: "Baner", roughness: 15, condition: "good", lengthKm: 3.0 }
      ]
    }
  }
];

export const CITY_HEALTH_STATS = {
  pune: {
    cityName: "Pune Metropolitan Area",
    overallScore: 74, // 0 - 100
    overallCondition: "average",
    totalMonitoredKm: 4850,
    totalPotholesCount: 382,
    activeRepairsCount: 47,
    fixedThisMonth: 128,
    distinctPassesRecorded: 284900,
    zones: [
      { name: "Shivajinagar - University", score: 58, condition: "poor", potholes: 84 },
      { name: "Kothrud - Karve Rd", score: 62, condition: "average", potholes: 61 },
      { name: "Baner - Balewadi Corridor", score: 79, condition: "good", potholes: 38 },
      { name: "Hinjewadi IT Park Expressway", score: 88, condition: "good", potholes: 19 },
      { name: "Viman Nagar - Nagar Rd", score: 71, condition: "average", potholes: 52 },
      { name: "Swargate - Katraj Ghat", score: 49, condition: "dangerous", potholes: 128 }
    ],
    leaderboard: {
      smoothest: [
        { road: "Western Bypass Expressway (Warje - Baner)", score: 96, passes: 42000, condition: "good" },
        { road: "Sus - Pashan Expressway Link", score: 94, passes: 18500, condition: "good" },
        { road: "Koregaon Park North Main Road", score: 91, passes: 31200, condition: "good" },
        { road: "Balewadi High Street (North Section)", score: 89, passes: 24100, condition: "good" }
      ],
      worst: [
        { road: "Satara Road (Swargate Underpass to Market Yard)", score: 38, passes: 54000, condition: "dangerous" },
        { road: "Ganeshkhind Road (Univ Circle Flyover Descent)", score: 42, passes: 68000, condition: "dangerous" },
        { road: "Paud Road (Vanaz Station section)", score: 46, passes: 39000, condition: "poor" },
        { road: "Hadapsar Industrial Bypass", score: 48, passes: 29000, condition: "poor" }
      ]
    }
  }
};

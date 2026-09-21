/**
 * helpers.js - Utility helper functions
 */

export function calculateDistanceKm(lat1, lon1, lat2, lon2) {
  const R = 6371; // Earth radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

export function formatDate(dateString) {
  if (!dateString) return "Recently";
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now - date;
  const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
  const diffDays = Math.floor(diffHours / 24);

  if (diffHours < 1) return "Just now";
  if (diffHours < 24) return `${diffHours} hr${diffHours > 1 ? 's' : ''} ago`;
  if (diffDays === 1) return "Yesterday";
  if (diffDays < 30) return `${diffDays} days ago`;

  return date.toLocaleDateString("en-IN", {
    month: "short",
    day: "numeric",
    year: "numeric"
  });
}

export function getRoughnessColor(score) {
  // score 0 = smooth green (#10b981), 50 = amber (#f59e0b), 100 = red (#ef4444)
  if (score <= 30) return "#10b981";
  if (score <= 55) return "#eab308";
  if (score <= 75) return "#f97316";
  return "#ef4444";
}

export function getComfortGrade(score) {
  if (score >= 90) return { grade: "Butter Smooth 🧈", label: "Excellent", class: "text-emerald-400" };
  if (score >= 75) return { grade: "Comfortable 🚗", label: "Good", class: "text-green-400" };
  if (score >= 55) return { grade: "Moderate Bumps ⚠️", label: "Fair", class: "text-amber-400" };
  return { grade: "Rough & Hazardous 💥", label: "Poor", class: "text-red-400" };
}

export function generateId(prefix = "ph") {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).substr(2, 4)}`;
}

# Countersteer — Motorcycle Navigation & Ride Tracking App

**Date:** 2026-03-18
**Status:** Draft
**Author:** Paul

## Overview

Countersteer is a self-contained Android motorcycle navigation app for personal use. It generates route options based on direction/distance or destination, scores them for curviness/scenery/speed, and provides turn-by-turn navigation with ride logging. Crash detection is handled by the Pixel's built-in Personal Safety app. No hosted servers — everything runs on-device with free external APIs.

## Target User

- Single user (Paul), Canadian, metric units (km/h, km)
- Phone: Pixel 10 Pro, handlebar-mounted
- Potential to share externally — BYOK (bring your own key) model for API access

## Core Features

### 1. Route Generation

**Two modes:**

- **Explore mode:** Pick a general direction + distance → app generates loop routes back to starting point
  - Input: compass rose overlay on map (drag to set direction, snaps to 8 compass points) + distance slider (20-500 km round trip)
  - Also: tap map to set direction visually
- **Destination mode:** Search for a destination or tap map → app generates route options
  - Address/POI search via Nominatim (free OSM geocoder, 1 req/sec rate limit — debounce search input to comply)
  - Recent destinations list

**Route generation pipeline:**
1. User provides direction+distance or destination
2. App generates 3 candidate routes via Valhalla with different costing profiles
3. Each route scored for curviness (geometry analysis), scenic value (POI proximity, road classification), and speed (traffic-aware via TomTom)
4. Routes filtered to paved roads only (OSM `surface` and `highway` tags)
5. Presented as 3 cards: Curvy, Scenic, Quickest

### 2. Route Scoring

- **Curvy:** Calculated from route geometry — angle changes between OSM way nodes. Higher angle variance = higher curvature score.
- **Scenic:** Composite of: proximity to parks/water/overlooks (OSM POIs), road classification (prefer secondary/tertiary over trunk/motorway), elevation changes, avoidance of industrial/commercial zones.
- **Quickest:** Shortest travel time. TomTom traffic data is used to adjust Valhalla's travel time estimates — fetch traffic flow for sampled points along the route and re-weight the ETA, not to alter the route geometry itself. Valhalla generates the route; TomTom refines the time estimate.
- **All routes:** Paved roads only. No dirt, gravel, or unpaved surfaces. Filtered via OSM `surface=paved` and `highway` type exclusions.

### 3. Turn-by-Turn Navigation

- Full-screen map (MapLibre) with route highlighted
- Top bar: next turn instruction + distance (large arrow, high contrast)
- Bottom strip: current speed | speed limit | ETA | distance remaining
- Dark theme only — optimized for outdoor/visor visibility
- Auto-rotate map to heading
- Audio turn-by-turn via speaker or Bluetooth helmet
- Route cached at navigation start for dead-spot resilience

**Speed limit display:**
- Source: OSM `maxspeed` tags from Valhalla route response
- Color coding: green (under limit), yellow (at limit to limit+5 km/h), red (more than 5 km/h over)
- Hidden when OSM data unavailable — no guessing

### 4. Ride Logging

- Starts automatically when navigation begins
- Stops when navigation ends or user manually stops
- Runs as Android foreground service
- Records GPS breadcrumbs every ~2 seconds
- **Tracked data:** GPS position, speed, altitude, bearing, accuracy
- **Computed stats:** total distance, duration, avg/max speed, elevation gain, curvature score

### 5. Crash Detection (Removed)

Crash detection is no longer handled by the app. Instead, the Pixel's built-in Personal Safety app provides crash detection, emergency calling, and contact notification natively. This simplifies the app and avoids duplicating OS-level safety features.

### 6. Road Alerts

**Fixed enforcement (OSM):**
- Speed cameras, red light cameras, average speed zones
- Queried via Overpass API along the current route
- Canadian cities generally well-mapped

**Personal markers:**
- User-created alerts: speed trap locations, road hazards, photo spots, custom notes
- Long-press map to add
- Stored locally in Room database
- Audio alert + banner overlay ~1 km before approaching on future rides
- Auto-dismiss after passing
- Included in backup/restore

### 7. Offline Maps

- Download map regions for offline use via MapLibre OfflineManager
- Pan/zoom to select region, see estimated size, download in background
- Tile source: OpenFreeMap (no key, no limits)
- Storage: city ~50-100 MB, extended region ~150-300 MB, province ~500 MB+
- Warning prompt if region exceeds 500 MB
- Auto-update: check on app launch, re-download tiles older than 30 days, Wi-Fi preferred
- Management screen in Settings: list regions, storage usage, delete, manual update

### 8. Backup & Restore

**Export:**
- Single `.zip` file containing JSON: rides, track points, saved routes, trusted contacts, road markers, preferences
- Excludes: API keys (security — re-enter on restore), offline maps (re-download)
- Saved via Android share sheet (Downloads, Google Drive, etc.)
- Filename: `countersteer-backup-YYYY-MM-DD.zip`
- Includes schema version for forward compatibility

**Restore:**
- Pick `.zip` file → validate schema → preview contents
- Two modes: merge (add to existing, skip duplicates) or replace (wipe + restore)
- Schema migration for older backups

### 9. Onboarding

First-launch flow:
1. **Welcome** — brief app overview
2. **TomTom API key** — link to TomTom developer signup, step-by-step instructions, paste key → validate → save
3. **Permissions** — location + notifications, with explanation of why each is needed

TomTom API is the only key users need. End-user documentation on key generation included in-app and in project docs.

## Architecture

### Single-Module Monolith

```
com.countersteer
├── app/          — Application, DI (Hilt), MainActivity
├── onboarding/   — API key setup, welcome flow
├── routing/      — Route generation, scoring, Valhalla + TomTom clients
├── navigation/   — Turn-by-turn, map display, voice, speed limit
├── logging/      — Ride recording, GPS track, foreground service
├── safety/       — (Removed — using Pixel Personal Safety instead)
├── alerts/       — Road markers, OSM enforcement, alert triggers
├── data/         — Room database, repositories, local storage
└── ui/           — Shared theme, components, design system
```

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose |
| DI | Hilt |
| Database | Room |
| Async | Coroutines + Flow |
| Navigation | Compose Navigation |
| Maps | MapLibre GL Native |
| Tiles | OpenFreeMap (no key) |
| Routing | Valhalla via FOSSGIS (no key) |
| Traffic | TomTom API (free tier, 2,500 req/day) |
| Geocoding | Nominatim (no key) |
| Enforcement data | Overpass API (no key) |
| HTTP | Ktor Client |
| JSON | Kotlinx Serialization |
| Background work | WorkManager |
| Preferences | DataStore + EncryptedSharedPreferences (for API keys) |

### Data Model

**Room tables:**

**rides**
- `id` INTEGER PK
- `started_at` TIMESTAMP
- `ended_at` TIMESTAMP
- `distance_km` REAL
- `duration_seconds` INTEGER
- `avg_speed_kmh` REAL
- `max_speed_kmh` REAL
- `elevation_gain_m` REAL
- `curvature_score` REAL
- `route_type` TEXT (curvy/scenic/quickest)
- `route_polyline` TEXT (encoded polyline)

**ride_track_points**
- `id` INTEGER PK
- `ride_id` INTEGER FK → rides
- `timestamp` TIMESTAMP
- `latitude` REAL
- `longitude` REAL
- `altitude_m` REAL
- `speed_kmh` REAL
- `bearing` REAL
- `accuracy_m` REAL

**trusted_contacts**
- `id` INTEGER PK
- `name` TEXT
- `phone_number` TEXT
- `is_primary` BOOLEAN

**saved_routes**
- `id` INTEGER PK
- `name` TEXT
- `created_at` TIMESTAMP
- `origin_lat` REAL
- `origin_lon` REAL
- `destination_lat` REAL (nullable)
- `destination_lon` REAL (nullable)
- `route_polyline` TEXT
- `route_type` TEXT
- `distance_km` REAL
- `valhalla_json` TEXT (cached response)

**offline_map_regions**
- `id` INTEGER PK
- `name` TEXT
- `min_lat` REAL
- `max_lat` REAL
- `min_lon` REAL
- `max_lon` REAL
- `min_zoom` INTEGER (default 6)
- `max_zoom` INTEGER (default 16)
- `size_bytes` INTEGER
- `downloaded_at` TIMESTAMP
- `last_updated_at` TIMESTAMP
- `maplibre_region_id` TEXT

**road_markers**
- `id` INTEGER PK
- `latitude` REAL
- `longitude` REAL
- `type` TEXT (speed_trap/hazard/photo_spot/custom)
- `note` TEXT (nullable)
- `source` TEXT (user/osm)
- `created_at` TIMESTAMP
- `alert_radius_m` INTEGER (default 1000)

**Encrypted preferences (DataStore):**
- `tomtom_api_key` STRING

**User preferences (DataStore):**
- `onboarding_complete` BOOLEAN
- `crash_detection_enabled` BOOLEAN
- `crash_countdown_seconds` INT (default 30)
- `speed_alert_enabled` BOOLEAN

### Storage Estimates

- Per ride (1 hour): ~150 KB (ride record + ~1,800 track points)
- 100 rides: ~15 MB
- Saved routes: ~5-20 KB each
- Offline maps: 50 MB - 1 GB depending on regions
- Total (excluding maps): well under 100 MB after years of use

## Screen Flow

```
Onboarding (first launch) → Home
Home ↔ Ride History ↔ Settings
Home → Explore Mode → Route Options → Active Navigation → Ride Summary
Home → Destination Mode → Route Options → Active Navigation → Ride Summary
Settings → Offline Maps
Settings → Backup/Restore
Settings → API Keys
```

**Screens:**

1. **Onboarding** — welcome, TomTom API key, permissions
2. **Home** — map centered on location, "Explore" and "Go Somewhere" buttons, bottom nav
3. **Explore Mode** — compass rose overlay + distance slider on map
4. **Destination Mode** — search bar + map tap + recent destinations
5. **Route Options** — 3 route cards (curvy/scenic/quickest) overlaid on map with stats
6. **Active Navigation** — full-screen map, turn instructions, speed/limit/ETA/distance, alerts
7. **Ride Summary** — route map, stats (distance, duration, speeds, elevation, curvature)
8. **Ride History** — list of past rides with thumbnails, tap for detail, re-ride option
9. **Settings** — offline maps, backup/restore, API keys, speed alerts

## UI Principles

- **Dark theme only** — better for outdoor/visor visibility, simpler to build
- **Glanceable** — large text, high contrast, designed for 60 km/h handlebar viewing
- **Minimal interaction while riding** — big tap targets, no typing. Set up before you go.
- **Metric units** — km/h and km throughout
- **Vertical layout priority** — optimized for phone in portrait orientation on handlebar mount

## External API Usage

| API | Auth | Free Tier | Usage |
|-----|------|-----------|-------|
| Valhalla (FOSSGIS) | None | Unlimited | Route generation |
| OpenFreeMap | None | Unlimited | Map tiles (online + offline) |
| Nominatim | None | 1 req/sec | Geocoding/search |
| Overpass API | None | Reasonable use | Enforcement data along route |
| TomTom Traffic | API key | 2,500 req/day | Real-time traffic for routing |

## BYOK (Bring Your Own Key) Model

- Each user generates their own free TomTom API key
- In-app documentation with step-by-step instructions + link to TomTom developer portal
- Key validated on entry before saving
- Stored encrypted on device
- Enables sharing the app without API key management burden

## Permissions Required

- `ACCESS_FINE_LOCATION` — GPS for navigation and ride logging
- `ACCESS_COARSE_LOCATION` — fallback location
- `FOREGROUND_SERVICE` — ride logging
- `FOREGROUND_SERVICE_LOCATION` — foreground service with location type
- `INTERNET` — API calls
- `ACCESS_NETWORK_STATE` — online/offline detection
- `POST_NOTIFICATIONS` — ride recording status (Android 13+)

**Note:** Crash detection is handled by the Pixel's built-in Personal Safety app, which manages its own permissions.

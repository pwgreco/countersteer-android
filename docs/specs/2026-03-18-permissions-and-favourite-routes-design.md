# Permissions Screen & Favourite Routes Design Spec

**Date:** 2026-03-18
**Status:** Approved
**Scope:** Two independent features: in-app permissions management screen, and save ride as favourite route for re-riding

## Feature 1: Permissions Screen

### Purpose

Allow users to view and manage app permissions from within the app, without needing to find the system settings page manually. Solves the problem of users getting stuck on "Acquiring GPS signal" because location permission was denied.

### Design

**New screen:** `PermissionsScreen` accessible from Settings.

**Navigation:** New item in SettingsScreen under a "Permissions" navigation item → opens PermissionsScreen.

**What it shows:**

A list of permissions the app uses, each as a row with:
- Icon (permission-specific)
- Permission name
- Description of why it's needed
- Status badge: "Granted" (green) or "Denied" (red)
- Action button when denied:
  - "Grant" — triggers the system permission request dialog
  - "Open Settings" — if the user previously denied permanently (shouldShowRequestPermissionRationale returns false after first denial), launches the system App Info page

**Permissions listed:**

| Permission | Description |
|-----------|-------------|
| Location (fine + coarse) | "Required for navigation and ride tracking" |
| Notifications | "Ride tracking and alert notifications" |

**Behavior:**
- Permission status updates reactively when the user returns from the system settings page or grants via dialog
- Uses `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` for each permission request
- Uses `context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))` for the "Open Settings" fallback

### Files

| File | Change |
|------|--------|
| New: `ui/screens/PermissionsScreen.kt` | Screen composable with permission rows |
| New: `ui/screens/PermissionsViewModel.kt` | Permission state checking and request logic |
| Modified: `ui/screens/SettingsScreen.kt` | Add "Permissions" navigation item |
| Modified: `ui/navigation/CountersteerNavigation.kt` | Add PermissionsScreen route |
| Modified: `ui/navigation/Screen.kt` | Add `Permissions` destination |

---

## Feature 2: Save Ride as Favourite Route

### Purpose

Allow riders to save a completed ride as a favourite route that can be re-ridden later. Useful for discovering fun roads and wanting to repeat them.

### Design

**Entry point:** RideSummaryScreen — after finishing a ride, alongside the existing Save/Discard buttons, add a "Save as Favourite" button.

**Save Flow:**

1. User finishes navigation → RideSummaryScreen appears with ride stats and track
2. Three buttons at bottom: **Discard** | **Save Ride** | **Save as Favourite**
3. "Save as Favourite" shows a dialog prompting for a route name
   - Pre-filled with: `"{RouteType} Route - {date}"` (e.g. "Curvy Route - Mar 18")
   - User can edit the name
   - Confirm button saves
4. Saving as favourite:
   - Saves the ride to history (same as "Save Ride" — favourite is a superset)
   - Creates a `SavedRouteEntity` from the ride data:
     - `name` from the dialog
     - `originLat/Lon` from first track point
     - `destinationLat/Lon` from last track point
     - `routePolyline` from `RideEntity.routePolyline`
     - `routeType` from `RideEntity.routeType`
     - `distanceKm` from `RideEntity.distanceKm`
     - `valhallaJson` left empty (ride-based route, not Valhalla-generated)

**Re-riding favourites — DestinationScreen:**

- The DestinationScreen currently shows favourite locations (`FavoriteLocationEntity`). Add a new section above or below: "Favourite Routes" showing `SavedRouteEntity` items that were created from rides (identified by empty `valhallaJson`... or add a `source` field).
- Each favourite route item shows: name, route type badge, distance, date saved
- Tapping a favourite route navigates to RouteOptionsScreen with the saved route pre-loaded. This requires adding a `savedRouteId` navigation parameter to the `Screen.RouteOptions` route (alongside existing `mode`, `bearing`, `distance`, etc.). A new `createFavouriteRoute(savedRouteId: Long)` helper on `Screen.RouteOptions` constructs the route string.

**Re-riding favourites — RouteOptionsScreen:**

- When navigated to with a `savedRouteId` parameter, RouteOptionsViewModel loads the `SavedRouteEntity` and displays it as a selectable option alongside any generated routes
- The favourite route uses its stored polyline directly for navigation (no Valhalla re-generation)
- Displayed with a "Favourite" badge/label to distinguish from generated options

**Surfacing during Explore:**

- When the user generates routes via ExploreScreen, the RouteOptionsViewModel checks for any `SavedRouteEntity` records with an origin within ~5km of the user's current location
- Matching favourites appear as a "Favourites" section above the generated routes
- This is a best-effort feature — if no favourites are nearby, nothing extra shows

### Data Model

Uses the existing `SavedRouteEntity` table. No new tables needed.

To distinguish ride-sourced favourites from Valhalla-generated saved routes, add a boolean field:
- `isFavouriteRide: Boolean = false` — true when saved from a completed ride via RideSummaryScreen

### Files

| File | Change |
|------|--------|
| Modified: `data/entity/SavedRouteEntity.kt` | Add `isFavouriteRide` boolean field |
| Modified: `data/dao/RouteDao.kt` | Add query for nearby favourite rides by origin coords (bounding box approximation on lat/lon) |
| Modified: `data/CountersteerDatabase.kt` | Database version bump + migration for new column |
| Modified: `ui/screens/RideSummaryScreen.kt` | Add "Save as Favourite" button + name dialog |
| Modified: `ui/screens/RideSummaryViewModel.kt` | Save-as-favourite logic converting ride → SavedRouteEntity |
| Modified: `ui/screens/DestinationScreen.kt` | Show favourite routes section |
| Modified: `ui/screens/DestinationViewModel.kt` | Load favourite routes from DAO |
| Modified: `ui/screens/RouteOptionsScreen.kt` | Accept and display pre-loaded favourite route |
| Modified: `ui/screens/RouteOptionsViewModel.kt` | Handle favourite route option, query nearby favourites |
| Modified: `ui/navigation/Screen.kt` | Add `savedRouteId` param to RouteOptions route + `createFavouriteRoute()` helper |
| Modified: `ui/navigation/CountersteerNavigation.kt` | Pass `savedRouteId` nav argument to RouteOptionsScreen |

---

## ETA Bug (separate investigation)

**Reported issue:** ETA estimates during active navigation don't match the estimates shown on the RouteOptionsScreen when selecting a route. This needs root cause investigation — not part of this spec, will be investigated separately.

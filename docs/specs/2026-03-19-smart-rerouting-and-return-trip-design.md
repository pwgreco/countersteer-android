# Smart Rerouting & Return Trip Design Spec

**Date:** 2026-03-19
**Status:** Approved

## Feature 1: Smart Rerouting for Loops and Round Trips

### Problem

When a rider goes off-route, `reroute()` targets `route.points.lastOrNull()` — the last point of the route. For round-trip loops, the last point is the origin, so rerouting shortcuts the rider home instead of getting them back on the planned loop.

### Design

When `isOffRoute` is detected in `NavigationViewModel.reroute()`:

1. Instead of routing to `route.points.last()`, find the nearest point on the original route that is **ahead** of the rider's current progress (`currentPointIndex`)
2. Skip nearby points — search forward from approximately 500m-1km ahead to find a good rejoin point. This avoids rejoining at a point the rider just passed.
3. Generate a Valhalla route from current position to that rejoin point via `routeGenerator.generateDestinationRoutes()`
4. **Splice the routes:** take the new reroute segment's points + the remainder of the original route from the rejoin point index onward. Combine into a single `Route` object.
5. Replace the active route with this spliced route. The rider navigates the reroute segment, then seamlessly continues the original loop.

**Edge case:** If the rider is within the last 500m of the route (near the end), fall back to the current behavior — route to the final destination point.

**Edge case:** If the rejoin route generation fails, fall back to routing to the final destination.

### Files

| File | Change |
|------|--------|
| Modified: `navigation/NavigationViewModel.kt` | Rewrite `reroute()` to find rejoin point, generate splice route |

---

## Feature 2: Return Trip from Ride History

### Problem

After completing a ride to a destination, there's no quick way to navigate back to the start. The rider has to manually search for the starting address.

### Design

Add a "Return Trip" button on `RideDetailScreen` (alongside existing Re-ride and Delete buttons).

**Flow:**
1. Rider views a completed ride in ride history
2. Taps "Return Trip"
3. The ride's first track point (original start location) becomes the destination
4. The rider's current GPS location becomes the origin
5. Navigates to RouteOptionsScreen in destination mode, generating fresh Curvy/Scenic/Quickest routes back
6. Rider picks a route and starts navigation as usual

Valhalla naturally generates different roads for the reverse direction, so the rider gets a fresh route home without retracing the outbound leg.

### Files

| File | Change |
|------|--------|
| Modified: `ui/screens/RideDetailScreen.kt` | Add "Return Trip" button, read first track point from existing `trackPoints` state |
| Modified: `ui/navigation/CountersteerNavigation.kt` | Add `onReturnTrip: (Double, Double) -> Unit` callback, wire to RouteOptions destination mode |

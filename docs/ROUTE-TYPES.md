# How Route Types Work

Countersteer generates up to 3 route options for every trip — **Curvy**, **Scenic**, and **Quickest**. This document explains how each is calculated and why you sometimes see fewer than 3 options.

## How Routes Are Generated

Countersteer doesn't just ask for 3 routes with different labels. Instead, it:

1. Sends **2 requests** to the Valhalla routing engine (an open-source routing server):
   - A **back-road request** with highway avoidance, asking for the main route + up to 2 alternates (3 routes total)
   - A **highway-preference request** for the fastest route

2. Collects all candidate routes (up to 4)

3. **Scores** each route for curvature and scenic value

4. **Labels** the best candidates:
   - **Curvy** = the route with the highest curvature score
   - **Quickest** = the route with the shortest travel time (traffic-adjusted if TomTom key is configured)
   - **Scenic** = the route with the highest scenic score among remaining candidates

## How Scoring Works

### Curvature Score (0–100)

Measures how twisty a route is by analyzing the geometry:

1. Decode the route into a series of GPS points
2. Calculate the **bearing** (compass direction) between each consecutive pair of points
3. Sum up all the **angle changes** between consecutive bearings
4. More angle changes = more turns = higher score
5. Normalize to a 0–100 scale

A straight highway scores near 0. A winding mountain road scores near 100.

### Scenic Score (0–100)

Approximates how scenic a route is based on road characteristics:

- **Maneuver density** — routes with more turns onto different streets tend to go through more varied terrain
- **Named road ratio** — routes on named streets (vs highway numbers) tend to be more scenic back roads
- Normalized to 0–100

Note: this is an approximation. True scenic scoring would require querying OpenStreetMap for nearby parks, lakes, overlooks, and elevation data — which would add significant API overhead. The current proxy correlates reasonably well with scenic quality.

### Quickest Determination

The shortest **effective travel time**, considering:

- Valhalla's base estimate (speed limits, road type, turn penalties)
- TomTom traffic adjustment (if API key is configured): samples 12 points along the route for real-time traffic flow, blends into the estimate

## Why You Sometimes See Fewer Than 3 Routes

### Only 1 route shown

This happens when Valhalla can only find **one viable path** between your waypoints. Common causes:

- **Short distance** — for a 5 km urban ride, there may only be one sensible route
- **Geographic constraints** — rivers, mountains, or limited road networks mean there's only one way to get there
- **Explore mode with tight distance** — the loop waypoints are close together, limiting options
- **Highway request returned the same route** — the fastest path and the back-road path happen to be identical

### Only 2 routes shown (e.g., just Curvy and Quickest, no Scenic)

The labeling algorithm assigns:
1. **Curvy** first (highest curvature score)
2. **Quickest** second (fastest among remaining)
3. **Scenic** only if there's a third candidate

If Valhalla only returned 2 distinct routes (the back-road request found no alternates), there's nothing to label as Scenic.

### Only Scenic shown, or only Curvy shown

When there's a single route, it gets labeled based on its strongest score. If it has higher curvature than scenic value, it's labeled **Curvy**. If scenic scores higher, it's labeled **Scenic**.

### Curvy and Scenic show the same path

This can happen on shorter routes where the only alternative to the highway is one back-road path. That path scores high for both curvature and scenic value, but since Curvy is assigned first, the Scenic label goes to a different (potentially less distinct) route — or doesn't appear if there are only 2 candidates.

## Route Type Parameters

Under the hood, the two Valhalla requests use these motorcycle costing parameters:

| Parameter | Back-Road Request | Highway Request |
|-----------|------------------|-----------------|
| `use_highways` | 0.1 (strongly avoid) | 1.0 (prefer) |
| `highway_factor` | 4.0 (heavy penalty) | default |
| `alternates` | 2 (request 3 total) | 0 (single route) |

- `use_highways` ranges from 0 (never) to 1 (always). We use 0.1 (not 0) because setting it to 0 makes routes impossible in many areas where short highway segments connect back roads.
- `highway_factor` is a cost multiplier on highway segments. 4.0 means highways cost 4x more than they normally would, pushing the router toward smaller roads.

## Distance in Explore Mode

When you set a round-trip distance (e.g., 100 km), the app places waypoints along your chosen compass direction. A **1.4x correction factor** accounts for the difference between straight-line distance and actual road distance (roads curve, go around obstacles, etc.).

The actual route distance depends on available roads and may vary ±20% from your target. This is inherent to how routing works — the router finds the best path through waypoints, not an exact distance match.

## Traffic Impact on Route Selection

If you have a TomTom API key configured:

- Traffic data is sampled at **12 points** along each route
- The traffic factor (current speed ÷ free-flow speed) adjusts the ETA
- The **Quickest** label considers traffic-adjusted time, not just base time
- A route that's shorter by distance might be labeled Quickest because the longer route has heavy traffic

Without a TomTom key, Quickest is based purely on Valhalla's speed-limit-based estimates.

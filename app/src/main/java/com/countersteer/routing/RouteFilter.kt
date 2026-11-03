package com.countersteer.routing

import com.countersteer.routing.model.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteFilter @Inject constructor() {

    /**
     * Check if a route contains unpaved segments.
     * This is already populated from Valhalla's rough_surface maneuver flag.
     */
    fun hasUnpavedSegments(route: Route): Boolean {
        return route.hasUnpavedSegments
    }

    /**
     * Filter a list of routes, optionally excluding those with unpaved segments.
     */
    fun filterRoutes(
        routes: List<Route>,
        excludeUnpaved: Boolean = false,
    ): List<Route> {
        if (!excludeUnpaved) return routes
        return routes.filter { !it.hasUnpavedSegments }
    }
}

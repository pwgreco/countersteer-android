package com.countersteer.navigation

import com.countersteer.routing.model.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton holder for passing the selected Route from RouteOptionsScreen
 * to ActiveNavigationScreen without serializing through nav arguments.
 * Also serves as the route cache during navigation (survives network loss).
 */
@Singleton
class ActiveRouteHolder @Inject constructor() {
    var route: Route? = null
        private set

    fun setActiveRoute(route: Route) {
        this.route = route
    }

    fun clear() {
        route = null
    }
}

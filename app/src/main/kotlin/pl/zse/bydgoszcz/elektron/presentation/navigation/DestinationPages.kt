package pl.zse.bydgoszcz.elektron.presentation.navigation

import pl.zse.bydgoszcz.elektron.presentation.common.BottomDestination

// A retained page can be requested during a 6 → 5 tab transition.
internal fun destinationPageKey(destinations: List<BottomDestination>, page: Int): String =
    destinations.getOrNull(page)?.route ?: "removed-page-$page"

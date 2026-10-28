package fr.mamaison.app

/** UI-only history. No address, permission or server state belongs here. */
internal data class MobileDestination(val tab: String, val page: String) {
    fun encode() = "$tab|$page"
    companion object {
        fun decode(value: String): MobileDestination {
            val parts = value.split('|', limit = 2)
            return MobileDestination(parts[0], parts.getOrElse(1) { parts[0] })
        }
    }
}

internal data class MobileNavigation(
    val history: List<MobileDestination> = listOf(MobileDestination("Accueil", "Accueil")),
    val remembered: Map<String, String> = tabs.associateWith { it }
) {
    val current get() = history.last()
    val canGoBack get() = history.size > 1

    fun open(page: String): MobileNavigation {
        val destination = MobileDestination(owner(page), page)
        if (destination == current) return this
        val previous = history.indexOfLast { it == destination }
        val next = if (previous >= 0) history.take(previous + 1) else history + destination
        return copy(history = next, remembered = remembered + (destination.tab to page))
    }

    fun select(tab: String): MobileNavigation {
        require(tab in tabs)
        if (current.tab == tab) {
            if (current.page == tab) return this
            val root = MobileDestination(tab, tab)
            val index = history.indexOfLast { it == root }
            val next = if (index >= 0) history.take(index + 1) else history.filter { it.tab != tab } + root
            return copy(history = next, remembered = remembered + (tab to tab))
        }
        return open(remembered[tab] ?: tab)
    }

    fun back(): MobileNavigation {
        if (!canGoBack) return this
        val next = history.dropLast(1)
        return copy(history = next, remembered = remembered + (next.last().tab to next.last().page))
    }

    fun save(): List<String> = tabs.map { remembered[it] ?: it } + history.map { it.encode() }

    companion object {
        val tabs = listOf("Accueil", "Seedbox", "Médias", "Maison", "Réglages")
        fun owner(page: String): String = when (page) {
            "Accueil" -> "Accueil"
            "Seedbox", "Téléchargements", "Fichiers", "Stockage" -> "Seedbox"
            "Médias", "Jaquettes" -> "Médias"
            "Maison", "Appareils", "Google Home", "Caméras", "Pièces et scènes" -> "Maison"
            else -> "Réglages"
        }
        fun restore(values: List<String>): MobileNavigation {
            if (values.size <= tabs.size) return MobileNavigation()
            return MobileNavigation(values.drop(tabs.size).map { MobileDestination.decode(it) }, tabs.zip(values.take(tabs.size)).toMap())
        }
    }
}

package fr.mamaison.app

import org.junit.Assert.*
import org.junit.Test

class MobileNavigationTest {
    @Test fun cameraShortcutReturnsToItsActualSource() {
        val navigation = MobileNavigation().open("Caméras")
        assertEquals("Maison", navigation.current.tab)
        assertEquals("Accueil", navigation.back().current.page)
    }

    @Test fun settingsIntegrationReturnsThroughItsActualPages() {
        val navigation = MobileNavigation().select("Réglages").open("Intégrations").open("Google Home")
        assertEquals("Maison", navigation.current.tab)
        assertEquals("Intégrations", navigation.back().current.page)
        assertEquals("Réglages", navigation.back().back().current.page)
    }

    @Test fun catalogBelongsToMediaAndReturnsToTheLibrary() {
        val navigation = MobileNavigation().select("Médias").open("Jaquettes")
        assertEquals("Médias", navigation.current.tab)
        assertEquals("Médias", navigation.back().current.page)
    }

    @Test fun switchingTabsRestoresTheLastPage() {
        val navigation = MobileNavigation().select("Seedbox").open("Fichiers")
            .select("Maison").open("Pièces et scènes").select("Seedbox")
        assertEquals("Fichiers", navigation.current.page)
        assertEquals("Pièces et scènes", navigation.select("Maison").current.page)
    }

    @Test fun repeatedDestinationsDoNotCreateLoops() {
        var navigation = MobileNavigation().select("Réglages").open("Installation").open("Réseau")
        repeat(10) { navigation = navigation.open("Réseau") }
        assertEquals(4, navigation.history.size)
        navigation = navigation.open("Installation")
        assertEquals(3, navigation.history.size)
        assertEquals("Réglages", navigation.back().current.page)
    }

    @Test fun savedHistoryRestoresBothBackAndTabMemory() {
        val navigation = MobileNavigation().select("Seedbox").open("Téléchargements")
            .select("Réglages").open("Téléviseurs")
        val restored = MobileNavigation.restore(navigation.save())
        assertEquals(navigation, restored)
        assertEquals("Réglages", restored.back().current.page)
        assertEquals("Téléchargements", restored.select("Seedbox").current.page)
    }

    @Test fun sharedDownloadTargetsSeedboxWithoutLosingTheOrigin() {
        val navigation = MobileNavigation().select("Médias").open("Téléchargements")
        assertEquals("Seedbox", navigation.current.tab)
        assertEquals("Médias", navigation.back().current.page)
    }

    @Test fun backingAtTheRootIsSafe() {
        val navigation = MobileNavigation()
        assertFalse(navigation.canGoBack)
        assertEquals(navigation, navigation.back())
        assertEquals(navigation, navigation.select("Accueil"))
    }

    @Test fun reselectingTheCurrentTabMakesItsHubReachable() {
        val navigation = MobileNavigation().open("Caméras").select("Maison")
        assertEquals("Maison", navigation.current.page)
        assertEquals("Accueil", navigation.back().current.page)
        assertEquals(navigation, navigation.select("Maison"))
    }
}

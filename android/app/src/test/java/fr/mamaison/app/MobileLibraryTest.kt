package fr.mamaison.app

import org.junit.Assert.*
import org.junit.Test

class MobileLibraryTest {
    @Test fun catalogPreservesMobileProgressAndNeverAddsUnknownFiles() {
        val library = listOf(MobileLibraryVideo("films/a.mkv", "a 1080p", 100.0, 120.0, 900.0))
        val catalog = listOf(
            MobileLibraryVideo("films/a.mkv", "A", position = 500.0, metadata = MobileMovieMetadata(title = "Astérix"), posterReady = true),
            MobileLibraryVideo("other/private.mkv", "Other")
        )
        val result = mergeMobileCatalog(library, catalog)
        assertEquals(1, result.size)
        assertEquals("Astérix", result[0].displayTitle)
        assertEquals(120.0, result[0].position, 0.0)
        assertEquals(900.0, result[0].duration, 0.0)
        assertEquals(100.0, result[0].size, 0.0)
        assertTrue(result[0].posterReady)
    }

    @Test fun identicalFilenamesInDifferentFoldersNeverShareMetadata() {
        val library = listOf(MobileLibraryVideo("one/movie.mkv", "one"), MobileLibraryVideo("two/movie.mkv", "two"))
        val result = mergeMobileCatalog(library, listOf(MobileLibraryVideo("two/movie.mkv", "two", metadata = MobileMovieMetadata(title = "Deux"))))
        assertNull(result[0].metadata)
        assertEquals("Deux", result[1].displayTitle)
    }

    @Test fun alphabeticalOrderUsesTheIdentifiedTitleAndStablePathTieBreak() {
        val videos = listOf(
            MobileLibraryVideo("z", "raw", metadata = MobileMovieMetadata(title = "Avatar")),
            MobileLibraryVideo("b", "Zulu"), MobileLibraryVideo("a", "avatar"),
            MobileLibraryVideo("d", "Écho"), MobileLibraryVideo("c", "Dune")
        )
        assertEquals(listOf("a", "z", "c", "d", "b"), visibleMobileVideos(videos, "", false).map { it.path })
    }

    @Test fun searchFindsAccentedTitleAndOriginalFilename() {
        val video = MobileLibraryVideo("Films/Release.2023.mkv", "Release 2023", metadata = MobileMovieMetadata(title = "Le Château"))
        assertEquals(listOf(video), visibleMobileVideos(listOf(video), "chateau", false))
        assertEquals(listOf(video), visibleMobileVideos(listOf(video), "release.2023", false))
        assertTrue(visibleMobileVideos(listOf(video), "introuvable", false).isEmpty())
    }

    @Test fun resumeFilterKeepsTheExistingPositivePositionRule() {
        val videos = listOf(MobileLibraryVideo("new", "New"), MobileLibraryVideo("started", "Started", position = 12.0), MobileLibraryVideo("finished", "Finished", position = 100.0, duration = 100.0))
        assertEquals(setOf("started", "finished"), visibleMobileVideos(videos, "", true).map { it.path }.toSet())
    }

    @Test fun missingCatalogKeepsEveryVideoPlayableAndNamed() {
        val video = MobileLibraryVideo("a.mkv", "Fichier original")
        assertEquals(listOf(video), mergeMobileCatalog(listOf(video), emptyList()))
        assertEquals("Fichier original", video.copy(metadata = MobileMovieMetadata()).displayTitle)
    }

    @Test fun malformedProgressDoesNotProduceAnInvalidProgressBar() {
        assertNull(MobileLibraryVideo("a", "A", position = Double.NaN, duration = 100.0).progressFraction)
        assertNull(MobileLibraryVideo("a", "A", position = 10.0, duration = 0.0).progressFraction)
        assertEquals(1f, MobileLibraryVideo("a", "A", position = 200.0, duration = 100.0).progressFraction!!, 0f)
        assertFalse(MobileLibraryVideo("a", "A", position = Double.NaN).hasProgress)
    }
}

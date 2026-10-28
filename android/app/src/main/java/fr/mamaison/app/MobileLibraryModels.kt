package fr.mamaison.app

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

internal data class MobileMovieMetadata(
    val title: String = "", val year: String = "", val overview: String = "",
    val genres: List<String> = emptyList(), val runtimeMinutes: Int = 0,
    val poster: String = "", val backdrop: String = ""
)

internal data class MobileLibraryVideo(
    val path: String, val title: String, val size: Double = 0.0,
    val position: Double = 0.0, val duration: Double = 0.0,
    val metadata: MobileMovieMetadata? = null, val posterReady: Boolean = false
) {
    val displayTitle get() = metadata?.title?.takeIf { it.isNotBlank() } ?: title
    val hasProgress get() = position.isFinite() && position > 0
    val progressFraction: Float? get() = if (duration.isFinite() && duration > 0 && position.isFinite())
        (position / duration).toFloat().coerceIn(0f, 1f) else null
}

/** The normal library remains authoritative for file access and mobile progress. */
internal fun mergeMobileCatalog(videos: List<MobileLibraryVideo>, catalog: List<MobileLibraryVideo>): List<MobileLibraryVideo> {
    val byPath = catalog.associateBy { it.path }
    return videos.map { video ->
        val enriched = byPath[video.path]
        if (enriched == null) video else video.copy(metadata = enriched.metadata, posterReady = enriched.posterReady)
    }
}

internal fun visibleMobileVideos(videos: List<MobileLibraryVideo>, query: String, resumeOnly: Boolean): List<MobileLibraryVideo> {
    fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.FRANCE)
    val needle = normalize(query.trim())
    val collator = Collator.getInstance(Locale.FRANCE).apply { strength = Collator.SECONDARY }
    return videos.filter { video ->
        (!resumeOnly || video.hasProgress) && (needle.isEmpty() ||
            normalize(video.displayTitle).contains(needle) || normalize(video.title).contains(needle) ||
            normalize(video.path.substringAfterLast('/')).contains(needle))
    }.sortedWith { a, b ->
        val titleOrder = collator.compare(a.displayTitle, b.displayTitle)
        if (titleOrder != 0) titleOrder else a.path.compareTo(b.path)
    }
}

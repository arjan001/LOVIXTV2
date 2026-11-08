/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.lovixtv.ui.vod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.lifecycle.viewmodel.compose.viewModel
import app.lovixtv.R
import app.lovixtv.data.model.CastMember
import app.lovixtv.data.model.Movie
import app.lovixtv.data.model.Person
import app.lovixtv.data.model.Series
import app.lovixtv.data.model.Source
import app.lovixtv.data.model.Tag
import app.lovixtv.data.parser.displayTitle
import app.lovixtv.ui.VodViewModel
import app.lovixtv.ui.channels.OnScreenKeyboard
import coil.compose.AsyncImage

private val ORANGE_ACTIVE = Color(0xFFFFA500)
private val PINK_ACCENT = Color(0xFFFF6EA8)
private val PINK_LIGHT = Color(0xFFFF9EC4)

// Gender/orientation options with their display icons (emoji-style unicode for simplicity)
private val GENRE_OPTIONS = listOf(
    GenreEntry("Straight", "⚤"),
    GenreEntry("Bisexual", "◈"),
    GenreEntry("Gay", "♂"),
    GenreEntry("Lesbian", "♀"),
    GenreEntry("Trans", "⚧"),
)

data class GenreEntry(val name: String, val icon: String)

/**
 * Movies: a modern, row-based home — Continue Watching, Recommended, Recently added and a row per
 * genre, each a horizontally-scrolling shelf of poster cards. A category chip strip along the top
 * keeps whole-category browsing one press away without a permanent rail eating the width. Clicking
 * a film opens its detail page rather than playing straight away, the streaming-app convention.
 */
@Composable
fun MoviesScreen(
    onOpenMovie: (Movie) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    onOpenPerson: (String) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    showFavouritesOnly: Boolean = false,
    favoritesOnly: Boolean = false,
    viewModel: VodViewModel = viewModel(),
) {
    val resume by viewModel.continueWatching.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedMovies.collectAsState()
    val genreRows by viewModel.movieGenreRows.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    
    // Search query state for live filtering
    var searchQuery by remember { mutableStateOf("") }
    
    // Gender/orientation filter state
    var genderFilter by remember { mutableStateOf<String?>(null) }
    
    // Covers/stills toggle state
    var showCovers by remember { mutableStateOf(true) }
    
    // Favorites toggle state
    var showFavoritesOnly by remember { mutableStateOf(false) }
    
    // All movies from the view model
    val movies by viewModel.movies.collectAsState()
    val favoriteMovieIds by viewModel.favoriteMovies.collectAsState()
    
    // Compute genre counts from all movies
    val genreCounts = remember(movies) {
        val counts = mutableMapOf<String, Int>()
        for (entry in GENRE_OPTIONS) {
            counts[entry.name] = 0
        }
        for (movie in movies) {
            val genre = movie.genre ?: continue
            val genreLower = genre.lowercase()
            for (entry in GENRE_OPTIONS) {
                if (genreLower.contains(entry.name.lowercase())) {
                    counts[entry.name] = counts.getOrDefault(entry.name, 0) + 1
                }
            }
        }
        counts
    }
    
    // Filter movies by selected tag, search query, gender filter, and favorites
    val filteredMovies = remember(selectedTag, movies, searchQuery, genderFilter, showFavoritesOnly, favoriteMovieIds) {
        val tag = selectedTag
        val query = searchQuery.trim().lowercase()
        val favIds = favoriteMovieIds.map { it.id }.toSet()
        var result = movies
        
        // Apply favorites filter
        if (showFavoritesOnly) {
            result = result.filter { favIds.contains(it.id) }
        }
        
        // Apply tag filter
        if (tag != null) {
            val tagLower = tag.lowercase()
            result = result.filter { movie ->
                movie.genre?.contains(tagLower, ignoreCase = true) == true ||
                movie.cast?.contains(tagLower, ignoreCase = true) == true ||
                movie.plot?.contains(tagLower, ignoreCase = true) == true ||
                movie.name?.contains(tagLower, ignoreCase = true) == true
            }
        }
        
        // Apply search query filter
        if (query.isNotEmpty()) {
            result = result.filter { movie ->
                movie.name?.contains(query, ignoreCase = true) == true ||
                movie.cast?.contains(query, ignoreCase = true) == true ||
                movie.genre?.contains(query, ignoreCase = true) == true ||
                movie.plot?.contains(query, ignoreCase = true) == true
            }
        }
        
        // Apply gender filter — check genre field first (primary source), then cast
        if (genderFilter != null) {
            val gender = genderFilter!!
            val genderLower = gender.lowercase()
            result = result.filter { movie ->
                // Check genre field (e.g. "Gay", "Lesbian", "Straight", "Bisexual", "Trans")
                movie.genre?.let { g ->
                    g.split(",", "|").map { it.trim().lowercase() }.any { it == genderLower || it.contains(genderLower) }
                } == true ||
                // Fallback: check cast field
                movie.cast?.contains(gender, ignoreCase = true) == true
            }
        }
        
        result
    }
    
    val displayMovies = filteredMovies
    
    val categoryMovies by viewModel.movies.collectAsState()
    val favoriteMovies by viewModel.favoriteMovies.collectAsState()
    val vodLoading by viewModel.vodLoading.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val selectedSource by viewModel.selectedVodSource.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()

    // Cast to concrete types to avoid ambiguity
    val allCastPeople = people.filterIsInstance<app.lovixtv.data.model.Person>()
    val allCastTags = tags.filterIsInstance<app.lovixtv.data.model.Tag>()

    // When a tag is selected, only show tags that appear in the filtered movies (related tags)
    val relatedTags = remember(selectedTag, allCastTags, movies) {
        if (selectedTag == null) allCastTags
        else {
            // Get the filtered movies for this tag
            val tagLower = selectedTag!!.lowercase()
            val tagMovies = movies.filter { movie ->
                movie.genre?.contains(tagLower, ignoreCase = true) == true ||
                movie.cast?.contains(tagLower, ignoreCase = true) == true ||
                movie.plot?.contains(tagLower, ignoreCase = true) == true ||
                movie.name?.contains(tagLower, ignoreCase = true) == true
            }
            // Collect all tags from these movies
            val tagCounts = mutableMapOf<String, Int>()
            for (movie in tagMovies) {
                movie.genre?.split(",")?.forEach { g ->
                    val trimmed = g.trim()
                    if (trimmed.isNotBlank()) tagCounts[trimmed] = tagCounts.getOrDefault(trimmed, 0) + 1
                }
                movie.cast?.split(",")?.forEach { c ->
                    val trimmed = c.trim()
                    if (trimmed.isNotBlank()) tagCounts[trimmed] = tagCounts.getOrDefault(trimmed, 0) + 1
                }
            }
            // Filter to only tags that have matches, sorted by count
            allCastTags.filter { tag ->
                tagCounts.containsKey(tag.name)
            }.map { tag ->
                Tag(tag.name, tagCounts.getOrDefault(tag.name, tag.count))
            }.sortedByDescending { it.count }
        }
    }

    // Build cast people list: prioritize castMembers from movies (real TMDB images),
    // then API people, then cast names from movie.cast string
    val mergedCastPeople = remember(allCastPeople, movies) {
        val nameToPerson = mutableMapOf<String, Person>()

        // 1. First, extract cast from movie.castMembers (these have real TMDB person images)
        for (movie in movies) {
            movie.castMembers?.forEach { cm ->
                val name = cm.name.trim()
                if (name.isNotBlank()) {
                    val key = name.lowercase()
                    val existing = nameToPerson[key]
                    if (existing != null) {
                        // Keep the one with image, increment count
                        val hasImage = cm.imageUrl != null && cm.imageUrl!!.isNotBlank()
                        if (hasImage && (existing.imageUrl == null || existing.imageUrl!!.isBlank())) {
                            nameToPerson[key] = Person(
                                id = name.hashCode().toString(),
                                name = name,
                                count = existing.count + 1,
                                imageUrl = cm.imageUrl,
                                gender = cm.gender
                            )
                        } else {
                            nameToPerson[key] = existing.copy(count = existing.count + 1)
                        }
                    } else {
                        nameToPerson[key] = Person(
                            id = name.hashCode().toString(),
                            name = name,
                            count = 1,
                            imageUrl = cm.imageUrl,
                            gender = cm.gender
                        )
                    }
                }
            }
        }

        // 2. Merge API people (they may have real person photos from TMDB)
        for (p in allCastPeople) {
            val key = p.name.lowercase()
            val existing = nameToPerson[key]
            if (existing != null) {
                // API person already found from castMembers — keep the one with image
                val apiHasImage = p.imageUrl != null && p.imageUrl!!.isNotBlank()
                val existingHasImage = existing.imageUrl != null && existing.imageUrl!!.isNotBlank()
                if (apiHasImage && !existingHasImage) {
                    nameToPerson[key] = Person(
                        id = existing.id,
                        name = existing.name,
                        count = existing.count,
                        imageUrl = p.imageUrl,
                        gender = p.gender ?: existing.gender
                    )
                } else if (apiHasImage && existingHasImage) {
                    // Both have images, keep existing (from castMembers, more likely correct)
                    nameToPerson[key] = existing.copy(
                        count = maxOf(existing.count, p.count)
                    )
                } else {
                    // Neither has image or existing has image, keep existing
                    nameToPerson[key] = existing.copy(
                        count = maxOf(existing.count, p.count)
                    )
                }
            } else if (p.imageUrl != null && p.imageUrl!!.isNotBlank()) {
                // New API person with real image
                nameToPerson[key] = p
            } else if (nameToPerson[key] == null) {
                // Add API person even without image
                nameToPerson[key] = p
            }
        }

        // 3. Count cast names from movie.cast string for any remaining names
        for (movie in movies) {
            movie.cast?.split(",")?.forEach { c ->
                val name = c.trim()
                if (name.isNotBlank()) {
                    val key = name.lowercase()
                    val existing = nameToPerson[key]
                    if (existing != null) {
                        nameToPerson[key] = existing.copy(count = existing.count + 1)
                    } else {
                        nameToPerson[key] = Person(
                            id = name.hashCode().toString(),
                            name = name,
                            count = 1,
                            imageUrl = null,
                            gender = null,
                        )
                    }
                }
            }
        }

        nameToPerson.values.toList().sortedByDescending { it.count }
    }

    // Filter cast people based on search query - only show people with images or known names
    val filteredCastPeople = remember(searchQuery, mergedCastPeople) {
        val query = searchQuery.trim().lowercase()
        val people = if (query.isEmpty()) {
            // For default view, only show people with real images to avoid clutter
            mergedCastPeople.filter { it.imageUrl != null && it.imageUrl!!.isNotBlank() }.take(50)
        } else {
            // For search, show all matching people (even without images)
            mergedCastPeople.filter { it.name.contains(query, ignoreCase = true) }
        }
        people
    }

    // Pull the movie library the first time this tab is opened, not at login; refresh the computed
    // home rows (recommended, by-genre) on open too — cheap, and covers a library already on disk.
    LaunchedEffect(Unit) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
    }

    val hasContent = resume.isNotEmpty() || recentlyAdded.isNotEmpty() ||
        genreRows.isNotEmpty() || displayMovies.isNotEmpty()

    // Determine which content state to show
    val tagsToShow = if (allCastTags.isNotEmpty()) allCastTags else relatedTags
    val posterWidth = if (showCovers) POSTER_WIDTH else POSTER_WIDTH_STILL
    val posterAspect = if (showCovers) (2f / 3f) else (16f / 10f)

    val contentState = when {
        displayMovies.isEmpty() && (selectedTag != null || searchQuery.isNotEmpty()) -> "empty_filtered"
        !hasContent && (vodLoading || isSyncing) -> "loading"
        !hasContent && hasSources -> "empty_provider"
        !hasContent -> "empty_add"
        else -> "content"
    }

    // Single LazyVerticalGrid to avoid nesting scrollable containers
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // Search bar
        item {
            SearchAffordance(
                onOpenSearch = onOpenSearch,
                viewModel = viewModel,
                onNavigateToMovie = { movie -> onOpenMovie(movie) },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                allCastPeople = allCastPeople,
                filteredCastPeople = filteredCastPeople,
                displayMovies = displayMovies,
                onOpenPerson = onOpenPerson,
                onOpenMovie = onOpenMovie,
            )
        }
        // Navbar controls: covers/stills, refresh, gender dropdown, favorites with badge
        item {
            NavbarControls(
                genderFilter = genderFilter,
                onGenderFilterChange = { genderFilter = it },
                showCovers = showCovers,
                onShowCoversChange = { showCovers = it },
                onRefresh = { viewModel.refreshVod() },
                showFavoritesOnly = showFavoritesOnly,
                onShowFavoritesOnlyChange = { showFavoritesOnly = it },
                genreCounts = genreCounts,
                favoriteCount = favoriteMovies.size,
            )
        }
        // Provider chips only when multiple sources configured
        if (sources.size > 1) {
            item {
                ProviderChips(
                    sources = sources,
                    selected = selectedSource,
                    onSelectAll = { viewModel.selectVodSource(null) },
                    onSelectSource = { id -> viewModel.selectVodSource(id) },
                )
            }
        }
        // Tags shelf with selected tag highlight and clear filter
        if (tagsToShow.isNotEmpty()) {
            item {
                TagsShelf(
                    tags = tagsToShow,
                    selectedTag = selectedTag,
                    searchQuery = searchQuery,
                    showFavoritesOnly = showFavoritesOnly,
                    onTagClick = { tag ->
                        if (tag == selectedTag) {
                            viewModel.selectTag(null) // clear filter
                        } else {
                            onTagClick(tag)
                            viewModel.selectTag(tag)
                        }
                    },
                    onClearFilter = {
                        viewModel.selectTag(null)
                        searchQuery = ""
                        showFavoritesOnly = false
                        genderFilter = null
                    }
                )
            }
        }
        // Cast shelf - consolidated cast images in one line
        if (filteredCastPeople.isNotEmpty()) {
            item {
                CastShelf(people = filteredCastPeople, onOpenPerson = onOpenPerson)
            }
        }

        // Continue Watching in its own separate row (only on home, not when filtering)
        if (selectedTag == null && searchQuery.isEmpty() && resume.isNotEmpty()) {
            item {
                ContinueWatchingRow(resume, onResume)
            }
        }

        // Empty/loading states or movie grid
        when (contentState) {
            "empty_filtered" -> item {
                EmptyVod(
                    stringResource(R.string.vod_no_movies),
                    if (selectedTag != null) "No movies with tag: $selectedTag"
                    else "No movies match your search"
                )
            }
            "loading" -> item {
                LoadingVod(stringResource(R.string.vod_loading_movies))
            }
            "empty_provider" -> item {
                EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_provider))
            }
            "empty_add" -> item {
                EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_add))
            }
            "content" -> {
                if (displayMovies.isNotEmpty()) {
                    gridItems(displayMovies, key = { it.id }) { movie ->
                        // Use posterUrl, fallback to backdropUrl
                        val effectivePoster = movie.posterUrl?.takeIf { it.isNotBlank() }
                            ?: movie.backdropUrl?.takeIf { it.isNotBlank() }
                        PosterCard(
                            title = movie.displayTitle,
                            posterUrl = effectivePoster,
                            subtitle = movie.year?.toString(),
                            rating = movie.rating,
                            onClick = { onOpenMovie(movie) },
                            aspectRatio = posterAspect,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shows: row-based home with Continue Watching, Recently Added, and all series in an adaptive grid.
 * A show opens its detail page — where episodes are fetched on demand.
 */
@Composable
fun SeriesScreen(
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    onOpenPerson: (String) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    viewModel: VodViewModel = viewModel(),
) {
    val resume by viewModel.continueWatching.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSeries.collectAsState()
    val categorySeries by viewModel.series.collectAsState()
    val vodLoading by viewModel.vodLoading.collectAsState()
    val sources by viewModel.sources.collectAsState()
    val selectedSource by viewModel.selectedVodSource.collectAsState()
    val people by viewModel.people.collectAsState()
    val tags by viewModel.tags.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var genderFilter by remember { mutableStateOf<String?>(null) }
    var showCovers by remember { mutableStateOf(true) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    val castPeople = people.filterIsInstance<app.lovixtv.data.model.Person>()
    val castTags = tags.filterIsInstance<app.lovixtv.data.model.Tag>()

    LaunchedEffect(Unit) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
    }

    // Single LazyVerticalGrid to avoid nesting scrollable containers
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        // Search bar
        item {
            SearchAffordance(
                onOpenSearch = onOpenSearch,
                viewModel = viewModel,
                onNavigateToMovie = null,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                allCastPeople = castPeople,
                filteredCastPeople = castPeople,
                displayMovies = emptyList(),
                onOpenPerson = onOpenPerson,
                onOpenMovie = null,
            )
        }
        // Navbar controls
        item {
            NavbarControls(
                genderFilter = genderFilter,
                onGenderFilterChange = { genderFilter = it },
                showCovers = showCovers,
                onShowCoversChange = { showCovers = it },
                onRefresh = { viewModel.refreshVod() },
                showFavoritesOnly = showFavoritesOnly,
                onShowFavoritesOnlyChange = { showFavoritesOnly = it },
                genreCounts = emptyMap(),
                favoriteCount = 0,
            )
        }
        // Provider chips
        if (sources.size > 1) {
            item {
                ProviderChips(
                    sources = sources,
                    selected = selectedSource,
                    onSelectAll = { viewModel.selectVodSource(null) },
                    onSelectSource = { id -> viewModel.selectVodSource(id) },
                )
            }
        }
        // Tags shelf
        if (castTags.isNotEmpty()) {
            item {
                TagsShelf(tags = castTags, selectedTag = null, onTagClick = onTagClick, onClearFilter = {})
            }
        }
        // Cast shelf
        if (castPeople.isNotEmpty()) {
            item {
                CastShelf(people = castPeople, onOpenPerson = onOpenPerson)
            }
        }

        // Continue Watching in its own separate row
        if (resume.isNotEmpty()) {
            item {
                ContinueWatchingRow(resume, onResume)
            }
        }

        // Empty/loading states or series grid
        when {
            categorySeries.isEmpty() && (vodLoading || isSyncing) -> item {
                LoadingVod(stringResource(R.string.vod_loading_shows))
            }
            categorySeries.isEmpty() && hasSources -> item {
                EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_provider))
            }
            categorySeries.isEmpty() -> item {
                EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_add))
            }
            categorySeries.isNotEmpty() -> {
                gridItems(categorySeries, key = { it.id }) { series ->
                    PosterCard(
                        title = series.displayTitle,
                        posterUrl = series.posterUrl,
                        subtitle = series.year?.toString(),
                        rating = series.rating,
                        onClick = { onOpenSeries(series) },
                    )
                }
            }
        }
    }
}

// ---- Whole-category browse grids ---------------------------------------------------------------

/** One category's films as a poster grid. Quality variants collapse to one card, badged. */
@Composable
private fun MovieCategoryGrid(movies: List<Movie>, viewModel: VodViewModel, onOpenMovie: (Movie) -> Unit) {
    if (movies.isEmpty()) { LoadingVod(stringResource(R.string.vod_loading_movies)); return }
    val groups = remember(movies) { viewModel.collapseVariants(movies) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = POSTER_WIDTH),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(groups, key = { it.primary.id }) { group ->
            val quality = group.variants.firstOrNull()?.qualityLabel?.takeIf { it.isNotBlank() }
            val badge = quality
                ?: if (group.hasMultipleQualities) {
                    stringResource(R.string.guide_qualities_count, group.variants.size)
                } else {
                    null
                }
            PosterCard(
                title = group.primary.displayTitle,
                posterUrl = group.primary.posterUrl,
                subtitle = group.primary.year?.toString(),
                rating = group.primary.rating,
                qualityBadge = badge,
                onClick = { onOpenMovie(group.primary) },
            )
        }
    }
}

/** One category's shows as a poster grid. */
@Composable
private fun SeriesCategoryGrid(series: List<Series>, onOpenSeries: (Series) -> Unit) {
    if (series.isEmpty()) { LoadingVod(stringResource(R.string.vod_loading_shows)); return }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = POSTER_WIDTH),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(series, key = { it.id }) { item ->
            PosterCard(
                title = item.displayTitle,
                posterUrl = item.posterUrl,
                subtitle = item.year?.toString(),
                rating = item.rating,
                onClick = { onOpenSeries(item) },
            )
        }
    }
}

// ---- Shared shelves ----------------------------------------------------------------------------

/** A titled horizontal shelf of movie poster cards. Shared by the home and the detail's "more like this". */
@Composable
internal fun MoviePosterRow(title: String, movies: List<Movie>, onOpenMovie: (Movie) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(movies, key = { it.id }) { movie ->
                PosterCard(
                    title = movie.displayTitle,
                    posterUrl = movie.posterUrl,
                    subtitle = movie.year?.toString(),
                    rating = movie.rating,
                    onClick = { onOpenMovie(movie) },
                )
            }
        }
    }
}

/** A titled horizontal shelf of series poster cards. */
@Composable
internal fun SeriesPosterRow(title: String, series: List<Series>, onOpenSeries: (Series) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(series, key = { it.id }) { item ->
                PosterCard(
                    title = item.displayTitle,
                    posterUrl = item.posterUrl,
                    subtitle = item.year?.toString(),
                    rating = item.rating,
                    onClick = { onOpenSeries(item) },
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

/**
 * The reusable poster card: art, title and an optional year, with a rating chip, a quality badge and
 * a resume progress bar drawn over the art where the data is there. The focused card scales up and
 * gains a primary border — the app's established focus cue — and, being focusable, the lazy row
 * brings it into view on its own.
 */
@Composable
internal fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rating: Double? = null,
    qualityBadge: String? = null,
    progress: Float? = null,
    aspectRatio: Float = 2f / 3f,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "posterScale")
    val cardWidth = if (aspectRatio > 1f) POSTER_WIDTH_STILL else POSTER_WIDTH
    Column(
        modifier
            .width(cardWidth)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    else Modifier,
                ),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            rating?.takeIf { it > 0.0 }?.let {
                Badge(
                    text = "★ ${formatRating(it)}",
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
            qualityBadge?.let {
                Badge(
                    text = it,
                    highlight = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** A small rounded chip drawn over poster art — a rating or a quality label. */
@Composable
private fun Badge(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val bg = if (highlight) MaterialTheme.colorScheme.primary
    else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.66f)
    val fg = if (highlight) MaterialTheme.colorScheme.onPrimary else androidx.compose.ui.graphics.Color.White
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---- Continue watching -------------------------------------------------------------------------

@Composable
internal fun ContinueWatchingRow(
    items: List<VodViewModel.ResumeItem>,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.vod_continue_watching))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.mediaKey }) { item ->
                ResumeCard(item) { onResume(item.mediaKey, item.streamUrl, item.title) }
            }
        }
    }
}

/** A landscape resume thumbnail with a progress fill — a movie or an episode part-way through. */
@Composable
private fun ResumeCard(item: VodViewModel.ResumeItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "resumeScale")
    Column(
        Modifier
            .width(190.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(107.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    else Modifier,
                ),
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Category chips ----------------------------------------------------------------------------

/**
 * A horizontal chip strip along the top of the home: an "All" chip returns to the curated rows, and
 * each following chip opens that category's full grid. Keeps whole-category browsing reachable on a
 * d-pad without a permanent side rail taking the width.
 */
@Composable
private fun CategoryChips(
    entries: List<Pair<String, String>>,
    selected: String?,
    onSelectHome: () -> Unit,
    onSelectCategory: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "label") {
            Text(
                stringResource(R.string.vod_categories),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        item(key = "all") { Chip(stringResource(R.string.vod_all), selected == null, onSelectHome) }
        items(entries, key = { it.first }) { (id, name) ->
            Chip(name, selected == id) { onSelectCategory(id) }
        }
    }
}

/**
 * A provider filter above the category chips, shown only when more than one source is configured —
 * pick a provider to browse just its Movies/Shows categories, or "All sources" to fold them.
 */
@Composable
private fun ProviderChips(
    sources: List<Source>,
    selected: Long?,
    onSelectAll: () -> Unit,
    onSelectSource: (Long) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "plabel") {
            Text(
                stringResource(R.string.channels_manager_source_header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        item(key = "pall") {
            Chip(stringResource(R.string.channels_manager_all_sources), selected == null, onSelectAll)
        }
        items(sources, key = { it.id }) { source ->
            Chip(source.name, selected == source.id) { onSelectSource(source.id) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        focused -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ---- Search / loading / empty ------------------------------------------------------------------

/**
 * Modern inline search bar with live filtering for movies and cast/actors.
 * As the user types, both movie covers and cast circles are filtered in real-time.
 */
@Composable
private fun SearchAffordance(
    onOpenSearch: () -> Unit,
    viewModel: VodViewModel,
    onNavigateToMovie: ((Movie) -> Unit)? = null,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    allCastPeople: List<Person> = emptyList(),
    filteredCastPeople: List<Person> = emptyList(),
    displayMovies: List<Movie> = emptyList(),
    onOpenPerson: (String) -> Unit = {},
    onOpenMovie: ((Movie) -> Unit)? = null,
) {
    var searching by remember { mutableStateOf(false) }
    val movieResults by viewModel.movieResults.collectAsState()
    val seriesResults by viewModel.seriesResults.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val people by viewModel.people.collectAsState()
    
    val suggestions = remember(searchQuery, movieResults) {
        if (searchQuery.length >= 2) {
            movieResults.take(5).map { it.displayTitle }
        } else emptyList()
    }
    
    val filteredTags = remember(searchQuery, tags) {
        val castTags = tags.filterIsInstance<app.lovixtv.data.model.Tag>()
        if (searchQuery.length >= 2) {
            castTags.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(5)
        } else emptyList()
    }
    
    val filteredPeople = remember(searchQuery, people) {
        val castPeople = people.filterIsInstance<app.lovixtv.data.model.Person>()
        if (searchQuery.length >= 2) {
            castPeople.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(5)
        } else emptyList()
    }
    
    LaunchedEffect(searchQuery) {
        viewModel.setVodSearchQuery(searchQuery)
    }
    
    val navigateToFirst = {
        if (movieResults.isNotEmpty() && onNavigateToMovie != null) {
            onNavigateToMovie(movieResults.first())
            onSearchQueryChange("")
            searching = false
            viewModel.setVodSearchQuery("")
        }
    }
    
    Column(Modifier.fillMaxWidth()) {
        // Search input bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (searching) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .clickable {
                    if (!searching) searching = true
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (searching && keyEvent.key == Key.Enter && keyEvent.type == KeyEventType.KeyDown) {
                        navigateToFirst()
                        true
                    } else if (searching && keyEvent.key == Key.DirectionCenter && keyEvent.type == KeyEventType.KeyDown) {
                        navigateToFirst()
                        true
                    } else false
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (searching) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
            
            Icon(Icons.Filled.Search, contentDescription = null, tint = tint)
            Spacer(Modifier.width(12.dp))
            
            if (searching) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { onSearchQueryChange(it) },
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                stringResource(R.string.vod_search_hint),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                        innerTextField()
                    },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    onSearchQueryChange("")
                    searching = false
                    viewModel.setVodSearchQuery("")
                }) {
                    Text(stringResource(R.string.common_done), color = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                Text(
                    stringResource(R.string.vod_search_hint),
                    style = MaterialTheme.typography.titleMedium,
                    color = tint,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Live filtered results section - shows when there's a search query
        if (searchQuery.isNotEmpty() && (filteredCastPeople.isNotEmpty() || displayMovies.isNotEmpty() || filteredPeople.isNotEmpty() || filteredTags.isNotEmpty())) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Live filtered cast circles
                if (filteredCastPeople.isNotEmpty()) {
                    Text(
                        "Actors (${filteredCastPeople.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredCastPeople.take(10), key = { it.id }) { person ->
                            CastCircleSmall(person, onOpenPerson)
                        }
                    }
                }
                // Live filtered movies
                if (displayMovies.isNotEmpty() && searchQuery.isNotEmpty()) {
                    Text(
                        "Movies (${displayMovies.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(displayMovies.take(10), key = { it.id }) { movie ->
                            val effectivePoster = movie.posterUrl?.takeIf { it.isNotBlank() } ?: movie.backdropUrl
                            MiniPosterCardWithPoster(movie, effectivePoster, onOpenMovie ?: {})
                        }
                    }
                }
            }
        }
        
        // Autocomplete suggestions dropdown (when searching mode is active)
        if (searching && (suggestions.isNotEmpty() || filteredTags.isNotEmpty() || filteredPeople.isNotEmpty())) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    Text(
                        suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val movie = movieResults.find { it.displayTitle == suggestion }
                                if (movie != null && onNavigateToMovie != null) {
                                    onNavigateToMovie(movie)
                                    onSearchQueryChange("")
                                    searching = false
                                    viewModel.setVodSearchQuery("")
                                }
                            }
                            .padding(vertical = 6.dp, horizontal = 12.dp)
                    )
                }
                if (filteredTags.isNotEmpty()) {
                    Text(
                        "Tags",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp, start = 12.dp)
                    )
                    filteredTags.forEach { tag ->
                        Text(
                            "${tag.name} (${tag.count})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSearchQueryChange(tag.name)
                                    viewModel.setVodSearchQuery(tag.name)
                                }
                                .padding(vertical = 6.dp, horizontal = 12.dp)
                        )
                    }
                }
                if (filteredPeople.isNotEmpty()) {
                    Text(
                        "Actors",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 12.dp)
                    )
                    filteredPeople.forEach { person ->
                        Text(
                            person.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSearchQueryChange(person.name) }
                                .padding(vertical = 6.dp, horizontal = 12.dp)
                        )
                    }
                }
            }
        }
        
        if (searching) {
            Spacer(Modifier.weight(1f))
            OnScreenKeyboard(
                onKey = { if (searchQuery.length < 40) onSearchQueryChange(searchQuery + it) },
                onSpace = { if (searchQuery.length < 40) onSearchQueryChange(searchQuery + " ") },
                onBackspace = { onSearchQueryChange(searchQuery.dropLast(1)) },
                onClear = { onSearchQueryChange("") },
                onEnter = { navigateToFirst() },
            )
        }
    }
}

/** Small circular cast image for live search results */
@Composable
private fun CastCircleSmall(person: Person, onOpenPerson: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val circleShape = CircleShape
    Column(
        Modifier
            .width(60.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onOpenPerson(person.name) }
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(circleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, circleShape)
                    else Modifier,
                ),
        ) {
            if (person.imageUrl != null) {
                AsyncImage(
                    model = person.imageUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(circleShape),
                )
            } else {
                Text(
                    person.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Text(
            person.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Mini poster card for live search results with explicit poster URL */
@Composable
private fun MiniPosterCardWithPoster(movie: Movie, posterUrl: String?, onOpenMovie: (Movie) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(100.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .clickable { onOpenMovie(movie) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    else Modifier,
                ),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = movie.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            movie.displayTitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Navbar controls row — all on one horizontal scrollable line:
 * Covers/Stills → Refresh → Gender dropdown → Favorites heart with badge.
 * Order matches the design: covers, refresh, gender select, favorites.
 */
@Composable
private fun NavbarControls(
    genderFilter: String?,
    onGenderFilterChange: (String?) -> Unit,
    showCovers: Boolean,
    onShowCoversChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    showFavoritesOnly: Boolean,
    onShowFavoritesOnlyChange: (Boolean) -> Unit,
    genreCounts: Map<String, Int>,
    favoriteCount: Int,
) {
    var genderExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val currentGenre = genderFilter ?: "All"
    val currentEntry = GENRE_OPTIONS.find { it.name == currentGenre }
    val currentCount = genreCounts[currentGenre] ?: 0

    // Horizontal scrollable row — all controls on one line, limited to ~75% width
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Controls container - takes up ~75% of the width
        Row(
            Modifier
                .weight(3f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Covers/Stills selector (rectangular)
            OutlinedButton(
                onClick = { onShowCoversChange(!showCovers) },
                modifier = Modifier.height(28.dp),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    if (showCovers) "Covers" else "Stills",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            // 2. Refresh button (rectangular)
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier.height(28.dp),
                shape = RoundedCornerShape(4.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
            }

            // 3. Gender dropdown with icon and count (rectangular)
            Box {
                val genreBg = if (genderFilter != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                val genreFg = if (genderFilter != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

                OutlinedButton(
                    onClick = { genderExpanded = true },
                    modifier = Modifier.height(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = genreBg,
                        contentColor = genreFg,
                    ),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    // Icon
                    Text(
                        currentEntry?.icon ?: "⚤",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.widthIn(min = 16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    // Label
                    Text(
                        currentGenre,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (genderFilter != null) FontWeight.Bold else FontWeight.Normal,
                    )
                    // Count badge
                    if (currentCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "($currentCount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = genreFg.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                }
                DropdownMenu(
                    expanded = genderExpanded,
                    onDismissRequest = { genderExpanded = false },
                ) {
                    // "All" option
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("All", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "(${genreCounts.values.sum()})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { onGenderFilterChange(null); genderExpanded = false },
                        modifier = if (genderFilter == null) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier,
                    )
                    // Genre options with icons and counts
                    for (entry in GENRE_OPTIONS) {
                        val count = genreCounts[entry.name] ?: 0
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.icon, modifier = Modifier.widthIn(min = 16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "($count)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = { onGenderFilterChange(entry.name); genderExpanded = false },
                            modifier = if (genderFilter == entry.name) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier,
                        )
                    }
                    // Favorites option with pink glow
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp), tint = PINK_ACCENT)
                                Spacer(Modifier.width(6.dp))
                                Text("Favorites", style = MaterialTheme.typography.bodyMedium, color = PINK_LIGHT)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "($favoriteCount)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PINK_LIGHT,
                                )
                            }
                        },
                        onClick = { onShowFavoritesOnlyChange(!showFavoritesOnly); genderExpanded = false },
                        modifier = if (showFavoritesOnly) Modifier.background(PINK_ACCENT.copy(alpha = 0.15f)) else Modifier,
                    )
                }
            }

            // 4. Favorites heart button with count badge
            Box(modifier = Modifier.size(28.dp)) {
                IconButton(
                    onClick = { onShowFavoritesOnlyChange(!showFavoritesOnly) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        if (showFavoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorites",
                        modifier = Modifier.size(18.dp),
                        tint = if (showFavoritesOnly) PINK_ACCENT else MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Count badge
                if (favoriteCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(PINK_ACCENT),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (favoriteCount > 99) "99+" else favoriteCount.toString(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // End of controls container (~75% width)
        // Remaining ~25% space (empty spacer)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun EmptyVod(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingVod(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.vod_large_provider_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Poster shelf card width; the grid uses an adaptive min size close to this.
* Sized for TV screens: ~180 dp gives 4–6 columns on 1080p/4K displays. */
private val POSTER_WIDTH = 180.dp
/** Wider card for stills/landscape mode */
private val POSTER_WIDTH_STILL = 240.dp

/** Rating to one decimal place, locale-independent (the "★" is drawn beside it). */
internal fun formatRating(rating: Double): String = String.format(java.util.Locale.US, "%.1f", rating)

// ---- Phase 2: Cast/Creators Shelf -----------------------------------------------------------

@Composable
private fun CastShelf(people: List<Person>, onOpenPerson: (String) -> Unit = {}) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(people, key = { it.id }) { person ->
            CastCard(person, onOpenPerson)
        }
    }
}

@Composable
private fun CastCard(person: Person, onOpenPerson: (String) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "castScale")
    val circleShape = CircleShape
    Column(
        Modifier
            .width(100.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = { onOpenPerson(person.name) })
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(circleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, circleShape)
                    else Modifier,
                ),
        ) {
            if (person.imageUrl != null) {
                AsyncImage(
                    model = person.imageUrl,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(circleShape),
                )
            } else {
                // Fallback: show initials if no image
                Text(
                    person.name.take(2).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            person.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (person.count > 0) {
            Text(
                "${person.count} films",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

// ---- Phase 2: Tags Shelf -----------------------------------------------------------------------

@Composable
private fun TagsShelf(
    tags: List<Tag>,
    selectedTag: String? = null,
    searchQuery: String = "",
    showFavoritesOnly: Boolean = false,
    onTagClick: (String) -> Unit = {},
    onClearFilter: () -> Unit = {},
) {
    val hasAnyFilter = selectedTag != null || searchQuery.isNotBlank() || showFavoritesOnly

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Favorites tag chip when favorites filter is active
        if (showFavoritesOnly) {
            item {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(PINK_ACCENT.copy(alpha = 0.2f))
                        .border(1.dp, PINK_ACCENT, RoundedCornerShape(16.dp))
                        .clickable(onClick = onClearFilter)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(12.dp), tint = PINK_LIGHT)
                        Spacer(Modifier.width(4.dp))
                        Text("Favorites", style = MaterialTheme.typography.labelSmall, color = PINK_LIGHT, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Text("×", style = MaterialTheme.typography.labelSmall, color = PINK_LIGHT)
                    }
                }
            }
        }
        // Tag chips
        items(tags, key = { it.name }) { tag ->
            TagChip(tag, selectedTag == tag.name, onTagClick)
        }
        // Pill-shaped "Clear all" button when any filter is active
        if (hasAnyFilter) {
            item {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(PINK_ACCENT.copy(alpha = 0.15f))
                        .border(1.dp, PINK_ACCENT.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .clickable(onClick = onClearFilter)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Clear all ×",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PINK_LIGHT,
                    )
                }
            }
        }
    }
}

@Composable
private fun TagChip(tag: Tag, selected: Boolean = false, onTagClick: (String) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> ORANGE_ACTIVE
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        selected -> Color.White
        focused -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .then(
                if (selected) Modifier.border(2.dp, ORANGE_ACTIVE, RoundedCornerShape(16.dp))
                else Modifier,
            )
            .clickable(onClick = { onTagClick(tag.name) })
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tag.name,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
        // Only show count if > 0, hide if 0 (no data)
        if (tag.count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                "${tag.count}",
                style = MaterialTheme.typography.labelSmall,
                color = fg,
            )
        }
    }
}

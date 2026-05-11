package com.ycs.movietracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.ui.components.StarRating
import com.ycs.movietracker.ui.components.WatchStatusBadge
import com.ycs.movietracker.util.AppConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MovieListContent(
    innerPadding: PaddingValues,
    movies: List<Movie>,
    isLoadingLists: Boolean,
    isLoadingMovies: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    hasMoreMovies: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    searchQuery: String,
    watchFilter: WatchFilter,
    homeLoadError: String? = null,
    onRetryLoad: () -> Unit = {}
) {
    // Must be called unconditionally before any early returns to satisfy Compose's composition rules
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && last >= total - AppConfig.PAGINATION_LOAD_THRESHOLD
        }
    }
    LaunchedEffect(nearEnd, hasMoreMovies, isLoadingMore) {
        if (nearEnd && hasMoreMovies && !isLoadingMore) onLoadMore()
    }

    if (isLoadingLists || isLoadingMovies) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("skeletonGrid"),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            userScrollEnabled = false
        ) {
            items(6) { SkeletonMovieCard() }
        }
        return
    }

    if (homeLoadError != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = homeLoadError,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetryLoad) {
                Text(stringResource(R.string.action_retry))
            }
        }
        return
    }

    if (movies.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            val emptyText = when {
                searchQuery.isNotBlank() -> stringResource(R.string.empty_no_movies_search)
                watchFilter != WatchFilter.ALL -> stringResource(R.string.empty_no_movies_filter)
                else -> stringResource(R.string.empty_no_movies)
            }
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        return
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().padding(innerPadding)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies, key = { it.id }) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = { onMovieClick(movie) },
                    modifier = Modifier.animateItem()
                )
            }
            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWatched = movie.status == WatchStatus.WATCHED

    val statusLabel = stringResource(
        if (isWatched) R.string.status_watched else R.string.status_want_to_watch
    )
    val ratingLabel = if (isWatched && movie.rating != null && movie.rating > 0) {
        val ratingStr = if (movie.rating == kotlin.math.floor(movie.rating))
            movie.rating.toInt().toString() else movie.rating.toString()
        stringResource(R.string.cd_rating, ratingStr)
    } else null
    val accessibilityLabel = buildString {
        append(movie.title)
        movie.year?.let { append(", $it") }
        movie.genre?.takeIf { it.isNotBlank() }?.let { append(", $it") }
        append(", $statusLabel")
        ratingLabel?.let { append(", $it") }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
            }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = movie.posterUrl?.takeIf { it.isNotEmpty() },
                    contentDescription = stringResource(R.string.cd_movie_poster, movie.title),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val imageState by painter.state.collectAsState()
                    if (imageState is AsyncImagePainter.State.Success) {
                        SubcomposeAsyncImageContent()
                    } else {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val meta = buildString {
                    movie.year?.let { append(it) }
                    if (movie.year != null && !movie.genre.isNullOrBlank()) append(" · ")
                    movie.genre?.takeIf { it.isNotBlank() }?.let { append(it) }
                }
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                WatchStatusBadge(isWatched = isWatched)

                if (isWatched && movie.rating != null && movie.rating > 0) {
                    StarRating(
                        rating = movie.rating,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

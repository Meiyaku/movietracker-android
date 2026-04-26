package com.ycs.movietracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.Movie
import com.ycs.movietracker.data.model.WatchFilter
import com.ycs.movietracker.data.model.WatchStatus
import com.ycs.movietracker.ui.components.StarRating
import com.ycs.movietracker.ui.components.WatchStatusBadge
import com.ycs.movietracker.util.AppConfig

@Composable
internal fun MovieListContent(
    innerPadding: PaddingValues,
    movies: List<Movie>,
    isLoadingLists: Boolean,
    isLoadingMovies: Boolean,
    hasMoreMovies: Boolean,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    onMovieClick: (Movie) -> Unit,
    searchQuery: String,
    watchFilter: WatchFilter
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
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

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
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

@Composable
internal fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWatched = movie.status == WatchStatus.WATCHED

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
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
                    if (painter.state is AsyncImagePainter.State.Success) {
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

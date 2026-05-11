package com.ycs.movietracker.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import com.ycs.movietracker.R
import com.ycs.movietracker.data.model.TmdbSearchResult
import com.ycs.movietracker.data.repository.TmdbRepository
import kotlinx.coroutines.launch

@Composable
fun TmdbSearchDialog(
    repository: TmdbRepository,
    onDismiss: () -> Unit,
    onResult: (TmdbSearchResult, trailerUrl: String?) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<TmdbSearchResult>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var lastQuery by remember { mutableStateOf("") }
    var loadingResultId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    fun performSearch() {
        if (query.isBlank() || isSearching) return
        isSearching = true
        error = null
        lastQuery = query
        scope.launch {
            repository.search(query).fold(
                onSuccess = { results = it },
                onFailure = { error = it.message ?: "Search failed" }
            )
            hasSearched = true
            isSearching = false
        }
    }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            error = null
            hasSearched = false
            return@LaunchedEffect
        }
        delay(500)
        if (!isSearching) performSearch()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.action_search_tmdb),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.placeholder_movie_title)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch() })
                    )
                    IconButton(
                        onClick = { performSearch() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.cd_search)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                when {
                    isSearching -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    }
                    error != null -> {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Red,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    hasSearched && results.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.empty_tmdb_no_results, lastQuery),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    results.isNotEmpty() -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(results) { result ->
                                val isLoadingThis = loadingResultId == result.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = loadingResultId == null) {
                                            loadingResultId = result.id
                                            scope.launch {
                                                val trailerUrl = repository.getTrailerUrl(result.id, result.mediaType).getOrNull()
                                                onResult(result, trailerUrl)
                                                onDismiss()
                                            }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    // Thumbnail
                                    val thumbUrl = result.posterUrl("w92")
                                    if (thumbUrl != null) {
                                        AsyncImage(
                                            model = thumbUrl,
                                            contentDescription = stringResource(R.string.cd_movie_poster, result.displayTitle),
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .width(48.dp)
                                                .height(72.dp)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .width(48.dp)
                                                .height(72.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                        )
                                    }

                                    // Text
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (result.mediaType == "tv") {
                                                Icon(
                                                    imageVector = Icons.Outlined.Tv,
                                                    contentDescription = stringResource(R.string.cd_tv_show),
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                            Text(
                                                text = result.displayTitle,
                                                style = MaterialTheme.typography.titleSmall,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isLoadingThis) {
                                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            } else {
                                                result.year?.let {
                                                    Text(
                                                        text = it,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                        result.overview?.takeIf { it.isNotBlank() }?.let {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }
}

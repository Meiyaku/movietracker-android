package com.ycs.movietracker.ui.detail

import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import androidx.compose.runtime.collectAsState
import com.ycs.movietracker.data.model.TmdbWatchProvider
import com.ycs.movietracker.data.model.TmdbWatchProviders
import com.ycs.movietracker.data.repository.TmdbRepository

@Composable
fun WhereToWatchDialog(
    tmdbId: Int,
    mediaType: String,
    tmdbRepository: TmdbRepository,
    onDismiss: () -> Unit
) {
    var providers by remember { mutableStateOf<TmdbWatchProviders?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(tmdbId, mediaType) {
        isLoading = true
        error = null
        tmdbRepository.getWatchProviders(tmdbId, mediaType, "US").fold(
            onSuccess = { providers = it },
            onFailure = { error = it.message ?: "Couldn't load providers." }
        )
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Where to Watch",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))

                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(32.dp)) }
                    }
                    error != null -> {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    providers != null && providers!!.isEmpty -> {
                        Text(
                            text = "TMDB has no streaming or purchase listings for this title in the US.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    providers != null -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                            if (providers!!.flatrate.isNotEmpty()) {
                                item { SectionHeader("Stream") }
                                items(providers!!.flatrate) { ProviderRow(it) }
                            }
                            if (providers!!.buy.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(8.dp))
                                    SectionHeader("Buy")
                                }
                                items(providers!!.buy) { ProviderRow(it) }
                            }
                        }
                    }
                }

                if (providers != null && !providers!!.isEmpty) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, "https://www.justwatch.com".toUri())
                            )
                        }
                    ) {
                        Text(
                            text = "Streaming data provided by ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        SubcomposeAsyncImage(
                            model = "https://www.justwatch.com/appassets/img/logo/JustWatch-logo-large.webp",
                            contentDescription = "JustWatch",
                            modifier = Modifier.height(18.dp)
                        ) {
                            val state by painter.state.collectAsState()
                            if (state is AsyncImagePainter.State.Success) {
                                SubcomposeAsyncImageContent()
                            } else {
                                Text(
                                    text = "JustWatch",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    providers?.link?.let { link ->
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()))
                        }) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Open on TMDB")
                        }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun ProviderRow(provider: TmdbWatchProvider) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val logo = provider.logoUrl()
        if (logo != null) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            )
        }
        Text(
            text = provider.providerName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.compose.foundation.layout.Box
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.annotation.InternalCoilApi
import coil3.util.MimeTypeMap

@Composable
fun AudioPlayerScreen(
    path: String,
    loggingOut: () -> Unit,
) {
    DropboxAssetTemplate(path, loggingOut) { audioUrl ->
        AudioPlayerContent(path, audioUrl)
    }
}

@OptIn(InternalCoilApi::class)
@Composable
fun AudioPlayerContent(
    path: String,
    audioUrl: String,
) {
    val context = LocalContext.current

    /**
     * デフォルトのシーク秒数は10秒
     */
    val defaultSeekMilliseconds = 10000L

    val player = remember {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(defaultSeekMilliseconds)
            .setSeekForwardIncrementMs(defaultSeekMilliseconds)
            .build().apply {
                playWhenReady = true
            }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsLoadingChanged(newIsLoading: Boolean) {
                isLoading = newIsLoading
            }
            override fun onIsPlayingChanged(newIsPlaying: Boolean) {
                isPlaying = newIsPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(player) {
        val mimeTypes = MimeTypeMap.getMimeTypeFromUrl(path)
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(audioUrl)
            .setMimeType(mimeTypes)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VideoControls(
            player,
            isPlaying = isPlaying,
            isLoading = isLoading,
            modifier = Modifier.fillMaxSize())
    }
}
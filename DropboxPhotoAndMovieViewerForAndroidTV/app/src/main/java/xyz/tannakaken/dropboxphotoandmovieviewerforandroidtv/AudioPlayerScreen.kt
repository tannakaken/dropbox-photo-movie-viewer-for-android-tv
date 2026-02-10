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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.compose.foundation.layout.Box
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import coil3.annotation.InternalCoilApi
import coil3.util.MimeTypeMap
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioPlayerViewModel @Inject constructor() : ViewModel() {

    private val _audioUrl = MutableStateFlow<String?>(null)
    val audioUrl: StateFlow<String?> = _audioUrl.asStateFlow()

    fun loadAudio(path: String, dropboxClient: DropboxClient) {
        viewModelScope.launch {
            _audioUrl.value = dropboxClient.getTemporaryLink(path)
        }
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(InternalCoilApi::class)
@Composable
fun AudioPlayerScreen(
    viewModel: VideoPlayerViewModel = hiltViewModel(),
    path: String,
) {
    val dropboxAccessToken = LocalDropboxAccessToken.current!!
    val dropboxClient = DropboxClient(dropboxAccessToken, DropboxPhotoAndMovieViewerApplication.client)
    val context = LocalContext.current
    val audioUrl by viewModel.videoUrl.collectAsState()

    if (audioUrl == null) {
        LaunchedEffect(path) {
            viewModel.loadVideo(path, dropboxClient)
        }
        LoadingScreen()
        return
    }

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
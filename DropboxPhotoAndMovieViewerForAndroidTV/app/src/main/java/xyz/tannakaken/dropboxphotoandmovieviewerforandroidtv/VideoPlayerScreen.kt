package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.util.Log
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
import androidx.media3.common.VideoSize
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.onKeyEvent
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil3.annotation.InternalCoilApi
import coil3.util.MimeTypeMap
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor() : ViewModel() {

    private val _videoUrl = MutableStateFlow<String?>(null)
    val videoUrl: StateFlow<String?> = _videoUrl.asStateFlow()

    fun loadVideo(path: String, dropboxClient: DropboxClient) {
        viewModelScope.launch {
            _videoUrl.value = dropboxClient.getTemporaryLink(path)
        }
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(InternalCoilApi::class)
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel = hiltViewModel(),
    path: String,
) {
    val dropboxAccessToken = LocalDropboxAccessToken.current!!
    val dropboxClient = DropboxClient(dropboxAccessToken, DropboxPhotoAndMovieViewerApplication.client)
    val context = LocalContext.current
    val videoUrl by viewModel.videoUrl.collectAsState()

    if (videoUrl == null) {
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

    var mutableVideoSize by remember { mutableStateOf<Size?>(null) }

    DisposableEffect(player) {
        // デバッグ用: 実際に描画されるサイズを取得
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                Log.d("VideoDebug", "=== VideoSize Changed ===")
                Log.d("VideoDebug", "Width: ${videoSize.width}")
                Log.d("VideoDebug", "Height: ${videoSize.height}")
                Log.d("VideoDebug", "Pixel Aspect Ratio: ${videoSize.pixelWidthHeightRatio}")
                mutableVideoSize = Size(videoSize.width.toFloat(), videoSize.height.toFloat())
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    Log.d("VideoDebug", "=== Playback Ready ===")
                    Log.d("VideoDebug", "MediaItem: ${player.currentMediaItem?.mediaId}")
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.release()
        }
    }
    // デバッグ用: MediaMetadataRetrieverでメタデータを取得
    LaunchedEffect(videoUrl) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoUrl)
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            Log.d("VideoDebug", "=== MediaMetadata ===")
            Log.d("VideoDebug", "Rotation: $rotation")
            Log.d("VideoDebug", "Width: $width")
            Log.d("VideoDebug", "Height: $height")
        } catch (e: Exception) {
            Log.e("VideoDebug", "MetadataRetriever failed: ${e.message}")
        } finally {
            retriever.release()
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
            .setUri(videoUrl)
            .setMimeType(mimeTypes)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
    }

    val aspectRatio = mutableVideoSize?.let { it.width / it.height } ?: (16f / 9f)
    var showControl by remember { mutableStateOf(false) }

    BackHandler(enabled = showControl) {
        showControl = false
    }

    Box(modifier = Modifier.fillMaxSize().onKeyEvent { keyEvent ->
        Log.d("VideoPlayerScreen", keyEvent.nativeKeyEvent.toString())
        if (!showControl && keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
            when (keyEvent.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_BACK -> false
                else -> {
                    showControl = true
                    true
                }
            }
        } else {
            false
        }
    }) {
        // ExoPlayerをJetpack Composeで使うには
        // 古いPlayerViewをAndroidViewに包むか
        // 公式がJetpack Composeに組み込んだPlayerSurfaceを使う二通の方法がある・
        // 前者は何もせずともある程度のUI/UXが出来ている。しかし、TextureViewを使うには、layout xmlを作るしかないなど、
        // 古い性質を残している面が多い。
        // 後者はUI/UXを自分で組み立てなくてはいけないし、アスペクト比なども自分で調節しなくてはならず、
        // 新しいAPIなので、まだ実験的で不安定あったり、必要であるがまだ公式が作っていなくて
        // 自作しなくてはいけないUIパーツもある、など一長一短である。
        // 最初は、PlayerViewの出来合いのUI/UXでいいかとも思ったが、リモコンでの操作に特化していないので
        // フォーカス管理などで不便な面が出てきたので、PlayerSurfaceに変更した。
        PlayerSurface(
            player = player,
            modifier = Modifier.fillMaxSize().focusable(!showControl).aspectRatio(aspectRatio, matchHeightConstraintsFirst = true),
            // ExoPlayerの実際の描画を担当するのはSurfaceViewかTextureViewである。
            // SurfaceViewはバックグラウンドでハードウェアを使って描画するので効率が良い。
            // TextureViewはUIスレッドでソフトウェアを使って描画するので、細かな制御ができる。
            // Androidのハードウェアによる描画が多機能になってきたことにより、Android 7.0以降は通常SurfaceViewが推奨される。
            // しかし、Android TVでは、縦長の動画や回転情報を含んだ動画を再生することをそもそも想定していないので、
            // ハードウェアによる回転の制御が正しく実装されていないことが多い
            // （動画のフレームの縦横自体には正しく回転が適応されるが、画像バッファには適応されないので、縦長のフレームに回転前の画像がそのまま表示される。当然アスペクト比もおかしくなる）。
            // そこで、TextureViewを使う。TextureViewならソフトウェアで動画の表示がされるので、正しく描画される。
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW
        )
        if (showControl) {
            VideoControls(
                player,
                isPlaying = isPlaying,
                isLoading = isLoading,
                modifier = Modifier.fillMaxSize()) {
                showControl = false
            }
        }
    }
}

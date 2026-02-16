package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.NetworkRequestBody
import coil3.network.httpBody
import coil3.network.httpHeaders
import coil3.network.httpMethod
import coil3.request.ImageRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okio.ByteString
import java.io.File


data class MediaItem(
    val name: String,
    val path: String,
    val id: String?,
    val duration: Int?,
    val type: MediaType
)

class DropboxMediaPagingSource(
    private val withRetry: WithRetry<Pair<FolderDigest, String?>>,
    private val folderPath: String,
    private val onFirstLoading: () -> Unit,
    private val onLoaded: () -> Unit,
    private val onError: (errorMessage: String?) -> Unit,
) : PagingSource<String, MediaItem>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, MediaItem> {
        return try {
            val cursor = params.key
            val (digest, newCursor) = withRetry { dropboxClient ->
                if (cursor == null) {
                    // 初回リクエスト
                    onFirstLoading()
                    dropboxClient.digestFolder(path = folderPath)
                } else {
                    // 続きのリクエスト
                    dropboxClient.digestFolderContinue(cursor = cursor)
                }
            }
            val mediaItems = digest.medias.map { entry ->
                MediaItem(
                    name = entry.name,
                    path = entry.pathDisplay ?: entry.pathLower ?: "",
                    id = entry.id,
                    duration = entry.mediaInfo?.metadata?.video?.duration,
                    type = mediaType(entry.pathDisplay ?: entry.pathLower ?: "")!!
                )
            }
            onLoaded()
            LoadResult.Page(
                data = mediaItems,
                prevKey = null, // Dropboxは前方ページングをサポートしていない
                nextKey = newCursor
            )
        } catch (error: Exception) {
            onError(error.message)
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<String, MediaItem>): String? {
        return null
    }
}

data class MediaGridScreenUiState(
    override val firstLoading: Boolean = false,
    override val errorMessage: String? = null
) : LazyUiState

@HiltViewModel(assistedFactory = MediaGalleryViewModel.Factory::class)
class MediaGalleryViewModel @AssistedInject constructor(
    @Assisted private val dropboxAccessToken: String,
    secureAuthStorage: SecureAuthStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val folderPath: String = savedStateHandle["folderPath"]!!
    private val _uiState = MutableStateFlow(MediaGridScreenUiState())
    private val withRetry = createWithRetry<Pair<FolderDigest, String?>>(dropboxAccessToken, secureAuthStorage)
    val uiState: StateFlow<MediaGridScreenUiState> = _uiState.asStateFlow()
    val filesPagingFlow = Pager(
        config = PagingConfig(
            // DropboxのAPIはサーバー側でフィルタリングができないので、
            // 表示したいデータを決められた数だけ取得することができない。
            // なので、このサイズは適当な値
            pageSize = 100,
            enablePlaceholders = false,
            prefetchDistance = 10
        ),
        pagingSourceFactory = {
            DropboxMediaPagingSource(
                withRetry = withRetry,
                folderPath = folderPath,
                onFirstLoading = {
                    _uiState.value = MediaGridScreenUiState(firstLoading = true)
                },
                onLoaded = {
                    _uiState.value = MediaGridScreenUiState()
                },
                onError = { errorMessage ->
                    _uiState.value = uiState.value.copy(errorMessage = errorMessage)
                }
            )
        }
    ).flow.cachedIn(viewModelScope)

    @AssistedFactory
    interface Factory {
        fun create(
            dropboxAccessToken: String,
        ): MediaGalleryViewModel
    }
}

@Composable
fun MediaGalleryScreen(
    folderPath: String,
    loggingOut: () -> Unit,
    onSelect: (MediaItem) -> Unit,
) {
    val dropboxAccessToken = LocalDropboxAccessToken.current!!
    val viewModel = hiltViewModel<MediaGalleryViewModel, MediaGalleryViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(dropboxAccessToken)
        }
    )
    var isLoggingOut by remember { mutableStateOf(false) }
    val lazyPagingItems = viewModel.filesPagingFlow.collectAsLazyPagingItems()
    val listState = rememberSaveable(
        saver = LazyGridState.Saver
    ) {
        LazyGridState()
    }

    if (isLoggingOut) {
        LoadingScreen()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxSize().padding(5.dp)
        ) {
            Row(modifier = Modifier.weight(6f).fillMaxWidth()) {
                Text(
                    text = File(folderPath).name,
                    fontSize = 24.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
                Button(
                    onClick = {},
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Text("場所を変更")
                }
                Button(
                    onClick = {},
                    modifier = Modifier.padding(end = 16.dp)
                ) {
                    Text("リロード")
                }
            }
            Row(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = {
                        isLoggingOut = true
                        loggingOut()
                    },
                ) {
                    Text("ログアウト")
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.weight(9f).fillMaxSize(),
            state = listState
        ) {
            items(lazyPagingItems.itemCount) { index ->
                lazyPagingItems[index]?.let { item ->
                    MediaGridItem(item, dropboxAccessToken, onSelect)
                }
            }
            item {
                FirstLoadingIndicator(viewModel.uiState)
            }
            item {
                if (lazyPagingItems.loadState.append is androidx.paging.LoadState.Loading) {
                    IndicatorRow()
                }
            }
            item {
                if (lazyPagingItems.loadState.hasError) {
                    LazyErrorMessage(viewModel.uiState, onReload = {
                        lazyPagingItems.retry()
                    })
                }
            }
        }
    }
}

fun escapeNonAscii(input: String): String {
    return input.map { char ->
        if (char.code > 127) {
            // 非ASCII文字の場合、\\uXXXX形式に変換
            "\\u${char.code.toString(16).padStart(4, '0')}"
        } else {
            // ASCII文字の場合、そのまま
            char.toString()
        }
    }.joinToString("")
}

private fun hasThumbnail(item: MediaItem): Boolean =
    when (item.type) {
        MediaType.AUDIO -> false
        else -> true
    }


@Composable
fun MediaGridItem(
    item: MediaItem,
    dropboxAccessToken: String,
    onClick: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    // httpアクセス時に例外が投げられた場合は、表示がうまくいかなくなるだけなので
    // ここでは敢えて処理しない。
    val request = if (hasThumbnail(item)) ImageRequest.Builder(context)
        .data("https://content.dropboxapi.com/2/files/get_thumbnail_v2")
        .httpMethod("POST")
        .httpHeaders(
            NetworkHeaders.Builder()
                .add(HttpHeaders.Authorization, "Bearer $dropboxAccessToken")
                .add("Dropbox-API-Arg", """
                    {
                      "format":"jpeg",
                      "mode":"bestfit",
                      "resource":{
                        ".tag":"path",
                        "path":"${escapeNonAscii(item.path)}"
                      },
                      "size":"w256h256"                      
                    }
                """.trimIndent().replace("\n", "")) // 非アスキー文字はユニコードのコードポイントでエスケープする必要がある。
                .build()
        ).httpBody(NetworkRequestBody(ByteString.EMPTY) // ボディが空であることを明記しないと400が返る
        ).diskCacheKey(item.path).memoryCacheKey(item.path).build()
    else null
    Log.d(Throwable().stackTrace[0].methodName, request.toString())

    Card(
        onClick = { onClick(item) },
        modifier = Modifier
            .padding(8.dp)
            .aspectRatio(1f)
    ) {
        Box {
            if (request != null) {
                AsyncImage(
                    model = request,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
            ) {
                Text(
                    item.name,
                    textAlign = TextAlign.Left,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (item.type == MediaType.VIDEO || item.type == MediaType.AUDIO) {
                Box(modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                    )
                }
                Text(
                    item.duration?.toString().orEmpty(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

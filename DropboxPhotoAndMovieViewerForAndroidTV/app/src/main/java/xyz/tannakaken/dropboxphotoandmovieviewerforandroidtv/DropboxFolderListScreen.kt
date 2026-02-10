package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.StateFlow


data class DropboxFolder(
    val name: String,
    val path: String
)

class DropboxFolderPagingSource(
    dropboxAccessToken: String,
    private val folderPath: String,
    private val onFirstLoading: () -> Unit,
    private val onLoaded: (digest: FolderDigest, newCursor: String?) -> Unit,
    private val onError: (errorMessage: String?) -> Unit,
) : PagingSource<String, DropboxFolder>() {
    private val dropboxClient = DropboxClient(dropboxAccessToken, DropboxPhotoAndMovieViewerApplication.client)
    override suspend fun load(params: LoadParams<String>): LoadResult<String, DropboxFolder> {
        return try {
            val cursor = params.key
            val (digest, newCursor) = if (cursor == null) {
                // 初回リクエスト
                onFirstLoading()
                dropboxClient.digestFolder(path = folderPath)
            } else {
                // 続きのリクエスト
                dropboxClient.digestFolderContinue(cursor = cursor)
            }
            onLoaded(digest, newCursor)
            val result = digest.folders
                .filter { folder -> folder.pathLower != null }
                .map { folder -> DropboxFolder(folder.name, folder.pathLower!!) }
            LoadResult.Page(
                data = result,
                prevKey = null, // Dropboxは前方ページングをサポートしていない
                nextKey = newCursor
            )
        } catch (error: Exception) {
            onError(error.message)
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<String, DropboxFolder>): String? {
        return null
    }
}

interface LazyUiState {
    val firstLoading: Boolean
    val errorMessage: String?
}

data class DropboxFolderSelectUiState(
    override val firstLoading: Boolean = false,
    val hasMore: Boolean = false,
    val mediaCounts: Map<MediaType, Int> = mapOf(),
    val totalMediaCount: Int = 0,
    override val errorMessage: String? = null
) : LazyUiState

@HiltViewModel(assistedFactory = DropboxFolderSelectViewModel.Factory::class)
    class DropboxFolderSelectViewModel @AssistedInject constructor(
    @Assisted private val dropboxAccessToken: String,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val folderPath: String = savedStateHandle["folderPath"]!!
    private val _uiState = MutableStateFlow(DropboxFolderSelectUiState())
    val uiState = _uiState.asStateFlow()
    val foldersPagingFlow = Pager(
        config = PagingConfig(
            pageSize = 100,
            enablePlaceholders = false,
            prefetchDistance = 10
        ),
        pagingSourceFactory = {
            DropboxFolderPagingSource(
                dropboxAccessToken = dropboxAccessToken,
                folderPath = folderPath,
                onFirstLoading = {
                    _uiState.value = DropboxFolderSelectUiState(firstLoading = true)
                },
                onLoaded = { digest, newCursor ->
                    _uiState.value = DropboxFolderSelectUiState(
                        mediaCounts = digest.mediaCounts.mapValues { entry ->
                            entry.value + _uiState.value.mediaCounts.getOrDefault(entry.key, 0)
                        } + _uiState.value.mediaCounts.filterKeys { it !in digest.mediaCounts },
                        totalMediaCount = _uiState.value.totalMediaCount + digest.medias.size,
                        hasMore = newCursor != null,
                    )
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
        ): DropboxFolderSelectViewModel
    }
}

private fun mediaTypeToString(mediaType: MediaType): String =
    when (mediaType) {
        MediaType.VIDEO -> "動画"
        MediaType.IMAGE -> "画像"
        MediaType.AUDIO -> "音声"
        MediaType.PDF -> "PDF"
    }


@Composable
fun DropboxFolderSelectScreen(
    folderPath: String,
    handleFolderMove: (folderPath: String) -> Unit,
    onSelect: (folderPath: String) -> Unit,
) {
    val dropboxAccessToken = LocalDropboxAccessToken.current!!
    val viewModel = hiltViewModel<DropboxFolderSelectViewModel, DropboxFolderSelectViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(dropboxAccessToken)
        }
    )
    val lazyPagingItems = viewModel.foldersPagingFlow.collectAsLazyPagingItems()
    val listState = rememberSaveable(
        saver = LazyListState.Saver
    ) {
        LazyListState()
    }

    val context = LocalContext.current
    BackHandler(enabled = folderPath.isEmpty()) {
        Toast.makeText(context, "Dropboxの親フォルダです。", Toast.LENGTH_SHORT).show()
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxSize().padding(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (folderPath.isEmpty()) "/" else File(folderPath).name,
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
            Button(
                onClick = {
                    onSelect(folderPath)
                },
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text("この場所を選択")
            }
            Button(
                onClick = {},
                modifier = Modifier.padding(end = 16.dp)
            ) {
                Text("リロード")
            }
            MediaInfoRow(viewModel)
        }
        LazyColumn(
            modifier = Modifier.weight(9f).fillMaxSize(),
            state = listState,
        ) {
            items(lazyPagingItems.itemCount) { index ->
                lazyPagingItems[index]?.let { folder ->
                    FolderItem(folder) {
                        handleFolderMove(
                            folder.path
                        )
                    }
                }

            }
            item {
                FirstLoadingIndicator(viewModel.uiState)
            }
            // ローディングインジケーター
            item {
                if (lazyPagingItems.loadState.append is androidx.paging.LoadState.Loading) {
                    IndicatorRow()
                }
            }

            // エラー表示
            item {
                if (lazyPagingItems.loadState.append is androidx.paging.LoadState.Error) {
                    ErrorMessage(viewModel.uiState)
                }
            }
        }
    }
}

@Composable
fun FolderItem(
    folder: DropboxFolder,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = folder.name,
            modifier = Modifier.padding(24.dp),
            fontSize = 24.sp
        )
    }
}

@Composable
fun IndicatorRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun MediaInfoRow(viewModel: DropboxFolderSelectViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    if (!uiState.firstLoading) {
        if (uiState.totalMediaCount == 0) {
            Text("メディアなし")
        } else {
            uiState.mediaCounts.forEach { entry ->
                Text(
                    text = "${mediaTypeToString(entry.key)}:${entry.value}個",
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        if (uiState.hasMore) {
            Text("...")
        }
    }
}

@Composable
fun FirstLoadingIndicator(uiStateFlow: StateFlow<LazyUiState>) {
    val uiState by uiStateFlow.collectAsState()
    if (uiState.firstLoading) {
        IndicatorRow()
    }
}

@Composable
fun ErrorMessage(uiStateFlow: StateFlow<LazyUiState>) {
    val uiState by uiStateFlow.collectAsState()
    Text(
        text = uiState.errorMessage ?: "読み込みエラーが発生しました",
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(16.dp)
    )
}

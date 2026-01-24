package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.ViewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class DropboxFolder(
    val name: String,
    val path: String
)


sealed interface DropboxFolderSelectUiState {
    data object Initial : DropboxFolderSelectUiState
    data class FolderDataWaiting(val dropboxAccessToken: String) : DropboxFolderSelectUiState
    data class FolderShowing(val folders: List<DropboxFolder>): DropboxFolderSelectUiState
    data class Error(val message: String) : DropboxFolderSelectUiState
}

@HiltViewModel
class DropboxFolderSelectViewModel @Inject constructor() : ViewModel() {
    private val _state = MutableStateFlow<DropboxFolderSelectUiState>(DropboxFolderSelectUiState.Initial)
    val state : StateFlow<DropboxFolderSelectUiState> = _state.asStateFlow()
    private val apiService = ApiService(BuildConfig.API_BASE_URL)

    suspend fun getDropboxAccessToken(deviceId: String, accessToken: String, deviceGenerateId: String) {
        val response = apiService.getDropboxAccessToken(deviceId, accessToken, deviceGenerateId)
        _state.value = DropboxFolderSelectUiState.FolderDataWaiting(response.dropboxAccessToken)
    }

    suspend fun getDropboxFolderData(dropboxAccessToken: String) {
        val dropboxClient = DropboxClient(dropboxAccessToken)
        try {
            val folders = dropboxClient.listRootFolders()

            val result = folders
                .filter { folder ->  folder.path_lower != null}
                .map { folder -> DropboxFolder(folder.name, folder.path_lower!!) }
            _state.value = DropboxFolderSelectUiState.FolderShowing(result)
        } finally {
            dropboxClient.close()
        }
    }
}


@Composable
fun DropboxFolderSelectScreen(
    deviceId: String,
    accessToken: String,
    refreshToken: String,
    deviceGenerateId: String,
    viewModel: DropboxFolderSelectViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    when (state) {
        is DropboxFolderSelectUiState.Initial -> {
            LaunchedEffect(Unit) {
                viewModel.getDropboxAccessToken(
                    deviceId = deviceId,
                    accessToken = accessToken,
                    deviceGenerateId = deviceGenerateId
                )
            }
            LoadingScreen()
        }
        is DropboxFolderSelectUiState.FolderDataWaiting -> {
            val s = state as DropboxFolderSelectUiState.FolderDataWaiting
            LaunchedEffect(Unit) {
                viewModel.getDropboxFolderData(
                    dropboxAccessToken = s.dropboxAccessToken
                )
            }
            LoadingScreen()
        }
        is DropboxFolderSelectUiState.FolderShowing -> {
            val s = state as DropboxFolderSelectUiState.FolderShowing
            DropboxFolderListScreen(s.folders) {
                // TODO 個別のフォルダへの移動
            }
        }
        is DropboxFolderSelectUiState.Error -> {
            ErrorScreen((state as DropboxFolderSelectUiState.Error).message)
        }
    }
}

@Composable
fun DropboxFolderListScreen(
    folders: List<DropboxFolder>,
    onFolderClick: (DropboxFolder) -> Unit
) {
    TvLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(32.dp)
    ) {
        items(folders.size) { index ->
            FolderItem(folders[index], onFolderClick)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun FolderItem(
    folder: DropboxFolder,
    onClick: (DropboxFolder) -> Unit
) {
    Card(
        onClick = { onClick(folder) },
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
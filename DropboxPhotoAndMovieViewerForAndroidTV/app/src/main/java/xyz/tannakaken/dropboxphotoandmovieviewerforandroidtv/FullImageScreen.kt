package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FullImageViewModel @Inject constructor() : ViewModel() {

    private val _imageUrl = MutableStateFlow<String?>(null)
    val imageUrl: StateFlow<String?> = _imageUrl.asStateFlow()

    fun loadImage(path: String, dropboxClient: DropboxClient) {
        viewModelScope.launch {
            _imageUrl.value = dropboxClient.getTemporaryLink(path)
        }
    }
}

@Composable
fun FullImageScreen(
    viewModel: FullImageViewModel = hiltViewModel(),
    path: String,
) {
    val dropboxAccessToken = LocalDropboxAccessToken.current!!
    val dropboxClient = DropboxClient(dropboxAccessToken, DropboxPhotoAndMovieViewerApplication.client)
    val imageUrl by viewModel.imageUrl.collectAsState()

    if (imageUrl == null) {
        LaunchedEffect(path) {
            viewModel.loadImage(path, dropboxClient)
        }
        LoadingScreen()
        return
    }


    AsyncImage(
        modifier = Modifier.fillMaxSize(),
        model = imageUrl,
        contentDescription = ""
    )
}

package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = DropboxAssetViewModel.Factory::class)
class DropboxAssetViewModel @AssistedInject constructor(
    secureAuthStorage: SecureAuthStorage,
    @Assisted private val dropboxAccessToken: String,
) : ViewModel() {
    private val _assetUrl = MutableStateFlow<String?>(null)
    val assetUrl: StateFlow<String?> = _assetUrl.asStateFlow()
    val withRetry = createWithRetry<String>(dropboxAccessToken, secureAuthStorage)
    private val _forceLoggingOut = MutableStateFlow(false)
    val forceLoggingOut = _forceLoggingOut.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun loadAsset(path: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            try {
                _assetUrl.value = withRetry { dropboxClient ->
                    dropboxClient.getTemporaryLink(path)
                }
            } catch (exception: ForceLoggingOutException) {
                _forceLoggingOut.value = true
            } catch (exception: ServiceErrorException) {
                _errorMessage.value = exception.message.orEmpty()
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            dropboxAccessToken: String,
        ): DropboxAssetViewModel
    }
}


@Composable
fun DropboxAssetTemplate(
    path: String,
    loggingOut: () -> Unit,
    content: @Composable (String) -> Unit
) {
    val dropboxAccessToken = LocalDropboxAccessToken.current!!
    val viewModel = hiltViewModel<DropboxAssetViewModel, DropboxAssetViewModel.Factory>(
        creationCallback = { factory ->
            factory.create(dropboxAccessToken)
        }
    )
    val assetUrl by viewModel.assetUrl.collectAsState()
    val forceLoggingOut by viewModel.forceLoggingOut.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    if (forceLoggingOut) {
        LaunchedEffect(Unit) {
            loggingOut()
        }
        LoadingScreen()
        return
    }

    if (errorMessage != null) {
        ErrorScreen(
            errorMessage.orEmpty(),
            onReload = {
                viewModel.loadAsset(path)
            }
        )
        return
    }

    if (assetUrl == null) {
        LaunchedEffect(path) {
            viewModel.loadAsset(path)
        }
        LoadingScreen()
        return
    }

    content(assetUrl!!)
}
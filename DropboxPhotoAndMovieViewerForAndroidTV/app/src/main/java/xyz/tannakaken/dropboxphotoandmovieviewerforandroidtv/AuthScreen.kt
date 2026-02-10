package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject


sealed interface AuthUiState {
    data object Initial : AuthUiState
    data class Waiting(
        val qrUrl: String,
        val remainingMinutes: Int
    ) : AuthUiState
    data class Authorized(
        val deviceId: String,
        val accessToken: String,
        val refreshToken: String,
        val deviceGenerateId: String,
    ) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    val uiState : StateFlow<AuthUiState> = _uiState.asStateFlow()
    private val apiService = ApiService(BuildConfig.API_BASE_URL, DropboxPhotoAndMovieViewerApplication.client)
    private var deviceGenerateId: String? = null

    private suspend fun startOauthFlow(): FlowResponse? {
        val uuidString = UUID.randomUUID().toString()
        deviceGenerateId = uuidString
        return try {
            apiService.startOAuthFlow(uuidString)
        } catch (error: ClientRequestException) {
            _uiState.value = AuthUiState.Error(clientErrorMessage)
            null
        } catch (error: ServerResponseException) {
            _uiState.value = AuthUiState.Error(serverErrorMessage)
            null
        } catch (error: IOException) {
            Log.d("AuthScreen", error.message.orEmpty())
            _uiState.value = AuthUiState.Error(networkErrorMessage)
            null
        } catch (error: Exception) {
            _uiState.value = AuthUiState.Error(unknownErrorMessage)
            null
        }
    }

    fun startAuth() {
        viewModelScope.launch {
            val response = startOauthFlow()
            response?.let {
                val qrUrl = "${BuildConfig.API_BASE_URL}?state=${response.state}"
                Log.d(TAG, qrUrl)
                _uiState.value = AuthUiState.Waiting(
                    qrUrl = qrUrl,
                    remainingMinutes = 10
                )
                val repository = PollingRepository(apiService)
                repository.pollWithAdaptiveInterval(
                    state = response.state,
                    deviceGenerateId = deviceGenerateId!!,
                    tmpToken = response.tmpToken)
                    .collect {result ->
                        _uiState.value = when (result) {
                            is PollingResult.InProgress -> AuthUiState.Waiting(
                                qrUrl = qrUrl,
                                remainingMinutes = 10 - (result.elapsedSeconds / 60)
                            )
                            is PollingResult.Success -> AuthUiState.Authorized(
                                deviceId = result.deviceId,
                                accessToken = result.accessToken,
                                refreshToken =  result.refreshToken,
                                deviceGenerateId = deviceGenerateId!!,
                            )
                            is PollingResult.Timeout -> AuthUiState.Initial
                            is PollingResult.Error -> AuthUiState.Error(
                                message = result.message
                            )
                        }
                    }
            }
        }
    }

    companion object {
        const val TAG = "AuthViewModel"
    }
}


@Composable
fun AuthScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onAuthorized: suspend (deviceId: String, accessToken: String, refreshToken: String, deviceGenerateId: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is AuthUiState.Initial -> {
            LaunchedEffect(Unit) {
                viewModel.startAuth()
            }
            LoadingScreen()
        }
        is AuthUiState.Waiting -> {
            AuthWaitingScreen(
                qrUrl = state.qrUrl,
                remainingMinutes = state.remainingMinutes,
                onRegenerate = { viewModel.startAuth() }
            )
        }
        is AuthUiState.Authorized -> {
            LaunchedEffect(Unit) {
                onAuthorized(state.deviceId, state.accessToken, state.refreshToken, state.deviceGenerateId)
            }
            LoadingScreen()
        }
        is AuthUiState.Error -> {
            ErrorScreen(state.message)
        }
    }
}

@Composable
fun AuthWaitingScreen(
    qrUrl: String,
    remainingMinutes: Int,
    onRegenerate: () -> Unit) {
    val bitmap = remember(qrUrl) {
        generateQrCode(qrUrl, 512)
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "スマートフォンでQRコードを読み取り、Dropboxにログインしてください。",
            fontSize = 24.sp,
        )
        Spacer(Modifier.height(32.dp))
        Box(
            modifier = Modifier
                .size(200.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "QR code: $qrUrl"
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "有効期限：あと約${remainingMinutes}分",
            fontSize = 20.sp
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRegenerate) {
            Text("QRコード再生成")
        }
    }
}
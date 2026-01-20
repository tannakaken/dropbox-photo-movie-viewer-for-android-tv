

package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Button
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv.ui.theme.DropboxPhotoAndMovieViewerForAndroidTVTheme
import xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv.ui.theme.Purple80
import xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv.ui.theme.PurpleGrey40
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

fun generateQrCode(
    text: String,
    size: Int = 512
): Bitmap {
    val bitMatrix: BitMatrix = MultiFormatWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size
    )

    val bitmap = createBitmap(size, size)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap[x, y] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return bitmap
}

sealed interface AuthUiState {
    data object Initial : AuthUiState
    data class Waiting(
        val qrUrl: String,
        val remainingSeconds: Int
    ) : AuthUiState
    data object Authorized : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel : ViewModel() {
    // 内部で変更可能な状態を定義
    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Initial)
    // 外部には読み取り専用で公開
    val state : StateFlow<AuthUiState> = _state.asStateFlow()

    private suspend fun mockApiCall(): Result<String> {
        delay(1_000L) // 1秒待つ（スレッドはブロックしない）
        return Result.success("mock response")
    }

    fun startAuth() {
        viewModelScope.launch {


            mockApiCall()
            _state.value = AuthUiState.Waiting(
                qrUrl = "https://google.com",
                remainingSeconds = 600
            )
            // TODO ポーリング開始
        }
    }

    fun onAuthorized() {
        _state.value = AuthUiState.Authorized
    }
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DropboxPhotoAndMovieViewerForAndroidTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    AuthScreen(AuthViewModel()) {
                        // TODO 認可フロー完了後は画面遷移
                    }
                }
            }
        }
    }
}



@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthorized: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    when (state) {
        is AuthUiState.Initial -> {
            LaunchedEffect(Unit) {
                viewModel.startAuth()
            }
            LoadingScreen()
        }
        is AuthUiState.Waiting -> {
            val s = state as AuthUiState.Waiting
            AuthWaitingScreen(
                qrUrl = s.qrUrl,
                remainingSeconds = s.remainingSeconds,
                onRegenerate = { viewModel.startAuth() }
            )
        }
        is AuthUiState.Authorized -> {
            LaunchedEffect(Unit) {
                onAuthorized()
            }
        }
        is AuthUiState.Error -> {
            ErrorScreen((state as AuthUiState.Error).message)
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = Purple80,
            trackColor = PurpleGrey40,
            strokeWidth = 10.dp,
        )
    }

}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AuthWaitingScreen(
    qrUrl: String,
    remainingSeconds: Int,
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
        // TODO QRコードを生成
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
            "有効期限：あと約${remainingSeconds / 60}分",
            fontSize = 20.sp
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRegenerate) {
            Text("QRコード再生成")
        }
    }

}

@Composable
fun ErrorScreen(message: String, modifier: Modifier = Modifier) {
    Text(
        text = "Now $message!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DropboxPhotoAndMovieViewerForAndroidTVTheme {
        LoadingScreen()
    }
}
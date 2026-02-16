

package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.Surface
import xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv.ui.theme.DropboxPhotoAndMovieViewerForAndroidTVTheme
import androidx.navigation.compose.NavHost
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

val LocalDropboxAccessToken = compositionLocalOf<String?> { null }

/**
 * ページの種類とページ遷移に必要なデータを定義
 */
@Serializable
sealed interface AppRoute {
    /**
     * Dropboxへのログインページ
     */
    @Serializable
    data object AuthRoute : AppRoute

    /**
     * Dropboxのフォルダを選択するページ
     */
    @Serializable
    data class DropboxFoldersRoute(
        val folderPath: String,
    ) : AppRoute

    /**
     * Dropboxの特定のフォルダの画像・動画一覧を表示するページ。
     */
    @Serializable
    data class DropboxImagesAndMoviesRoute(
        val folderPath: String,
    )

    /**
     * 画像を表示するページ
     */
    @Serializable
    data class ImageRoute(
        val path: String,
    )

    /**
     * 動画を表示するページ
     */
    @Serializable
    data class VideoRoute(
        val path: String,
    )

    /**
     * 音声を再生するページ
     */
    @Serializable
    data class AudioRoute(
        val path: String,
    )

    /**
     * PDFを表示するページ
     */
    @Serializable
    data class PdfRoute(
        val path: String,
    )
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val secureAuthStorage: SecureAuthStorage
) : ViewModel() {
    private val apiService = ApiService(BuildConfig.API_BASE_URL, DropboxPhotoAndMovieViewerApplication.client)
    fun loggingOut(onLoggingOut: () -> Unit) {
        viewModelScope.launch {
            val auth = secureAuthStorage.getAuth().first()
            if (auth != null) {
                apiService.loggingOut(
                    auth.deviceId,
                    auth.accessToken,
                    auth.deviceGenerateId
                )
                onLoggingOut()
            }
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel = hiltViewModel(),
    handleDropboxAccessToken: (dropboxAccessToken: String) -> Unit,
) {
    val navController = rememberNavController()
    val loggingOut = remember {
        {
            viewModel.loggingOut {
                navController.navigate(AppRoute.AuthRoute)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.AuthRoute
    ) {
        composable<AppRoute.AuthRoute> {
            AuthScreen { dropboxAccessToken ->
                handleDropboxAccessToken(dropboxAccessToken)
                // Dropboxのフォルダにおいて、ルートフォルダは"/"ではなく""でアクセスするのが正しい。
                navController.navigate(AppRoute.DropboxFoldersRoute(""))
            }
        }
        composable<AppRoute.DropboxFoldersRoute> {
            val route = it.toRoute<AppRoute.DropboxFoldersRoute>()
            DropboxFolderSelectScreen(
                route.folderPath,
                handleFolderMove = { folderPath ->
                    navController.navigate(AppRoute.DropboxFoldersRoute(folderPath))
                },
                loggingOut = loggingOut,
                onSelect = { folderPath ->
                    navController.navigate(AppRoute.DropboxImagesAndMoviesRoute(
                        folderPath = folderPath,
                    ))
                }
            )
        }
        composable<AppRoute.DropboxImagesAndMoviesRoute> {
            val route = it.toRoute<AppRoute.DropboxImagesAndMoviesRoute>()
            MediaGalleryScreen(
                folderPath = route.folderPath,
                loggingOut = loggingOut,
            ) { mediaItem ->
                when (mediaItem.type) {
                    MediaType.VIDEO -> {
                        navController.navigate(
                            AppRoute.VideoRoute(
                                path = mediaItem.path,
                            )
                        )
                    }
                    MediaType.AUDIO -> {
                        navController.navigate(
                            AppRoute.AudioRoute(
                                path = mediaItem.path,
                            )
                        )
                    }
                    MediaType.IMAGE -> {
                        navController.navigate(
                            AppRoute.ImageRoute(
                                path = mediaItem.path,
                            )
                        )
                    }
                    MediaType.PDF -> {
                        navController.navigate(
                            AppRoute.PdfRoute(
                                path = mediaItem.path,
                            )
                        )
                    }
                }
            }
        }
        composable<AppRoute.ImageRoute> {
            val route = it.toRoute<AppRoute.ImageRoute>()
            FullImageScreen(
                path = route.path,
                loggingOut = loggingOut,
            )
        }
        composable<AppRoute.VideoRoute> {
            val route = it.toRoute<AppRoute.VideoRoute>()
            VideoPlayerScreen(
                path = route.path,
                loggingOut = loggingOut,
            )
        }
        composable<AppRoute.AudioRoute> {
            val route = it.toRoute<AppRoute.AudioRoute>()
            AudioPlayerScreen(
                path = route.path,
                loggingOut = loggingOut,
            )
        }
        composable<AppRoute.PdfRoute> {
            val route = it.toRoute<AppRoute.PdfRoute>()
            PDFScreen(
                path = route.path,
                loggingOut = loggingOut,
            )
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var dropboxAccessToken by remember { mutableStateOf<String?>(null)}
            CompositionLocalProvider(LocalDropboxAccessToken provides dropboxAccessToken) {
                DropboxPhotoAndMovieViewerForAndroidTVTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape
                    ) {
                        AppNavigation { newDropboxAccessToken ->
                            dropboxAccessToken = newDropboxAccessToken
                        }
                    }
                }
            }

        }
    }
}

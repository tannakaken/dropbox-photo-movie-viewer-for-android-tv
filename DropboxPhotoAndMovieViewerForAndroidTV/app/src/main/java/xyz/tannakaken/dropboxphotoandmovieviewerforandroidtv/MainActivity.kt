

package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv.ui.theme.DropboxPhotoAndMovieViewerForAndroidTVTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable


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
    data class DropboxFolderRoute(
        val deviceId: String,
        val accessToken: String,
        val refreshToken: String,
        val deviceGenerateId: String
    ) : AppRoute
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoute.AuthRoute
    ) {
        composable<AppRoute.AuthRoute> {
            AuthScreen { deviceId, accessToken, refreshToken, deviceGenerateId ->
                navController.navigate(AppRoute.DropboxFolderRoute(
                    deviceId = deviceId,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    deviceGenerateId = deviceGenerateId)
                )
            }
        }
        composable<AppRoute.DropboxFolderRoute> {
            val route = it.toRoute<AppRoute.DropboxFolderRoute>()
            DropboxFolderSelectScreen(
                deviceId = route.deviceId,
                accessToken = route.accessToken,
                refreshToken = route.refreshToken,
                deviceGenerateId = route.deviceGenerateId
            )
        }
    }
}

@AndroidEntryPoint
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
                    AppNavigation()
                }
            }
        }
    }
}

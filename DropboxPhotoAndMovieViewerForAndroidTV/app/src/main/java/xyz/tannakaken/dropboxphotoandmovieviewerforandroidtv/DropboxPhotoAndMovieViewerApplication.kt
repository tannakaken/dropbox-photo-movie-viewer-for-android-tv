package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.util.DebugLogger
import coil3.util.Logger
import dagger.hilt.android.HiltAndroidApp
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@HiltAndroidApp
class DropboxPhotoAndMovieViewerApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .logger(DebugLogger(minLevel = Logger.Level.Verbose))
            .build()
    }

    companion object {
        val client: HttpClient = HttpClient(Android) {
            install(ContentNegotiation) {
                install(Logging) {
                    logger = io.ktor.client.plugins.logging.Logger.DEFAULT
                    level = LogLevel.ALL
                }
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }

    }
}
package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage

@Composable
fun FullImageScreen(
    path: String,
    loggingOut: () -> Unit,
) {
    DropboxAssetTemplate(path, loggingOut) { imageUrl ->
        AsyncImage(
            modifier = Modifier.fillMaxSize(),
            model = imageUrl,
            contentDescription = ""
        )
    }
}

package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv.ui.theme.Purple80
import xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv.ui.theme.PurpleGrey40

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

@Composable
fun ErrorScreen(message: String, modifier: Modifier = Modifier) {
    Text(
        text = "Now $message!",
        modifier = modifier
    )
}
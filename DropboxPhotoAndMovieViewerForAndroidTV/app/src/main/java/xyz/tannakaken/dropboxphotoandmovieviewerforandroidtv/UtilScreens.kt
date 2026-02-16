package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
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
fun ErrorScreen(
    message: String,
    modifier: Modifier = Modifier,
    onReload: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(16.dp)
        )
        if (onReload != null) {
            IconButton(
                onClick = onReload,
                modifier = Modifier.size(36.dp).padding(start = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "リロード",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                )
            }
        }
    }
}
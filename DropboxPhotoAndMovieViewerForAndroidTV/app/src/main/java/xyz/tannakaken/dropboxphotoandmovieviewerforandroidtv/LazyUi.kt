package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.StateFlow

interface LazyUiState {
    val firstLoading: Boolean
    val errorMessage: String?
}

@Composable
fun FirstLoadingIndicator(uiStateFlow: StateFlow<LazyUiState>) {
    val uiState by uiStateFlow.collectAsState()
    if (uiState.firstLoading) {
        IndicatorRow()
    }
}

@Composable
fun LazyErrorMessage(
    uiStateFlow: StateFlow<LazyUiState>,
    onReload: (() -> Unit)? = null
) {
    val uiState by uiStateFlow.collectAsState()
    Column(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        Text(
            text = uiState.errorMessage ?: "読み込みエラーが発生しました",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp)
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
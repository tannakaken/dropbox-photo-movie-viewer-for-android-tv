package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.ui.compose.indicators.TimeText
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlin.math.roundToLong

/**
 * ビデオコントロールパネル
 * 少し戻る、再生/停止、少し進む、シークバー、時間表示を含む
 */
@Composable
fun VideoControls(
    player: Player,
    isPlaying: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        // ボタン群（少し戻る、再生/停止、少し進む）
        Row(
            modifier = Modifier.weight(11f).fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 10秒戻るボタン
            IconButton(
                onClick = {
                    player.seekBack()
                },
                modifier = Modifier.size(90.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.media3_icon_skip_back_10),
                    contentDescription = "10秒戻る",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp),
                )
            }

            Spacer(modifier = Modifier.width(50.dp))

            // 再生/停止ボタン
            IconButton(
                enabled = !isLoading,
                onClick = {
                    if (isLoading) {
                        return@IconButton
                    }
                    if (isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                modifier = Modifier.size(100.dp).focusRequester(focusRequester),
            ) {
                if (isLoading) {
                    return@IconButton
                }
                if (isPlaying) {
                    Icon(
                        painter = painterResource(R.drawable.media3_icon_pause),
                        contentDescription = "一時停止",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.media3_icon_play),
                        contentDescription = "再生",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(50.dp))

            // 10秒進むボタン
            IconButton(
                onClick = {
                    player.seekForward()
                },
                modifier = Modifier.size(90.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.media3_icon_skip_forward_10),
                    contentDescription = "10秒進む",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp),
                )
            }
        }
        Row(modifier = Modifier.weight(1f).fillMaxSize()) {
            VideoSeekBar(player = player) { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            focusRequester.requestFocus()
                            true
                        }

                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onClose != null) {
                                onClose()
                            }
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            }
        }
    }
}

/**
 * カスタムシークバーコンポーネント
 *
 * 注: Media3 1.9.0時点では、プリビルトのシークバーコンポーネントは
 * まだ提供されていません（開発中）。そのため、ProgressStateと
 * Material3のSliderを組み合わせて実装しています。
 *
 * 参考: https://android-developers.googleblog.com/2025/12/media3-190-whats-new.html
 * "We are also still working on even more Compose components,
 *  like a prebuilt seek bar"
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSeekBar(
    player: Player,
    modifier: Modifier = Modifier,
    onKeyEvent: (KeyEvent) -> Boolean
) {
    // プレイヤーの進捗状態を取得（100msごとに更新）
    val progressState = rememberProgressStateWithTickInterval(
        player = player,
        tickIntervalMs = 100L
    )

    // 現在位置と全体の長さ
    val currentPosition = progressState.currentPositionMs
    val duration = progressState.durationMs
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().onFocusChanged { focusState ->
            isFocused = focusState.isFocused
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // シークバー（Material3 Slider使用）
        Slider(
            value = if (duration > 0) {
                (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            },
            onValueChange = { newValue ->
                // シーク操作
                val seekPosition = (newValue * duration).roundToLong()
                player.seekTo(seekPosition)
            },
            modifier = Modifier.weight(6f).fillMaxWidth().onPreviewKeyEvent(onKeyEvent),
            colors = SliderDefaults.colors(
                thumbColor = if (isFocused) Color.Unspecified else MaterialTheme.colorScheme.primary,
                activeTrackColor = if (isFocused) Color.Unspecified else MaterialTheme.colorScheme.primary,
                inactiveTrackColor = if (isFocused) Color.Unspecified.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
            ),
            enabled = duration > 0
        )


        // 現在の再生時間
        TimeText(player) {
            val currentPositionText = Util.getStringForTime(this.currentPositionMs)
            val durationText = Util.getStringForTime(this.durationMs)
            Text(
                text ="$currentPositionText/$durationText",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Gray.copy(alpha = 0.5f)),
            )
        }

    }
}
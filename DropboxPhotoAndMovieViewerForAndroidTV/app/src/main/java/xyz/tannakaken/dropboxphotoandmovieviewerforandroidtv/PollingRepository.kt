package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.util.Log
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*

// 結果を表す sealed class
sealed class PollingResult {
    data class Success(val deviceId: String, val accessToken: String, val refreshToken: String) : PollingResult()
    data class InProgress(val elapsedSeconds: Int) : PollingResult()
    data class Error(val message: String) : PollingResult()
    data object Timeout : PollingResult()
}

class PollingRepository(private val apiService: ApiService) {
    /**
     * 段階的な間隔でポーリングを実行
     * @param state ポーリング対象のリクエストID
     * @return 処理結果のFlow
     */
    fun pollWithAdaptiveInterval(state: String, deviceGenerateId: String, tmpToken: String): Flow<PollingResult> = flow {
        Log.d("PollingRepository", state)
        Log.d("PollingRepository", deviceGenerateId)
        Log.d("PollingRepository", tmpToken)
        val startTime = System.currentTimeMillis()
        val timeoutMilliseconds = 10 * 60 * 1000L // 10分
        while (true) {
            val elapsedMilliseconds = System.currentTimeMillis() - startTime
            if (elapsedMilliseconds >= timeoutMilliseconds) {
                emit(PollingResult.Timeout)
                break
            }

            try {
                val response = apiService.checkStatus(
                    state = state,
                    deviceGenerateId = deviceGenerateId,
                    tmpToken = tmpToken)
                when {
                    response.completed -> {
                        emit(PollingResult.Success(response.deviceId!!, response.accessToken!!, response.refreshToken!!))
                        break
                    }
                    else -> {
                        emit(PollingResult.InProgress((elapsedMilliseconds / 1000).toInt()))
                    }
                }
            } catch (error: ClientRequestException) {
                emit(PollingResult.Error(clientErrorMessage))
            } catch (error: ServerResponseException) {
                emit(PollingResult.Error(serverErrorMessage))
            } catch (error: IOException) {
                Log.d("PollingRepository", error.message.orEmpty())
                emit(PollingResult.Error(networkErrorMessage))
            } catch (error: Exception) {
                Log.d("PollingRepository", error.message.orEmpty())
                emit(PollingResult.Error(unknownErrorMessage))
            }

            // 経過時間に応じた待機時間を決定
            val delayMillis = when {
                elapsedMilliseconds < 60_000 -> 3_000L  // 0-1分: 3秒
                elapsedMilliseconds < 300_000 -> 5_000L // 1-5分: 5秒
                else -> 10_000L                    // 5分以降: 10秒
            }

            delay(delayMillis)
        }
    }
}
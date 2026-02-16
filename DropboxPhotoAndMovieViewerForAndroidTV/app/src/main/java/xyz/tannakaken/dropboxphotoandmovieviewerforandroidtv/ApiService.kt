package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.Serializable

@Serializable
data class FlowResponse(val state: String, val tmpToken: String)

@Serializable
data class FlowRequest(val deviceGenerateId: String)

@Serializable
data class FlowCheckResponse(
    val completed: Boolean,
    val deviceId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

@Serializable
data class DropboxAccessTokenResponse(
    val dropboxAccessToken: String,
)

@Serializable
data class RefreshTokenRequest(
    val deviceId: String,
    val refreshToken: String,
)

@Serializable
data class RefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class OkResponse(
    val ok: Boolean
)

/**
 * 500系のエラー、ネットワークエラー、その他のエラー
 */
class ServiceErrorException(message: String): Exception(message)

/**
 * 401によるトークンのリフレッシュのエラー、もしくはその他の400系のエラー
 */
class ForceLoggingOutException(message: String): Exception(message)

private suspend inline fun<reified T> withProcessHttpError(f: () -> HttpResponse): T =
    try {
        val response = f()
        if (response.status.value / 100 == 4) {
            Log.d("[${ApiService.TAG}: ${Throwable().stackTrace[0].methodName}]", "${response.status.value}: ${response.status.description}")
            throw ForceLoggingOutException(clientErrorMessage)
        } else if (response.status.value / 100 == 5) {
            Log.d("[${ApiService.TAG}: ${Throwable().stackTrace[0].methodName}]", "${response.status.value}: ${response.status.description}")
            throw ServiceErrorException(serverErrorMessage)
        } else if (response.status.value != 200) {
            Log.d("[${ApiService.TAG}: ${Throwable().stackTrace[0].methodName}]", "${response.status.value}: ${response.status.description}")
            throw ServiceErrorException(unknownErrorMessage)
        }
        response.body()
    } catch (error: IOException) {
        Log.d("[${ApiService.TAG}: ${Throwable().stackTrace[0].methodName}]", error.message.orEmpty())
        throw ServiceErrorException(networkErrorMessage)
    } catch (error: Exception) {
        if (error is ForceLoggingOutException || error is ServiceErrorException || error is DropboxTokenExpireException) {
            throw error
        }
        Log.d("[${ApiService.TAG}: ${Throwable().stackTrace[0].methodName}]", error.message.orEmpty())
        throw ServiceErrorException(unknownErrorMessage)
    }



class ApiService(private val baseUrl: String, private val client: HttpClient) {
    private val flowApiUrl = "$baseUrl/api/auth/flows"
    suspend fun startOAuthFlow(deviceGenerateId: String): FlowResponse {
        val flowRequest = FlowRequest(deviceGenerateId)
        return withProcessHttpError {
            client.post(flowApiUrl) {
                contentType(ContentType.Application.Json)
                setBody(flowRequest)
            }
        }
    }

    suspend fun checkStatus(state: String, deviceGenerateId: String, tmpToken: String): FlowCheckResponse {
        val checkStatusUrl = "$baseUrl/api/auth/flows/$state"
        return withProcessHttpError {
            client.get(checkStatusUrl) {
                headers {
                    append(DEVICE_GENERATE_ID_KEY, deviceGenerateId)
                    append(HttpHeaders.Authorization, "Bearer $tmpToken")
                }
            }
        }
    }

    /**
     * 細かなエラー処理ができていない。
     */
    suspend fun getDropboxAccessToken(
        deviceId: String,
        accessToken: String,
        refreshToken: String,
        deviceGenerateId: String,
        onRefresh: suspend (newAccessToken: String, newRefreshToken: String) -> Unit
    ): DropboxAccessTokenResponse {
        val getTokenUrl = "$baseUrl/api/devices/$deviceId"

       return withProcessHttpError {
            val response  = client.get(getTokenUrl) {
                headers {
                    append(DEVICE_GENERATE_ID_KEY, deviceGenerateId)
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }
            if (response.status.value != 401) {
                response
            } else {
                val refreshTokenUrl = "$baseUrl/api/auth/tokens"
                val refreshTokenResponse = client.post(refreshTokenUrl) {
                    headers {
                        append(DEVICE_GENERATE_ID_KEY, deviceGenerateId)
                    }
                    contentType(ContentType.Application.Json)
                    setBody(RefreshTokenRequest(
                        deviceId = deviceId,
                        refreshToken = refreshToken
                    ))
                }
                if (response.status.value / 100 == 5) {
                    throw ServiceErrorException("Service Temporary Unavailable")
                }
                if (response.status.value != 200) {
                    throw ForceLoggingOutException("Can not refresh token")
                }

                val newTokens: RefreshTokenResponse = refreshTokenResponse.body()
                onRefresh(newTokens.accessToken, newTokens.refreshToken)
                val retryResponse  = client.get(getTokenUrl) {
                    headers {
                        append(DEVICE_GENERATE_ID_KEY, deviceGenerateId)
                        append(HttpHeaders.Authorization, "Bearer ${newTokens.accessToken}")
                    }
                }
                retryResponse
            }
        }

    }

    suspend fun loggingOut(
        deviceId: String,
        accessToken: String,
        deviceGenerateId: String,
    ) {
        val loggingOutUrl = "$baseUrl/api/devices/$deviceId"
        withProcessHttpError<OkResponse> {
            client.delete(loggingOutUrl) {
                headers {
                    append(DEVICE_GENERATE_ID_KEY, deviceGenerateId)
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }
        }
    }

    companion object {
        const val TAG = "ApiService"
        const val DEVICE_GENERATE_ID_KEY = "X-Tannakaken-Android-TV-Dropbox-Device-Generate-ID"
    }
}
package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.logging.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

class ApiService(private val baseUrl: String) {
    private val flowApiUrl = "$baseUrl/api/auth/flows"
    suspend fun startOAuthFlow(deviceGenerateId: String): FlowResponse {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.ALL
                }
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }

        val flowRequest = FlowRequest(deviceGenerateId)
        return client.use { clientLocal ->
            val response = clientLocal.post(flowApiUrl) {
                contentType(ContentType.Application.Json)
                setBody(flowRequest)
            }
            Log.d("ApiService", response.status.toString())
            response.body()
        }
    }

    suspend fun checkStatus(state: String, deviceGenerateId: String, tmpToken: String): FlowCheckResponse {
        val checkStatusUrl = "$baseUrl/api/auth/flows/$state"
        Log.d("ApiService", checkStatusUrl)
        val client = HttpClient(CIO) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        return client.use { clientLocal ->
            val response = clientLocal.get(checkStatusUrl) {
                headers {
                    append(deviceGenerateIdHeaderKey, deviceGenerateId)
                    append(authorizationHeaderKey, "Bearer $tmpToken")
                }
            }
            if (response.status.value / 100 == 4) {
                throw ClientRequestException(response, "${response.status.value}: ${response.status.description}")
            }
            if (response.status.value / 100 == 5) {
                throw ServerResponseException(response, "${response.status.value}: ${response.status.description}")
            }
            if (response.status.value != 200) {
                throw Exception("${response.status.value}: ${response.status.description}")
            }
            response.body()
        }
    }

    suspend fun getDropboxAccessToken(deviceId: String, accessToken: String, deviceGenerateId: String): DropboxAccessTokenResponse {
        val getTokenUrl = "$baseUrl/api/devices/$deviceId"
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.ALL
                }
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
        }
        return client.use { clientLocal ->
            clientLocal.get(getTokenUrl) {
                headers {
                    append(deviceGenerateIdHeaderKey, deviceGenerateId)
                    append(authorizationHeaderKey, "Bearer $accessToken")
                }
            }.body()
        }
    }
}
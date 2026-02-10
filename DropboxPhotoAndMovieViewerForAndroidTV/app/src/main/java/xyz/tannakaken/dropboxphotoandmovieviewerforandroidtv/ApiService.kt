package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
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

class ApiService(private val baseUrl: String, private val client: HttpClient) {
    private val flowApiUrl = "$baseUrl/api/auth/flows"
    suspend fun startOAuthFlow(deviceGenerateId: String): FlowResponse {

        val flowRequest = FlowRequest(deviceGenerateId)
        val response = client.post(flowApiUrl) {
            contentType(ContentType.Application.Json)
            setBody(flowRequest)
        }
        return response.body()
    }

    suspend fun checkStatus(state: String, deviceGenerateId: String, tmpToken: String): FlowCheckResponse {
        val checkStatusUrl = "$baseUrl/api/auth/flows/$state"
        val response = client.get(checkStatusUrl) {
            headers {
                append(DEVICE_GENERATE_ID_KEY, deviceGenerateId)
                append(HttpHeaders.Authorization, "Bearer $tmpToken")
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
        return response.body()
    }

    suspend fun getDropboxAccessToken(deviceId: String, accessToken: String, deviceGenerateId: String): DropboxAccessTokenResponse {
        val getTokenUrl = "$baseUrl/api/devices/$deviceId"

        val response  = client.get(getTokenUrl) {
            headers {
                append(DEVICE_GENERATE_ID_KEY, deviceGenerateId)
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }
        return response.body()
    }

    companion object {
        const val DEVICE_GENERATE_ID_KEY = "X-Tannakaken-Android-TV-Dropbox-Device-Generate-ID"
    }
}
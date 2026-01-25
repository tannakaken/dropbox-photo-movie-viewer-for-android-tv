package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ListFolderRequest(
    val path: String = "",
    val recursive: Boolean = false,
    val include_mounted_folders: Boolean = true
)

@Serializable
data class Metadata(
    val tag: String? = null,
    val name: String,
    val path_lower: String? = null,
    val path_display: String? = null,
    val id: String? = null
)

@Serializable
data class ListFolderResponse(
    val entries: List<Metadata>,
    val cursor: String,
    val has_more: Boolean
)

class DropboxClient(private val accessToken: String) {
    private val client = HttpClient(CIO) {
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }
    }
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listRootFolders(): List<Metadata> {
        val response: HttpResponse = client.post("https://api.dropboxapi.com/2/files/list_folder") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody("""{"path": "", "recursive": false}""")
        }

        val responseBody = response.bodyAsText()
        val listFolderResponse = json.decodeFromString<ListFolderResponse>(responseBody)

        // フォルダのみをフィルタリング (.tag == "folder")
        return listFolderResponse.entries.filter { it.tag == "folder" }
    }

    fun close() {
        client.close()
    }
}

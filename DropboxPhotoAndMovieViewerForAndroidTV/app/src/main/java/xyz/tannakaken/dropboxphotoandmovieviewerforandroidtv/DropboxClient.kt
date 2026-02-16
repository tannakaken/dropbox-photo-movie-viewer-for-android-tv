package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.lang.ref.WeakReference

@Serializable
data class VideoMetadata(
    val duration: Int? = null
)

@Serializable
data class MediaMetadata(
    val video: VideoMetadata? = null
)

@Serializable
data class MediaInfo(
    val metadata: MediaMetadata
)

@Serializable
data class Metadata(
    @SerialName(".tag")
    val tag: String,
    val name: String,
    @SerialName("path_lower")
    val pathLower: String? = null,
    @SerialName("path_display")
    val pathDisplay: String? = null,
    val id: String? = null,
    @SerialName("media_info")
    val mediaInfo: MediaInfo? = null
)

@Serializable
data class ListFolderResponse(
    val entries: List<Metadata>,
    val cursor: String? = null,
    @SerialName("has_more")
    val hasMore: Boolean
)

@Serializable
data class TemporaryLinkResponse(
    val link: String
)

enum class MediaType {
    IMAGE, VIDEO, AUDIO, PDF
}

data class MutableFolderDigest(
    val folders: MutableList<Metadata> = mutableListOf(),
    val medias: MutableList<Metadata> = mutableListOf(),
    val mediaCounts: MutableMap<MediaType, Int> = mutableMapOf()
) {
    fun toImmutable() =
        FolderDigest(
            folders,
            medias,
            mediaCounts,
        )

}

data class FolderDigest(
    val folders: List<Metadata>,
    val medias: List<Metadata>,
    val mediaCounts: Map<MediaType, Int>,
)

class WeakCache<K, V> {
    private val cache = mutableMapOf<K, WeakReference<V>>()

    fun put(key: K, value: V) {
        cache[key] = WeakReference(value)
    }

    fun get(key: K): V? {
        // nullを返す可能性があるため、安全に呼び出す
        return cache[key]?.get()
    }
}

fun mediaType(filename: String): MediaType? =
    when (filename.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> MediaType.IMAGE
        "mp4", "mov", "avi", "mkv", "webm", "m4v" -> MediaType.VIDEO
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "3gp" -> MediaType.AUDIO
        "pdf" -> MediaType.PDF
        else -> null
    }

class DropboxTokenExpireException: Exception("Dropbox token expires")

class DropboxClient(private val accessToken: String, private val client: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun digestFolder(path: String): Pair<FolderDigest, String?> {
        val cache = cacheStoreForPath.get(path)
        if (cache != null) {
            return cache
        }
        val response: HttpResponse = client.post("$API_URL/files/list_folder") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody("""{"path": "$path", "recursive": false, "include_media_info": true}""")
        }

        if (response.status.value == 401) {
            throw DropboxTokenExpireException()
        }

        if (response.status.value / 100 == 5) {
            throw ServiceErrorException(dropboxErrorMessage)
        }

        val responseBody = response.bodyAsText()
        val listFolderResponse = json.decodeFromString<ListFolderResponse>(responseBody)
        val digest = aggregateMetadata(listFolderResponse.entries)
        val cursor = if (listFolderResponse.hasMore) listFolderResponse.cursor else null
        val result = Pair(digest, cursor)
        cacheStoreForPath.put(path, result)
        return result
    }

    suspend fun digestFolderContinue(cursor: String): Pair<FolderDigest, String?> {
        val cache = cacheStoreForCursor.get(cursor)
        if (cache != null) {
            return cache
        }
        val response: HttpResponse = client.post("$API_URL/files/list_folder/continue") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody("""{"cursor": "$cursor"}""")
        }

        if (response.status.value == 401) {
            throw DropboxTokenExpireException()
        }

        if (response.status.value / 100 == 5) {
            throw ServiceErrorException(dropboxErrorMessage)
        }

        val responseBody = response.bodyAsText()
        val listFolderResponse = json.decodeFromString<ListFolderResponse>(responseBody)
        val digest = aggregateMetadata(listFolderResponse.entries)
        val newCursor = if (listFolderResponse.hasMore) listFolderResponse.cursor else null
        val result = Pair(digest, newCursor)
        cacheStoreForCursor.put(cursor, result)
        return result
    }

    private fun aggregateMetadata(entries: List<Metadata>): FolderDigest {
        val digest = MutableFolderDigest()
        entries.forEach({ entry ->
            if (entry.tag == "folder") {
                digest.folders.add(entry)
            } else mediaType(entry.name)?.let {
                digest.mediaCounts[it] = digest.mediaCounts.getOrDefault(it, 0) + 1
                digest.medias.add(entry)
            }
        })
        return digest.toImmutable()
    }

    suspend fun getTemporaryLink(path: String): String {
        val response: HttpResponse = client.post("$API_URL/files/get_temporary_link") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
                append(HttpHeaders.ContentType, "application/json")
            }
            setBody("""{"path": "$path"}""")
        }

        if (response.status.value == 401) {
            throw DropboxTokenExpireException()
        }

        if (response.status.value / 100 == 5) {
            throw ServiceErrorException(dropboxErrorMessage)
        }

        val responseBody = response.bodyAsText()
        val result = json.decodeFromString<TemporaryLinkResponse>(responseBody)
        return result.link
    }

    companion object {
        private const val API_URL = "https://api.dropboxapi.com/2"
        private val cacheStoreForPath = WeakCache<String, Pair<FolderDigest, String?>>()
        private val cacheStoreForCursor = WeakCache<String, Pair<FolderDigest, String?>>()
    }
}

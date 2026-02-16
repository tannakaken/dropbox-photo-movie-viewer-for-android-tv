package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.util.Log
import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.flow.first
import kotlin.random.Random


fun generateQrCode(
    text: String,
    size: Int = 512
): Bitmap {
    val bitMatrix: BitMatrix = MultiFormatWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size
    )

    val bitmap = createBitmap(size, size)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap[x, y] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return bitmap
}

suspend fun refreshDropboxToken(secureAuthStorage: SecureAuthStorage): String {
    val auth = secureAuthStorage.getAuth().first()
    return if (auth != null) {
        val apiService = ApiService(BuildConfig.API_BASE_URL, DropboxPhotoAndMovieViewerApplication.client)
        apiService.getDropboxAccessToken(
            deviceId = auth.deviceId,
            refreshToken = auth.refreshToken,
            accessToken = auth.accessToken,
            deviceGenerateId = auth.deviceGenerateId,
            onRefresh = { newAccessToken, newRefreshToken ->
                secureAuthStorage.updateTokens(newAccessToken, newRefreshToken)
            }
        ).dropboxAccessToken
    } else {
        throw ForceLoggingOutException("not login")
    }
}

typealias DropboxApiAction<T> = suspend (DropboxClient) -> T
typealias  WithRetry<T> = suspend (DropboxApiAction<T>) -> T

fun <T> createWithRetry(dropboxAccessToken: String, secureAuthStorage: SecureAuthStorage): WithRetry<T> {
    suspend fun f(dropboxApiAction: DropboxApiAction<T>): T {
        val dropboxClient =
            DropboxClient(dropboxAccessToken, DropboxPhotoAndMovieViewerApplication.client)
        return try {
            dropboxApiAction(dropboxClient)
        } catch (exception: DropboxTokenExpireException) {
            val newDropboxAccessToken = refreshDropboxToken(secureAuthStorage)
            val newDropboxClient =
                DropboxClient(newDropboxAccessToken, DropboxPhotoAndMovieViewerApplication.client)
            try {
                dropboxApiAction(newDropboxClient)
            } catch (exception: DropboxTokenExpireException) {
                throw ForceLoggingOutException("illegal token expiration")
            }
        }
    }
    return ::f
}
package xyz.tannakaken.dropboxphotoandmovieviewerforandroidtv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import javax.inject.Inject
import androidx.core.graphics.createBitmap
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@HiltViewModel
class PDFViewModel @Inject constructor() : ViewModel() {

    private val _pdfFile = MutableStateFlow<File?>(null)

    val pdfFile: StateFlow<File?> = _pdfFile.asStateFlow()

    fun loadPDF(path: String, cacheDir: File, dropboxClient: DropboxClient) {
        val file = File(cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
        viewModelScope.launch {
            val pfrUrl = dropboxClient.getTemporaryLink(path)
            // メインスレッドでのネットワーク接続は禁じられている。
            withContext(Dispatchers.IO) {
                URL(pfrUrl).openStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                _pdfFile.value = file
            }

        }
    }
}

@Composable
fun PDFScreen(
    viewModel: PDFViewModel = hiltViewModel(),
    path: String,
) {
    val dropboxAccessToken = LocalDropboxAccessToken.current!!
    val pdfFile by viewModel.pdfFile.collectAsState()
    if (pdfFile == null) {
        val dropboxClient = DropboxClient(dropboxAccessToken, DropboxPhotoAndMovieViewerApplication.client)
        val context = LocalContext.current
        LaunchedEffect(path) {
            viewModel.loadPDF(path, context.cacheDir, dropboxClient)
        }
        LoadingScreen()
        return
    }

    PdfViewer(pdfFile!!)
}

@Composable
fun PdfViewer(pdfFile: File) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(pdfFile, currentPage) {
        withContext(Dispatchers.IO) {
            val fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            val pdfRenderer = PdfRenderer(fileDescriptor)
            pageCount = pdfRenderer.pageCount
            val page = pdfRenderer.openPage(currentPage)

            val renderedBitmap = createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(renderedBitmap)
            canvas.drawColor(Color.WHITE)
            page.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            bitmap = renderedBitmap
            page.close()
            pdfRenderer.close()
            fileDescriptor.close()
        }
    }

    bitmap?.let {
        Column(modifier = Modifier.fillMaxSize()) {
            PdfImage(
                bitmap = it,
                modifier = Modifier
                    .weight(11f)
                    .fillMaxSize()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (currentPage < pageCount - 1) {
                                        ++currentPage
                                    }
                                    true
                                }

                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    if (0 < currentPage) {
                                        --currentPage
                                    }
                                    true
                                }

                                else -> false
                            }
                        } else {
                            false
                        }
                    }
            )
            Text(
                text = "${currentPage + 1}/${pageCount}",
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier =  Modifier.weight(1f).fillMaxSize().padding(5.dp),
                )
        }
    }
}

@Composable
fun PdfImage(bitmap: Bitmap, modifier: Modifier) {
    // 表示と同時にフォーカスを当てる。
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "PDF Page",
        modifier = modifier.focusRequester(focusRequester).focusable()
    )
}

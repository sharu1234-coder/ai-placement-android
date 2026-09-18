package com.aiplacementos.app.ui.resume

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.aiplacementos.app.ui.common.LoadingState
import com.aiplacementos.app.ui.common.SecondaryButton
import com.aiplacementos.app.ui.theme.Spacing
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Caps how wide a page is rasterised. Rendering at the raw pixel width of a large screen
 * produces a bitmap several times bigger than anything the eye gains from, for a document that
 * is mostly text. */
private const val MAX_RENDER_WIDTH_PX = 1400

/**
 * Renders the student's resume PDF inside the app, one page at a time.
 *
 * Uses Android's own PdfRenderer -- no PDF library is added for this. The bytes are written to
 * a private cache file first because PdfRenderer needs a seekable file descriptor and cannot
 * read from memory; the file lives in the app's own cache directory, which other apps cannot
 * read, and is deleted as soon as the preview leaves the screen.
 *
 * Only the page being looked at is held as a bitmap. Rasterising every page up front is what
 * makes PDF previews run out of memory on a long resume.
 */
@Composable
fun ResumePdfPreview(pdfBytes: ByteArray, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var document by remember(pdfBytes) { mutableStateOf<PdfDocument?>(null) }
    var failure by remember(pdfBytes) { mutableStateOf<String?>(null) }
    var pageIndex by remember(pdfBytes) { mutableIntStateOf(0) }

    // Open once per document, and make sure the renderer and its cache file are released --
    // a leaked PdfRenderer keeps a file descriptor open for the life of the process.
    DisposableEffect(pdfBytes) {
        onDispose { document?.close() }
    }

    LaunchedEffect(pdfBytes) {
        failure = null
        document = try {
            withContext(Dispatchers.IO) { PdfDocument.open(context.cacheDir, pdfBytes) }
        } catch (e: Exception) {
            // The message is deliberately generic: a PDF that will not open is not something
            // the student can act on differently depending on why.
            failure = "This resume can't be previewed here, but it's safely uploaded."
            null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val targetWidthPx = with(density) { maxWidth.toPx() }.toInt().coerceIn(1, MAX_RENDER_WIDTH_PX)
        val doc = document
        val currentFailure = failure

        when {
            currentFailure != null -> Text(
                text = currentFailure,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            doc == null -> LoadingState()

            else -> {
                var page by remember(doc, pageIndex, targetWidthPx) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(doc, pageIndex, targetWidthPx) {
                    page = withContext(Dispatchers.IO) { doc.renderPage(pageIndex, targetWidthPx) }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    val bitmap = page
                    if (bitmap == null) {
                        // Hold the page's shape while it rasterises so the layout doesn't jump.
                        LoadingState(modifier = Modifier.fillMaxWidth().aspectRatio(1f / 1.414f))
                    } else {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Page ${pageIndex + 1} of your resume",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (doc.pageCount > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            SecondaryButton(
                                text = "Previous",
                                onClick = { if (pageIndex > 0) pageIndex-- },
                                enabled = pageIndex > 0,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "Page ${pageIndex + 1} of ${doc.pageCount}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = Spacing.xs),
                            )
                            SecondaryButton(
                                text = "Next",
                                onClick = { if (pageIndex < doc.pageCount - 1) pageIndex++ },
                                enabled = pageIndex < doc.pageCount - 1,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Owns the renderer, its file descriptor and the temporary cache file as one unit, so that
 * closing the preview cannot leave any of the three behind. */
private class PdfDocument(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
    private val file: File,
) {
    val pageCount: Int get() = renderer.pageCount

    /** PdfRenderer allows only one open page at a time and is not thread-safe, so every render
     * goes through this one synchronized path. */
    @Synchronized
    fun renderPage(index: Int, widthPx: Int): Bitmap? = try {
        renderer.openPage(index.coerceIn(0, renderer.pageCount - 1)).use { page ->
            val heightPx = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            // PDF pages are transparent where nothing is drawn; without this the text renders
            // onto a black background on screen.
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    } catch (e: Exception) {
        null
    }

    @Synchronized
    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
        runCatching { file.delete() }
    }

    companion object {
        fun open(cacheDir: File, bytes: ByteArray): PdfDocument {
            val file = File.createTempFile("resume-preview", ".pdf", cacheDir)
            file.writeBytes(bytes)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            return try {
                PdfDocument(PdfRenderer(descriptor), descriptor, file)
            } catch (e: Exception) {
                runCatching { descriptor.close() }
                file.delete()
                throw e
            }
        }
    }
}

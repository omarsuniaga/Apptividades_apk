package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.data.PdfStatementSharer
import com.example.domain.model.BankAccount
import com.example.domain.rules.AccountStatement
import com.example.domain.rules.AccountStatementPdfGenerator
import com.example.domain.rules.GeneratedStatementPdf
import com.example.domain.rules.PdfBrandAssets
import com.example.ui.theme.ApptividadesThemeDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfPreviewDialog(
    statement: AccountStatement,
    pdf: GeneratedStatementPdf,
    availableBankAccounts: List<BankAccount> = emptyList(),
    brandAssets: PdfBrandAssets? = null,
    onSend: (AccountStatement, GeneratedStatementPdf) -> Unit,
    onDownload: (GeneratedStatementPdf) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var currentStatement by remember(statement) { mutableStateOf(statement) }
    var currentPdf by remember(pdf) { mutableStateOf(pdf) }
    var pdfRevision by remember(pdf) { mutableIntStateOf(0) }
    var isEdited by remember(statement) { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var isRegenerating by remember { mutableStateOf(false) }

    var renderedPages by remember(pdfRevision, currentPdf.fileName) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isRendering by remember(pdfRevision, currentPdf.fileName) { mutableStateOf(true) }
    var renderError by remember(pdfRevision, currentPdf.fileName) { mutableStateOf<String?>(null) }

    LaunchedEffect(pdfRevision, currentPdf.fileName) {
        isRendering = true
        renderError = null
        val result = withContext(Dispatchers.IO) {
            renderPdfPagesToBitmaps(context, currentPdf)
        }
        result.onSuccess { pages ->
            renderedPages = pages
        }.onFailure { err ->
            renderError = err.message ?: context.getString(R.string.cobros_pdf_error)
        }
        isRendering = false
    }

    // Bitmaps are intentionally NOT recycled manually: Compose/RenderThread may still draw a frame
    // referencing them after disposal, and recycling them then crashes the app
    // ("Canvas: trying to use a recycled bitmap"). The GC reclaims them once unreferenced.

    Dialog(
        onDismissRequest = onDismiss,
        properties = FullScreenDialogProperties,
    ) {
        FullScreenDialogWindow()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.ground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("pdf_preview_dialog"),
        ) {
            // Top bar with Edit button + Edited badge
            Surface(
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UtilityIconTile(
                        icon = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.nav_close),
                        onClick = onDismiss,
                        testTag = "pdf_preview_close_button",
                        borderColor = colors.line,
                        backgroundColor = colors.surface,
                        tint = colors.ink,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = currentStatement.documentTitle.ifBlank {
                                    stringResource(R.string.pdf_preview_title)
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isEdited) {
                                StatusPill(
                                    label = stringResource(R.string.pdf_preview_edited_badge),
                                    backgroundColor = colors.accentSoft,
                                    textColor = colors.accentHover,
                                    modifier = Modifier.testTag("pdf_preview_edited_badge"),
                                )
                            }
                        }
                        val pagesText = if (currentPdf.pageCount == 1) "1 página" else "${currentPdf.pageCount} páginas"
                        Text(
                            text = "${currentStatement.supplierDisplayName} · $pagesText",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Surface(
                        onClick = { showEditor = true },
                        shape = RoundedCornerShape(12.dp),
                        color = colors.accentSoft,
                        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("pdf_preview_edit_button"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = colors.accentHover,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.pdf_preview_edit),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accentHover,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.line)

            // Rendered PDF pages area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isRendering || isRegenerating -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(color = colors.accent)
                            Text(
                                text = stringResource(R.string.pdf_preview_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.muted,
                            )
                        }
                    }

                    renderError != null -> {
                        Text(
                            text = renderError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.dangerText,
                            modifier = Modifier.padding(24.dp),
                        )
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = 680.dp)
                                .fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            itemsIndexed(renderedPages) { index, bitmap ->
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = androidx.compose.ui.graphics.Color.White,
                                        border = BorderStroke(1.dp, colors.line),
                                        shadowElevation = 3.dp,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        val ratio = (bitmap.width.toFloat() / bitmap.height.toFloat())
                                            .coerceIn(0.5f, 1.5f)
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = stringResource(
                                                R.string.pdf_preview_page_cd,
                                                index + 1,
                                                renderedPages.size,
                                            ),
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(ratio),
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.pdf_preview_page_label,
                                            index + 1,
                                            renderedPages.size,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.muted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.line)

            // Bottom action bar: Descargar + Enviar
            Surface(
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { onDownload(currentPdf) },
                        enabled = !isRegenerating,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("pdf_preview_download_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            tint = colors.ink,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.pdf_preview_download),
                            color = colors.ink,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Button(
                        onClick = { onSend(currentStatement, currentPdf) },
                        enabled = !isRegenerating,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.surface,
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp)
                            .testTag("pdf_preview_send_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.pdf_preview_send),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        PdfStatementEditorDialog(
            statement = currentStatement,
            originalStatement = statement,
            availableBankAccounts = availableBankAccounts,
            onApply = { editedStatement ->
                showEditor = false
                isRegenerating = true
                coroutineScope.launch {
                    val regenerated = withContext(Dispatchers.Default) {
                        runCatching { AccountStatementPdfGenerator.generate(editedStatement, brandAssets) }
                    }
                    regenerated.onSuccess { updatedPdf ->
                        currentStatement = editedStatement
                        currentPdf = updatedPdf
                        isEdited = editedStatement != statement
                        pdfRevision += 1
                    }.onFailure { err ->
                        renderError = err.message ?: context.getString(R.string.cobros_pdf_error)
                    }
                    isRegenerating = false
                }
            },
            onDismiss = { showEditor = false },
        )
    }
}

private fun renderPdfPagesToBitmaps(
    context: android.content.Context,
    pdf: GeneratedStatementPdf,
): Result<List<Bitmap>> = runCatching {
    val previewFile: File = PdfStatementSharer.writeToCache(context, pdf)
    val bitmaps = mutableListOf<Bitmap>()
    val pfd = ParcelFileDescriptor.open(previewFile, ParcelFileDescriptor.MODE_READ_ONLY)
    try {
        PdfRenderer(pfd).use { renderer ->
            val targetWidth = 1080
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val scale = targetWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
                    val width = targetWidth
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps.add(bitmap)
                }
            }
        }
    } finally {
        runCatching { pfd.close() }
        runCatching { if (previewFile.exists()) previewFile.delete() }
    }
    bitmaps
}

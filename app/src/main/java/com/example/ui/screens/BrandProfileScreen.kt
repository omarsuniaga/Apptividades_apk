package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.BrandAssetStorage
import com.example.domain.model.ArtistBrandProfile
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BrandProfileScreen(
    brandProfile: ArtistBrandProfile,
    onBack: () -> Unit,
    onSaveProfile: (ArtistBrandProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ApptividadesThemeDefaults.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var artistName by remember(brandProfile) { mutableStateOf(brandProfile.artistName) }
    var subtitle by remember(brandProfile) { mutableStateOf(brandProfile.subtitle) }
    var contactLine by remember(brandProfile) { mutableStateOf(brandProfile.contactLine) }
    var logoFileName by remember(brandProfile) { mutableStateOf(brandProfile.logoFileName) }
    var signatureFileName by remember(brandProfile) { mutableStateOf(brandProfile.signatureFileName) }

    var assetReloadTick by remember { mutableIntStateOf(0) }
    var logoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var signatureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSignaturePad by remember { mutableStateOf(false) }

    LaunchedEffect(logoFileName, assetReloadTick) {
        val loaded = withContext(Dispatchers.IO) {
            BrandAssetStorage.loadBitmap(context, logoFileName)
        }
        val prev = logoBitmap
        logoBitmap = loaded
        if (prev != null && prev !== loaded && !prev.isRecycled) prev.recycle()
    }

    LaunchedEffect(signatureFileName, assetReloadTick) {
        val loaded = withContext(Dispatchers.IO) {
            BrandAssetStorage.loadBitmap(context, signatureFileName)
        }
        val prev = signatureBitmap
        signatureBitmap = loaded
        if (prev != null && prev !== loaded && !prev.isRecycled) prev.recycle()
    }

    DisposableEffect(Unit) {
        onDispose {
            logoBitmap?.let { if (!it.isRecycled) it.recycle() }
            signatureBitmap?.let { if (!it.isRecycled) it.recycle() }
        }
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            errorMessage = null
            scope.launch {
                val res = BrandAssetStorage.importLogoFromUri(context, uri)
                res.onSuccess { savedName ->
                    logoFileName = savedName
                    assetReloadTick += 1
                    onSaveProfile(
                        ArtistBrandProfile(
                            artistName = artistName.trim(),
                            subtitle = subtitle.trim(),
                            contactLine = contactLine.trim(),
                            logoFileName = savedName,
                            signatureFileName = signatureFileName,
                        )
                    )
                }.onFailure {
                    errorMessage = context.getString(R.string.brand_import_error)
                }
            }
        }
    }

    val signaturePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            errorMessage = null
            scope.launch {
                val res = BrandAssetStorage.importSignatureFromUri(context, uri)
                res.onSuccess { savedName ->
                    signatureFileName = savedName
                    assetReloadTick += 1
                    onSaveProfile(
                        ArtistBrandProfile(
                            artistName = artistName.trim(),
                            subtitle = subtitle.trim(),
                            contactLine = contactLine.trim(),
                            logoFileName = logoFileName,
                            signatureFileName = savedName,
                        )
                    )
                }.onFailure {
                    errorMessage = context.getString(R.string.brand_import_error)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UtilityIconTile(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(R.string.nav_back),
                onClick = onBack,
                testTag = "brand_back_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.brand_screen_title),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.dp, colors.dangerBorder),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.dangerText,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                // Professional identity card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.brand_section_identity),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        OutlinedTextField(
                            value = artistName,
                            onValueChange = { artistName = it },
                            label = { Text(stringResource(R.string.brand_artist_name_label)) },
                            placeholder = { Text(stringResource(R.string.brand_artist_name_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("brand_artist_name_input"),
                        )
                        OutlinedTextField(
                            value = subtitle,
                            onValueChange = { subtitle = it },
                            label = { Text(stringResource(R.string.brand_subtitle_label)) },
                            placeholder = { Text(stringResource(R.string.brand_subtitle_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("brand_subtitle_input"),
                        )
                        OutlinedTextField(
                            value = contactLine,
                            onValueChange = { contactLine = it },
                            label = { Text(stringResource(R.string.brand_contact_label)) },
                            placeholder = { Text(stringResource(R.string.brand_contact_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("brand_contact_input"),
                        )
                    }
                }

                // Logo card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.brand_section_logo),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.brand_logo_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, colors.line),
                                modifier = Modifier.size(72.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    val bmp = logoBitmap
                                    if (bmp != null && !bmp.isRecycled) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = stringResource(R.string.brand_logo_preview_cd),
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.Image,
                                            contentDescription = null,
                                            tint = colors.muted,
                                            modifier = Modifier.size(28.dp),
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        logoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, colors.line),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("brand_logo_pick_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Upload,
                                        contentDescription = null,
                                        tint = colors.ink,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(
                                            if (logoFileName == null) R.string.brand_logo_pick
                                            else R.string.brand_logo_change
                                        ),
                                        color = colors.ink,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }

                                if (logoFileName != null) {
                                    OutlinedButton(
                                        onClick = {
                                            BrandAssetStorage.deleteAsset(context, logoFileName)
                                            logoFileName = null
                                            onSaveProfile(
                                                ArtistBrandProfile(
                                                    artistName = artistName.trim(),
                                                    subtitle = subtitle.trim(),
                                                    contactLine = contactLine.trim(),
                                                    logoFileName = null,
                                                    signatureFileName = signatureFileName,
                                                )
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, colors.dangerBorder),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = colors.dangerText,
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("brand_logo_remove_button"),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DeleteOutline,
                                            contentDescription = stringResource(R.string.brand_logo_remove),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Signature card
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.brand_section_signature),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.ink,
                        )
                        Text(
                            text = stringResource(R.string.brand_signature_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )

                        val sigBmp = signatureBitmap
                        if (sigBmp != null && !sigBmp.isRecycled) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, colors.line),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                ) {
                                    Image(
                                        bitmap = sigBmp.asImageBitmap(),
                                        contentDescription = stringResource(R.string.brand_signature_preview_cd),
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { showSignaturePad = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentSoft,
                                    contentColor = colors.accentHover,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("brand_signature_draw_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Draw,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.brand_signature_draw),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    signaturePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.line),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("brand_signature_upload_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Upload,
                                    contentDescription = null,
                                    tint = colors.ink,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.brand_signature_upload),
                                    color = colors.ink,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }

                            if (signatureFileName != null) {
                                OutlinedButton(
                                    onClick = {
                                        BrandAssetStorage.deleteAsset(context, signatureFileName)
                                        signatureFileName = null
                                        onSaveProfile(
                                            ArtistBrandProfile(
                                                artistName = artistName.trim(),
                                                subtitle = subtitle.trim(),
                                                contactLine = contactLine.trim(),
                                                logoFileName = logoFileName,
                                                signatureFileName = null,
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, colors.dangerBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = colors.dangerText,
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("brand_signature_remove_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.brand_signature_remove),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onSaveProfile(
                            ArtistBrandProfile(
                                artistName = artistName.trim(),
                                subtitle = subtitle.trim(),
                                contactLine = contactLine.trim(),
                                logoFileName = logoFileName,
                                signatureFileName = signatureFileName,
                            )
                        )
                        onBack()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.ink,
                        contentColor = colors.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("brand_save_button"),
                ) {
                    Text(
                        text = stringResource(R.string.brand_save_cta),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onSaveBitmap = { drawnBmp ->
                showSignaturePad = false
                scope.launch {
                    val res = BrandAssetStorage.saveDrawnSignature(context, drawnBmp)
                    if (!drawnBmp.isRecycled) drawnBmp.recycle()
                    res.onSuccess { savedName ->
                        signatureFileName = savedName
                        assetReloadTick += 1
                        onSaveProfile(
                            ArtistBrandProfile(
                                artistName = artistName.trim(),
                                subtitle = subtitle.trim(),
                                contactLine = contactLine.trim(),
                                logoFileName = logoFileName,
                                signatureFileName = savedName,
                            )
                        )
                    }.onFailure {
                        errorMessage = context.getString(R.string.brand_import_error)
                    }
                }
            },
        )
    }
}

@Composable
private fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSaveBitmap: (Bitmap) -> Unit,
) {
    val colors = ApptividadesThemeDefaults.colors
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.brand_signature_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.brand_signature_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, colors.line),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { padSize = it }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        activeStroke = listOf(start)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        activeStroke = activeStroke + change.position
                                    },
                                    onDragEnd = {
                                        if (activeStroke.isNotEmpty()) {
                                            strokes.add(activeStroke)
                                            activeStroke = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        activeStroke = emptyList()
                                    },
                                )
                            }
                            .testTag("signature_pad_canvas"),
                    ) {
                        val inkColor = Color(0xFF15171C)
                        val guideY = size.height * 0.78f
                        drawLine(
                            color = Color(0xFFE2E8F0),
                            start = Offset(24f, guideY),
                            end = Offset(size.width - 24f, guideY),
                            strokeWidth = 2f,
                        )
                        val strokeStyle = Stroke(
                            width = 6f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        )
                        val allStrokes = if (activeStroke.isNotEmpty()) strokes + listOf(activeStroke) else strokes
                        allStrokes.forEach { pts ->
                            if (pts.size == 1) {
                                drawCircle(color = inkColor, radius = 3.5f, center = pts.first())
                            } else if (pts.size > 1) {
                                val path = Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    for (i in 1 until pts.size) {
                                        lineTo(pts[i].x, pts[i].y)
                                    }
                                }
                                drawPath(path = path, color = inkColor, style = strokeStyle)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val bmp = renderSignatureStrokesToBitmap(
                        strokes = strokes.toList(),
                        srcWidth = padSize.width.coerceAtLeast(300),
                        srcHeight = padSize.height.coerceAtLeast(150),
                    )
                    onSaveBitmap(bmp)
                },
                enabled = strokes.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = colors.surface,
                ),
                modifier = Modifier.testTag("signature_pad_save_button"),
            ) {
                Text(stringResource(R.string.brand_signature_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        strokes.clear()
                        activeStroke = emptyList()
                    },
                    modifier = Modifier.testTag("signature_pad_clear_button"),
                ) {
                    Text(stringResource(R.string.brand_signature_clear), color = colors.muted)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.form_cancel), color = colors.muted)
                }
            }
        },
    )
}

private fun renderSignatureStrokesToBitmap(
    strokes: List<List<Offset>>,
    srcWidth: Int,
    srcHeight: Int,
): Bitmap {
    val outW = 600
    val outH = ((srcHeight.toFloat() / srcWidth.toFloat()) * outW).toInt().coerceIn(180, 420)
    val scaleX = outW.toFloat() / srcWidth.toFloat()
    val scaleY = outH.toFloat() / srcHeight.toFloat()

    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.TRANSPARENT)

    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#15171C")
        style = AndroidPaint.Style.STROKE
        strokeWidth = 6.5f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    val dotPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#15171C")
        style = AndroidPaint.Style.FILL
    }

    strokes.forEach { pts ->
        if (pts.size == 1) {
            canvas.drawCircle(pts.first().x * scaleX, pts.first().y * scaleY, 3.5f, dotPaint)
        } else if (pts.size > 1) {
            val path = AndroidPath().apply {
                moveTo(pts.first().x * scaleX, pts.first().y * scaleY)
                for (i in 1 until pts.size) {
                    lineTo(pts[i].x * scaleX, pts[i].y * scaleY)
                }
            }
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}

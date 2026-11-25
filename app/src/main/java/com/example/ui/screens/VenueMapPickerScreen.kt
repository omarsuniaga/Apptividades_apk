package com.example.ui.screens

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.port.PlacePrediction
import com.example.data.port.ResolvedPlace
import com.example.data.remote.OpenMapsApiClient
import com.example.domain.model.Venue
import com.example.domain.rules.DepartureTimeCalculator
import com.example.ui.components.NavigationActionButtons
import com.example.ui.components.UtilityIconTile
import com.example.ui.theme.ApptividadesThemeDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale

@SuppressLint("ClickableViewAccessibility")
@Composable
fun VenueMapPickerScreen(
    title: String,
    initialName: String,
    initialLatitude: Double?,
    initialLongitude: Double?,
    savedVenues: List<Venue>,
    venuesFeaturePending: Boolean,
    isHomePicker: Boolean = false,
    onSearchPlaces: suspend (String) -> Result<List<PlacePrediction>>,
    onFetchPlaceDetails: suspend (String) -> Result<ResolvedPlace>,
    onConfirmVenue: (Venue) -> Unit,
    onDismiss: () -> Unit,
    initialAddress: String? = null,
    onConfirmManualAddress: ((name: String, address: String?) -> Unit)? = null,
) {
    val colors = ApptividadesThemeDefaults.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(onBack = onDismiss)

    // Configure osmdroid user-agent & app-private cache directory (no external storage permissions needed)
    remember(context) {
        runCatching {
            val cfg = Configuration.getInstance()
            cfg.userAgentValue = "${context.packageName}/1.0"
            val baseDir = File(context.cacheDir, "osmdroid").apply { mkdirs() }
            val tileDir = File(baseDir, "tiles").apply { mkdirs() }
            cfg.osmdroidBasePath = baseDir
            cfg.osmdroidTileCache = tileDir
        }
        true
    }

    var searchQuery by remember { mutableStateOf(initialName) }
    var selectedName by remember { mutableStateOf(initialName) }
    var manualAddress by remember { mutableStateOf(initialAddress.orEmpty()) }
    var selectedPlaceId by remember { mutableStateOf<String?>(null) }
    var selectedVenueId by remember { mutableStateOf("") }
    var selectedLat by remember {
        mutableDoubleStateOf(initialLatitude ?: DepartureTimeCalculator.DEFAULT_BAVARO_LAT)
    }
    var selectedLng by remember {
        mutableDoubleStateOf(initialLongitude ?: DepartureTimeCalculator.DEFAULT_BAVARO_LNG)
    }
    var hasExplicitSelection by remember {
        mutableStateOf(initialLatitude != null && initialLongitude != null)
    }

    var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }
    var mapInitFailed by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }

    // Debounced Photon autocomplete (400 ms debounce, minimum 3 letters)
    LaunchedEffect(searchQuery) {
        val clean = searchQuery.trim()
        if (clean.length < OpenMapsApiClient.MIN_SEARCH_CHARS) {
            predictions = emptyList()
            searchFailed = false
            isSearching = false
            return@LaunchedEffect
        }
        delay(OpenMapsApiClient.SEARCH_DEBOUNCE_MS)
        isSearching = true
        searchFailed = false
        val result = onSearchPlaces(clean)
        isSearching = false
        result.onSuccess {
            predictions = it
            searchFailed = false
        }.onFailure {
            predictions = emptyList()
            searchFailed = true
        }
    }

    val filteredSavedVenues = remember(searchQuery, savedVenues) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) savedVenues
        else savedVenues.filter {
            it.name.lowercase().contains(q) || (it.address?.lowercase()?.contains(q) == true)
        }
    }

    fun updateMapPin(lat: Double, lng: Double, animate: Boolean = true, zoom: Double = 15.0) {
        val map = mapViewRef ?: return
        runCatching {
            val point = OsmGeoPoint(lat, lng)
            markerRef?.let { m ->
                m.position = point
                m.title = selectedName.ifBlank { if (isHomePicker) "Casa" else "Lugar seleccionado" }
            }
            if (animate) {
                map.controller.setZoom(zoom)
                map.controller.animateTo(point)
            }
            map.invalidate()
        }
    }

    // Keep MapView lifecycle cleanly bound to Compose host
    DisposableEffect(lifecycleOwner, mapViewRef) {
        val map = mapViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { map?.onResume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { map?.onPause() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { map?.onDetach() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.ground)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UtilityIconTile(
                icon = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.nav_close),
                onClick = onDismiss,
                testTag = "map_picker_close_button",
                borderColor = colors.line,
                backgroundColor = colors.surface,
                tint = colors.ink,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .testTag("map_picker_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Photon search field (min 3 letters, 400 ms debounce)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (!isHomePicker && selectedPlaceId == null) {
                                    selectedName = it
                                }
                            },
                            placeholder = { Text(stringResource(R.string.map_picker_search_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = colors.muted,
                                )
                            },
                            trailingIcon = {
                                if (isSearching) {
                                    CircularProgressIndicator(
                                        color = colors.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.ink,
                                unfocusedBorderColor = colors.line,
                                focusedContainerColor = colors.surface,
                                unfocusedContainerColor = colors.surface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("map_picker_search_input"),
                        )
                        val trimmedLen = searchQuery.trim().length
                        if (trimmedLen in 1 until OpenMapsApiClient.MIN_SEARCH_CHARS) {
                            Text(
                                text = stringResource(R.string.map_picker_min_chars_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted,
                            )
                        }
                    }
                }

                // Graceful non-blocking notice if Photon search fails
                if (searchFailed) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.accentSoft,
                            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("map_picker_search_fallback_notice"),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = colors.accentHover,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.map_picker_search_fallback_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.ink,
                                )
                            }
                        }
                    }
                }

                // Interactive OpenStreetMap MapView (osmdroid, zero API key) + required OSM attribution
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.neutralChipBg,
                            border = BorderStroke(1.dp, colors.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .testTag("map_picker_osm_map"),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (!mapInitFailed) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize(),
                                        factory = { ctx ->
                                            try {
                                                MapView(ctx).apply {
                                                    setTileSource(TileSourceFactory.MAPNIK)
                                                    setMultiTouchControls(true)
                                                    controller.setZoom(14.5)
                                                    val startPoint = OsmGeoPoint(selectedLat, selectedLng)
                                                    controller.setCenter(startPoint)

                                                    // Prevent LazyColumn from stealing touch drag gestures on the map
                                                    setOnTouchListener { v, event ->
                                                        if (event.action == MotionEvent.ACTION_DOWN ||
                                                            event.action == MotionEvent.ACTION_MOVE
                                                        ) {
                                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                                        }
                                                        false
                                                    }

                                                    val marker = Marker(this).apply {
                                                        position = startPoint
                                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                                        this.title = selectedName.ifBlank {
                                                            if (isHomePicker) "Casa" else "Lugar seleccionado"
                                                        }
                                                    }
                                                    overlays.add(marker)

                                                    val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
                                                        override fun singleTapConfirmedHelper(p: OsmGeoPoint): Boolean {
                                                            selectedLat = p.latitude
                                                            selectedLng = p.longitude
                                                            hasExplicitSelection = true
                                                            marker.position = p
                                                            invalidate()
                                                            return true
                                                        }

                                                        override fun longPressHelper(p: OsmGeoPoint): Boolean = false
                                                    })
                                                    overlays.add(0, tapOverlay)

                                                    mapViewRef = this
                                                    markerRef = marker
                                                }
                                            } catch (_: Throwable) {
                                                mapInitFailed = true
                                                android.view.View(ctx)
                                            }
                                        },
                                        update = { view ->
                                            if (view is MapView) {
                                                runCatching {
                                                    markerRef?.position = OsmGeoPoint(selectedLat, selectedLng)
                                                    view.invalidate()
                                                }
                                            }
                                        },
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Map,
                                            contentDescription = null,
                                            tint = colors.muted,
                                            modifier = Modifier.size(28.dp),
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.map_picker_map_unavailable),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.muted,
                                        )
                                    }
                                }

                                // Required OpenStreetMap attribution overlay
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.surface.copy(alpha = 0.92f),
                                    border = BorderStroke(1.dp, colors.line),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .semantics(mergeDescendants = true) {}
                                        .testTag("osm_attribution_label"),
                                ) {
                                    Text(
                                        text = stringResource(R.string.map_picker_osm_attribution),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.ink,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.map_picker_tap_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                }

                // Selected location card + manual address fallback + Google Maps / Waze shortcuts
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surface,
                        border = BorderStroke(1.dp, colors.line),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = selectedName.ifBlank {
                                            if (isHomePicker) "Ubicación de casa" else "Punto seleccionado"
                                        },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = if (hasExplicitSelection || isHomePicker) {
                                            String.format(
                                                Locale.US,
                                                "%.5f, %.5f",
                                                selectedLat,
                                                selectedLng,
                                            )
                                        } else {
                                            stringResource(R.string.map_picker_no_pin_selected)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted,
                                    )
                                }
                            }

                            if (!isHomePicker) {
                                OutlinedTextField(
                                    value = manualAddress,
                                    onValueChange = { manualAddress = it },
                                    label = { Text(stringResource(R.string.map_picker_manual_address_label)) },
                                    placeholder = { Text(stringResource(R.string.map_picker_manual_address_hint)) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = colors.ink,
                                        unfocusedBorderColor = colors.line,
                                        focusedContainerColor = colors.surface,
                                        unfocusedContainerColor = colors.surface,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("map_picker_manual_address_input"),
                                )

                                NavigationActionButtons(
                                    venueName = listOfNotNull(
                                        selectedName.trim().ifBlank { null },
                                        manualAddress.trim().ifBlank { null },
                                    ).joinToString(", ").ifBlank { null },
                                    latitude = if (hasExplicitSelection) selectedLat else null,
                                    longitude = if (hasExplicitSelection) selectedLng else null,
                                    idTag = "map_picker_selected",
                                )
                            }
                        }
                    }
                }

                if (venuesFeaturePending && !isHomePicker) {
                    item {
                        Text(
                            text = stringResource(R.string.map_picker_server_pending),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted,
                        )
                    }
                }

                // Saved venues section (always first!)
                if (filteredSavedVenues.isNotEmpty() && !isHomePicker) {
                    item {
                        Text(
                            text = stringResource(R.string.map_picker_saved_venues),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.muted,
                        )
                    }
                    items(filteredSavedVenues, key = { it.id.ifBlank { "${it.name}_${it.latitude}" } }) { venue ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            border = BorderStroke(
                                width = if (selectedName.equals(venue.name, ignoreCase = true) && hasExplicitSelection) 1.5.dp else 1.dp,
                                color = if (selectedName.equals(venue.name, ignoreCase = true) && hasExplicitSelection) colors.accent else colors.line,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    selectedVenueId = venue.id
                                    selectedPlaceId = venue.placeId
                                    selectedName = venue.name
                                    manualAddress = venue.address.orEmpty()
                                    selectedLat = venue.latitude
                                    selectedLng = venue.longitude
                                    hasExplicitSelection = true
                                    updateMapPin(venue.latitude, venue.longitude)
                                }
                                .testTag("saved_venue_${venue.name.lowercase().replace(" ", "_")}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = venue.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = venue.address ?: String.format(
                                            Locale.US,
                                            "%.4f, %.4f",
                                            venue.latitude,
                                            venue.longitude,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.muted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // Photon autocomplete results
                if (predictions.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.map_picker_search_results),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.muted,
                        )
                    }
                    items(predictions, key = { it.placeId }) { prediction ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.line),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    scope.launch {
                                        isSearching = true
                                        val details = onFetchPlaceDetails(prediction.placeId)
                                        isSearching = false
                                        details.onSuccess { resolved ->
                                            selectedVenueId = ""
                                            selectedPlaceId = resolved.placeId
                                            selectedName = resolved.name
                                            manualAddress = resolved.address.orEmpty()
                                            selectedLat = resolved.latitude
                                            selectedLng = resolved.longitude
                                            hasExplicitSelection = true
                                            predictions = emptyList()
                                            updateMapPin(resolved.latitude, resolved.longitude)
                                        }.onFailure {
                                            searchFailed = true
                                        }
                                    }
                                }
                                .testTag("photon_result_${prediction.placeId}"),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = colors.muted,
                                    modifier = Modifier.size(18.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = prediction.primaryText,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (prediction.secondaryText.isNotBlank()) {
                                        Text(
                                            text = prediction.secondaryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }

        // Sticky confirm footer
        Surface(
            color = colors.ground,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HorizontalDivider(thickness = 1.dp, color = colors.line)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = {
                            val finalName = selectedName.trim().ifBlank {
                                searchQuery.trim().ifBlank {
                                    if (isHomePicker) "Casa" else "Lugar"
                                }
                            }
                            val cleanAddr = manualAddress.trim().ifBlank { null }
                            if (!hasExplicitSelection && !isHomePicker && onConfirmManualAddress != null) {
                                onConfirmManualAddress(finalName, cleanAddr)
                            } else {
                                onConfirmVenue(
                                    Venue(
                                        id = selectedVenueId,
                                        name = finalName,
                                        placeId = selectedPlaceId,
                                        address = cleanAddr,
                                        latitude = selectedLat,
                                        longitude = selectedLng,
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.ink,
                            contentColor = colors.surface,
                        ),
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("map_picker_confirm_button"),
                    ) {
                        Text(
                            text = stringResource(R.string.map_picker_confirm),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
        }
    }
}

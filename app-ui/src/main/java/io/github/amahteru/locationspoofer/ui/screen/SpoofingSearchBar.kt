package io.github.amahteru.locationspoofer.ui.screen

import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiSearch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.amahteru.locationspoofer.ui.R
import androidx.compose.runtime.Composable
import com.amap.api.maps.AMapException
import com.amap.api.services.poisearch.PoiResult
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener
import com.baidu.mapapi.search.poi.PoiCitySearchOption
import com.baidu.mapapi.search.poi.PoiDetailResult
import com.baidu.mapapi.search.poi.PoiDetailSearchResult
import com.baidu.mapapi.search.poi.PoiIndoorResult
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import io.github.amahteru.locationspoofer.data.model.MapEngine
import io.github.amahteru.locationspoofer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchBar(
    mapEngine: MapEngine,
    onResultClick: (AppPoiItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    var results by remember { mutableStateOf<List<AppPoiItem>?>(null) }
    val collapse: () -> Unit = { scope.launch { searchBarState.animateToCollapsed() } }

    LaunchedEffect(textFieldState, mapEngine) {
        snapshotFlow { textFieldState.text.toString() }.collectLatest { query ->
            if (query.isBlank()) {
                results = null
            } else {
                delay(300)
                performPoiSearch(context, mapEngine, query.trim()) {
                    if (textFieldState.text.toString() == query) results = it
                }
            }
        }
    }

    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            onSearch = {},
            placeholder = { Text(stringResource(R.string.search_place_building_coord_hint), maxLines = 1) },
            leadingIcon = {
                if (searchBarState.targetValue == SearchBarValue.Expanded) {
                    IconButton(onClick = collapse) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            trailingIcon = if (textFieldState.text.isNotEmpty()) {
                {
                    IconButton(onClick = { textFieldState.clearText() }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            } else null
        )
    }

    SearchBar(
        state = searchBarState,
        inputField = inputField,
        modifier = modifier,
        shadowElevation = 3.dp
    )
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        LazyColumn {
            if (results?.isEmpty() == true) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.no_search_results)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            items(results.orEmpty()) { poi ->
                ListItem(
                    headlineContent = { Text(poi.title) },
                    supportingContent = if (poi.snippet.isNotBlank()) {
                        { Text(poi.snippet) }
                    } else null,
                    leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        onResultClick(poi)
                        collapse()
                    }
                )
            }
        }
    }
}

private var cachedPlacesClient: com.google.android.libraries.places.api.net.PlacesClient? = null

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private fun performPoiSearch(
    context: Context,
    mapEngine: MapEngine,
    keyword: String,
    onResult: (List<AppPoiItem>) -> Unit
) {
    if (mapEngine == MapEngine.BAIDU) {
        try {
            val mPoiSearch = com.baidu.mapapi.search.poi.PoiSearch.newInstance()
            mPoiSearch.setOnGetPoiSearchResultListener(object :
                OnGetPoiSearchResultListener {
                override fun onGetPoiResult(result: com.baidu.mapapi.search.poi.PoiResult?) {
                    if (result == null || result.error != com.baidu.mapapi.search.core.SearchResult.ERRORNO.NO_ERROR) {
                        onResult(emptyList())
                        mPoiSearch.destroy()
                        return
                    }
                    val items = result.allPoi?.map {
                        AppPoiItem(
                            it.name ?: "",
                            it.address ?: "",
                            it.location.latitude,
                            it.location.longitude
                        )
                    } ?: emptyList()
                    onResult(items)
                    mPoiSearch.destroy()
                }

                override fun onGetPoiDetailResult(p0: PoiDetailResult?) {}
                override fun onGetPoiDetailResult(p0: PoiDetailSearchResult?) {}
                override fun onGetPoiIndoorResult(p0: PoiIndoorResult?) {}
            })
            val option = PoiCitySearchOption()
                .city("全国")
                .keyword(keyword)
                .pageNum(0)
                .pageCapacity(20)
            mPoiSearch.searchInCity(option)
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(emptyList())
        }
    } else if (mapEngine == MapEngine.GOOGLE) {
        try {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            val key = prefs.getString("google_api_key", "")
            if (key.isNullOrBlank()) {
                Toast.makeText(context, context.getString(R.string.google_key_required_hint), Toast.LENGTH_LONG).show()
                onResult(emptyList())
                return
            }
            if (!com.google.android.libraries.places.api.Places.isInitialized()) {
                com.google.android.libraries.places.api.Places.initializeWithNewPlacesApiEnabled(context.applicationContext, key)
            }
            val placesClient = cachedPlacesClient ?: com.google.android.libraries.places.api.Places.createClient(context.applicationContext).also { cachedPlacesClient = it }
            val sessionToken = com.google.android.libraries.places.api.model.AutocompleteSessionToken.newInstance()
            val worldBounds = com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                com.google.android.gms.maps.model.LatLng(-90.0, -180.0),
                com.google.android.gms.maps.model.LatLng(90.0, 180.0)
            )
            val autocompleteRequest = com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest.builder()
                .setQuery(keyword)
                .setLocationBias(worldBounds)
                .setSessionToken(sessionToken)
                .build()

            placesClient.findAutocompletePredictions(autocompleteRequest)
                .addOnSuccessListener { autocompleteResponse ->
                    val predictions = autocompleteResponse.autocompletePredictions
                    if (predictions.isEmpty()) {
                        onResult(emptyList())
                        return@addOnSuccessListener
                    }
                    val fetchFields = listOf(
                        com.google.android.libraries.places.api.model.Place.Field.ID,
                        com.google.android.libraries.places.api.model.Place.Field.DISPLAY_NAME,
                        com.google.android.libraries.places.api.model.Place.Field.LOCATION,
                        com.google.android.libraries.places.api.model.Place.Field.FORMATTED_ADDRESS
                    )
                    val resultList = mutableListOf<AppPoiItem>()
                    val topPredictions = predictions.take(5)
                    var completedCount = 0
                    topPredictions.forEach { prediction ->
                        val fetchRequest = com.google.android.libraries.places.api.net.FetchPlaceRequest.newInstance(prediction.placeId, fetchFields)
                        placesClient.fetchPlace(fetchRequest)
                            .addOnSuccessListener { fetchResponse ->
                                val place = fetchResponse.place
                                val latLng = place.location
                                if (latLng != null) {
                                    resultList.add(
                                        AppPoiItem(
                                            title = place.displayName ?: prediction.getPrimaryText(null).toString(),
                                            snippet = place.formattedAddress ?: prediction.getSecondaryText(null).toString(),
                                            lat = latLng.latitude,
                                            lng = latLng.longitude
                                        )
                                    )
                                }
                            }
                            .addOnCompleteListener {
                                completedCount++
                                if (completedCount == topPredictions.size) {
                                    onResult(resultList)
                                }
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Google Search Error: ${exception.message}", Toast.LENGTH_LONG).show()
                    onResult(emptyList())
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Google Search Catch Error: ${e.message}", Toast.LENGTH_LONG).show()
            onResult(emptyList())
        }
    } else {
        try {
            val query = PoiSearch.Query(keyword, "", "")
            query.pageSize = 10
            query.pageNum = 0
            val search = PoiSearch(context, query)
            search.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                override fun onPoiSearched(
                    result: PoiResult?,
                    rCode: Int
                ) {
                    if (rCode == 1000 && result != null) {
                        onResult(result.pois?.map {
                            AppPoiItem(
                                it.title ?: "",
                                it.snippet ?: "",
                                it.latLonPoint.latitude,
                                it.latLonPoint.longitude
                            )
                        } ?: emptyList())
                    } else {
                        if (rCode == 10003 || rCode == 10012 || rCode == 10013 || rCode == 10014 || rCode == 1800 || rCode == 18000) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.amap_search_failed_quota),
                                Toast.LENGTH_LONG
                            ).show()
                        } else if (rCode != 1000) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.amap_search_failed_code_format, rCode),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        onResult(emptyList())
                    }
                }

                override fun onPoiItemSearched(item: PoiItem?, rCode: Int) {}
            })
            search.searchPOIAsyn()
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.message ?: ""
            if (e is AMapException || msg.contains(
                    "limit",
                    ignoreCase = true
                ) || msg.contains("额度")
            ) {
                Toast.makeText(
                    context,
                    context.getString(R.string.amap_search_exception_format, msg),
                    Toast.LENGTH_LONG
                ).show()
            }
            onResult(emptyList())
        }
    }
}

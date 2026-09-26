package com.railway.ticketsystem.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.data.PhysicalRailCorridorResolver
import com.railway.ticketsystem.data.MapRailNetwork
import com.railway.ticketsystem.data.StationCoordinateCatalog
import com.railway.ticketsystem.data.OfflineTravelRepository
import com.railway.ticketsystem.data.AccessibilityPreferences
import android.webkit.WebSettings
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.railway.ticketsystem.data.RealWeatherService
import com.railway.ticketsystem.databinding.ActivityRailwayMapBinding

/** One saved timetable call used to calculate the live position on the map. */
data class RailwayMapTimetableStop(
    val stationName: String,
    val arrivalTime: String,
    val departureTime: String
)

/**
 * Shows a complete physical route as a continuous line and only its published
 * calls as markers. Both layers are saved at checkout, so an order never
 * redraws against a newly generated stopping pattern.
 */
class RailwayMapActivity : AccessibleActivity() {
    private lateinit var binding: ActivityRailwayMapBinding
    private val gson = Gson()
    private var mapReady = false
    private lateinit var payload: RailwayMapPayload
    private var satelliteBase = true
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val positionTicker = object : Runnable {
        override fun run() {
            updateLivePosition()
            binding.root.postDelayed(this, LIVE_POSITION_REFRESH_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRailwayMapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyImmersiveSystemBars()

        val requestedRouteStations = normalizeStations(
            intent.getStringArrayListExtra(EXTRA_ROUTE_STATIONS)
                ?: intent.getStringArrayExtra(EXTRA_ROUTE_STATIONS)?.toList().orEmpty()
        )
        val network = intent.getStringExtra(EXTRA_NETWORK)
            ?.let { runCatching { MapRailNetwork.valueOf(it) }.getOrNull() }
            ?: inferNetwork(intent.getStringExtra(EXTRA_TRAIN_NUMBER).orEmpty())
        val resolvedRoute = PhysicalRailCorridorResolver.resolveForMap(requestedRouteStations, network)
        val routeStationNames = resolvedRoute.stationNames
        val callingStationNames = normalizeStations(
            intent.getStringArrayListExtra(EXTRA_CALLING_STATIONS)
                ?: intent.getStringArrayExtra(EXTRA_CALLING_STATIONS)?.toList().orEmpty()
        ).ifEmpty { requestedRouteStations }
        val trainNumber = intent.getStringExtra(EXTRA_TRAIN_NUMBER).orEmpty()
        val boardingStation = intent.getStringExtra(EXTRA_BOARDING).orEmpty()
        val alightingStation = intent.getStringExtra(EXTRA_ALIGHTING).orEmpty()
        val departureDate = intent.getStringExtra(EXTRA_DEPARTURE_DATE).orEmpty()
        val seatNumber = intent.getStringExtra(EXTRA_SEAT_NUMBER).orEmpty()
        val timetableStops = readTimetableStops(
            intent.getStringExtra(EXTRA_TIMETABLE_STOPS_JSON)
        )
        payload = RailwayMapPayload(
            trainNumber = trainNumber,
            boardingStation = boardingStation,
            alightingStation = alightingStation,
            routeStations = withInterpolatedPositions(
                routeStationNames.map { station ->
                    val location = StationCoordinateCatalog.resolve(station)
                    RailwayMapStation(
                        name = station,
                        longitude = location?.longitude,
                        latitude = location?.latitude,
                        exactStation = location?.isExactStation == true
                    )
                }
            ),
            routeCorridorHints = resolvedRoute.legCorridorHints,
            network = network.name,
            callingStations = callingStationNames,
            departureDate = departureDate,
            timetableStops = timetableStops,
            seatNumber = seatNumber
        )

        binding.btnMapBack.setOnClickListener { finish() }
        binding.btnMapBase.setOnClickListener { toggleMapBase() }
        binding.tvMapTitle.text = if (trainNumber.isBlank()) "线路地图" else "$trainNumber 线路地图"
        binding.tvMapRoute.text = listOfNotNull(
            routeStationNames.firstOrNull(), routeStationNames.lastOrNull()
        ).joinToString(" → ")
        binding.tvMapHint.text = "${routeStationNames.size} 个线路站点 · ${callingStationNames.size} 个实际经停站"
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        registerNetworkMonitoring()
        configureMap()
    }

    private fun registerNetworkMonitoring() {
        val cm = connectivityManager ?: return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    binding.webRailwayMap.evaluateJavascript("window.onNetworkStateChanged && window.onNetworkStateChanged(true);", null)
                }
            }
            override fun onLost(network: Network) {
                runOnUiThread {
                    binding.webRailwayMap.evaluateJavascript("window.onNetworkStateChanged && window.onNetworkStateChanged(false);", null)
                }
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                runOnUiThread {
                    binding.webRailwayMap.evaluateJavascript("window.onNetworkStateChanged && window.onNetworkStateChanged($hasInternet);", null)
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, networkCallback!!) }
    }

    /**
     * The map is the whole screen, so the bars go transparent and the WebView draws behind
     * them.  The floating title card is then pushed clear of the status bar by its inset —
     * without that it would sit underneath the clock.
     */
    private fun applyImmersiveSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        AccessibilityPreferences.applySystemBarAppearance(this)
        val baseMargin = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val topDp = (bars.top / density).toInt()
            val bottomDp = (bars.bottom / density).toInt()
            binding.webRailwayMap.evaluateJavascript("window.setSystemInsets && window.setSystemInsets($topDp, $bottomDp);", null)
            binding.cardMapHeader.layoutParams =
                (binding.cardMapHeader.layoutParams as FrameLayout.LayoutParams).apply {
                    topMargin = baseMargin + bars.top
                    marginStart = baseMargin + bars.left
                    marginEnd = baseMargin + bars.right
                }
            binding.cardMapHeader.requestLayout()
            // The summary is another floating map control.  Keep it above the gesture
            // area rather than letting it overlap the WebView controls at the bottom.
            binding.cardMapSummary.layoutParams =
                (binding.cardMapSummary.layoutParams as FrameLayout.LayoutParams).apply {
                    bottomMargin = baseMargin + bars.bottom
                    marginStart = baseMargin + bars.left
                    marginEnd = baseMargin + bars.right
                }
            binding.cardMapSummary.requestLayout()
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    override fun onResume() {
        super.onResume()
        binding.root.post(positionTicker)
    }

    override fun onPause() {
        binding.root.removeCallbacks(positionTicker)
        super.onPause()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureMap() {
        binding.webRailwayMap.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            settings.allowFileAccess = true
            setBackgroundColor(Color.parseColor("#081320"))
            addJavascriptInterface(MapJavascriptBridge(), "RailwayMapBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    mapReady = true
                    val insets = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(WindowInsetsCompat.Type.systemBars())
                    if (insets != null) {
                        val density = resources.displayMetrics.density
                        val topDp = (insets.top / density).toInt()
                        val bottomDp = (insets.bottom / density).toInt()
                        binding.webRailwayMap.evaluateJavascript("window.setSystemInsets && window.setSystemInsets($topDp, $bottomDp);", null)
                    }
                    val isOnline = connectivityManager?.activeNetwork?.let {
                        connectivityManager?.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    } ?: false
                    binding.webRailwayMap.evaluateJavascript("window.onNetworkStateChanged && window.onNetworkStateChanged($isOnline);", null)
                    renderRoute()
                }
            }
            // A completed offline pack contains the same map shell, local Leaflet runtime and
            // national tile atlas. Fall back to bundled assets when the atlas has not been downloaded.
            val offlineMap = OfflineTravelRepository(this@RailwayMapActivity).offlineMapHtml()
            loadUrl(offlineMap?.toURI()?.toString() ?: "file:///android_asset/railway_map.html")
        }
    }

    private fun toggleMapBase() {
        satelliteBase = !satelliteBase
        binding.btnMapBase.text = if (satelliteBase) "标准" else "卫星"
        binding.btnMapBase.contentDescription = if (satelliteBase) "切换回标准地图" else "切换到高清卫星影像"
        val base = if (satelliteBase) "satellite" else "standard"
        if (mapReady) binding.webRailwayMap.evaluateJavascript("window.switchMapBase('$base');", null)
    }

    private fun renderRoute() {
        if (!mapReady) return
        val base = if (satelliteBase) "satellite" else "standard"
        binding.webRailwayMap.evaluateJavascript(
            "window.renderRailwayRoute(${gson.toJson(payload)});window.switchMapBase('$base');",
            null
        )
    }

    /**
     * The live-train computation remains in the WebView because it follows the drawn rail
     * geometry.  Only its short label is sent to the native bottom card, avoiding a second
     * floating "运行动态" panel on top of the map.
     */
    private inner class MapJavascriptBridge {
        @JavascriptInterface
        fun finishActivity() {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                finish()
            }
        }

        @JavascriptInterface
        fun updateLiveStatus(status: String?) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val text = status.orEmpty()
                binding.tvMapLiveStatus.text = text
                binding.tvMapLiveStatus.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
        }

        @JavascriptInterface
        fun triggerArrivalHaptic() {
            runOnUiThread {
                runCatching {
                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 100, 200), -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(longArrayOf(0, 150, 100, 200), -1)
                    }
                }
            }
        }

        @JavascriptInterface
        fun openStationGuide(stationName: String, target: String) {
            runOnUiThread {
                runCatching {
                    startActivity(
                        Intent(this@RailwayMapActivity, StationGuideMapActivity::class.java).apply {
                            putExtra(StationGuideMapActivity.EXTRA_STATION, stationName)
                            putExtra(StationGuideMapActivity.EXTRA_TARGET, target.ifBlank { "便捷换乘通道" })
                        }
                    )
                }
            }
        }

        @JavascriptInterface
        fun fetchRealWeather(latitude: Double, longitude: Double, stationName: String) {
            if (!latitude.isFinite() || !longitude.isFinite() || stationName.isBlank()) return
            Thread {
                val weather = RealWeatherService.fetchCurrentWeather(latitude, longitude)
                if (weather != null) {
                    val safeStation = stationName.replace("'", "\\'")
                    val safeDesc = weather.description.replace("'", "\\'")
                    val safeIcon = weather.icon.replace("'", "\\'")
                    val temp = weather.temperature
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        binding.webRailwayMap.evaluateJavascript(
                            "window.onRealWeatherLoaded && window.onRealWeatherLoaded('$safeStation', '$safeDesc', $temp, '$safeIcon');",
                            null
                        )
                    }
                }
            }.start()
        }
    }

    private fun updateLivePosition() {
        if (!mapReady) return
        binding.webRailwayMap.evaluateJavascript(
            "window.updateTrainPosition(${System.currentTimeMillis()});",
            null
        )
    }

    override fun onDestroy() {
        networkCallback?.let { runCatching { connectivityManager?.unregisterNetworkCallback(it) } }
        binding.root.removeCallbacks(positionTicker)
        binding.webRailwayMap.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        super.onDestroy()
    }

    data class RailwayMapPayload(
        val trainNumber: String,
        val boardingStation: String,
        val alightingStation: String,
        val routeStations: List<RailwayMapStation>,
        val callingStations: List<String>,
        /** Physical OSM corridor for every line segment, used to avoid parallel-line jumps. */
        val routeCorridorHints: List<String?>,
        val network: String,
        val departureDate: String,
        val timetableStops: List<RailwayMapTimetableStop>,
        val seatNumber: String? = null
    )

    data class RailwayMapStation(
        val name: String,
        val longitude: Double?,
        val latitude: Double?,
        val exactStation: Boolean,
        /** Position was derived from neighbouring stations because the catalog has no entry. */
        val estimated: Boolean = false
    )

    /**
     * Gives every station a position, deriving the ones the catalog does not cover from their
     * neighbours.  Dropping them instead silently broke two things at once: the station lost its
     * marker, and any leg touching it became un-geometrisable, which made the live-position
     * lookup fall through to its "about to arrive" default mid-journey.
     */
    private fun withInterpolatedPositions(stations: List<RailwayMapStation>): List<RailwayMapStation> {
        val known = stations.indices.filter {
            stations[it].latitude != null && stations[it].longitude != null
        }
        if (known.size < 2) return stations
        val result = stations.toMutableList()

        // Gaps between two indexed stations follow the straight line joining them.
        known.zipWithNext { from, to ->
            if (to - from <= 1) return@zipWithNext
            for (index in from + 1 until to) {
                val fraction = (index - from).toDouble() / (to - from)
                result[index] = result[index].copy(
                    longitude = blend(stations[from].longitude!!, stations[to].longitude!!, fraction),
                    latitude = blend(stations[from].latitude!!, stations[to].latitude!!, fraction),
                    estimated = true
                )
            }
        }

        // Stations before the first indexed one continue the direction of the first leg, and
        // likewise at the far end.  Extrapolating keeps the segment lengths non-zero, which the
        // live-position interpolation divides by.
        val first = known.first()
        val second = known[1]
        for (index in first - 1 downTo 0) {
            val steps = (first - index).toDouble()
            result[index] = result[index].copy(
                longitude = stations[first].longitude!! + (stations[first].longitude!! - stations[second].longitude!!) * steps,
                latitude = stations[first].latitude!! + (stations[first].latitude!! - stations[second].latitude!!) * steps,
                estimated = true
            )
        }
        val last = known.last()
        val beforeLast = known[known.size - 2]
        for (index in last + 1 until stations.size) {
            val steps = (index - last).toDouble()
            result[index] = result[index].copy(
                longitude = stations[last].longitude!! + (stations[last].longitude!! - stations[beforeLast].longitude!!) * steps,
                latitude = stations[last].latitude!! + (stations[last].latitude!! - stations[beforeLast].latitude!!) * steps,
                estimated = true
            )
        }
        return result
    }

    private fun blend(from: Double, to: Double, fraction: Double): Double = from + (to - from) * fraction

    companion object {
        private const val EXTRA_ROUTE_STATIONS = "route_map_route_stations"
        private const val EXTRA_CALLING_STATIONS = "route_map_calling_stations"
        private const val EXTRA_TRAIN_NUMBER = "route_map_train_number"
        private const val EXTRA_BOARDING = "route_map_boarding"
        private const val EXTRA_ALIGHTING = "route_map_alighting"
        private const val EXTRA_DEPARTURE_DATE = "route_map_departure_date"
        private const val EXTRA_TIMETABLE_STOPS_JSON = "route_map_timetable_stops_json"
        private const val EXTRA_NETWORK = "route_map_network"
        private const val EXTRA_SEAT_NUMBER = "route_map_seat_number"
        private const val LIVE_POSITION_REFRESH_MILLIS = 1_000L

        fun intent(
            context: Context,
            trainNumber: String,
            routeStations: List<String>,
            callingStations: List<String>,
            boardingStation: String,
            alightingStation: String,
            departureDate: String,
            timetableStops: List<RailwayMapTimetableStop>,
            network: MapRailNetwork? = null,
            seatNumber: String? = null
        ): Intent = Intent(context, RailwayMapActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_ROUTE_STATIONS, ArrayList(routeStations))
            putStringArrayListExtra(EXTRA_CALLING_STATIONS, ArrayList(callingStations))
            putExtra(EXTRA_TRAIN_NUMBER, trainNumber)
            putExtra(EXTRA_BOARDING, boardingStation)
            putExtra(EXTRA_ALIGHTING, alightingStation)
            putExtra(EXTRA_DEPARTURE_DATE, departureDate)
            putExtra(EXTRA_TIMETABLE_STOPS_JSON, Gson().toJson(timetableStops))
            putExtra(EXTRA_SEAT_NUMBER, seatNumber)
            network?.let { putExtra(EXTRA_NETWORK, it.name) }
        }

        private fun inferNetwork(trainNumber: String): MapRailNetwork {
            val prefix = trainNumber.trim().firstOrNull()?.uppercaseChar()
            return if (prefix in setOf('K', 'T', 'Z', 'Y', 'S', 'L')) MapRailNetwork.CONVENTIONAL else MapRailNetwork.HIGH_SPEED
        }
        private fun normalizeStations(stations: List<String>): List<String> =
            stations.asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .fold(mutableListOf<String>()) { result, station ->
                    if (result.lastOrNull() != station) result.add(station)
                    result
                }

        private fun readTimetableStops(raw: String?): List<RailwayMapTimetableStop> =
            runCatching {
                Gson().fromJson<List<RailwayMapTimetableStop>>(
                    raw,
                    object : TypeToken<List<RailwayMapTimetableStop>>() {}.type
                )
            }.getOrNull().orEmpty()
                .filter { it.stationName.isNotBlank() }
    }
}

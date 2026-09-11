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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.data.StationCoordinateCatalog
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
class RailwayMapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRailwayMapBinding
    private val gson = Gson()
    private var mapReady = false
    private lateinit var payload: RailwayMapPayload
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

        val routeStationNames = normalizeStations(
            intent.getStringArrayListExtra(EXTRA_ROUTE_STATIONS).orEmpty()
        )
        val callingStationNames = normalizeStations(
            intent.getStringArrayListExtra(EXTRA_CALLING_STATIONS).orEmpty()
        ).ifEmpty { routeStationNames }
        val trainNumber = intent.getStringExtra(EXTRA_TRAIN_NUMBER).orEmpty()
        val boardingStation = intent.getStringExtra(EXTRA_BOARDING).orEmpty()
        val alightingStation = intent.getStringExtra(EXTRA_ALIGHTING).orEmpty()
        val departureDate = intent.getStringExtra(EXTRA_DEPARTURE_DATE).orEmpty()
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
            callingStations = callingStationNames,
            departureDate = departureDate,
            timetableStops = timetableStops
        )

        binding.btnMapBack.setOnClickListener { finish() }
        binding.tvMapTitle.text = if (trainNumber.isBlank()) "线路地图" else "$trainNumber 线路地图"
        binding.tvMapRoute.text = listOfNotNull(
            routeStationNames.firstOrNull(), routeStationNames.lastOrNull()
        ).joinToString(" → ")
        binding.tvMapHint.text = "${routeStationNames.size} 个线路站点 · ${callingStationNames.size} 个实际经停站 · 走向按真实轨道绘制"
        configureMap()
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
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val baseMargin = (16 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
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
            setBackgroundColor(Color.rgb(238, 247, 255))
            addJavascriptInterface(MapJavascriptBridge(), "RailwayMapBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    mapReady = true
                    renderRoute()
                }
            }
            loadUrl("file:///android_asset/railway_map.html")
        }
    }

    private fun renderRoute() {
        if (!mapReady) return
        binding.webRailwayMap.evaluateJavascript(
            "window.renderRailwayRoute(${gson.toJson(payload)});",
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
        fun updateLiveStatus(status: String?) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val text = status.orEmpty()
                binding.tvMapLiveStatus.text = text
                binding.tvMapLiveStatus.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            }
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
        val departureDate: String,
        val timetableStops: List<RailwayMapTimetableStop>
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
        private const val LIVE_POSITION_REFRESH_MILLIS = 1_000L

        fun intent(
            context: Context,
            trainNumber: String,
            routeStations: List<String>,
            callingStations: List<String>,
            boardingStation: String,
            alightingStation: String,
            departureDate: String,
            timetableStops: List<RailwayMapTimetableStop>
        ): Intent = Intent(context, RailwayMapActivity::class.java).apply {
            putStringArrayListExtra(EXTRA_ROUTE_STATIONS, ArrayList(routeStations))
            putStringArrayListExtra(EXTRA_CALLING_STATIONS, ArrayList(callingStations))
            putExtra(EXTRA_TRAIN_NUMBER, trainNumber)
            putExtra(EXTRA_BOARDING, boardingStation)
            putExtra(EXTRA_ALIGHTING, alightingStation)
            putExtra(EXTRA_DEPARTURE_DATE, departureDate)
            putExtra(EXTRA_TIMETABLE_STOPS_JSON, Gson().toJson(timetableStops))
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

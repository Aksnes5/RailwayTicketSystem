package com.railway.ticketsystem.data

import android.content.Context
import com.google.gson.Gson
import com.railway.ticketsystem.model.Order
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** Durable app-private tickets, documents and map tiles for travel without a network. */
data class OfflineTravelBundle(val generatedAt: Long, val orders: List<Order>, val hotels: List<HotelReservation>, val invoices: List<ElectronicInvoice>)
data class OfflinePackStatus(
    val updatedAt: Long, val tripCount: Int, val hotelCount: Int, val invoiceCount: Int,
    val hasNationalMap: Boolean, val hasSatelliteMap: Boolean, val mapBytes: Long, val satelliteBytes: Long
)

class OfflineTravelRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun isAutoDownloadEnabled() = prefs.getBoolean(KEY_AUTO, true)
    fun setAutoDownloadEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO, enabled).apply()

    fun prepareForUpcomingTrips(
        userId: String,
        includeNationalMap: Boolean = false,
        includeSatelliteMap: Boolean = false,
        progress: ((String) -> Unit)? = null
    ): OfflinePackStatus {
        progress?.invoke("正在整理车票和经停时刻…")
        val root = packRoot().apply { mkdirs() }
        val orders = OrderRepository(appContext).getOrdersByUserId(userId)
            .filter { it.status == "已支付" || it.status == "已完成" }.sortedBy { it.departureDate }
        val hotels = HotelReservationRepository(appContext).getByUser(userId)
        val invoices = InvoiceRepository(appContext).getByUser(userId)
        File(root, "travel_bundle.json").writeText(gson.toJson(OfflineTravelBundle(System.currentTimeMillis(), orders, hotels, invoices)))
        val invoiceDir = File(root, "invoices").apply { mkdirs() }
        invoices.forEach { invoice -> runCatching {
            invoice.pdfUri?.takeIf(String::isNotBlank)?.let { uri ->
                appContext.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { input ->
                    File(invoiceDir, "${invoice.id}.pdf").outputStream().use(input::copyTo)
                }
            }
        } }
        copyBundledMapShell(progress)
        if (includeNationalMap) cacheNationalMap(progress)
        if (includeSatelliteMap) cacheSatelliteAtlas(orders, progress)
        val status = status(); prefs.edit().putLong(KEY_UPDATED, System.currentTimeMillis()).apply(); return status
    }

    fun autoPrepare(userId: String) { if (isAutoDownloadEnabled()) runCatching { prepareForUpcomingTrips(userId) } }

    fun status(): OfflinePackStatus {
        val root = packRoot(); val bundle = File(root, "travel_bundle.json")
        val saved = bundle.takeIf(File::exists)?.let { runCatching { gson.fromJson(it.readText(), OfflineTravelBundle::class.java) }.getOrNull() }
        val normal = nationalMapDirectory(); val satellite = satelliteDirectory()
        return OfflinePackStatus(
            bundle.takeIf(File::exists)?.lastModified() ?: prefs.getLong(KEY_UPDATED, 0L), saved?.orders?.size ?: 0, saved?.hotels?.size ?: 0, saved?.invoices?.size ?: 0,
            File(normal, "railway_map.html").exists() && File(normal, "leaflet.js").exists() && File(normal, "railway_map_base.js").exists(),
            countTiles(satellite, "jpg") > 0,
            directoryBytes(normal), directoryBytes(satellite)
        )
    }

    /**
     * An offline tile atlas can survive an APK update, but its HTML shell and rail
     * geometry cannot: a stale shell may reference assets which no longer exist and
     * leave the WebView blank. In that case use the current bundled map immediately.
     */
    fun offlineMapHtml(): File? {
        val map = nationalMapDirectory()
        val html = File(map, "railway_map.html")
        val complete = html.exists() && File(map, "leaflet.js").exists() && File(map, "railway_map_base.js").exists()
        val revision = File(map, MAP_SHELL_REVISION_FILE).takeIf(File::exists)?.readText()?.trim()
        return html.takeIf { complete && revision == MAP_SHELL_REVISION }
    }

    fun clear() { packRoot().deleteRecursively(); prefs.edit().remove(KEY_UPDATED).apply() }

    private fun copyBundledMapShell(progress: ((String) -> Unit)?) {
        val map = nationalMapDirectory().apply { mkdirs() }
        progress?.invoke("正在准备全国铁路地图…")
        copyAsset("railway_network.js", File(map, "railway_network.js"), overwrite = true)
        copyAsset("railway_map_base.js", File(map, "railway_map_base.js"), overwrite = true)
        copyAsset("railway_map_fallback.js", File(map, "railway_map_fallback.js"), overwrite = true)
        val html = appContext.assets.open("railway_map.html").bufferedReader().use { it.readText() }
            .replace("https://unpkg.com/leaflet@1.9.4/dist/leaflet.css", "leaflet.css")
            .replace("https://unpkg.com/leaflet@1.9.4/dist/leaflet.js", "leaflet.js")
            .replace("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", "../satellite_atlas/{z}/{y}/{x}.jpg")
            .replace("maxZoom: 19, minZoom: 3", "maxZoom: 10, maxNativeZoom: 7, minZoom: 3")
        File(map, "railway_map.html").writeText(html)
        File(map, MAP_SHELL_REVISION_FILE).writeText(MAP_SHELL_REVISION)
    }

    /** Nationwide overview plus high-detail (z15–16) tiles around saved trip stations. */
    private fun cacheSatelliteAtlas(orders: List<Order>, progress: ((String) -> Unit)?) {
        val root = satelliteDirectory().apply { mkdirs() }
        progress?.invoke("正在下载全国卫星概览…")
        val overview = (5..7).flatMap { z -> tileRange(z, 73.0, 135.0, 18.0, 54.0) }
        overview.forEachIndexed { index, tile ->
            downloadSatellite(root, tile); if (index % 24 == 0 || index == overview.lastIndex) progress?.invoke("全国卫星概览 ${index + 1}/${overview.size}")
        }
        val stations = orders.flatMap { listOf(it.departureStation, it.arrivalStation) }.distinct().take(14)
            .mapNotNull { StationCoordinateCatalog.resolve(it) }
        val detail = linkedSetOf<Triple<Int, Int, Int>>()
        stations.forEach { station -> listOf(15, 16).forEach { z ->
            val cx = lonToTile(station.longitude, z); val cy = latToTile(station.latitude, z)
            for (x in cx - 1..cx + 1) for (y in cy - 1..cy + 1) detail += Triple(z, x, y)
        } }
        detail.forEachIndexed { index, tile ->
            downloadSatellite(root, tile); if (index % 12 == 0 || index == detail.size - 1) progress?.invoke("行程高清卫星图 ${index + 1}/${detail.size}")
        }
    }

    private fun cacheNationalMap(progress: ((String) -> Unit)?) {
        val map = nationalMapDirectory().apply { mkdirs() }
        progress?.invoke("正在下载离线地图引擎…")
        download("https://unpkg.com/leaflet@1.9.4/dist/leaflet.js", File(map, "leaflet.js")); download("https://unpkg.com/leaflet@1.9.4/dist/leaflet.css", File(map, "leaflet.css"))
        listOf("marker-icon.png", "marker-icon-2x.png", "marker-shadow.png", "layers.png", "layers-2x.png").forEach { download("https://unpkg.com/leaflet@1.9.4/dist/images/$it", File(map, "images/$it")) }
        // The default map is a bundled railway-first vector base, so it remains
        // available offline without downloading generic OSM road tiles.
        progress?.invoke("全国铁路矢量底图已就绪")
    }

    private fun tileRange(z: Int, west: Double, east: Double, south: Double, north: Double): List<Triple<Int, Int, Int>> =
        (lonToTile(west, z)..lonToTile(east, z)).flatMap { x -> (latToTile(north, z)..latToTile(south, z)).map { y -> Triple(z, x, y) } }
    private fun downloadSatellite(root: File, tile: Triple<Int, Int, Int>) { val (z, x, y) = tile; download("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x", File(root, "$z/$y/$x.jpg")) }
    private fun countTiles(root: File, extension: String) = root.takeIf(File::exists)?.walkTopDown()?.count { it.isFile && it.extension == extension } ?: 0
    private fun directoryBytes(root: File) = if (root.exists()) root.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    private fun copyAsset(name: String, target: File, overwrite: Boolean = false) {
        if (!overwrite && target.exists() && target.length() > 0) return
        target.parentFile?.mkdirs()
        appContext.assets.open(name).use { input -> target.outputStream().use(input::copyTo) }
    }
    private fun download(url: String, target: File) { if (target.exists() && target.length() > 200) return; target.parentFile?.mkdirs(); val temp = File(target.parentFile, "${target.name}.part"); runCatching { val c = URL(url).openConnection() as HttpURLConnection; c.connectTimeout = 12_000; c.readTimeout = 20_000; c.setRequestProperty("User-Agent", "RailwayTravelOfflinePack/2.0"); c.inputStream.use { input -> temp.outputStream().use(input::copyTo) }; if (temp.length() > 0) { if (target.exists()) target.delete(); temp.renameTo(target) } }.onFailure { temp.delete() } }
    private fun lonToTile(lon: Double, z: Int) = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt()
    private fun latToTile(lat: Double, z: Int): Int { val r = lat * PI / 180.0; return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)).toInt() }
    private fun packRoot() = File(appContext.filesDir, "offline_travel_pack")
    private fun nationalMapDirectory() = File(packRoot(), "national_hd_map")
    private fun satelliteDirectory() = File(packRoot(), "satellite_atlas")
    private companion object {
        const val PREFS = "offline_travel_settings"
        const val KEY_AUTO = "auto_prepare"
        const val KEY_UPDATED = "last_updated"
        const val MAP_SHELL_REVISION_FILE = ".railway_map_shell_revision"
        const val MAP_SHELL_REVISION = "2026.09.25-satellite-labels-4"
    }
}

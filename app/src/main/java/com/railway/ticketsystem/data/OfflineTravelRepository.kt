package com.railway.ticketsystem.data

import android.content.Context
import com.google.gson.Gson
import com.railway.ticketsystem.model.Order
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos
import kotlin.math.PI

/**
 * A durable, user-controlled offline snapshot.  It intentionally stores copies in app-private
 * storage so tickets and documents remain available after temporary network loss.
 */
data class OfflineTravelBundle(
    val generatedAt: Long,
    val orders: List<Order>,
    val hotels: List<HotelReservation>,
    val invoices: List<ElectronicInvoice>
)

data class OfflinePackStatus(
    val updatedAt: Long,
    val tripCount: Int,
    val hotelCount: Int,
    val invoiceCount: Int,
    val hasNationalMap: Boolean,
    val mapBytes: Long
)

class OfflineTravelRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun isAutoDownloadEnabled(): Boolean = prefs.getBoolean(KEY_AUTO, true)
    fun setAutoDownloadEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO, enabled).apply()

    fun prepareForUpcomingTrips(userId: String, includeNationalMap: Boolean = false, progress: ((String) -> Unit)? = null): OfflinePackStatus {
        progress?.invoke("正在整理车票和经停时刻…")
        val root = packRoot().apply { mkdirs() }
        val orders = OrderRepository(appContext).getOrdersByUserId(userId)
            .filter { it.status == "已支付" || it.status == "已完成" }
            .sortedBy { it.departureDate }
        val hotels = HotelReservationRepository(appContext).getByUser(userId)
        val invoices = InvoiceRepository(appContext).getByUser(userId)
        File(root, "travel_bundle.json").writeText(gson.toJson(OfflineTravelBundle(System.currentTimeMillis(), orders, hotels, invoices)))

        progress?.invoke("正在保存电子凭证与酒店订单…")
        val invoiceDir = File(root, "invoices").apply { mkdirs() }
        invoices.forEach { invoice ->
            val uriText = invoice.pdfUri.orEmpty()
            if (uriText.isNotBlank()) runCatching {
                appContext.contentResolver.openInputStream(android.net.Uri.parse(uriText))?.use { input ->
                    File(invoiceDir, "${invoice.id}.pdf").outputStream().use(input::copyTo)
                }
            }
        }

        copyBundledMapShell(progress)
        if (includeNationalMap) cacheNationalHdMap(progress)
        val status = status()
        prefs.edit().putLong(KEY_UPDATED, status.updatedAt).apply()
        return status
    }

    /** Called on a worker after payment; it never triggers the large national tile download. */
    fun autoPrepare(userId: String) {
        if (isAutoDownloadEnabled()) runCatching { prepareForUpcomingTrips(userId, includeNationalMap = false) }
    }

    fun status(): OfflinePackStatus {
        val root = packRoot()
        val bundle = File(root, "travel_bundle.json")
        val saved = bundle.takeIf(File::exists)?.let { runCatching {
            gson.fromJson(it.readText(), OfflineTravelBundle::class.java)
        }.getOrNull() }
        val mapRoot = nationalMapDirectory()
        val hasMap = File(mapRoot, "railway_map.html").exists() && File(mapRoot, "leaflet.js").exists() && tileCount() > 0
        return OfflinePackStatus(
            updatedAt = bundle.takeIf(File::exists)?.lastModified() ?: prefs.getLong(KEY_UPDATED, 0L),
            tripCount = saved?.orders?.size ?: 0,
            hotelCount = saved?.hotels?.size ?: 0,
            invoiceCount = saved?.invoices?.size ?: 0,
            hasNationalMap = hasMap,
            mapBytes = if (mapRoot.exists()) mapRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        )
    }

    fun offlineMapHtml(): File? = File(nationalMapDirectory(), "railway_map.html")
        .takeIf { it.exists() && File(nationalMapDirectory(), "leaflet.js").exists() && tileCount() > 0 }

    fun clear() {
        packRoot().deleteRecursively()
        prefs.edit().remove(KEY_UPDATED).apply()
    }

    private fun copyBundledMapShell(progress: ((String) -> Unit)?) {
        val mapDir = nationalMapDirectory().apply { mkdirs() }
        progress?.invoke("正在准备全国铁路矢量底图…")
        copyAsset("railway_network.js", File(mapDir, "railway_network.js"))
        val source = appContext.assets.open("railway_map.html").bufferedReader().use { it.readText() }
        // The cached page is self-contained: Leaflet and every base tile use local relative paths.
        val local = source
            .replace("https://unpkg.com/leaflet@1.9.4/dist/leaflet.css", "leaflet.css")
            .replace("https://unpkg.com/leaflet@1.9.4/dist/leaflet.js", "leaflet.js")
            .replace("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", "tiles/{z}/{x}/{y}.png")
            .replace("maxZoom: 18", "maxZoom: 10, maxNativeZoom: 7")
        File(mapDir, "railway_map.html").writeText(local)
    }

    /** Downloads a national 5–7 level raster atlas plus its JS runtime for offline rendering. */
    private fun cacheNationalHdMap(progress: ((String) -> Unit)?) {
        val mapDir = nationalMapDirectory().apply { mkdirs() }
        progress?.invoke("正在下载离线地图引擎…")
        download("https://unpkg.com/leaflet@1.9.4/dist/leaflet.js", File(mapDir, "leaflet.js"))
        download("https://unpkg.com/leaflet@1.9.4/dist/leaflet.css", File(mapDir, "leaflet.css"))
        val images = File(mapDir, "images").apply { mkdirs() }
        listOf("marker-icon.png", "marker-icon-2x.png", "marker-shadow.png", "layers.png", "layers-2x.png").forEach { name ->
            download("https://unpkg.com/leaflet@1.9.4/dist/images/$name", File(images, name))
        }

        val ranges = (5..7).flatMap { zoom ->
            val minX = lonToTile(73.0, zoom); val maxX = lonToTile(135.0, zoom)
            val minY = latToTile(54.0, zoom); val maxY = latToTile(18.0, zoom)
            (minX..maxX).flatMap { x -> (minY..maxY).map { y -> Triple(zoom, x, y) } }
        }
        ranges.forEachIndexed { index, (zoom, x, y) ->
            val target = File(mapDir, "tiles/$zoom/$x/$y.png")
            if (!target.exists() || target.length() < 200) {
                download("https://tile.openstreetmap.org/$zoom/$x/$y.png", target)
            }
            if (index % 12 == 0 || index == ranges.lastIndex) {
                progress?.invoke("全国高清地图 ${index + 1}/${ranges.size}")
            }
        }
    }

    private fun tileCount(): Int = File(nationalMapDirectory(), "tiles").takeIf(File::exists)
        ?.walkTopDown()?.count { it.isFile && it.extension == "png" } ?: 0

    private fun copyAsset(name: String, target: File) {
        if (target.exists() && target.length() > 0) return
        target.parentFile?.mkdirs()
        appContext.assets.open(name).use { input -> target.outputStream().use(input::copyTo) }
    }

    private fun download(url: String, target: File) {
        if (target.exists() && target.length() > 200) return
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", "RailwayTravelOfflinePack/1.0")
            connection.inputStream.use { input -> temp.outputStream().use(input::copyTo) }
            if (temp.length() > 0) {
                if (target.exists()) target.delete()
                temp.renameTo(target)
            }
        }.onFailure { temp.delete() }
    }

    private fun lonToTile(lon: Double, zoom: Int): Int = floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    private fun latToTile(lat: Double, zoom: Int): Int {
        val radians = lat * PI / 180.0
        return floor((1.0 - ln(tan(radians) + 1.0 / cos(radians)) / PI) / 2.0 * (1 shl zoom)).toInt()
    }
    private fun packRoot() = File(appContext.filesDir, "offline_travel_pack")
    private fun nationalMapDirectory() = File(packRoot(), "national_hd_map")

    private companion object {
        const val PREFS = "offline_travel_settings"
        const val KEY_AUTO = "auto_prepare"
        const val KEY_UPDATED = "last_updated"
    }
}
package com.railway.ticketsystem.data

import android.content.Context
import android.util.Log
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.RailwayRoute
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 异步数据加载管理器
 * 使用多线程和缓存优化启动性能
 */
object AsyncDataLoader {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val loadingProgress = AtomicInteger(0)
    private val isInitialized = AtomicInteger(0)
    
    // 缓存数据
    private val routeCache = ConcurrentHashMap<String, RailwayRoute>()
    private val trainCache = ConcurrentHashMap<String, List<Train>>()
    private val crossLineTrainCache = ConcurrentHashMap<String, List<Train>>()
    
    // 加载状态回调
    private var progressCallback: ((String, Int) -> Unit)? = null
    
    /**
     * 异步初始化所有数据
     */
    suspend fun initializeAsync(context: Context, progressCallback: (String, Int) -> Unit) {
        this.progressCallback = progressCallback
        
        try {
            Log.d("AsyncDataLoader", "开始异步初始化数据")
            
            // 初始化缓存管理器
            DataCacheManager.initialize(context)
            
            // 检查缓存是否有效
            if (DataCacheManager.isCacheValid()) {
                updateProgress("从缓存加载数据...", 20)
                
                // 从缓存加载数据
                val routes = DataCacheManager.loadRoutesFromCache()
                val directTrains = DataCacheManager.loadTrainsFromCache()
                val crossLineTrains = DataCacheManager.loadCrossLineTrainsFromCache()
                val graphSnap = DataCacheManager.loadGraphSnapshot()
                
                if (routes != null && directTrains != null && crossLineTrains != null && graphSnap != null) {
                    // 缓存数据有效，直接使用
                    updateProgress("缓存数据加载完成", 50)
                    
                    // 恢复图数据
                    com.railway.ticketsystem.model.RailwayRouteManager.importGraphSnapshot(graphSnap)
                    // 构建图数据（已有图，仅注入列车）
                    buildGraphDataAsync(routes, Unit, directTrains, crossLineTrains)
                    
                    updateProgress("数据初始化完成", 100)
                    isInitialized.set(1)
                    
                    Log.d("AsyncDataLoader", "从缓存加载完成")
                    return
                }
            }
            
            // 缓存无效或不存在，重新生成数据
            updateProgress("重新生成数据...", 10)
            
            // 并行加载基础数据
            val routesDeferred = scope.async { loadRoutes() }
            val stationsDeferred = scope.async { loadStations() }
            
            // 等待基础数据加载完成
            val routes = routesDeferred.await()
            val stations = stationsDeferred.await()
            
            updateProgress("基础数据加载完成", 30)
            
            // 并行生成车次数据
            val directTrainsDeferred = scope.async { generateDirectTrainsAsync(routes) }
            val crossLineTrainsDeferred = scope.async { generateCrossLineTrainsAsync(routes) }
            
            // 等待车次数据生成完成
            val directTrains = directTrainsDeferred.await()
            val crossLineTrains = crossLineTrainsDeferred.await()
            
            updateProgress("车次数据生成完成", 80)
            
            // 保存到缓存
            scope.async { DataCacheManager.saveRoutesToCache(routes) }
            scope.async { DataCacheManager.saveTrainsToCache(directTrains) }
            scope.async { DataCacheManager.saveCrossLineTrainsToCache(crossLineTrains) }
            scope.async {
                val snap = com.railway.ticketsystem.model.RailwayRouteManager.exportGraphSnapshot()
                DataCacheManager.saveGraphSnapshot(snap)
            }
            
            // 构建图数据
            buildGraphDataAsync(routes, stations, directTrains, crossLineTrains)
            
            updateProgress("数据初始化完成", 100)
            isInitialized.set(1)
            
            Log.d("AsyncDataLoader", "异步初始化完成")
            
        } catch (e: Exception) {
            Log.e("AsyncDataLoader", "异步初始化失败", e)
            throw e
        }
    }
    
    /**
     * 异步加载路线数据
     */
    private suspend fun loadRoutes(): List<RailwayRoute> {
        return withContext(Dispatchers.IO) {
            updateProgress("加载铁路线路...", 10)
            RealRailwayRoutes.initializeRoutes()
            val routes = RealRailwayRoutes.getAllRoutes()
            
            // 缓存路线数据
            routes.forEach { route ->
                routeCache[route.routeId] = route
            }
            
            Log.d("AsyncDataLoader", "加载了 ${routes.size} 条路线")
            routes
        }
    }
    
    /**
     * 异步加载车站数据
     */
    private suspend fun loadStations() {
        withContext(Dispatchers.IO) {
            updateProgress("加载车站数据...", 20)
            // 车站数据已经在ChinaRailwayData中，这里只是预热
            val stations = ChinaRailwayData.stations
            Log.d("AsyncDataLoader", "加载了 ${stations.size} 个车站")
        }
    }
    
    /**
     * 异步生成直达车次（按需生成策略：初始化时不生成车次）
     */
    private suspend fun generateDirectTrainsAsync(routes: List<RailwayRoute>): List<Train> {
        return withContext(Dispatchers.Default) {
            updateProgress("初始化路线图（按需生成车次）...", 40)
            
            // 不在启动时生成车次，采用按需生成策略
            // 车次将在用户查询时通过 RailwayGraphManager.findTrains() 自动生成
            val allTrains = emptyList<Train>()
            
            Log.d("AsyncDataLoader", "采用按需生成策略，初始化完成（0条预生成车次）")
            allTrains
        }
    }
    
    /**
     * 异步生成跨线车次（按需生成策略：初始化时不生成车次）
     */
    private suspend fun generateCrossLineTrainsAsync(routes: List<RailwayRoute>): List<Train> {
        return withContext(Dispatchers.Default) {
            updateProgress("准备跨线查询（按需生成）...", 60)
            
            // 不在启动时生成跨线车次，采用按需生成策略
            // 跨线车次将在用户查询时通过 generateTrainsForPair() 自动生成
            val crossLineTrains = emptyList<Train>()
            
            Log.d("AsyncDataLoader", "采用按需生成策略，跨线车次将在查询时生成")
            crossLineTrains
        }
    }
    
    /**
     * 异步构建图数据
     */
    private suspend fun buildGraphDataAsync(
        routes: List<RailwayRoute>,
        stations: Any,
        directTrains: List<Train>,
        crossLineTrains: List<Train>
    ) {
        withContext(Dispatchers.IO) {
            updateProgress("构建路线图...", 90)
            
            // 初始化图数据管理器
            RailwayGraphManager.initializeRealRoutes()
            
            Log.d("AsyncDataLoader", "图数据构建完成")
        }
    }
    
    /**
     * 为指定路线异步生成车次
     */
    private suspend fun generateTrainsForRouteAsync(route: RailwayRoute, count: Int): List<Train> {
        return withContext(Dispatchers.Default) {
            val trains = mutableListOf<Train>()
            
            for (i in 1..count) {
                val train = TrainGenerator.generateSingleTrain(route, i)
                trains.add(train)
            }
            
            trains
        }
    }
    
    /**
     * 更新进度
     */
    private fun updateProgress(status: String, progress: Int) {
        loadingProgress.set(progress)
        progressCallback?.invoke(status, progress)
        Log.d("AsyncDataLoader", "$status ($progress%)")
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized.get() == 1
    
    /**
     * 获取加载进度
     */
    fun getProgress(): Int = loadingProgress.get()
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
        routeCache.clear()
        trainCache.clear()
        crossLineTrainCache.clear()
    }
    
    /**
     * 获取缓存的路线
     */
    fun getCachedRoute(routeId: String): RailwayRoute? = routeCache[routeId]
    
    /**
     * 获取缓存的车次
     */
    fun getCachedTrains(routeId: String): List<Train>? = trainCache[routeId]
    
    /**
     * 获取缓存的跨线车次
     */
    fun getCachedCrossLineTrains(): List<Train>? = crossLineTrainCache["crossline"]
}

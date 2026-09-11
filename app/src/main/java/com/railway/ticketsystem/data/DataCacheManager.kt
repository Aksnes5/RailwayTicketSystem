package com.railway.ticketsystem.data

import android.content.Context
import android.util.Log
import com.railway.ticketsystem.model.Train
import com.railway.ticketsystem.model.RailwayRoute
import com.railway.ticketsystem.model.RailwayRouteManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * 数据缓存管理器
 * 使用文件缓存避免重复生成数据
 */
object DataCacheManager {
    
    private const val CACHE_VERSION = "v3.6_nanchang_hub"
    private const val ROUTES_CACHE_FILE = "routes_cache.dat"
    private const val TRAINS_CACHE_FILE = "trains_cache.dat"
    private const val CROSSLINE_TRAINS_CACHE_FILE = "crossline_trains_cache.dat"
    private const val GRAPH_CACHE_FILE = "graph_cache.dat"
    
    private var cacheDir: File? = null
    
    /**
     * 初始化缓存目录
     */
    fun initialize(context: Context) {
        cacheDir = File(context.cacheDir, "railway_cache")
        cacheDir?.mkdirs()
        Log.d("DataCacheManager", "缓存目录初始化: ${cacheDir?.absolutePath}")
    }
    
    /**
     * 检查缓存是否有效
     */
    fun isCacheValid(): Boolean {
        val cacheDir = this.cacheDir ?: return false
        
        val routesFile = File(cacheDir, ROUTES_CACHE_FILE)
        val trainsFile = File(cacheDir, TRAINS_CACHE_FILE)
        val crossLineTrainsFile = File(cacheDir, CROSSLINE_TRAINS_CACHE_FILE)
        val graphFile = File(cacheDir, GRAPH_CACHE_FILE)
        
        // 检查所有缓存文件是否存在且不为空
        return routesFile.exists() && routesFile.length() > 0 &&
               trainsFile.exists() && trainsFile.length() > 0 &&
               crossLineTrainsFile.exists() && crossLineTrainsFile.length() > 0 &&
               graphFile.exists() && graphFile.length() > 0
    }
    
    /**
     * 保存路线数据到缓存
     */
    suspend fun saveRoutesToCache(routes: List<RailwayRoute>) {
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = this@DataCacheManager.cacheDir ?: return@withContext
                val file = File(cacheDir, ROUTES_CACHE_FILE)
                
                ObjectOutputStream(file.outputStream()).use { oos ->
                    oos.writeObject(CACHE_VERSION)
                    oos.writeObject(routes)
                }
                
                Log.d("DataCacheManager", "路线数据已保存到缓存: ${routes.size} 条")
            } catch (e: Exception) {
                Log.e("DataCacheManager", "保存路线数据失败", e)
            }
        }
    }
    
    /**
     * 从缓存加载路线数据
     */
    suspend fun loadRoutesFromCache(): List<RailwayRoute>? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = this@DataCacheManager.cacheDir ?: return@withContext null
                val file = File(cacheDir, ROUTES_CACHE_FILE)
                
                if (!file.exists()) return@withContext null
                
                ObjectInputStream(file.inputStream()).use { ois ->
                    val version = ois.readObject() as String
                    if (version != CACHE_VERSION) {
                        Log.d("DataCacheManager", "缓存版本不匹配，需要重新生成")
                        return@withContext null
                    }
                    
                    val routes = ois.readObject() as List<RailwayRoute>
                    Log.d("DataCacheManager", "从缓存加载路线数据: ${routes.size} 条")
                    return@withContext routes
                }
            } catch (e: Exception) {
                Log.e("DataCacheManager", "加载路线数据失败", e)
                null
            }
        }
    }
    
    /**
     * 保存车次数据到缓存
     */
    suspend fun saveTrainsToCache(trains: List<Train>) {
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = this@DataCacheManager.cacheDir ?: return@withContext
                val file = File(cacheDir, TRAINS_CACHE_FILE)
                
                ObjectOutputStream(file.outputStream()).use { oos ->
                    oos.writeObject(CACHE_VERSION)
                    oos.writeObject(trains)
                }
                
                Log.d("DataCacheManager", "车次数据已保存到缓存: ${trains.size} 条")
            } catch (e: Exception) {
                Log.e("DataCacheManager", "保存车次数据失败", e)
            }
        }
    }
    
    /**
     * 从缓存加载车次数据
     */
    suspend fun loadTrainsFromCache(): List<Train>? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = this@DataCacheManager.cacheDir ?: return@withContext null
                val file = File(cacheDir, TRAINS_CACHE_FILE)
                
                if (!file.exists()) return@withContext null
                
                ObjectInputStream(file.inputStream()).use { ois ->
                    val version = ois.readObject() as String
                    if (version != CACHE_VERSION) {
                        Log.d("DataCacheManager", "缓存版本不匹配，需要重新生成")
                        return@withContext null
                    }
                    
                    val trains = ois.readObject() as List<Train>
                    Log.d("DataCacheManager", "从缓存加载车次数据: ${trains.size} 条")
                    return@withContext trains
                }
            } catch (e: Exception) {
                Log.e("DataCacheManager", "加载车次数据失败", e)
                null
            }
        }
    }
    
    /**
     * 保存跨线车次数据到缓存
     */
    suspend fun saveCrossLineTrainsToCache(trains: List<Train>) {
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = this@DataCacheManager.cacheDir ?: return@withContext
                val file = File(cacheDir, CROSSLINE_TRAINS_CACHE_FILE)
                
                ObjectOutputStream(file.outputStream()).use { oos ->
                    oos.writeObject(CACHE_VERSION)
                    oos.writeObject(trains)
                }
                
                Log.d("DataCacheManager", "跨线车次数据已保存到缓存: ${trains.size} 条")
            } catch (e: Exception) {
                Log.e("DataCacheManager", "保存跨线车次数据失败", e)
            }
        }
    }
    
    /**
     * 保存图快照
     */
    suspend fun saveGraphSnapshot(snapshot: RailwayRouteManager.GraphSnapshot) {
        withContext(Dispatchers.IO) {
            try {
                val cacheDir = this@DataCacheManager.cacheDir ?: return@withContext
                val file = File(cacheDir, GRAPH_CACHE_FILE)
                ObjectOutputStream(file.outputStream()).use { oos ->
                    oos.writeObject(CACHE_VERSION)
                    oos.writeObject(snapshot)
                }
                Log.d("DataCacheManager", "图快照已保存")
            } catch (e: Exception) {
                Log.e("DataCacheManager", "保存图快照失败", e)
            }
        }
    }

    /**
     * 加载图快照
     */
    suspend fun loadGraphSnapshot(): RailwayRouteManager.GraphSnapshot? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = this@DataCacheManager.cacheDir ?: return@withContext null
                val file = File(cacheDir, GRAPH_CACHE_FILE)
                if (!file.exists()) return@withContext null
                ObjectInputStream(file.inputStream()).use { ois ->
                    val version = ois.readObject() as String
                    if (version != CACHE_VERSION) return@withContext null
                    return@withContext ois.readObject() as RailwayRouteManager.GraphSnapshot
                }
            } catch (e: Exception) {
                Log.e("DataCacheManager", "加载图快照失败", e)
                null
            }
        }
    }

    /**
     * 从缓存加载跨线车次数据
     */
    suspend fun loadCrossLineTrainsFromCache(): List<Train>? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheDir = this@DataCacheManager.cacheDir ?: return@withContext null
                val file = File(cacheDir, CROSSLINE_TRAINS_CACHE_FILE)
                
                if (!file.exists()) return@withContext null
                
                ObjectInputStream(file.inputStream()).use { ois ->
                    val version = ois.readObject() as String
                    if (version != CACHE_VERSION) {
                        Log.d("DataCacheManager", "缓存版本不匹配，需要重新生成")
                        return@withContext null
                    }
                    
                    val trains = ois.readObject() as List<Train>
                    Log.d("DataCacheManager", "从缓存加载跨线车次数据: ${trains.size} 条")
                    return@withContext trains
                }
            } catch (e: Exception) {
                Log.e("DataCacheManager", "加载跨线车次数据失败", e)
                null
            }
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        try {
            val cacheDir = this.cacheDir ?: return
            
            val files = listOf(
                File(cacheDir, ROUTES_CACHE_FILE),
                File(cacheDir, TRAINS_CACHE_FILE),
                File(cacheDir, CROSSLINE_TRAINS_CACHE_FILE)
            )
            
            files.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
            
            Log.d("DataCacheManager", "缓存已清理")
        } catch (e: Exception) {
            Log.e("DataCacheManager", "清理缓存失败", e)
        }
    }
    
    /**
     * 获取缓存大小
     */
    fun getCacheSize(): Long {
        val cacheDir = this.cacheDir ?: return 0
        
        val files = listOf(
            File(cacheDir, ROUTES_CACHE_FILE),
            File(cacheDir, TRAINS_CACHE_FILE),
            File(cacheDir, CROSSLINE_TRAINS_CACHE_FILE)
        )
        
        return files.sumOf { if (it.exists()) it.length() else 0L }
    }
}




package com.railway.ticketsystem.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.RouteInfoAdapter
import com.railway.ticketsystem.data.StationImportManager
import com.railway.ticketsystem.databinding.ActivityStationImportBinding
import com.railway.ticketsystem.model.RouteType

class StationImportActivity : ImmersiveActivity() {
    
    private lateinit var binding: ActivityStationImportBinding
    private lateinit var routeAdapter: RouteInfoAdapter
    private var allRoutes: List<StationImportManager.RouteInfo> = emptyList()
    private var selectedRoutes: MutableSet<String> = mutableSetOf()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStationImportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupRecyclerView()
        loadRouteData()
    }
    
    private fun setupUI() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "导入车站"
        
        binding.btnSelectAll.setOnClickListener {
            selectAllRoutes()
        }
        
        binding.btnClearSelection.setOnClickListener {
            clearSelection()
        }
        
        binding.btnImportStations.setOnClickListener {
            importSelectedStations()
        }
        
        binding.btnPreviewImport.setOnClickListener {
            previewImport()
        }
    }
    
    private fun setupRecyclerView() {
        routeAdapter = RouteInfoAdapter { routeId, isSelected ->
            if (isSelected) {
                selectedRoutes.add(routeId)
            } else {
                selectedRoutes.remove(routeId)
            }
            updateImportButton()
        }
        
        binding.rvRoutes.apply {
            layoutManager = LinearLayoutManager(this@StationImportActivity)
            adapter = routeAdapter
        }
    }
    
    private fun loadRouteData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.rvRoutes.visibility = View.GONE
        
        try {
            allRoutes = StationImportManager.getAllRouteInfo()
            routeAdapter.updateRoutes(allRoutes)
            
            binding.progressBar.visibility = View.GONE
            binding.rvRoutes.visibility = View.VISIBLE
            
            if (allRoutes.isEmpty()) {
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = "暂无可用线路"
            } else {
                binding.tvEmptyState.visibility = View.GONE
            }
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.tvEmptyState.text = "加载线路数据失败: ${e.message}"
        }
    }
    
    private fun selectAllRoutes() {
        selectedRoutes.clear()
        selectedRoutes.addAll(allRoutes.map { it.routeId })
        routeAdapter.updateSelection(selectedRoutes)
        updateImportButton()
    }
    
    private fun clearSelection() {
        selectedRoutes.clear()
        routeAdapter.updateSelection(selectedRoutes)
        updateImportButton()
    }
    
    private fun updateImportButton() {
        val selectedCount = selectedRoutes.size
        binding.btnImportStations.isEnabled = selectedCount > 0
        binding.btnImportStations.text = "导入车站 ($selectedCount)"
        binding.btnPreviewImport.isEnabled = selectedCount > 0
    }
    
    private fun previewImport() {
        if (selectedRoutes.isEmpty()) {
            Toast.makeText(this, "请先选择要导入的线路", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedRouteNames = allRoutes
            .filter { it.routeId in selectedRoutes }
            .map { it.routeName }
        
        val stationsToImport = mutableListOf<com.railway.ticketsystem.model.Station>()
        selectedRoutes.forEach { routeId ->
            val route = allRoutes.find { it.routeId == routeId }
            if (route != null) {
                val stations = StationImportManager.extractStationsFromRoute(routeId)
                stationsToImport.addAll(stations)
            }
        }
        
        val newStations = StationImportManager.getStationsToImport(stationsToImport)
        val duplicateStations = StationImportManager.getDuplicateStations(stationsToImport)
        
        val message = buildString {
            appendLine("预览导入结果：")
            appendLine("选择线路：${selectedRouteNames.joinToString("、")}")
            appendLine("总车站数：${stationsToImport.size}")
            appendLine("新车站：${newStations.size}")
            appendLine("重复车站：${duplicateStations.size}")
            if (newStations.isNotEmpty()) {
                appendLine("\n新车站列表：")
                newStations.take(10).forEach { station ->
                    appendLine("• ${station.name} (${station.code})")
                }
                if (newStations.size > 10) {
                    appendLine("• ... 还有 ${newStations.size - 10} 个车站")
                }
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("导入预览")
            .setMessage(message)
            .setPositiveButton("确认导入") { _, _ ->
                performImport(stationsToImport)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun importSelectedStations() {
        if (selectedRoutes.isEmpty()) {
            Toast.makeText(this, "请先选择要导入的线路", Toast.LENGTH_SHORT).show()
            return
        }
        
        val stationsToImport = mutableListOf<com.railway.ticketsystem.model.Station>()
        selectedRoutes.forEach { routeId ->
            val stations = StationImportManager.extractStationsFromRoute(routeId)
            stationsToImport.addAll(stations)
        }
        
        performImport(stationsToImport)
    }
    
    private fun performImport(stations: List<com.railway.ticketsystem.model.Station>) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnImportStations.isEnabled = false
        
        try {
            val result = StationImportManager.importStations(stations)
            
            binding.progressBar.visibility = View.GONE
            binding.btnImportStations.isEnabled = true
            
            val message = if (result.success) {
                "导入完成！\n${result.message}"
            } else {
                "导入失败：\n${result.message}"
            }
            
            AlertDialog.Builder(this)
                .setTitle(if (result.success) "导入成功" else "导入失败")
                .setMessage(message)
                .setPositiveButton("确定") { _, _ ->
                    if (result.success) {
                        // 刷新数据
                        loadRouteData()
                        clearSelection()
                    }
                }
                .show()
                
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            binding.btnImportStations.isEnabled = true
            
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}






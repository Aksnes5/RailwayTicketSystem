package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.StationImportManager
import com.railway.ticketsystem.databinding.ItemRouteInfoBinding
import com.railway.ticketsystem.model.RouteType

class RouteInfoAdapter(
    private val onRouteSelectionChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<RouteInfoAdapter.RouteInfoViewHolder>() {
    
    private var routes: List<StationImportManager.RouteInfo> = emptyList()
    private var selectedRoutes: Set<String> = emptySet()
    
    fun updateRoutes(newRoutes: List<StationImportManager.RouteInfo>) {
        routes = newRoutes
        notifyDataSetChanged()
    }
    
    fun updateSelection(newSelection: Set<String>) {
        selectedRoutes = newSelection
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteInfoViewHolder {
        val binding = ItemRouteInfoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RouteInfoViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: RouteInfoViewHolder, position: Int) {
        holder.bind(routes[position])
    }
    
    override fun getItemCount(): Int = routes.size
    
    inner class RouteInfoViewHolder(
        private val binding: ItemRouteInfoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(route: StationImportManager.RouteInfo) {
            binding.apply {
                tvRouteName.text = route.routeName
                tvRouteId.text = "ID: ${route.routeId}"
                tvStationCount.text = "${route.stationCount} 个车站"
                tvRouteType.text = getRouteTypeText(route.routeType)
                tvRouteStations.text = "${route.startStation} → ${route.endStation}"
                
                cbSelect.isChecked = route.routeId in selectedRoutes
                
                cbSelect.setOnCheckedChangeListener { _, isChecked ->
                    onRouteSelectionChanged(route.routeId, isChecked)
                }
                
                root.setOnClickListener {
                    cbSelect.isChecked = !cbSelect.isChecked
                }
            }
        }
        
        private fun getRouteTypeText(routeType: RouteType): String {
            return when (routeType) {
                RouteType.HIGH_SPEED -> "高速铁路"
                RouteType.INTERCITY -> "城际铁路"
                RouteType.CONVENTIONAL -> "普通铁路"
            }
        }
    }
}






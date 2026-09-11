package com.railway.ticketsystem.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.railway.ticketsystem.R
import com.railway.ticketsystem.model.Station

class StationAdapter(
    private val onStationClick: (Station) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    private var stations: List<Station> = emptyList()
    private var groupedStations: Map<Char, List<Station>> = emptyMap()
    
    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_STATION = 1
    }
    
    fun updateStations(stations: List<Station>) {
        this.stations = stations
        this.groupedStations = stations.groupBy { station ->
            // 获取车站名称的拼音首字母
            getPinyinFirstLetter(station.name)
        }
        notifyDataSetChanged()
    }
    
    private fun getPinyinFirstLetter(name: String): Char {
        // 简化的拼音首字母映射，只覆盖实际存在的车站
        return when (name.first()) {
            // B组
            '北' -> 'B'  // 北京南、北京西、北京北
            '包' -> 'B'  // 包头
            '蚌' -> 'B'  // 蚌埠南
            
            // C组
            '成' -> 'C'  // 成都东、成都南
            '重' -> 'C'  // 重庆北、重庆西
            '长' -> 'C'  // 长沙南、长春西
            '常' -> 'C'  // 常州北
            '滁' -> 'C'  // 滁州
            '苍' -> 'C'  // 苍南
            
            // D组
            '大' -> 'D'  // 大连北、大理、大冶北
            '德' -> 'D'  // 德安
            
            // E组
            '鄂' -> 'E'  // 鄂州
            
            // F组
            '福' -> 'F'  // 福州、福州南、福清、福鼎
            '抚' -> 'F'  // 抚州东
            
            // G组
            '广' -> 'G'  // 广州南、广州东
            '贵' -> 'G'  // 贵阳北
            '桂' -> 'G'  // 桂林北
            
            // H组
            '汉' -> 'H'  // 汉口、汉川、汉川北
            '哈' -> 'H'  // 哈尔滨西
            '杭' -> 'H'  // 杭州东、杭州南
            '合' -> 'H'  // 合肥南
            '海' -> 'H'  // 海口东
            '湖' -> 'H'  // 湖州
            '惠' -> 'H'  // 惠安
            '衡' -> 'H'  // 衡阳东
            '黄' -> 'H'  // 黄石北
            '呼' -> 'H'  // 呼和浩特东
            
            // J组
            '济' -> 'J'  // 济南西
            '京' -> 'J'  // 京山南
            '荆' -> 'J'  // 荆门西、荆州
            '金' -> 'J'  // 金华
            '九' -> 'J'  // 九江
            '晋' -> 'J'  // 晋江
            '建' -> 'J'  // 建瓯西
            '进' -> 'J'  // 进贤南
            '嘉' -> 'J'  // 嘉兴南
            
            // K组
            '昆' -> 'K'  // 昆明南
            
            // L组
            '丽' -> 'L'  // 丽江、丽水
            '连' -> 'L'  // 连江
            '龙' -> 'L'  // 龙岩
            '柳' -> 'L'  // 柳州
            '庐' -> 'L'  // 庐山
            '兰' -> 'L'  // 兰州西
            '拉' -> 'L'  // 拉萨
            
            // M组
            '马' -> 'M'  // 马鞍山东
            
            // N组
            '南' -> 'N'  // 南京南、南宁东、南昌西、南昌、南平北
            '宁' -> 'N'  // 宁波、宁德
            
            // P组
            '平' -> 'P'  // 平阳
            '莆' -> 'P'  // 莆田
            
            // Q组
            '青' -> 'Q'  // 青岛北
            '潜' -> 'Q'  // 潜江
            '衢' -> 'Q'  // 衢州
            '泉' -> 'Q'  // 泉州
            '齐' -> 'Q'  // 齐齐哈尔南
            
            // R组
            '瑞' -> 'R'  // 瑞安、瑞昌西
            
            // S组
            '上' -> 'S'  // 上海虹桥、上海南
            '深' -> 'S'  // 深圳北
            '沈' -> 'S'  // 沈阳北、沈阳南
            '石' -> 'S'  // 石家庄、石家庄北
            '苏' -> 'S'  // 苏州北
            '三' -> 'S'  // 三亚、三明北
            '绍' -> 'S'  // 绍兴北
            
            // T组
            '天' -> 'T'  // 天津西、天津南、天门、天门南
            '太' -> 'T'  // 太原南、太姥山
            '台' -> 'T'  // 台州
            
            // W组
            '武' -> 'W'  // 武汉、武昌
            '无' -> 'W'  // 无锡东
            '温' -> 'W'  // 温州南、温州
            '芜' -> 'W'  // 芜湖
            '乌' -> 'W'  // 乌鲁木齐
            
            // X组
            '西' -> 'X'  // 西安北、西宁
            '仙' -> 'X'  // 仙桃西
            '徐' -> 'X'  // 徐州东
            '宣' -> 'X'  // 宣城
            '厦' -> 'X'  // 厦门北、厦门
            
            // Y组
            '宜' -> 'Y'  // 宜昌北、宜昌东
            '烟' -> 'Y'  // 烟台南
            '银' -> 'Y'  // 银川
            '阳' -> 'Y'  // 阳新
            '永' -> 'Y'  // 永修
            '鹰' -> 'Y'  // 鹰潭北
            '岳' -> 'Y'  // 岳阳东
            
            // Z组
            '郑' -> 'Z'  // 郑州东
            '钟' -> 'Z'  // 钟祥南
            '镇' -> 'Z'  // 镇江
            '漳' -> 'Z'  // 漳州
            '中' -> 'Z'  // 中山北
            '珠' -> 'Z'  // 珠海
            '湛' -> 'Z'  // 湛江西
            '株' -> 'Z'  // 株洲西
            
            else -> 'A'  // 其他未分类的车站归入A组
        }
    }
    
    override fun getItemViewType(position: Int): Int {
        var currentPosition = 0
        for ((letter, stationList) in groupedStations.toSortedMap()) {
            if (currentPosition == position) {
                return TYPE_HEADER
            }
            currentPosition++
            if (position < currentPosition + stationList.size) {
                return TYPE_STATION
            }
            currentPosition += stationList.size
        }
        return TYPE_STATION
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_station_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_station, parent, false)
                StationViewHolder(view)
            }
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                val letter = getLetterAtPosition(position)
                holder.bind(letter)
            }
            is StationViewHolder -> {
                val station = getStationAtPosition(position)
                holder.bind(station)
            }
        }
    }
    
    private fun getLetterAtPosition(position: Int): Char {
        var currentPosition = 0
        for ((letter, _) in groupedStations.toSortedMap()) {
            if (currentPosition == position) {
                return letter
            }
            currentPosition++
            val stationCount = groupedStations[letter]?.size ?: 0
            if (position < currentPosition + stationCount) {
                return letter
            }
            currentPosition += stationCount
        }
        return 'A'
    }
    
    private fun getStationAtPosition(position: Int): Station {
        var currentPosition = 0
        for ((letter, stationList) in groupedStations.toSortedMap()) {
            currentPosition++ // 跳过header
            if (position < currentPosition + stationList.size) {
                return stationList[position - currentPosition]
            }
            currentPosition += stationList.size
        }
        return stations.first()
    }
    
    override fun getItemCount(): Int {
        return groupedStations.values.sumOf { it.size } + groupedStations.size
    }
    
    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLetter: TextView = itemView.findViewById(R.id.tvLetter)
        
        fun bind(letter: Char) {
            tvLetter.text = letter.toString()
        }
    }
    
    inner class StationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvStationName: TextView = itemView.findViewById(R.id.tvStationName)
        
        fun bind(station: Station) {
            tvStationName.text = station.name
            
            itemView.setOnClickListener {
                onStationClick(station)
            }
        }
    }
}

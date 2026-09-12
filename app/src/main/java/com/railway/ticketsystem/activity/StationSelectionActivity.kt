package com.railway.ticketsystem.activity

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.StationDirectoryAdapter
import com.railway.ticketsystem.data.RailwayData
import com.railway.ticketsystem.databinding.ActivityStationSelectionBinding
import com.railway.ticketsystem.model.Station

class StationSelectionActivity : ImmersiveActivity() {
    
    private lateinit var binding: ActivityStationSelectionBinding
    private lateinit var stationAdapter: StationDirectoryAdapter
    private lateinit var searchAdapter: StationDirectoryAdapter
    private var allStations: List<Station> = emptyList()
    private var hotStations: List<Station> = emptyList()
    private var lastAlphabetIndex = -1
    private val alphabet = ('A'..'Z').toList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStationSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupData()
        setupUI()
        setupSearch()
        setupRecyclerViews()
        setupHotStations()
    }
    
    private fun setupData() {
        allStations = RailwayData.stations.distinctBy { it.name }
        hotStations = RailwayData.getHotStations()
    }
    
    private fun setupUI() {
        binding.btnBackStation.setOnClickListener { finish() }
        binding.tvAlphabetIndex.text = alphabet.joinToString("\n")
        binding.tvAlphabetIndex.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    blockAncestorInterception(view, true)
                    val usableHeight = (view.height - view.paddingTop - view.paddingBottom).coerceAtLeast(1)
                    val y = (event.y - view.paddingTop).coerceIn(0f, usableHeight.toFloat())
                    val index = (y * alphabet.size / usableHeight).toInt().coerceIn(0, alphabet.lastIndex)
                    if (index != lastAlphabetIndex) {
                        lastAlphabetIndex = index
                        val letter = alphabet[index]
                        stationAdapter.scrollToSection(letter, binding.rvAllStations)
                        binding.tvAlphabetPreview.text = letter.toString()
                        binding.tvAlphabetPreview.visibility = View.VISIBLE
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    blockAncestorInterception(view, false)
                    lastAlphabetIndex = -1
                    binding.tvAlphabetPreview.postDelayed(
                        { binding.tvAlphabetPreview.visibility = View.GONE },
                        450L
                    )
                    true
                }
                else -> true
            }
        }
    }
    
    
    private fun blockAncestorInterception(view: View, disallow: Boolean) {
        var parent = view.parent
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow)
            parent = parent.parent
        }
    }

    private fun setupSearch() {
        binding.etSearchStation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    showSearchResults(query)
                } else {
                    hideSearchResults()
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun setupRecyclerViews() {
        // 全部车站列表
        stationAdapter = StationDirectoryAdapter { station ->
            selectStation(station)
        }
        binding.rvAllStations.apply {
            layoutManager = LinearLayoutManager(this@StationSelectionActivity)
            adapter = stationAdapter
        }
        stationAdapter.updateStations(allStations)
        
        // 搜索结果列表
        searchAdapter = StationDirectoryAdapter { station ->
            selectStation(station)
        }
        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(this@StationSelectionActivity)
            adapter = searchAdapter
        }
    }
    
    private fun setupHotStations() {
        binding.gridHotStations.removeAllViews()
        
        for (station in hotStations) {
            val button = MaterialButton(this)
            button.text = station.name
            button.textSize = 14f
            button.setPadding(16, 12, 16, 12)
            // 热门站独立采用白底黑字，避免继承全局的蓝色弱操作按钮样式。
            button.backgroundTintList = ColorStateList.valueOf(getColor(R.color.white))
            button.setTextColor(getColor(R.color.black))
            button.strokeColor = ColorStateList.valueOf(getColor(R.color.button_stroke))
            button.strokeWidth = resources.getDimensionPixelSize(R.dimen.border_thin)
            button.cornerRadius = resources.getDimensionPixelSize(R.dimen.corner_large)
            
            val params = GridLayout.LayoutParams()
            params.width = 0
            params.height = GridLayout.LayoutParams.WRAP_CONTENT
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            params.setMargins(8, 8, 8, 8)
            button.layoutParams = params
            
            button.setOnClickListener {
                selectStation(station)
            }
            
            binding.gridHotStations.addView(button)
        }
    }
    
    private fun showSearchResults(query: String) {
        val filteredStations = RailwayData.searchStations(query)
        
        if (filteredStations.isNotEmpty()) {
            searchAdapter.updateStations(filteredStations)
            binding.llSearchResults.visibility = android.view.View.VISIBLE
        } else {
            binding.llSearchResults.visibility = android.view.View.GONE
        }
    }
    
    private fun hideSearchResults() {
        binding.llSearchResults.visibility = android.view.View.GONE
    }
    
    private fun selectStation(station: Station) {
        val result = Intent()
        result.putExtra("selectedStation", station)
        setResult(RESULT_OK, result)
        finish()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

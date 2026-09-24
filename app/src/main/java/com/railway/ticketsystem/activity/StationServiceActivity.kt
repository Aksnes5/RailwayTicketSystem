package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.railway.ticketsystem.data.StationServiceCatalog
import com.railway.ticketsystem.data.StationServiceRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityStationServiceBinding

/** Hub only selects a service; every reservation now has its own full-screen flow. */
class StationServiceActivity : ImmersiveActivity() {
    private lateinit var binding: ActivityStationServiceBinding
    private lateinit var repository: StationServiceRepository
    private lateinit var users: UserRepository
    private var station = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStationServiceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = StationServiceRepository(this)
        users = UserRepository(this)
        station = intent.getStringExtra(EXTRA_STATION).orEmpty()

        binding.cardStationServiceInlineForm.visibility = View.GONE
        binding.btnStationServiceBack.setOnClickListener { finish() }
        binding.btnServiceOrders.setOnClickListener { showMyRequests() }
        binding.cardPickupDropoff.setOnClickListener { open(StationServiceCatalog.PICKUP_DROPOFF) }
        binding.cardParking.setOnClickListener { open(StationServiceCatalog.PARKING) }
        binding.cardPriorityPassenger.setOnClickListener { open(StationServiceCatalog.PRIORITY_PASSENGER) }
        binding.cardLostFound.setOnClickListener { open(StationServiceCatalog.LOST_FOUND) }
        binding.cardLounge.setOnClickListener { open(StationServiceCatalog.LOUNGE) }
        binding.cardLuggageConsignment.setOnClickListener { open(StationServiceCatalog.LUGGAGE_CONSIGNMENT) }
        binding.cardLuggageDelivery.setOnClickListener { open(StationServiceCatalog.LUGGAGE_DELIVERY) }
        binding.cardIndoorNavigation.setOnClickListener {
            startActivity(Intent(this, IndoorNavigationActivity::class.java)
                .putExtra(IndoorNavigationActivity.EXTRA_STATION, station)
                .putExtra(IndoorNavigationActivity.EXTRA_TICKET_ORDER_ID, intent.getStringExtra(EXTRA_TICKET_ORDER_ID)))
        }
        binding.cardStationLiveService.setOnClickListener {
            startActivity(Intent(this, StationFacilityActivity::class.java)
                .putExtra(StationFacilityActivity.EXTRA_STATION, station)
                .putExtra(StationFacilityActivity.EXTRA_TICKET_ORDER_ID, intent.getStringExtra(EXTRA_TICKET_ORDER_ID)))
        }
        renderRequestCount()
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) renderRequestCount()
    }

    private fun open(service: String) {
        startActivity(StationServiceDetailActivity.intent(this, service, station, intent.getStringExtra(EXTRA_TICKET_ORDER_ID)))
    }

    private fun renderRequestCount() {
        val count = users.getCurrentUser()?.let { repository.getByUser(it.id).size } ?: 0
        binding.btnServiceOrders.text = if (count == 0) "我的服务预约" else "我的服务预约（" + count + "）"
    }

    private fun showMyRequests() {
        val user = users.getCurrentUser() ?: run {
            Toast.makeText(this, "请先登录后查看服务预约", Toast.LENGTH_SHORT).show()
            return
        }
        val records = repository.getByUser(user.id)
        val message = if (records.isEmpty()) "暂未提交车站服务。选择服务项目后可进入独立预约页面。" else {
            records.take(8).joinToString("\n\n") { it.serviceType + " · " + it.status + "\n" + it.station + if (it.schedule.isBlank()) "" else " · " + it.schedule + "\n" + it.createdAt + " · " + it.id }
        }
        AlertDialog.Builder(this).setTitle("我的服务预约").setMessage(message).setPositiveButton("知道了", null).show()
    }

    companion object {
        const val EXTRA_STATION = "station_service_station"
        const val EXTRA_TICKET_ORDER_ID = "station_service_ticket_order_id"
    }
}


package com.railway.ticketsystem.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.MessageRepository
import com.railway.ticketsystem.data.StationServiceRepository
import com.railway.ticketsystem.data.StationServiceRequest
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityStationServiceBinding
import com.railway.ticketsystem.model.Station

/** Station-side services that complete the ticket, meal and hotel travel chain. */
class StationServiceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStationServiceBinding
    private lateinit var serviceRepository: StationServiceRepository
    private lateinit var userRepository: UserRepository
    private var selectedService: ServiceDefinition? = null

    private val stationPicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val station = result.data?.getSerializableExtra("selectedStation") as? Station
        if (result.resultCode == RESULT_OK && station != null) {
            binding.actServiceStation.setText(station.name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStationServiceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = getColor(R.color.railway_blue)
        serviceRepository = StationServiceRepository(this)
        userRepository = UserRepository(this)

        binding.btnStationServiceBack.setOnClickListener { finish() }
        binding.actServiceStation.setOnClickListener { stationPicker.launch(Intent(this, StationSelectionActivity::class.java)) }
        binding.btnServiceOrders.setOnClickListener { showMyRequests() }
        binding.btnSubmitStationService.setOnClickListener { submitService() }

        binding.cardPickupDropoff.setOnClickListener { selectService(PICKUP_DROPOFF) }
        binding.cardParking.setOnClickListener { selectService(PARKING) }
        binding.cardIndoorNavigation.setOnClickListener { selectService(NAVIGATION) }
        binding.cardPriorityPassenger.setOnClickListener { selectService(PRIORITY_PASSENGER) }
        binding.cardLostFound.setOnClickListener { selectService(LOST_FOUND) }
        binding.cardLounge.setOnClickListener { selectService(LOUNGE) }

        binding.actServiceStation.setText(intent.getStringExtra(EXTRA_STATION).orEmpty())
        val user = userRepository.getCurrentUser()
        if (user != null) {
            binding.etServiceContact.setText(listOf(user.realName.ifBlank { user.username }, user.phone)
                .filter { it.isNotBlank() }.joinToString(" · "))
        }
        renderRequestCount()
    }

    private fun selectService(service: ServiceDefinition) {
        selectedService = service
        binding.tvServiceFormTitle.text = service.label
        binding.tvServiceFormDescription.text = service.description
        binding.tilServiceSchedule.hint = service.scheduleHint
        binding.etServiceDetails.hint = service.detailHint
        binding.tvServiceNotice.text = service.notice
        binding.btnSubmitStationService.isEnabled = true
        binding.btnSubmitStationService.text = service.submitLabel
        binding.tvServiceResult.visibility = View.GONE
    }

    private fun submitService() {
        val service = selectedService ?: return
        val station = binding.actServiceStation.text?.toString()?.trim().orEmpty()
        if (station.isBlank()) {
            Toast.makeText(this, "请先选择服务车站", Toast.LENGTH_SHORT).show()
            return
        }
        val schedule = binding.etServiceSchedule.text?.toString()?.trim().orEmpty()
        val contact = binding.etServiceContact.text?.toString()?.trim().orEmpty()
        val details = binding.etServiceDetails.text?.toString()?.trim().orEmpty()
        if (service.requiresDetail && details.isBlank()) {
            Toast.makeText(this, "请补充${service.detailHint}", Toast.LENGTH_SHORT).show()
            return
        }
        val user = userRepository.getCurrentUser()
        if (user == null) {
            Toast.makeText(this, "请先登录后提交车站服务", Toast.LENGTH_SHORT).show()
            return
        }
        val status = if (service == NAVIGATION) "导航已生成" else "已提交"
        val request = StationServiceRepository.newRequest(
            userId = user.id,
            serviceType = service.label,
            station = station,
            schedule = schedule,
            contact = contact.ifBlank { user.phone.ifBlank { user.realName.ifBlank { user.username } } },
            details = details,
            status = status
        )
        if (!serviceRepository.save(request)) {
            Toast.makeText(this, "服务提交失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        MessageRepository(this).add(
            user.id,
            MessageRepository.TRAVEL,
            "${service.label}已${if (service == NAVIGATION) "生成" else "提交"}",
            "${station} · ${service.confirmation(schedule, details)}",
            eventKey = "station_service:${request.id}"
        )
        binding.tvServiceResult.text = if (service == NAVIGATION) {
            "已生成站内路线：${station}进站口 → 安检区 → 候车区 → 检票口"
        } else {
            "已提交 ${service.label}，服务编号 ${request.id}，可在“我的服务预约”查看。"
        }
        binding.tvServiceResult.visibility = View.VISIBLE
        renderRequestCount()
        Toast.makeText(this, if (service == NAVIGATION) "站内导航已生成" else "服务已提交", Toast.LENGTH_SHORT).show()
    }

    private fun renderRequestCount() {
        val user = userRepository.getCurrentUser()
        val count = user?.let { serviceRepository.getByUser(it.id).size } ?: 0
        binding.btnServiceOrders.text = if (count == 0) "我的服务预约" else "我的服务预约（$count）"
    }

    private fun showMyRequests() {
        val user = userRepository.getCurrentUser()
        if (user == null) {
            Toast.makeText(this, "请先登录后查看服务预约", Toast.LENGTH_SHORT).show()
            return
        }
        val requests = serviceRepository.getByUser(user.id)
        val message = if (requests.isEmpty()) {
            "暂未提交车站服务。选择服务项目并填写信息后，预约会保存在这里。"
        } else {
            requests.take(8).joinToString("\n\n") { request -> request.summary() }
        }
        AlertDialog.Builder(this)
            .setTitle("我的服务预约")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun StationServiceRequest.summary(): String = buildString {
        append(serviceType).append(" · ").append(status).append("\n")
        append(station)
        if (schedule.isNotBlank()) append(" · ").append(schedule)
        append("\n").append(createdAt).append(" · ").append(id)
    }

    private data class ServiceDefinition(
        val label: String,
        val description: String,
        val scheduleHint: String,
        val detailHint: String,
        val notice: String,
        val submitLabel: String,
        val requiresDetail: Boolean = false
    ) {
        fun confirmation(schedule: String, details: String): String = when {
            schedule.isNotBlank() -> schedule
            details.isNotBlank() -> details.take(24)
            else -> "工作人员将按预留联系方式确认"
        }
    }

    companion object {
        const val EXTRA_STATION = "station_service_station"

        private val PICKUP_DROPOFF = ServiceDefinition(
            "接送站", "预约接站或送站用车，提前确认服务时间与车辆信息。",
            "接站/送站时间，例如 9月12日 08:30", "接站或送站、乘车人数、行李情况",
            "提交后将由服务人员通过预留联系方式确认。", "提交接送站预约"
        )
        private val PARKING = ServiceDefinition(
            "停车预约", "提前登记车辆，查询车站停车场余位和入场指引。",
            "预计入场时间", "车牌号码、车型、预计停车时长",
            "车位以到场时实际情况为准，请按引导标识入场。", "提交停车预约", true
        )
        private val NAVIGATION = ServiceDefinition(
            "站内导航", "根据车站生成从进站口到候车、检票区域的推荐步行路线。",
            "检票口或目标区域（可选）", "无障碍、电梯或母婴室等路线偏好",
            "导航仅作站内指引，请以现场标识与工作人员提示为准。", "生成站内导航"
        )
        private val PRIORITY_PASSENGER = ServiceDefinition(
            "重点旅客服务", "为老幼病残孕等重点旅客预约进站、候车、乘降协助。",
            "预计到站时间 / 车次", "需要的协助、同行人数、辅助器具情况",
            "请至少提前 2 小时提交；现场工作人员将按实际条件提供协助。", "提交重点旅客服务", true
        )
        private val LOST_FOUND = ServiceDefinition(
            "遗失物登记", "登记遗失物特征与地点，便于车站失物招领核查联系。",
            "遗失时间 / 可能地点", "物品名称、颜色、品牌、特征及可证明信息",
            "请勿填写银行卡完整号码、密码等敏感信息。", "提交遗失物登记", true
        )
        private val LOUNGE = ServiceDefinition(
            "贵宾厅预约", "预约高铁贵宾候车区，享受候车休息、餐饮与专属引导服务。",
            "到厅时间 / 关联车次", "使用人数、是否使用贵宾候车厅券",
            "贵宾厅开放及席位以车站当日安排为准。", "提交贵宾厅预约"
        )
    }
}

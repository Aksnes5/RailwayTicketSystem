package com.railway.ticketsystem.activity

import android.app.AlertDialog
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.railway.ticketsystem.R
import com.railway.ticketsystem.adapter.PassengerAdapter
import com.railway.ticketsystem.data.PassengerRepository
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityPassengerManageBinding
import com.railway.ticketsystem.databinding.DialogEditPassengerBinding
import com.railway.ticketsystem.model.Passenger

class PassengerManageActivity : ImmersiveActivity() {

    private lateinit var binding: ActivityPassengerManageBinding
    private lateinit var passengerAdapter: PassengerAdapter
    private lateinit var passengerRepository: PassengerRepository
    private lateinit var userRepository: UserRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPassengerManageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userRepository = UserRepository(this)
        passengerRepository = PassengerRepository(this)

        setupUI()
        setupRecyclerView()
        loadPassengers()
    }

    private fun setupUI() {
        // 设置返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 设置添加按钮
        binding.btnAddPassenger.setOnClickListener {
            showEditPassengerDialog()
        }
    }

    private fun setupRecyclerView() {
        passengerAdapter = PassengerAdapter(
            onPassengerClick = { passenger ->
                showEditPassengerDialog(passenger)
            },
            onEditClick = { passenger ->
                showEditPassengerDialog(passenger)
            },
            onDeleteClick = { passenger ->
                showDeleteConfirmDialog(passenger)
            }
        )

        binding.rvPassengers.apply {
            layoutManager = LinearLayoutManager(this@PassengerManageActivity)
            adapter = passengerAdapter
        }
    }

    private fun loadPassengers() {
        val currentUser = userRepository.getCurrentUser()
        if (currentUser != null) {
            val passengers = passengerRepository.getPassengersByUserId(currentUser.id)
            if (passengers.isNotEmpty()) {
                passengerAdapter.updatePassengers(passengers)
                binding.rvPassengers.visibility = android.view.View.VISIBLE
                binding.llEmptyState.visibility = android.view.View.GONE
            } else {
                binding.rvPassengers.visibility = android.view.View.GONE
                binding.llEmptyState.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun showEditPassengerDialog(passenger: Passenger? = null) {
        val dialogBinding = DialogEditPassengerBinding.inflate(LayoutInflater.from(this))
        dialogBinding.tvPassengerDialogTitle.text = if (passenger == null) "添加乘车人" else "编辑乘车人"
        
        // 如果是编辑模式，填充现有数据
        if (passenger != null) {
            dialogBinding.etPassengerName.setText(passenger.name)
            dialogBinding.etPassengerIdCard.setText(passenger.idCard)
            dialogBinding.etPassengerPhone.setText(passenger.phone)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.setOnShowListener {
            dialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                setLayout((resources.displayMetrics.widthPixels * 0.90f).toInt(), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
            }
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etPassengerName.text.toString().trim()
            val idCard = dialogBinding.etPassengerIdCard.text.toString().trim()
            val phone = dialogBinding.etPassengerPhone.text.toString().trim()

            if (name.isEmpty() || idCard.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (idCard.length != 18) {
                Toast.makeText(this, "身份证号格式不正确", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (phone.length != 11) {
                Toast.makeText(this, "手机号格式不正确", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = userRepository.getCurrentUser()
            if (currentUser != null) {
                val passengerToSave = if (passenger != null) {
                    passenger.copy(name = name, idCard = idCard, phone = phone)
                } else {
                    Passenger(
                        id = "PASSENGER_${System.currentTimeMillis()}",
                        userId = currentUser.id,
                        name = name,
                        idCard = idCard,
                        phone = phone,
                        addTime = System.currentTimeMillis().toString()
                    )
                }

                if (passenger != null) {
                    passengerRepository.updatePassenger(passengerToSave)
                } else {
                    passengerRepository.addPassenger(passengerToSave)
                }
                loadPassengers()
                dialog.dismiss()
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showDeleteConfirmDialog(passenger: Passenger) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除乘车人 ${passenger.name} 吗？")
            .setPositiveButton("删除") { _, _ ->
                val currentUser = userRepository.getCurrentUser()
                if (currentUser != null) {
                    passengerRepository.deletePassenger(passenger.id, currentUser.id)
                    loadPassengers()
                    Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

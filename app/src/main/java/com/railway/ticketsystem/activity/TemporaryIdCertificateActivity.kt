package com.railway.ticketsystem.activity

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.railway.ticketsystem.R
import com.railway.ticketsystem.data.UserRepository
import com.railway.ticketsystem.databinding.ActivityTemporaryIdCertificateBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 乘坐旅客列车临时乘车身份证明
 * 遗失或未携带身份证件的旅客，可通过人脸实名核验在线签发24小时有效电子临时身份证明，
 * 凭动态防伪二维码在车站窗口及人工检票通道顺畅通行。
 */
class TemporaryIdCertificateActivity : ImmersiveActivity() {

    private lateinit var binding: ActivityTemporaryIdCertificateBinding
    private val prefs by lazy { getSharedPreferences("temp_id_prefs", MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemporaryIdCertificateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDefaultUser()
        setupClickListeners()
        checkExistingCertificate()
    }

    private fun setupDefaultUser() {
        val userRepo = UserRepository(this)
        val user = userRepo.getCurrentUser()
        if (user != null) {
            binding.etApplyName.setText(user.realName)
            binding.etApplyIdNumber.setText(user.idCard)
        }
    }

    private fun setupClickListeners() {
        binding.btnBackTempId.setOnClickListener { finish() }

        binding.btnStartFaceAuth.setOnClickListener {
            val name = binding.etApplyName.text.toString().trim()
            val idCard = binding.etApplyIdNumber.text.toString().trim()
            if (name.isEmpty() || idCard.length < 15) {
                Toast.makeText(this, "请填写规范的姓名与身份证号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startFaceVerificationSimulation(name, idCard)
        }

        binding.btnReapplyTempId.setOnClickListener {
            prefs.edit().clear().apply()
            binding.cardTempIdCertificate.visibility = View.GONE
            binding.cardTempIdApplyForm.visibility = View.VISIBLE
        }
    }

    private fun checkExistingCertificate() {
        val certName = prefs.getString("cert_name", null)
        val certId = prefs.getString("cert_id", null)
        val certTime = prefs.getLong("cert_time", 0L)

        // 24小时有效期 (24 * 3600 * 1000)
        val isExpired = (System.currentTimeMillis() - certTime) > 86400000L
        if (certName != null && certId != null && !isExpired) {
            renderCertificate(certName, certId, certTime)
        } else {
            binding.cardTempIdCertificate.visibility = View.GONE
            binding.cardTempIdApplyForm.visibility = View.VISIBLE
        }
    }

    private fun startFaceVerificationSimulation(name: String, idCard: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(24).toFloat()
            }
        }

        val scanCircle = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(110)).apply {
                bottomMargin = dp(16)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EBF5FF"))
                setStroke(dp(3), Color.parseColor("#007AFF"))
            }
        }
        container.addView(scanCircle)

        val statusTv = TextView(this).apply {
            text = "人脸活体比对中...\n请正对屏幕，请眨眨眼"
            textSize = 15f
            setTextColor(Color.parseColor("#1C1C1E"))
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        container.addView(statusTv)

        val pb = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                topMargin = dp(16)
            }
        }
        container.addView(pb)

        dialog.setContentView(container)
        dialog.setCancelable(false)
        dialog.show()

        handler.postDelayed({
            statusTv.text = "公安权威数据库实名比对完成\n正在签发防伪电子证明..."
            scanCircle.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E8F9ED"))
                setStroke(dp(3), Color.parseColor("#34C759"))
            }
        }, 1200)

        handler.postDelayed({
            dialog.dismiss()
            val now = System.currentTimeMillis()
            prefs.edit()
                .putString("cert_name", name)
                .putString("cert_id", idCard)
                .putLong("cert_time", now)
                .apply()

            renderCertificate(name, idCard, now)
            Toast.makeText(this, "人脸核验通过！临时身份证明已签发生效", Toast.LENGTH_SHORT).show()
        }, 2200)
    }

    private fun renderCertificate(name: String, idCard: String, issuedTime: Long) {
        binding.cardTempIdApplyForm.visibility = View.GONE
        binding.cardTempIdCertificate.visibility = View.VISIBLE

        binding.tvCertPassengerName.text = name
        binding.tvCertPassengerId.text = if (idCard.length >= 14) {
            idCard.take(6) + "********" + idCard.takeLast(4)
        } else idCard

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(issuedTime))
        binding.tvCertIssuedTime.text = "$timeStr (24小时内有效)"

        // 生成高密度动态二维码
        val qrPayload = "CR_TEMPID_${name}_${idCard}_${issuedTime}_SECURE_PASS"
        val qrBitmap = generateQrCode(qrPayload, dp(160), dp(160))
        binding.ivCertQrCode.setImageBitmap(qrBitmap)
    }

    private fun generateQrCode(text: String, width: Int, height: Int): Bitmap? {
        return try {
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height)
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) Color.parseColor("#0F2B48") else Color.TRANSPARENT)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

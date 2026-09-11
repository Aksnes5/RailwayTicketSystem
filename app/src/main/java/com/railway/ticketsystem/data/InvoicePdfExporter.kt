package com.railway.ticketsystem.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.railway.ticketsystem.model.Order
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Creates a customer-readable A4 invoice PDF and stores it in Downloads. The document
 * intentionally uses a service-voucher heading instead of reproducing an official tax form.
 */
object InvoicePdfExporter {
    private const val PAGE_WIDTH = 1240
    private const val PAGE_HEIGHT = 1754
    private const val BLUE = 0xFF2D78D7.toInt()
    private const val DARK = 0xFF1B2737.toInt()
    private const val MUTED = 0xFF667085.toInt()
    private const val LINE = 0xFFD9E2EE.toInt()

    fun export(context: Context, invoice: ElectronicInvoice, order: Order): Uri? = runCatching {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            drawInvoice(page.canvas, invoice, order)
            document.finishPage(page)
            val displayName = "铁路电子客票服务凭证_${invoice.invoiceNumber}.pdf"
            writeToDownloads(context, document, displayName)
        } finally {
            document.close()
        }
    }.getOrNull()

    private fun writeToDownloads(context: Context, document: PdfDocument, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/铁路12306/电子发票")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            return try {
                context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
                    ?: throw IllegalStateException("Unable to open PDF output")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } catch (error: Exception) {
                context.contentResolver.delete(uri, null, null)
                null
            }
        }

        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "电子发票").apply { mkdirs() }
        val file = File(folder, displayName)
        FileOutputStream(file).use { document.writeTo(it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun drawInvoice(canvas: Canvas, invoice: ElectronicInvoice, order: Order) {
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans", Typeface.NORMAL) }
        fun text(value: String, x: Float, y: Float, size: Float, color: Int = DARK, bold: Boolean = false) {
            paint.color = color
            paint.textSize = size
            paint.typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(value, x, y, paint)
        }
        fun line(y: Float) {
            paint.color = LINE
            paint.strokeWidth = 2f
            canvas.drawLine(76f, y, PAGE_WIDTH - 76f, y, paint)
        }
        fun rounded(rect: RectF, color: Int, radius: Float = 22f) {
            paint.color = color
            canvas.drawRoundRect(rect, radius, radius, paint)
        }

        rounded(RectF(0f, 0f, PAGE_WIDTH.toFloat(), 230f), BLUE, 0f)
        text("铁路电子客票", 78f, 98f, 35f, Color.WHITE, true)
        text("服务凭证", 78f, 145f, 35f, Color.WHITE, true)
        text("电子发票信息单", PAGE_WIDTH - 310f, 100f, 22f, 0xFFDCEBFF.toInt())
        text("No. ${invoice.invoiceNumber}", PAGE_WIDTH - 310f, 140f, 21f, Color.WHITE, true)

        text("铁路客运服务电子凭证", 76f, 298f, 40f, DARK, true)
        text("已完成行程 · 价税合计", 78f, 336f, 18f, MUTED)
        rounded(RectF(PAGE_WIDTH - 296f, 270f, PAGE_WIDTH - 76f, 336f), 0xFFE7F1FF.toInt())
        text("¥${money(invoice.totalCents)}", PAGE_WIDTH - 270f, 316f, 30f, BLUE, true)
        line(374f)

        section(canvas, "购方信息", 414f)
        infoRow(canvas, "抬头", invoice.buyerTitle, 460f)
        infoRow(canvas, "服务名称", invoice.serviceName, 508f)
        infoRow(canvas, "开具时间", invoice.issueDate, 556f)
        line(584f)

        section(canvas, "行程信息", 628f)
        infoRow(canvas, "车次", order.trainNumber, 674f)
        infoRow(canvas, "行程", "${order.departureStation} - ${order.arrivalStation}", 722f)
        infoRow(canvas, "乘车日期", order.departureDate, 770f)
        infoRow(canvas, "乘车人", order.passengerName, 818f)
        line(850f)

        section(canvas, "费用明细", 894f)
        rounded(RectF(76f, 920f, PAGE_WIDTH - 76f, 1060f), 0xFFF6F9FD.toInt(), 18f)
        text("项目", 104f, 961f, 18f, MUTED, true)
        paint.textAlign = Paint.Align.RIGHT
        text("金额", PAGE_WIDTH - 104f, 961f, 18f, MUTED, true)
        paint.textAlign = Paint.Align.LEFT
        text(invoice.serviceName, 104f, 1018f, 22f, DARK)
        paint.textAlign = Paint.Align.RIGHT
        text("¥${money(invoice.totalCents)}", PAGE_WIDTH - 104f, 1018f, 22f, DARK, true)
        paint.textAlign = Paint.Align.LEFT

        infoRow(canvas, "不含税金额", "¥${money(invoice.amountCents)}", 1104f)
        infoRow(canvas, "税率 / 税额", "9% / ¥${money(invoice.taxCents)}", 1152f)
        rounded(RectF(76f, 1184f, PAGE_WIDTH - 76f, 1266f), 0xFFE7F1FF.toInt(), 18f)
        text("价税合计", 104f, 1237f, 24f, DARK, true)
        paint.textAlign = Paint.Align.RIGHT
        text("¥${money(invoice.totalCents)}", PAGE_WIDTH - 104f, 1241f, 32f, BLUE, true)
        paint.textAlign = Paint.Align.LEFT

        line(1310f)
        section(canvas, "验真信息", 1354f)
        infoRow(canvas, "发票代码", invoice.invoiceCode, 1400f)
        infoRow(canvas, "校验码", grouped(invoice.verificationCode), 1448f)
        text("订单号", 76f, 1496f, 19f, MUTED)
        text(order.id, 260f, 1496f, 19f, DARK)
        qrBitmap(invoice.id + invoice.verificationCode)?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, RectF(PAGE_WIDTH - 256f, 1332f, PAGE_WIDTH - 96f, 1492f), paint)
        }
        text("扫描查看电子凭证", PAGE_WIDTH - 288f, 1528f, 14f, MUTED)

        line(1570f)
        text("本电子凭证由铁路出行服务生成，请妥善保存。", 76f, 1616f, 16f, MUTED)
        text("第 1 页 / 共 1 页", PAGE_WIDTH - 230f, 1660f, 16f, MUTED)
    }

    private fun section(canvas: Canvas, title: String, baseline: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLUE }
        canvas.drawRoundRect(RectF(76f, baseline - 24f, 84f, baseline + 8f), 4f, 4f, paint)
        paint.color = DARK
        paint.textSize = 24f
        paint.typeface = Typeface.create("sans", Typeface.BOLD)
        canvas.drawText(title, 100f, baseline, paint)
    }

    private fun infoRow(canvas: Canvas, label: String, value: String, baseline: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = 19f
        paint.color = MUTED
        paint.typeface = Typeface.create("sans", Typeface.NORMAL)
        canvas.drawText(label, 76f, baseline, paint)
        paint.color = DARK
        paint.textSize = 21f
        canvas.drawText(value, 260f, baseline, paint)
    }

    private fun qrBitmap(value: String): Bitmap? = runCatching {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 160, 160)
        Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (x in 0 until 160) for (y in 0 until 160) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
    }.getOrNull()

    private fun grouped(value: String): String = value.chunked(4).joinToString(" ")
    private fun money(cents: Long): String = String.format(Locale.CHINA, "%.2f", cents / 100.0)
}

package com.railway.ticketsystem.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
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
 * Exports an attractive customer-readable travel voucher. It deliberately avoids tax authority
 * seals, official invoice formats, official QR encodings and verification numbers.
 */
object InvoicePdfExporter {
    private const val PAGE_WIDTH = 1600
    private const val PAGE_HEIGHT = 1020
    private const val PAPER = 0xFFF5FBFF.toInt()
    private const val PAPER_STRONG = 0xFFE8F5FC.toInt()
    private const val BLUE = 0xFF2376C5.toInt()
    private const val BLUE_DARK = 0xFF135DA5.toInt()
    private const val DARK = 0xFF1B354E.toInt()
    private const val MUTED = 0xFF668099.toInt()
    private const val LINE = 0xFFBED6E7.toInt()

    fun export(context: Context, invoice: ElectronicInvoice, order: Order): Uri? = runCatching {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            drawVoucher(page.canvas, invoice, order)
            document.finishPage(page)
            writeToDownloads(context, document, "铁路电子客票行程单_${invoice.invoiceNumber}.pdf")
        } finally {
            document.close()
        }
    }.getOrNull()

    private fun writeToDownloads(context: Context, document: PdfDocument, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/铁路12306/电子凭证")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            return try {
                context.contentResolver.openOutputStream(uri)?.use(document::writeTo)
                    ?: throw IllegalStateException("Unable to open PDF output")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } catch (_: Exception) {
                context.contentResolver.delete(uri, null, null)
                null
            }
        }
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "电子凭证").apply { mkdirs() }
        val file = File(folder, displayName)
        FileOutputStream(file).use(document::writeTo)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun drawVoucher(canvas: Canvas, invoice: ElectronicInvoice, order: Order) {
        canvas.drawColor(PAPER)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        fun text(value: String, x: Float, y: Float, size: Float, color: Int = DARK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
            paint.style = Paint.Style.FILL
            paint.pathEffect = null
            paint.color = color
            paint.textSize = size
            paint.typeface = Typeface.create("sans", if (bold) Typeface.BOLD else Typeface.NORMAL)
            paint.textAlign = align
            canvas.drawText(value, x, y, paint)
        }
        fun rounded(rect: RectF, color: Int, radius: Float = 26f, stroke: Int? = null) {
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRoundRect(rect, radius, radius, paint)
            stroke?.let {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f
                paint.color = it
                canvas.drawRoundRect(rect, radius, radius, paint)
                paint.style = Paint.Style.FILL
            }
        }
        fun divider(y: Float, x1: Float = 110f, x2: Float = PAGE_WIDTH - 110f) {
            paint.color = LINE
            paint.strokeWidth = 2f
            paint.pathEffect = null
            canvas.drawLine(x1, y, x2, y, paint)
        }
        fun detail(label: String, value: String, x: Float, y: Float) {
            text(label, x, y, 18f, MUTED)
            text(value, x, y + 34f, 25f, DARK, true)
        }

        rounded(RectF(52f, 42f, PAGE_WIDTH - 52f, PAGE_HEIGHT - 42f), Color.WHITE, 38f, LINE)
        rounded(RectF(53f, 43f, PAGE_WIDTH - 53f, 178f), BLUE, 37f)
        rounded(RectF(53f, 142f, PAGE_WIDTH - 53f, 179f), BLUE, 0f)
        text("铁路电子客票行程单", 110f, 108f, 42f, Color.WHITE, true)
        text("电子客运服务凭证", 112f, 143f, 19f, 0xFFD9EEFF.toInt())
        text("凭证编号  ${invoice.invoiceNumber}", PAGE_WIDTH - 110f, 102f, 21f, Color.WHITE, true, Paint.Align.RIGHT)
        text("生成日期  ${invoice.issueDate.substringBefore(" ")}", PAGE_WIDTH - 110f, 139f, 18f, 0xFFD9EEFF.toInt(), false, Paint.Align.RIGHT)

        text("出行信息", 110f, 234f, 25f, BLUE_DARK, true)
        text("请以实际乘车记录为准", PAGE_WIDTH - 110f, 234f, 18f, MUTED, false, Paint.Align.RIGHT)

        val startX = 156f
        val endX = PAGE_WIDTH - 424f
        text(station(order.departureStation), startX, 316f, 40f, DARK, true)
        text(order.trainNumber, PAGE_WIDTH / 2f, 290f, 24f, BLUE_DARK, true, Paint.Align.CENTER)
        text("${order.departureDate}  ${order.departureTime}", PAGE_WIDTH / 2f, 323f, 18f, MUTED, false, Paint.Align.CENTER)
        text(station(order.arrivalStation), endX, 316f, 40f, DARK, true, Paint.Align.RIGHT)
        paint.color = BLUE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        canvas.drawLine(350f, 300f, PAGE_WIDTH - 620f, 300f, paint)
        val arrowX = PAGE_WIDTH - 620f
        canvas.drawLine(arrowX - 16f, 284f, arrowX, 300f, paint)
        canvas.drawLine(arrowX - 16f, 316f, arrowX, 300f, paint)
        paint.style = Paint.Style.FILL
        text(order.departureTime, startX, 354f, 22f, MUTED)
        text(order.arrivalTime, endX, 354f, 22f, MUTED, false, Paint.Align.RIGHT)

        divider(392f)
        detail("乘车日期", order.departureDate, 110f, 434f)
        detail("车厢 / 席位", "${order.carNumber}车  ${order.seatNumber}", 410f, 434f)
        detail("席别", order.seatType, 735f, 434f)
        detail("乘车人", order.passengerName, 1010f, 434f)

        rounded(RectF(110f, 520f, 1115f, 716f), PAPER_STRONG, 28f)
        text("费用信息", 150f, 574f, 23f, BLUE_DARK, true)
        text("服务项目", 150f, 623f, 18f, MUTED)
        text(invoice.serviceName, 330f, 623f, 22f, DARK, true)
        text("票面金额", 150f, 670f, 18f, MUTED)
        text("¥${money(invoice.totalCents)}", 330f, 672f, 30f, BLUE_DARK, true)
        text("费用合计", 1055f, 623f, 19f, MUTED, false, Paint.Align.RIGHT)
        text("¥${money(invoice.totalCents)}", 1055f, 675f, 39f, BLUE_DARK, true, Paint.Align.RIGHT)

        rounded(RectF(1150f, 250f, PAGE_WIDTH - 110f, 716f), 0xFFF7FBFE.toInt(), 28f, LINE)
        text("凭证校验", 1192f, 304f, 23f, BLUE_DARK, true)
        qrBitmap("travel-voucher:${invoice.id}:${invoice.verificationCode}")?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, RectF(1220f, 334f, 1432f, 546f), paint)
        }
        text("应用内行程校验码", PAGE_WIDTH - 230f, 582f, 18f, DARK, true, Paint.Align.CENTER)
        text("用于查看该笔行程信息", PAGE_WIDTH - 230f, 614f, 16f, MUTED, false, Paint.Align.CENTER)
        text("校验标识  ${grouped(invoice.verificationCode)}", PAGE_WIDTH - 230f, 658f, 15f, MUTED, false, Paint.Align.CENTER)

        divider(760f)
        text("行程信息", 110f, 814f, 23f, BLUE_DARK, true)
        text("订单编号  ${order.id.takeLast(26)}", 110f, 852f, 18f, DARK)
        text("凭证抬头  ${invoice.buyerTitle}", 650f, 852f, 18f, DARK)
        text("本凭证仅用于应用内行程信息展示与保存，不作为税务发票或报销凭据。", 110f, 897f, 16f, MUTED)
        paint.color = LINE
        paint.strokeWidth = 2f
        paint.pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        canvas.drawLine(110f, 928f, PAGE_WIDTH - 110f, 928f, paint)
        paint.pathEffect = null
        text("铁路出行服务 · 第 1 页 / 共 1 页", 110f, 964f, 16f, MUTED)
        text("请妥善保存此电子行程凭证", PAGE_WIDTH - 110f, 964f, 16f, MUTED, false, Paint.Align.RIGHT)
    }

    private fun qrBitmap(value: String): Bitmap? = runCatching {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 212, 212)
        Bitmap.createBitmap(212, 212, Bitmap.Config.ARGB_8888).also { bitmap ->
            for (x in 0 until 212) for (y in 0 until 212) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.rgb(27, 53, 78) else Color.WHITE)
            }
        }
    }.getOrNull()

    private fun station(value: String): String = value.trim().let { if (it.endsWith("站")) it else "${it}站" }
    private fun grouped(value: String): String = value.chunked(4).joinToString(" ")
    private fun money(cents: Long): String = String.format(Locale.CHINA, "%.2f", cents / 100.0)
}

package com.railway.ticketsystem.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class SimpleAvatarManager(private val context: Context) {
    
    private val avatarDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "avatars")
    
    init {
        // 创建头像目录
        try {
            if (!avatarDir.exists()) {
                avatarDir.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 保存头像到本地文件
     */
    fun saveAvatar(bitmap: Bitmap, userId: String): String? {
        return try {
            val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"
            val file = File(avatarDir, fileName)
            
            // 确保目录存在
            if (!avatarDir.exists()) {
                avatarDir.mkdirs()
            }
            
            // 简单的文件保存
            val outputStream = FileOutputStream(file)
            try {
                val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                if (success) {
                    outputStream.flush()
                    file.absolutePath
                } else {
                    null
                }
            } finally {
                outputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 从文件路径加载头像
     */
    fun loadAvatar(avatarPath: String): Bitmap? {
        return try {
            if (avatarPath.isNotEmpty() && File(avatarPath).exists()) {
                BitmapFactory.decodeFile(avatarPath)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 删除旧的头像文件
     */
    fun deleteOldAvatar(avatarPath: String) {
        try {
            if (avatarPath.isNotEmpty()) {
                val file = File(avatarPath)
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 从Uri加载图片
     */
    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                // 简单的压缩
                if (bitmap != null) {
                    compressBitmap(bitmap, 300, 300)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 压缩图片
     */
    private fun compressBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            if (width <= maxWidth && height <= maxHeight) {
                return bitmap
            }
            
            val scaleWidth = maxWidth.toFloat() / width
            val scaleHeight = maxHeight.toFloat() / height
            val scale = minOf(scaleWidth, scaleHeight)
            
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            e.printStackTrace()
            return bitmap
        }
    }
}


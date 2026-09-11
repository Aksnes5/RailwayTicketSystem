package com.railway.ticketsystem.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class TestAvatarManager(private val context: Context) {
    
    private val avatarDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "avatars")
    
    init {
        Log.d("TestAvatarManager", "初始化头像管理器")
        try {
            if (!avatarDir.exists()) {
                val created = avatarDir.mkdirs()
                Log.d("TestAvatarManager", "创建目录: $created, 路径: ${avatarDir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e("TestAvatarManager", "创建目录失败", e)
        }
    }
    
    /**
     * 保存头像到本地文件
     */
    fun saveAvatar(bitmap: Bitmap, userId: String): String? {
        Log.d("TestAvatarManager", "开始保存头像，用户ID: $userId")
        return try {
            val fileName = "avatar_${userId}_${System.currentTimeMillis()}.jpg"
            val file = File(avatarDir, fileName)
            Log.d("TestAvatarManager", "文件路径: ${file.absolutePath}")
            
            // 确保目录存在
            if (!avatarDir.exists()) {
                val created = avatarDir.mkdirs()
                Log.d("TestAvatarManager", "重新创建目录: $created")
            }
            
            // 检查文件是否可写
            if (file.parentFile?.canWrite() != true) {
                Log.e("TestAvatarManager", "目录不可写")
                return null
            }
            
            // 简单的文件保存
            var outputStream: FileOutputStream? = null
            try {
                outputStream = FileOutputStream(file)
                Log.d("TestAvatarManager", "创建输出流成功")
                
                val success = bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                Log.d("TestAvatarManager", "压缩结果: $success")
                
                if (success) {
                    outputStream.flush()
                    Log.d("TestAvatarManager", "保存成功: ${file.absolutePath}")
                    file.absolutePath
                } else {
                    Log.e("TestAvatarManager", "压缩失败")
                    null
                }
            } catch (e: Exception) {
                Log.e("TestAvatarManager", "保存过程中出错", e)
                null
            } finally {
                try {
                    outputStream?.close()
                    Log.d("TestAvatarManager", "输出流已关闭")
                } catch (e: Exception) {
                    Log.e("TestAvatarManager", "关闭输出流失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e("TestAvatarManager", "保存头像失败", e)
            null
        }
    }
    
    /**
     * 从文件路径加载头像
     */
    fun loadAvatar(avatarPath: String): Bitmap? {
        Log.d("TestAvatarManager", "加载头像: $avatarPath")
        return try {
            if (avatarPath.isNotEmpty() && File(avatarPath).exists()) {
                val bitmap = BitmapFactory.decodeFile(avatarPath)
                Log.d("TestAvatarManager", "加载头像成功: ${bitmap != null}")
                bitmap
            } else {
                Log.d("TestAvatarManager", "头像文件不存在")
                null
            }
        } catch (e: Exception) {
            Log.e("TestAvatarManager", "加载头像失败", e)
            null
        }
    }
    
    /**
     * 删除旧的头像文件
     */
    fun deleteOldAvatar(avatarPath: String) {
        Log.d("TestAvatarManager", "删除旧头像: $avatarPath")
        try {
            if (avatarPath.isNotEmpty()) {
                val file = File(avatarPath)
                if (file.exists()) {
                    val deleted = file.delete()
                    Log.d("TestAvatarManager", "删除结果: $deleted")
                }
            }
        } catch (e: Exception) {
            Log.e("TestAvatarManager", "删除头像失败", e)
        }
    }
    
    /**
     * 从Uri加载图片
     */
    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        Log.d("TestAvatarManager", "从Uri加载图片: $uri")
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                Log.d("TestAvatarManager", "从Uri加载成功: ${bitmap != null}")
                
                if (bitmap != null) {
                    compressBitmap(bitmap, 300, 300)
                } else {
                    null
                }
            } else {
                Log.e("TestAvatarManager", "无法打开输入流")
                null
            }
        } catch (e: Exception) {
            Log.e("TestAvatarManager", "从Uri加载失败", e)
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
            Log.d("TestAvatarManager", "原始尺寸: ${width}x${height}")
            
            if (width <= maxWidth && height <= maxHeight) {
                return bitmap
            }
            
            val scaleWidth = maxWidth.toFloat() / width
            val scaleHeight = maxHeight.toFloat() / height
            val scale = minOf(scaleWidth, scaleHeight)
            
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            
            Log.d("TestAvatarManager", "压缩后尺寸: ${newWidth}x${newHeight}")
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } catch (e: Exception) {
            Log.e("TestAvatarManager", "压缩图片失败", e)
            return bitmap
        }
    }
}

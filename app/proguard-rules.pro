# 12306 Railway Ticket System Proguard / R8 Optimization Rules

# 保持数据模型与实体类不被混淆（Gson 序列化/反序列化依赖）
-keep class com.railway.ticketsystem.model.** { *; }
-keep class com.railway.ticketsystem.data.SeatAvailability { *; }
-keep class com.railway.ticketsystem.data.RefundBreakdown { *; }
-keep class com.railway.ticketsystem.data.TrainStopSchedule { *; }
-keep class com.railway.ticketsystem.data.StationCoordinates { *; }
-keep class com.railway.ticketsystem.viewmodel.** { *; }

# 保持 Gson 相关注解和内部类
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保持 WebView JavaScript 交互接口不被混淆
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.railway.ticketsystem.activity.RailwayMapActivity$WebAppInterface {
    public *;
}

# 保持 AndroidX ViewModel 与 Lifecycle
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}

# 保持 ViewBinding
-keep class com.railway.ticketsystem.databinding.** { *; }

# 保持 ZXing 二维码/乘车凭证生成库
-dontwarn com.google.zxing.**
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.barcodescanner.** { *; }

# 保持 AndroidX 安全加密组件
-keep class androidx.security.crypto.** { *; }

# 保持调试行号信息便于崩溃堆栈排查
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
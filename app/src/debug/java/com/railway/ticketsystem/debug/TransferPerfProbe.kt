package com.railway.ticketsystem.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import com.railway.ticketsystem.activity.AdvancedSearchResultsActivity
import com.railway.ticketsystem.data.SeatInventoryRepository
import kotlin.concurrent.thread

/**
 * 调试构建专用的中转搜索基准入口。
 *
 * 直接跑 TransferWorkload，不起 Activity：SearchResultsActivity 没有导出，adb 起不来；
 * 就算绕过去，它还要先过登录页，而且从后台启动 Activity 会被 BAL 拦掉。这里只关心
 * "中转搜索这份工作要花多久"，起不起界面不影响答案。
 *
 * 用法：
 *   adb shell am broadcast -n com.railway.ticketsystem/.debug.TransferPerfProbe \
 *       -a com.railway.ticketsystem.PERF_PROBE -f 0x20
 */
class TransferPerfProbe : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val from = intent.getStringExtra("from") ?: "北京西"
        val to = intent.getStringExtra("to") ?: "上海虹桥"
        val date = intent.getStringExtra("date") ?: "2026-09-11"

        // 带 open=true 时直接以"中转"模式打开结果页，用来复现界面上的切换问题。
        // 真机上 adb 的 input tap 被 MIUI 拒（INJECT_EVENTS），只能由应用自己起。
        if (intent.getBooleanExtra("open", false)) {
            Log.w(TAG, "打开中转结果页 $from → $to")
            context.startActivity(
                Intent(context, AdvancedSearchResultsActivity::class.java).apply {
                    putExtra("departureStation", from)
                    putExtra("arrivalStation", to)
                    putExtra("departureDate", date)
                    putExtra("forceTransferSearch", intent.getBooleanExtra("transfer", true))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return
        }

        Log.d(TAG, "开始基准 $from → $to")

        // 别占着广播的主线程：这份工作量在慢设备上要几十秒。
        thread(name = "transfer-perf-probe") {
            try {
                // 用真的库存仓库，排序那段的开销才是线上那个量级。
                val seats = SeatInventoryRepository(context.applicationContext)
                val result = TransferWorkload.run(
                    from, to,
                    onStation = { station -> Log.w(TAG, "  候选站 $station") },
                    availabilityPenalty = { train ->
                        seats.getAvailability(train, date, "二等座").requiresWaitlist
                    }
                )
                Log.d(TAG, "基准结果 $from→$to $result")
            } catch (t: Throwable) {
                Log.e(TAG, "基准失败", t)
            }
        }
    }

    private companion object {
        const val TAG = "TransferPerf"
    }
}

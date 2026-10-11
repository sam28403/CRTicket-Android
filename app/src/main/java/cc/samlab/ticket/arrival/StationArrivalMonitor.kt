package cc.samlab.ticket.arrival

import android.content.Context
import android.annotation.SuppressLint
import android.location.Location

class StationArrivalMonitor private constructor(context: Context) {
    companion object {
        // 单例只持有 applicationContext，不持有 Activity 或 WebView。
        @SuppressLint("StaticFieldLeak")
        private var instance: StationArrivalMonitor? = null
        @Synchronized fun get(context: Context): StationArrivalMonitor = instance
            ?: StationArrivalMonitor(context.applicationContext).also { instance = it }
    }
    private val context = context.applicationContext
    private val store = ArrivalStore(context)
    private val fences = ArrivalGeofences(context)
    private var running = false
    private var busy = false
    private var generation = 0
    private var queued: Location? = null
    private var center: Location? = null
    private var radius = 1000f
    private val completions = mutableListOf<() -> Unit>()

    fun start(finished: () -> Unit = {}) {
        check(ArrivalAccess.ready(context)) { "请开启精确定位、始终允许定位和到站通知" }
        store.enabled = true
        if (running && store.registered) {
            finished()
            return
        }
        running = true
        val token = ++generation
        store.registered = false
        store.message = "正在获取附近车站，请保持系统定位开启"
        fences.updates().addOnFailureListener {
            if (token == generation) store.message = "低功耗定位不可用，请检查系统定位后重新开启"
        }
        fences.current().addOnCompleteListener { task ->
            if (token != generation || !store.enabled) {
                finished()
                return@addOnCompleteListener
            }
            val location = if (task.isSuccessful) task.result else null
            if (location != null) update(location, true, finished)
            else {
                store.message = "暂未取得位置，获取到位置后将自动监控附近车站"
                finished()
            }
        }
    }

    fun restore(finished: () -> Unit = {}) {
        if (!store.enabled) {
            finished()
            return
        }
        if (!ArrivalAccess.ready(context)) {
            suspendMonitoring()
            store.message = "到站提醒已暂停，请检查精确定位、始终允许定位和通知权限"
            finished()
            return
        }
        runCatching { start(finished) }.onFailure {
            store.message = "恢复到站提醒失败，请重新开启"
            finished()
        }
    }

    fun stop() {
        store.enabled = false
        suspendMonitoring()
        store.message = "已关闭到站提醒"
    }

    private fun suspendMonitoring() {
        generation++
        running = false
        queued = null
        center = null
        store.registered = false
        fences.remove()
        finishWaiters()
    }

    fun update(location: Location, force: Boolean = false, finished: () -> Unit = {}) {
        if (!store.enabled || !ArrivalAccess.ready(context)) {
            finished()
            return
        }
        if (!ArrivalLocations.usable(location, 2000f)) {
            finished()
            return
        }
        ArrivalVisits.check(context, location)
        if (!force && center?.distanceTo(location)?.let { it < radius / 2 } == true) {
            finished()
            return
        }
        completions.add(finished)
        queued = Location(location)
        if (busy) return
        registerNext()
    }

    private fun registerNext() {
        val location = queued ?: return
        queued = null
        if (!store.enabled || !ArrivalAccess.ready(context)) {
            finishWaiters()
            return
        }
        busy = true
        val token = generation
        val plan = ArrivalPolicy.plan(ArrivalCatalog.stations(context), location.latitude, location.longitude)
        fences.replace(plan, location) { store.enabled && token == generation }
            .addOnCompleteListener { task ->
                busy = false
                if (token == generation && store.enabled) {
                    store.registered = task.isSuccessful
                    store.message = if (task.isSuccessful) "已开启到站提醒，正在监控附近车站"
                        else "车站围栏注册失败，请检查系统定位后重新开启"
                    if (task.isSuccessful) {
                        center = location
                        radius = plan.refreshRadius
                    }
                } else if (!store.enabled || !ArrivalAccess.ready(context)) {
                    // 关闭发生在注册期间时，再移除一次，防止晚返回的围栏留下。
                    fences.remove()
                }
                if (queued != null) registerNext() else finishWaiters()
            }
    }

    fun refresh(location: Location?, finished: () -> Unit = {}) {
        center = null
        if (location != null) update(location, true, finished)
        else fences.current().addOnCompleteListener { task ->
            val current = if (task.isSuccessful) task.result else null
            if (current != null) update(current, true, finished) else finished()
        }
    }

    private fun finishWaiters() {
        val callbacks = completions.toList()
        completions.clear()
        callbacks.forEach { it() }
    }

    fun unavailable() {
        store.registered = false
        running = false
        center = null
        store.message = "系统地理围栏暂不可用，恢复定位后将重试"
    }
}

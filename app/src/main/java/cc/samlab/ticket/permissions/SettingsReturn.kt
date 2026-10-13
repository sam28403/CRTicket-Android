package cc.samlab.ticket.permissions

/** 部分系统设置会提前返回 Activity result，必须等应用实际离开并返回。 */
class SettingsReturn {
    private var launched = false
    private var away = false

    fun launch() {
        launched = true
        away = false
    }

    fun onPause() {
        if (launched) away = true
    }

    fun onResume(): Boolean {
        if (!launched || !away) return false
        cancel()
        return true
    }

    fun cancel() {
        launched = false
        away = false
    }
}

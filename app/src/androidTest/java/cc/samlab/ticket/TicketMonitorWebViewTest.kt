package cc.samlab.ticket

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cc.samlab.ticket.monitor.TicketMonitorNotifications
import cc.samlab.ticket.monitor.TicketMonitorService
import cc.samlab.ticket.monitor.TicketMonitorStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class TicketMonitorWebViewTest {
    private val fixture = MonitorLifecycleDeviceFixture(InstrumentationRegistry.getInstrumentation())
    private val context = fixture.context

    @Test fun savedNativeStateRestoresControlsAndResultsAcrossPageNavigation() = fixture.withSnapshot {
        val config = JSONObject(MonitorTestPayloads.tickets()).put("interval", 3600)
        fixture.onMain { TicketMonitorService.start(context, config.toString()) }
        fixture.await("Initial native query did not complete", timeout = 60_000) {
            val status = TicketMonitorStore.status(context)
            status.optInt("attempts") > 0 && !status.optBoolean("loading")
        }
        // Preserve a deterministic cached result between the real query and its next hourly poll.
        val cached = TicketMonitorStore.read(context)
            .put("trains", JSONArray().put(JSONObject()
                .put("id", "webview-cached-result")
                .put("train", "TEST000")
                .put("from", "北京南").put("to", "上海虹桥")
                .put("departure", "09:00").put("arrival", "13:30")
                .put("duration", "04:30").put("canBuy", false)
                .put("status", "恢复验证缓存")
                .put("seats", JSONObject().put("second", "0"))))
            .put("queriedAt", Instant.now().toString())
        TicketMonitorStore.save(context, cached)

        navigate("/monitor")
        awaitScript("Saved configuration and cached result were not restored", """
            (() => {
                const inputs = [...document.querySelectorAll('.station-route-fields input')];
                return inputs[0]?.value === '北京南' && inputs[1]?.value === '上海虹桥'
                    && document.querySelector('.el-input-number input')?.value === '3600'
                    && document.querySelector('.actions [role="status"]')?.textContent.includes('监控中')
                    && Boolean(document.querySelector('.ticket-card[aria-label="TEST000 北京南至上海虹桥"]'));
            })()
        """.trimIndent())
        assertTrue(TicketMonitorStore.status(context).getBoolean("active"))

        navigate("/")
        awaitScript("Monitor page did not unmount", "!document.querySelector('.monitor-page')")
        assertTrue(TicketMonitorStore.status(context).getBoolean("active"))
        assertTrue(fixture.notice(TicketMonitorNotifications.ID) != null)
        navigate("/monitor")
        awaitStatus("监控中")
        assertEquals("\"北京南\"", fixture.evaluate(
            "document.querySelector('.station-route-fields input')?.value"))
        assertTrue(TicketMonitorStore.status(context).getBoolean("active"))
    }

    @Test fun pageButtonsStartAndStopNativeMonitoringAndReloadKeepsItStopped() = fixture.withSnapshot {
        fixture.onMain { TicketMonitorService.stop(context) }
        fixture.await("Original monitor did not stop") {
            fixture.notice(TicketMonitorNotifications.ID) == null
        }
        TicketMonitorStore.save(context, JSONObject().put("active", false))
        navigate("/monitor")
        awaitStatus("未监控")
        fixture.evaluate("""
            (() => {
                const inputs = [...document.querySelectorAll('.station-route-fields input')];
                ['北京南', '上海虹桥'].forEach((value, index) => {
                    inputs[index].value = value;
                    inputs[index].dispatchEvent(new Event('input', { bubbles: true }));
                });
                const train = document.querySelector('.train-filter-input input');
                train.value = 'TEST000';
                train.dispatchEvent(new Event('input', { bubbles: true }));
                train.dispatchEvent(new KeyboardEvent('keydown', {
                    key: 'Enter', code: 'Enter', bubbles: true,
                }));
                return true;
            })()
        """.trimIndent())
        awaitScript("Protective train filter was not entered", """
            [...document.querySelectorAll('.train-filter-input .el-tag')]
                .some(tag => tag.textContent.includes('TEST000'))
        """.trimIndent())
        clickButton("开始监控")
        fixture.await("Page start button did not start the native monitor") {
            TicketMonitorStore.status(context).getBoolean("active")
        }
        awaitStatus("监控中")
        assertEquals("TEST000", TicketMonitorStore.status(context).getJSONObject("config")
            .getJSONObject("filters").getJSONArray("trainFilters").getString(0))
        clickButton("停止")
        fixture.await("Page stop button did not clear the native intent") {
            !TicketMonitorStore.status(context).getBoolean("active") &&
                fixture.notice(TicketMonitorNotifications.ID) == null
        }
        awaitStatus("未监控")
        fixture.evaluate("window.monitorReloadToken = 'before-reload'")
        fixture.reload()
        awaitScript("Monitor document did not reload", """
            typeof window.monitorReloadToken === 'undefined'
                && Boolean(document.querySelector('.monitor-page'))
        """.trimIndent())
        awaitStatus("未监控")
        assertFalse(TicketMonitorStore.status(context).getBoolean("active"))
    }

    private fun navigate(route: String) {
        awaitScript("Application WebView was not ready", "Boolean(document.querySelector('#app')?.children.length)")
        fixture.evaluate("location.hash = ${JSONObject.quote("#$route")}")
        if (route == "/monitor") {
            awaitScript("Monitor page did not mount", "Boolean(document.querySelector('.monitor-page'))")
        }
    }

    private fun clickButton(label: String) {
        assertEquals("true", fixture.evaluate("""
            (() => {
                const button = [...document.querySelectorAll('.actions button')]
                    .find(item => item.textContent.trim() === ${JSONObject.quote(label)});
                if (!button || button.disabled) return false;
                button.click();
                return true;
            })()
        """.trimIndent()))
    }

    private fun awaitStatus(label: String) = awaitScript("Page status did not become $label", """
        document.querySelector('.actions [role="status"]')?.textContent.trim()
            .startsWith(${JSONObject.quote(label)}) === true
    """.trimIndent())

    private fun awaitScript(message: String, script: String) =
        fixture.await(message, timeout = 30_000) { fixture.evaluate(script) == "true" }
}

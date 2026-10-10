package cc.samlab.ticket

import cc.samlab.ticket.network.UpstreamPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UpstreamPolicyTest {
    @Test fun acceptsOnlyOfficialRailwayEndpoints() {
        val urls = listOf(
            "https://search.12306.cn/search/v1/train/search?keyword=G1",
            "https://kyfw.12306.cn/otn/leftTicket/queryG?purpose_codes=ADULT",
            "https://kyfw.12306.cn/otn/queryTrainInfo/query",
            "http://down.lltskb.com/an.db",
            "http://223.107.87.50:8011/an.db"
        )
        for (url in urls) assertEquals(url, UpstreamPolicy.validate(url).toString())
    }

    @Test fun rejectsArbitraryServersAndRedirectTargets() {
        val urls = listOf(
            "http://127.0.0.1:3000/api/user/session",
            "https://evil.example/an.db",
            "https://kyfw.12306.cn.evil.example/otn/leftTicket/queryG",
            "https://kyfw.12306.cn:8443/otn/leftTicket/queryG",
            "https://kyfw.12306.cn/otn/leftTicket/../../other",
            "https://a:b@kyfw.12306.cn/otn/leftTicket/queryG",
            "http://down.lltskb.com/unrelated"
        )
        for (url in urls) {
            assertThrows(IllegalArgumentException::class.java) {
                UpstreamPolicy.validate(url)
            }
        }
    }
}

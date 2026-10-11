package cc.samlab.ticket

import cc.samlab.ticket.arrival.ArrivalPolicy
import cc.samlab.ticket.arrival.ArrivalStation
import org.junit.Assert.*
import org.junit.Test

class ArrivalPolicyTest {
    @Test fun distanceUsesWgs84LatitudeAndLongitudeInCorrectOrder() {
        val station = ArrivalStation("测试", 30.0, 120.0)
        assertEquals(0.0, ArrivalPolicy.distance(30.0, 120.0, station), 0.001)
        assertEquals(333.6, ArrivalPolicy.distance(30.003, 120.0, station), 1.0)
        assertTrue(ArrivalPolicy.distance(30.0, 120.003, station) < 300)
    }

    @Test fun allStationsAreConsideredButOnlyNearest99UseTheSystemLimit() {
        val stations = (1..300).reversed().map {
            ArrivalStation("站$it", 30.0 + it * 0.01, 120.0)
        }
        val plan = ArrivalPolicy.plan(stations, 30.0, 120.0)
        assertEquals(99, plan.stations.size)
        assertEquals("站1", plan.stations.first().name)
        assertEquals("站99", plan.stations.last().name)
        assertEquals(30_000f, plan.refreshRadius, 0.01f)
    }

    @Test fun denseCitiesRefreshBeforeReachingAnUnmonitoredStation() {
        val stations = (1..120).map { ArrivalStation("站$it", 30.0 + it * 0.0004, 120.0) }
        val plan = ArrivalPolicy.plan(stations, 30.0, 120.0)
        val omittedDistance = ArrivalPolicy.distance(30.0, 120.0, stations[99])
        assertTrue(plan.refreshRadius < omittedDistance - ArrivalPolicy.RADIUS)
        assertTrue(plan.refreshRadius >= 1000f)
    }

    @Test fun emptyCatalogAndSameStationCooldownAreSafe() {
        assertTrue(ArrivalPolicy.plan(emptyList(), 30.0, 120.0).stations.isEmpty())
        val now = 10_000_000L
        assertTrue(ArrivalPolicy.canNotify(0, now))
        assertFalse(ArrivalPolicy.canNotify(now, now + 1000))
        assertFalse(ArrivalPolicy.canNotify(now, now - 1000))
        assertTrue(ArrivalPolicy.canNotify(now, now + ArrivalPolicy.COOLDOWN_MS))
    }
}

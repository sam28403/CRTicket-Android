package cc.samlab.ticket

import cc.samlab.ticket.ui.fare.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class OrdinaryFareTest {
    @Test fun trainGradeBoundariesMatchUpstreamWithoutChangingGlobalRules() {
        listOf("Z1", "Z1998", "N1", "N1998", "Y998", "Y1001", "Y9998", " v5998 ").forEach {
            assertEquals(it, FareGrade.FAST, OrdinaryFare.gradeForTrain(it))
        }
        listOf("V6001", "V6998").forEach {
            assertEquals(it, FareGrade.EXPRESS, OrdinaryFare.gradeForTrain(it))
        }
        listOf("V7001", "V7998").forEach {
            assertEquals(it, FareGrade.LOCAL, OrdinaryFare.gradeForTrain(it))
        }
        listOf(
            "", "G1", "D1", "K1", "T1", "1461", "Z01", "Z1999", "N1999", "Z1/2",
            "Y999", "Y1000", "Y9999", "V5999", "V6000", "V6999", "V7000", "V7999",
        ).forEach { assertNull(it, OrdinaryFare.gradeForTrain(it)) }
    }

    @Test fun all5000DistancesAndNineConditionsMatchOriginalJavaScript() {
        // Generated with the synced upstream JS, not the Kotlin implementation.
        val expected = checkNotNull(javaClass.getResourceAsStream("/ordinary-fare-parity.txt"))
            .bufferedReader().use { it.readLines() }
        for (line in expected) {
            val (gradeName, airName, checksum) = line.split(' ')
            val grade = FareGrade.valueOf(gradeName)
            val air = FareAir.valueOf(airName)
            val hash = MessageDigest.getInstance("SHA-256")
            for (distance in 1..5000) {
                val prices = OrdinaryFare.calculate(grade, air, distance)
                val row = "$distance|" + prices.joinToString(",") { (it.price * 2).toInt().toString() } + "\n"
                hash.update(row.toByteArray(Charsets.UTF_8))
            }
            assertEquals("$gradeName/$airName", checksum,
                hash.digest().joinToString("") { "%02x".format(it) })
        }
    }

    @Test fun calibratedShortTripsAndSevenSeatPrices() {
        listOf(108 to 16.5, 162 to 24.5, 147 to 23.5, 36 to 9.0, 63 to 12.5).forEach { (km, price) ->
            assertEquals(price, OrdinaryFare.calculate(FareGrade.FAST, FareAir.NEW, km).first().price, 0.0)
        }
        assertEquals(listOf(128.5, 204.5, 237.5, 230.5, 222.5, 363.5, 348.5),
            OrdinaryFare.calculate(FareGrade.FAST, FareAir.NEW, 1000).map { it.price })
        assertEquals(FareSeat.entries, OrdinaryFare.calculate(FareGrade.LOCAL, FareAir.NONE, 1).map { it.seat })
    }

    @Test fun minimumAndBandBoundariesRemainExact() {
        listOf(20 to 20, 200 to 195, 201 to 210, 400 to 390, 401 to 415,
            700 to 685, 701 to 720, 1100 to 1080, 4601 to 4650).forEach { (km, fareKm) ->
            assertEquals(fareKm, OrdinaryFare.fareDistance(km))
        }
        assertEquals(100, OrdinaryFare.fareDistance(36, 100))
        assertEquals(400, OrdinaryFare.fareDistance(108, 400))
        for (distance in listOf(0, -1, 10001)) {
            assertThrows(IllegalArgumentException::class.java) {
                OrdinaryFare.calculate(FareGrade.FAST, FareAir.NEW, distance)
            }
        }
    }
}

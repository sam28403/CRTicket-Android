package cc.samlab.ticket

import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationCatalogTest {
    private val resources = sequenceOf(File("src/main/res"), File("app/src/main/res"))
        .first { it.isDirectory }
    private val directories = resources.listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("values-") && File(it, "strings.xml").isFile }
        .map { it.name }
        .sorted()
    private val format = Regex("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z]")

    private fun catalog(directory: String): Map<String, Element> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "$directory/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        val entries = (0 until nodes.length).map { nodes.item(it) as Element }
        assertEquals("Duplicate IDs in $directory", entries.size, entries.map { it.getAttribute("name") }.toSet().size)
        return entries.associateBy { it.getAttribute("name") }
    }

    @Test
    fun translationsCoverAllTranslatableStrings() {
        val expected = catalog("values").filterValues { it.getAttribute("translatable") != "false" }.keys
        for (directory in directories) {
            assertEquals(directory, expected, catalog(directory).keys)
        }
    }

    @Test
    fun translationsPreserveFormatArguments() {
        val defaults = catalog("values")
        for (directory in directories) {
            for ((key, value) in catalog(directory)) {
                val expected = format.findAll(defaults.getValue(key).textContent).map { it.value }.sorted().toList()
                val actual = format.findAll(value.textContent).map { it.value }.sorted().toList()
                assertEquals("$directory/$key", expected, actual)
            }
        }
    }

    @Test
    fun translationsContainNoEmptyEntriesOrDraftMarkers() {
        val markers = Regex("ZXQ\\d+|QXZ|__P\\d+|\\[\\[\\d{4}\\]\\]")
        for (directory in directories) {
            for ((key, value) in catalog(directory)) {
                assertTrue("Empty translation: $directory/$key", value.textContent.isNotBlank())
                assertFalse("Draft marker in $directory/$key", markers.containsMatchIn(value.textContent))
            }
        }
    }

    @Test
    fun defaultIsEnglishAndStationExamplesStayUnchanged() {
        for ((key, value) in catalog("values")) {
            if (key == "ticket_sell_place_hint") {
                assertTrue(value.textContent.contains("南京南站"))
            } else {
                assertFalse("Chinese prose left in default: $key", Regex("[\\p{IsHan}]").containsMatchIn(value.textContent))
            }
        }
        for (directory in directories) {
            assertTrue(
                "$directory changed the station example",
                catalog(directory).getValue("ticket_sell_place_hint").textContent.contains("南京南站"),
            )
        }
    }

    @Test
    fun chineseCatalogsUseTheExpectedRegionalTerms() {
        val simplified = catalog("values-zh-rCN")
        assertEquals("制作CR样式车票", simplified.getValue("home_ticket_description").textContent)
        assertEquals("接受到访车站的通知", simplified.getValue("settings_arrival_description").textContent)
        assertEquals("電池最佳化", catalog("values-zh-rTW").getValue("permissions_battery").textContent)
        assertEquals("電池優化", catalog("values-zh-rHK").getValue("permissions_battery").textContent)
    }

    @Test
    fun systemLanguageDeclarationMatchesSupportedLanguages() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resources, "xml/locales_config.xml"))
        val nodes = document.getElementsByTagName("locale")
        val declarations = (0 until nodes.length).map {
            (nodes.item(it) as Element).getAttribute("android:name")
        }
        val expected = directories.map { directory ->
            Locale.forLanguageTag(directory.removePrefix("values-").replace("-r", "-"))
                .toLanguageTag()
        }.toSet() + "en"
        assertEquals("Duplicate language declarations", declarations.size, declarations.toSet().size)
        assertEquals(expected, declarations.toSet())
    }
}

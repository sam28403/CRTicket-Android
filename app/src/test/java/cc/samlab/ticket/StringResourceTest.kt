package cc.samlab.ticket

import cc.samlab.ticket.i18n.AppStrings
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Before

/** Uses the production catalog while keeping policy tests independent of Android. */
open class StringResourceTest {
    @Before
    fun loadStringResources() {
        val directory = sequenceOf(
            File("src/main/res"),
            File("app/src/main/res"),
        ).first { it.isDirectory }
        val ids = R.string::class.java.fields.associate { it.name to it.getInt(null) }
        // Existing policy tests intentionally use zh-CN. Common resources fall
        // back to the English catalog just as they do on an Android device.
        val strings = buildMap {
            for (locale in listOf("values", "values-zh-rCN")) {
                val document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder().parse(File(directory, "$locale/strings.xml"))
                val nodes = document.getElementsByTagName("string")
                for (index in 0 until nodes.length) {
                    val node = nodes.item(index)
                    val id = ids.getValue(node.attributes.getNamedItem("name").nodeValue)
                    val text = node.textContent.removeSurrounding("\"")
                        .replace("\\n", "\n")
                        .replace("\\t", "\t")
                        .replace("\\'", "'")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                    put(id, text)
                }
            }
        }
        AppStrings.resolve = { id, arguments ->
            val value = strings.getValue(id)
            if (arguments.isEmpty()) value
            else String.format(Locale.ROOT, value, *arguments)
        }
    }
}

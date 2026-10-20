package cc.samlab.ticket

import android.content.Context
import org.xmlpull.v1.XmlPullParser

/** Exercise the shipped language list without maintaining a second copy in tests. */
internal fun supportedTestLocales(context: Context): List<String> {
    val languages = mutableListOf<String>()
    context.resources.getXml(R.xml.locales_config).use { parser ->
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                languages += checkNotNull(
                    parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name"),
                )
            }
            parser.next()
        }
    }
    return languages
}

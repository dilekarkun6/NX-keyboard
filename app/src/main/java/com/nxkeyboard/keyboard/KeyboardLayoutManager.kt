package com.nxkeyboard.keyboard

import android.content.Context
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

data class Key(
    val code: Int,
    val label: String,
    val popupCharacters: String = "",
    val widthPercent: Float = 10f,
    val iconName: String? = null
) {
    val isFunctional: Boolean get() = code <= 0
    val popupList: List<String> get() {
        if (popupCharacters.isEmpty()) return emptyList()
        return popupCharacters.map { it.toString() }
    }
}

data class KeyboardRow(val keys: List<Key>)

data class KeyboardLayout(
    val rows: List<KeyboardRow>,
    val isRTL: Boolean = false,
    val locale: String = "en"
)

object KeyboardLayoutManager {

    fun load(context: Context, resourceId: Int, locale: String, rtl: Boolean): KeyboardLayout {
        val parser: XmlResourceParser = context.resources.getXml(resourceId)
        val rows = mutableListOf<KeyboardRow>()
        var currentKeys: MutableList<Key>? = null

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "Row" -> currentKeys = mutableListOf()
                            "Key" -> {
                                val key = parseKey(parser)
                                currentKeys?.add(key)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Row" && currentKeys != null) {
                            rows.add(KeyboardRow(currentKeys.toList()))
                            currentKeys = null
                        }
                    }
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }

        return KeyboardLayout(rows, rtl, locale)
    }

    private fun parseKey(parser: XmlResourceParser): Key {
        var code = 0
        var label = ""
        var popup = ""
        var widthPercent = 10f
        var iconName: String? = null

        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i) ?: ""
            when (name) {
                "codes" -> code = value.toIntOrNull() ?: 0
                "keyLabel" -> label = value
                "popupCharacters" -> popup = value
                "keyWidth" -> {
                    val cleaned = value.removeSuffix("%p").removeSuffix("%P")
                    widthPercent = cleaned.toFloatOrNull() ?: 10f
                }
                "keyIcon" -> {
                    iconName = value.substringAfterLast("/").substringBeforeLast(".")
                }
            }
        }

        return Key(
            code = code,
            label = label,
            popupCharacters = popup,
            widthPercent = widthPercent,
            iconName = iconName
        )
    }

    const val CODE_SHIFT = -1
    const val CODE_EMOJI = -2
    const val CODE_SYMBOLS = -3
    const val CODE_LETTERS = -4
    const val CODE_BACKSPACE = -5
    const val CODE_LANGUAGE = -10
    const val CODE_VOICE = -11
    const val CODE_CLIPBOARD = -12
    const val CODE_AI = -13
    const val CODE_SETTINGS = -14
    const val CODE_TRANSLATE = -15
    const val CODE_ENTER = 10
    const val CODE_SPACE = 32
}

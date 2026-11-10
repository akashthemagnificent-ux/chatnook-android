package com.chatnook.app.domain.model

/** How much of a channel the user wants to be notified about. */
enum class NotificationLevel(val wire: String) {
    ALL("all"),
    MENTIONS("mentions"),
    NONE("none"),
    ;

    companion object {
        fun fromWire(value: String?): NotificationLevel =
            entries.firstOrNull { it.wire == value } ?: ALL
    }
}

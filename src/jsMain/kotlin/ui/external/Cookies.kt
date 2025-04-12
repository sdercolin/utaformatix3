package ui.external

@JsModule("js-cookie")
@JsNonModule
external class Cookies {
    companion object {
        fun set(
            key: String,
            value: String,
        )

        fun get(key: String): String?
    }
}

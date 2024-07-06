package core.external

@JsModule("js-yaml")
@JsNonModule
external object JsYaml {
    fun load(text: String): dynamic
    fun dump(`object`: dynamic): String
}

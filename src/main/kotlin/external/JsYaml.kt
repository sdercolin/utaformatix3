package external

import kotlinx.js.Object

@JsModule("js-yaml")
@JsNonModule
external object JsYaml {
    fun load(text: String): Object
    fun dump(`object`: Object): String
}

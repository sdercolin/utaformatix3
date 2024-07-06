package core.external

import kotlin.js.Promise

@JsModule("jszip")
@JsNonModule
external class JsZip {
    fun loadAsync(data: dynamic): Promise<JsZip>
    fun file(name: String): JsZipObject?
    fun file(name: String, data: dynamic): JsZip
    fun generateAsync(option: JsZipOption): Promise<dynamic>
}

@JsName("ZipObject")
external class JsZipObject {
    fun async(type: String): Promise<Any>
}

@JsName("Option")
external class JsZipOption {
    var type: String
    var mimeType: String = definedExternally
}

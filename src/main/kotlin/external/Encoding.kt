package external

import org.khronos.webgl.Uint8Array

@JsModule("encoding-japanese")
@JsNonModule
external class Encoding {

    companion object {
        fun convert(data: Array<Byte>, to: String): Array<Byte>
        fun detect(data: Array<Byte>): String
        fun codeToString(data: Uint8Array): String
    }
}

package core.external

@JsModule("encoding-japanese")
@JsNonModule
external class Encoding {

    companion object {
        fun convert(data: Array<Byte>, to: String, from: String = definedExternally): Array<Byte>
        fun detect(data: Array<Byte>): String
    }
}

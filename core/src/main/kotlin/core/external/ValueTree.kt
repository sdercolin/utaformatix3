package core.external

import org.khronos.webgl.Uint8Array

fun createValueTree(): ValueTree {
    return js("{type: '', attributes: {}, children: []}").unsafeCast<ValueTree>()
}

fun baseVariantType(): dynamic {
    return js("({type: '', value: undefined})")
}

fun String.toVariantType(): dynamic {
    val value = baseVariantType()
    value.type = "string"
    value.value = this

    return value
}
fun Int.toVariantType(): dynamic {
    val value = baseVariantType()
    value.type = "int"
    value.value = this

    return value
}
fun Double.toVariantType(): dynamic {
    val value = baseVariantType()
    value.type = "double"
    value.value = this

    return value
}
fun Boolean.toVariantType(): dynamic {
    val value = baseVariantType()
    if (this) {
        value.type = "boolTrue"
        value.value = true
    } else {
        value.type = "boolFalse"
        value.value = false
    }

    return value
}
fun Uint8Array.toVariantType(): dynamic {
    val value = baseVariantType()
    value.type = "binary"
    value.value = this

    return value
}

external interface ValueTree {
    var type: String
    var attributes: dynamic
    var children: Array<ValueTree>
}

@JsModule("@sevenc-nanashi/valuetree-ts")
@JsNonModule
external object ValueTreeTs {
    fun parseValueTree(text: Uint8Array): ValueTree
    fun dumpValueTree(tree: ValueTree): Uint8Array
}

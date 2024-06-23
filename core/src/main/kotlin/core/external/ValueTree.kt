package core.external

import org.khronos.webgl.Uint8Array

external class ValueTree {
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




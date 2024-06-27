@file:JsModule("@sevenc-nanashi/valuetree-ts")

package core.external

import org.khronos.webgl.Uint8Array

external interface ValueTree {
    var type: String
    var attributes: dynamic
    var children: Array<ValueTree>
}

external fun parseValueTree(text: Uint8Array): ValueTree
external fun dumpValueTree(tree: ValueTree): Uint8Array

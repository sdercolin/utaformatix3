@file:JsModule("@material-ui/core/Avatar")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val avatar: RClass<AvatarProps>

external interface AvatarProps : RProps {
    var src: String
    var style: Style
}

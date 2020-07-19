@file:JsModule("@material-ui/core/Link")
@file:JsNonModule

package ui.external.materialui

import react.RClass
import react.RProps

@JsName("default")
external val link: RClass<LinkProps>

external interface LinkProps : RProps {
    var href: String
    var color: String
    var target: String
}

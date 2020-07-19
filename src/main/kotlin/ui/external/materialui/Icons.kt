package ui.external.materialui

import react.RClass
import react.RProps

@JsModule("@material-ui/icons")
@JsNonModule
private external val iconModule: dynamic

object Icons {
    val save = iconModule.SaveAlt.unsafeCast<RClass<IconProps>>()
    val refresh = iconModule.Refresh.unsafeCast<RClass<IconProps>>()
    val language = iconModule.Language.unsafeCast<RClass<IconProps>>()
    val feedback = iconModule.Feedback.unsafeCast<RClass<IconProps>>()
    val arrowBack = iconModule.ArrowBack.unsafeCast<RClass<IconProps>>()
}

external interface IconProps : RProps {
    var color: String
    var fontSize: String
}

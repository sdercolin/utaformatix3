package ui

import csstype.NamedColor
import csstype.TextAlign
import csstype.pct
import csstype.px
import mui.material.Link
import mui.material.Typography
import mui.material.styles.TypographyVariant
import react.FC
import react.Props
import react.css.css
import react.dom.html.AnchorTarget
import react.dom.html.ReactHTML.footer
import ui.strings.Strings

val CustomFooter = FC<CustomFooterProps> { props ->
    footer {
        css {
            width = 100.pct
            textAlign = TextAlign.center
            color = NamedColor.grey
            marginBottom = 15.px
            marginTop = 5.px
        }
        Typography {
            variant = TypographyVariant.body2
            +"UtaFormatix © 2015 - 2022　|　"
            Link {
                href = "https://github.com/sdercolin/utaformatix3"
                target = AnchorTarget._blank
                +"GitHub"
            }
            +"　|　"
            Link {
                href = "https://discord.gg/TyEcQ6P73y"
                target = AnchorTarget._blank
                +"Discord"
            }
            +"　|　"
            Link {
                onClick = { props.onOpenEmbeddedPage(Strings.ReleaseNotesUrl) }
                +"Release Notes"
            }
        }
    }
}

external interface CustomFooterProps : Props {
    var onOpenEmbeddedPage: (urlKey: Strings) -> Unit
}

package ui

import csstype.NamedColor
import mui.material.Link
import mui.material.LinkUnderline
import mui.material.Typography
import mui.material.TypographyAlign
import mui.material.styles.TypographyVariant
import react.FC
import react.Props
import react.css.css
import react.dom.html.AnchorTarget
import react.dom.html.ReactHTML.footer
import ui.strings.Strings

val CustomFooter = FC<CustomFooterProps> { props ->
    footer {
        Typography {
            align = TypographyAlign.center
            variant = TypographyVariant.body2
            css {
                color = NamedColor.grey
            }
            +"UtaFormatix © 2015 - 2023　|　"
            Link {
                color = NamedColor.grey
                underline = LinkUnderline.hover
                href = "https://github.com/sdercolin/utaformatix3"
                target = AnchorTarget._blank
                +"GitHub"
            }
            +"　|　"
            Link {
                color = NamedColor.grey
                underline = LinkUnderline.hover
                href = "https://discord.gg/TyEcQ6P73y"
                target = AnchorTarget._blank
                +"Discord"
            }
            +"　|　"
            Link {
                color = NamedColor.grey
                underline = LinkUnderline.hover
                onClick = { props.onOpenEmbeddedPage(Strings.ReleaseNotesUrl) }
                +"Release Notes"
            }
            +"　|　"
            Link {
                color = NamedColor.grey
                underline = LinkUnderline.hover
                onClick = { props.onOpenEmbeddedPage(Strings.GoogleAnalyticsUsageInfoUrl) }
                +"About Usage of Google Analytics"
            }
        }
    }
}

external interface CustomFooterProps : Props {
    var onOpenEmbeddedPage: (urlKey: Strings) -> Unit
}

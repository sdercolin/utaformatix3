package ui

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mui.icons.material.Language
import mui.material.Button
import mui.material.ButtonColor
import mui.material.Menu
import mui.material.MenuItem
import org.w3c.dom.Element
import org.w3c.dom.HTMLButtonElement
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.useState
import ui.strings.Language
import ui.strings.changeLanguage

typealias MyLanguage = Language

val LanguageSelector = FC<LanguageSelectorProps> { props ->
    var anchorElement: Element? by useState()

    fun openMenu(currentTarget: HTMLButtonElement) {
        anchorElement = currentTarget
    }

    fun closeMenu() {
        anchorElement = null
    }

    fun selectLanguage(language: MyLanguage) {
        GlobalScope.launch {
            changeLanguage(language.code)
            props.onChangeLanguage(language)
        }
    }

    div {
        Button {
            color = ButtonColor.inherit
            onClick = { event ->
                openMenu(event.currentTarget)
            }
            Language()
        }

        Menu {
            anchorEl = anchorElement?.let { { _ -> it } }
            open = anchorElement != null
            onClose = { closeMenu() }
            MyLanguage.values().forEach { language ->
                MenuItem {
                    onClick = {
                        selectLanguage(language)
                        closeMenu()
                    }
                    +language.displayName
                }
            }
        }
    }
}

external interface LanguageSelectorProps : Props {
    var onChangeLanguage: (Language) -> Unit
}

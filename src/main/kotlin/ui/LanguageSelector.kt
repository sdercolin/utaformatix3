package ui

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mui.icons.material.Language
import mui.material.Button
import mui.material.ButtonColor
import mui.material.Menu
import mui.material.MenuItem
import org.w3c.dom.Element
import react.FC
import react.Props
import react.dom.findDOMNode
import react.useState
import ui.strings.changeLanguage

typealias MyLanguage = ui.strings.Language

val LanguageSelector = FC<LanguageSelectorProps> { props ->
    var anchorElement: Element? by useState()

    fun Props.openMenu() {
        anchorElement = findDOMNode(this)
    }

    fun closeMenu() {
        anchorElement = null
    }

    fun selectLanguage(language: MyLanguage) {
        GlobalScope.launch {
            changeLanguage(language.code)
            props.onChangeLanguage()
        }
    }

    Button {
        color = ButtonColor.inherit
        onClick = { openMenu() }
        Language()
    }

    Menu {
        keepMounted = false
        anchorEl = anchorElement?.let { { it } }
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

external interface LanguageSelectorProps : Props {
    var onChangeLanguage: () -> Unit
}

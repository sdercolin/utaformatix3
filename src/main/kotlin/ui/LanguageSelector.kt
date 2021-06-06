package ui

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.w3c.dom.Element
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.setState
import ui.external.materialui.Color
import ui.external.materialui.Icons
import ui.external.materialui.button
import ui.external.materialui.menu
import ui.external.materialui.menuItem
import ui.external.react.findDOMNode
import ui.strings.Language
import ui.strings.changeLanguage

class LanguageSelector : RComponent<LanguageSelectorProps, LanguageSelectorState>() {
    override fun LanguageSelectorState.init() {
        anchorElement = null
    }

    override fun RBuilder.render() {
        button {
            attrs {
                color = Color.inherit
                onClick = { openMenu() }
            }
            Icons.language {}
        }
        menu {
            attrs {
                keepMounted = false
                anchorEl = state.anchorElement
                open = state.anchorElement != null
                onClose = { setState { anchorElement = null } }
            }
            Language.values().forEach {
                menuItem {
                    attrs.onClick = {
                        selectLanguage(it)
                        closeMenu()
                    }
                    +it.displayName
                }
            }
        }
    }

    private fun selectLanguage(language: Language) {
        GlobalScope.launch {
            changeLanguage(language.code)
            props.onChangeLanguage()
        }
    }

    private fun openMenu() {
        setState { anchorElement = findDOMNode(this@LanguageSelector) }
    }

    private fun closeMenu() {
        setState { anchorElement = null }
    }
}

external interface LanguageSelectorProps : RProps {
    var onChangeLanguage: () -> Unit
}

external interface LanguageSelectorState : RState {
    var anchorElement: Element?
}

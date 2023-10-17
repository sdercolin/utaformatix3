package ui.common

import csstype.px
import kotlinx.browser.window
import kotlinx.js.jso
import mui.material.Alert
import mui.material.AlertColor
import mui.material.Button
import mui.material.ButtonColor
import mui.material.Dialog
import mui.material.DialogActions
import mui.material.DialogTitle
import react.ChildrenBuilder
import react.useCallback
import react.useMemo
import ui.strings.Strings
import ui.strings.string

fun ChildrenBuilder.warningDialog(
    id: String,
    state: DialogWarningState,
    close: () -> Unit,
) {
    val ignoredIds = useMemo(id) {
        window.localStorage.getItem("ignoredWarnings")?.split(",")?.toSet() ?: emptySet()
    }
    val ignored = useMemo(id) {
        ignoredIds.contains(id)
    }
    val closeAndIgnore = useCallback(id) { warningId: String ->
        window.localStorage.setItem("ignoredWarnings", (ignoredIds + warningId).joinToString(","))
        close()
    }
    if (!ignored) {
        Dialog {
            open = state.isShowing
            onClose = { _, _ -> close() }
            DialogTitle {
                +state.title
            }
            Alert {
                severity = AlertColor.warning
                style = jso { borderRadius = 0.px }
                +state.message
            }
            DialogActions {
                Button {
                    onClick = { close() }
                    color = ButtonColor.inherit
                    +string(Strings.ConfirmButton)
                }
                Button {
                    onClick = { closeAndIgnore(id) }
                    color = ButtonColor.secondary
                    +string(Strings.DoNotShownAgainButton)
                }
            }
        }
    }
}

data class DialogWarningState(
    val isShowing: Boolean = false,
    val title: String = "",
    val message: String = "",
)

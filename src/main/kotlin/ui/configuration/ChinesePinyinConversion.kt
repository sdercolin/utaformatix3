package ui.configuration

import mui.material.FormGroup
import react.dom.html.ReactHTML.div
import ui.ChinesePinyinConversionState
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings

external interface ChinesePinyinConversionProps : SubProps<ChinesePinyinConversionState>

val ChinesePinyinConversionBlock =
    subFC<ChinesePinyinConversionProps, ChinesePinyinConversionState> { _, state, editState ->
        FormGroup {
            div {
                configurationSwitch(
                    isOn = state.isOn,
                    onSwitched = { editState { copy(isOn = it) } },
                    labelStrings = Strings.ChinesePinyinConversion,
                )
            }
        }
    }

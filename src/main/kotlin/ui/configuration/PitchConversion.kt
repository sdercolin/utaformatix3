package ui.configuration

import csstype.VerticalAlign
import kotlinx.js.jso
import mui.icons.material.ErrorOutline
import mui.material.FormGroup
import mui.material.Tooltip
import mui.material.TooltipPlacement
import react.ReactNode
import react.dom.html.ReactHTML
import ui.PitchConversionState
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings
import ui.strings.string

external interface PitchConversionProps : SubProps<PitchConversionState>

val PitchConversionBlock = subFC<PitchConversionProps, PitchConversionState> { _, state, editState ->
    FormGroup {
        ReactHTML.div {
            configurationSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.ConvertPitchData
            )
            Tooltip {
                title = ReactNode(string(Strings.ConvertPitchDataDescription))
                placement = TooltipPlacement.right
                disableInteractive = false
                ErrorOutline {
                    style = jso {
                        verticalAlign = VerticalAlign.middle
                    }
                }
            }
        }
    }
}

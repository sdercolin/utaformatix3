package ui.common

import mui.material.FormControlLabel
import mui.material.LabelPlacement
import mui.material.Switch
import mui.material.SwitchColor
import react.ChildrenBuilder
import react.ReactNode
import react.create
import ui.strings.Strings
import ui.strings.string

fun ChildrenBuilder.configurationSwitch(
    isOn: Boolean,
    onSwitched: (Boolean) -> Unit,
    labelStrings: Strings,
) {
    FormControlLabel {
        label = ReactNode(string(labelStrings))
        control = Switch.create {
            color = SwitchColor.secondary
            checked = isOn
            onChange = { event, _ ->
                val checked = event.target.checked
                onSwitched(checked)
            }
        }
        labelPlacement = LabelPlacement.end
    }
}

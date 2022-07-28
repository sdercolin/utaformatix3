package ui.configuration

import csstype.Length
import csstype.Margin
import csstype.px
import model.Format
import model.LyricsType
import model.Project
import mui.material.FormControl
import mui.material.FormControlLabel
import mui.material.FormControlMargin
import mui.material.FormGroup
import mui.material.FormLabel
import mui.material.Paper
import mui.material.Radio
import mui.material.RadioColor
import mui.material.RadioGroup
import mui.material.Typography
import mui.material.styles.TypographyVariant
import react.ChildrenBuilder
import react.create
import react.css.css
import react.dom.html.ReactHTML
import ui.LyricsConversionState
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings
import ui.strings.string

external interface LyricsConversionProps : SubProps<LyricsConversionState> {
    var project: Project
    var outputFormat: Format
}

val LyricsConversionBlock = subFC<LyricsConversionProps, LyricsConversionState> { props, state, editState ->
    FormGroup {
        configurationSwitch(
            isOn = state.isOn,
            onSwitched = { editState { copy(isOn = it) } },
            labelStrings = Strings.JapaneseLyricsConversion
        )
    }

    if (state.isOn) buildLyricsDetail(
        props = props,
        fromLyricsType = state.fromType,
        setFromLyricsType = { editState { copy(fromType = it) } },
        toLyricsType = state.toType,
        setToLyricsType = { editState { copy(toType = it) } },
    )
}

private fun ChildrenBuilder.buildLyricsDetail(
    props: LyricsConversionProps,
    fromLyricsType: LyricsType?,
    setFromLyricsType: (LyricsType) -> Unit,
    toLyricsType: LyricsType?,
    setToLyricsType: (LyricsType) -> Unit
) {
    ReactHTML.div {
        css {
            margin = Margin(horizontal = 40.px, vertical = 0.px)
            width = Length.maxContent
        }
        Paper {
            elevation = 0
            ReactHTML.div {
                css {
                    margin = Margin(
                        horizontal = 24.px,
                        top = 16.px,
                        bottom = 24.px
                    )
                }
                FormGroup {
                    buildLyricsTypeControl(
                        labelText = string(
                            Strings.FromLyricsTypeLabel,
                            "type" to props.project.lyricsType.displayName
                        ),
                        type = fromLyricsType,
                        setType = setFromLyricsType,
                        lyricTypeOptions = listOf(
                            LyricsType.RomajiCv,
                            LyricsType.RomajiVcv,
                            LyricsType.KanaCv,
                            LyricsType.KanaVcv
                        )
                    )

                    buildLyricsTypeControl(
                        labelText = string(Strings.ToLyricsTypeLabel),
                        type = toLyricsType,
                        setType = setToLyricsType,
                        lyricTypeOptions = props.outputFormat.possibleLyricsTypes
                    )
                }
            }
        }
    }
}

private fun ChildrenBuilder.buildLyricsTypeControl(
    labelText: String,
    type: LyricsType?,
    setType: (LyricsType) -> Unit,
    lyricTypeOptions: List<LyricsType>
) {
    FormControl {
        margin = FormControlMargin.normal
        FormLabel {
            focused = false
            +labelText
        }
        RadioGroup {
            row = true
            value = type?.name.orEmpty()
            onChange = { event, _ ->
                val value = event.target.value
                setType(LyricsType.valueOf(value))
            }
            lyricTypeOptions.forEach { lyricsType ->
                FormControlLabel {
                    value = lyricsType.name
                    control = Radio.create {
                        color = RadioColor.secondary
                    }
                    label = Typography.create {
                        variant = TypographyVariant.subtitle2
                        +lyricsType.displayName
                    }
                }
            }
        }
    }
}

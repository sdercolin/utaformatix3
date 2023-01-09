package ui.configuration

import csstype.Length
import csstype.Margin
import csstype.px
import model.Format
import model.JapaneseLyricsType
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
import react.dom.html.ReactHTML.div
import ui.JapaneseLyricsConversionState
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings
import ui.strings.string

external interface LyricsConversionProps : SubProps<JapaneseLyricsConversionState> {
    var projects: List<Project>
    var outputFormat: Format
}

val JapaneseLyricsConversionBlock =
    subFC<LyricsConversionProps, JapaneseLyricsConversionState> { props, state, editState ->
        FormGroup {
            configurationSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.JapaneseLyricsConversion,
            )
        }

        if (state.isOn) buildLyricsDetail(
            props = props,
            detectedType = state.detectedType,
            fromLyricsType = state.fromType,
            setFromLyricsType = { editState { copy(fromType = it) } },
            toLyricsType = state.toType,
            setToLyricsType = { editState { copy(toType = it) } },
        )
    }

private fun ChildrenBuilder.buildLyricsDetail(
    props: LyricsConversionProps,
    detectedType: JapaneseLyricsType,
    fromLyricsType: JapaneseLyricsType?,
    setFromLyricsType: (JapaneseLyricsType) -> Unit,
    toLyricsType: JapaneseLyricsType?,
    setToLyricsType: (JapaneseLyricsType) -> Unit,
) {
    div {
        css {
            margin = Margin(horizontal = 40.px, vertical = 0.px)
            width = Length.maxContent
        }
        Paper {
            elevation = 0
            div {
                css {
                    margin = Margin(
                        horizontal = 24.px,
                        top = 16.px,
                        bottom = 24.px,
                    )
                }
                FormGroup {
                    buildLyricsTypeControl(
                        labelText = string(
                            Strings.FromLyricsTypeLabel,
                            "type" to detectedType.text,
                        ),
                        type = fromLyricsType,
                        setType = setFromLyricsType,
                        lyricTypeOptions = listOf(
                            JapaneseLyricsType.RomajiCv,
                            JapaneseLyricsType.RomajiVcv,
                            JapaneseLyricsType.KanaCv,
                            JapaneseLyricsType.KanaVcv,
                        ),
                    )

                    buildLyricsTypeControl(
                        labelText = string(Strings.ToLyricsTypeLabel),
                        type = toLyricsType,
                        setType = setToLyricsType,
                        lyricTypeOptions = props.outputFormat.possibleLyricsTypes,
                    )
                }
            }
        }
    }
}

private fun ChildrenBuilder.buildLyricsTypeControl(
    labelText: String,
    type: JapaneseLyricsType?,
    setType: (JapaneseLyricsType) -> Unit,
    lyricTypeOptions: List<JapaneseLyricsType>,
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
                setType(JapaneseLyricsType.valueOf(value))
            }
            lyricTypeOptions.forEach { lyricsType ->
                FormControlLabel {
                    value = lyricsType.name
                    control = Radio.create {
                        color = RadioColor.secondary
                    }
                    label = Typography.create {
                        variant = TypographyVariant.subtitle2
                        +lyricsType.text
                    }
                }
            }
        }
    }
}

private val JapaneseLyricsType.text get() = string(strings)

private val JapaneseLyricsType.strings
    get() = when (this) {
        JapaneseLyricsType.RomajiCv -> Strings.LyricsTypeRomajiCV
        JapaneseLyricsType.RomajiVcv -> Strings.LyricsTypeRomajiVCV
        JapaneseLyricsType.KanaCv -> Strings.LyricsTypeKanaCV
        JapaneseLyricsType.KanaVcv -> Strings.LyricsTypeKanaVCV
        JapaneseLyricsType.Unknown -> Strings.LyricsTypeUnknown
    }

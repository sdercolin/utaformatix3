package ui

import external.Resources
import kotlinx.css.LinearDimension
import kotlinx.css.marginLeft
import model.Format
import model.Format.Ccs
import model.Format.Dv
import model.Format.MusicXml
import model.Format.Ppsf
import model.Format.S5p
import model.Format.Svp
import model.Format.Ust
import model.Format.Vpr
import model.Format.Vsq
import model.Format.Midi
import model.Format.Vsqx
import model.ImportWarning
import model.Project
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.div
import styled.css
import styled.styledDiv
import ui.external.materialui.Severity
import ui.external.materialui.Style
import ui.external.materialui.TypographyVariant
import ui.external.materialui.alert
import ui.external.materialui.alertTitle
import ui.external.materialui.avatar
import ui.external.materialui.list
import ui.external.materialui.listItem
import ui.external.materialui.listItemText
import ui.external.materialui.typography
import ui.strings.Strings
import ui.strings.string

class OutputFormatSelector : RComponent<OutputFormatSelectorProps, RState>() {

    override fun RBuilder.render() {
        title(Strings.SelectOutputFormatCaption)
        buildImportWarnings()
        buildFormatList()
    }

    private fun RBuilder.buildImportWarnings() {
        val importWarnings = props.project.importWarnings
        if (importWarnings.isEmpty()) return

        alert {
            attrs {
                severity = Severity.warning
            }
            alertTitle { +string(Strings.ImportWarningTitle) }
            importWarnings.map { it.text }
                .forEach {
                    div { +it }
                }
        }
    }

    private fun RBuilder.buildFormatList() {
        list {
            for (format in props.formats) {
                listItem {
                    attrs {
                        button = true
                        onClick = { props.onSelected(format) }
                        style = Style(padding = "24px")
                    }
                    avatar {
                        attrs {
                            style = Style(width = "96px", height = "96px")
                            src = format.iconPath.orEmpty()
                        }
                    }
                    styledDiv {
                        css {
                            marginLeft = LinearDimension("24px")
                        }
                        listItemText {
                            attrs {
                                primary = typography {
                                    attrs {
                                        variant = TypographyVariant.h4
                                        style = Style(fontWeight = "200")
                                    }
                                    +format.name.replace("_", " ")
                                }
                                secondary = format.description
                            }
                        }
                    }
                }
            }
        }
    }

    private val ImportWarning.text: String
        get() = when (this) {
            is ImportWarning.TempoNotFound -> string(Strings.ImportWarningTempoNotFound)
            is ImportWarning.TempoIgnoredInFile -> string(
                Strings.ImportWarningTempoIgnoredInFile,
                "bpm" to tempo.bpm.toString(),
                "tick" to tempo.tickPosition.toString(),
                "file" to file.name
            )
            is ImportWarning.TempoIgnoredInTrack -> string(
                Strings.ImportWarningTempoIgnoredInTrack,
                "bpm" to tempo.bpm.toString(),
                "tick" to tempo.tickPosition.toString(),
                "number" to (track.id + 1).toString(),
                "name" to track.name
            )
            is ImportWarning.TempoIgnoredInPreMeasure -> string(
                Strings.ImportWarningTempoIgnoredInPreMeasure,
                "bpm" to tempo.bpm.toString()
            )
            is ImportWarning.TimeSignatureNotFound -> string(Strings.ImportWarningTimeSignatureNotFound)
            is ImportWarning.TimeSignatureIgnoredInTrack -> string(
                Strings.ImportWarningTimeSignatureIgnoredInTrack,
                "timeSignature" to timeSignature.displayValue,
                "measure" to timeSignature.measurePosition.toString(),
                "number" to (track.id + 1).toString(),
                "name" to track.name
            )
            is ImportWarning.TimeSignatureIgnoredInPreMeasure -> string(
                Strings.ImportWarningTimeSignatureIgnoredInPreMeasure,
                "timeSignature" to timeSignature.displayValue
            )
        }

    private val Format.description: String?
        get() = when (this) {
            Midi -> Strings.MidiFormatDescription
            Vsqx -> Strings.VsqxFormatDescription
            Vpr -> Strings.VprFormatDescription
            Ust -> Strings.UstFormatDescription
            Ccs -> Strings.CcsFormatDescription
            Svp -> Strings.SvpFormatDescription
            S5p -> Strings.S5pFormatDescription
            MusicXml -> Strings.MusicXmlFormatDescription
            Dv -> Strings.DvFormatDescription
            Vsq -> Strings.VsqFormatDescription
            Ppsf -> null
        }?.let { string(it) }

    private val Format.iconPath: String?
        get() = when (this) {
            Midi -> Resources.midiIcon
            Vsqx -> Resources.vsqxIcon
            Vpr -> Resources.vprIcon
            Ust -> Resources.ustIcon
            Ccs -> Resources.ccsIcon
            Svp -> Resources.svpIcon
            S5p -> Resources.s5pIcon
            MusicXml -> Resources.ccsIcon
            Dv -> Resources.dvIcon
            Vsq -> Resources.vsqIcon
            Ppsf -> null
        }
}

external interface OutputFormatSelectorProps : RProps {
    var project: Project
    var formats: List<Format>
    var onSelected: (Format) -> Unit
}

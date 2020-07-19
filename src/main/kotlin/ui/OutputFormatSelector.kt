package ui

import external.Resources
import kotlinx.css.LinearDimension
import kotlinx.css.marginLeft
import model.Format
import model.Format.CCS
import model.Format.MUSIC_XML
import model.Format.S5P
import model.Format.SVP
import model.Format.UST
import model.Format.VPR
import model.Format.VSQX
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
import ui.strings.Strings.CCSFormatDescription
import ui.strings.Strings.ImportWarningTempoIgnoredInFile
import ui.strings.Strings.ImportWarningTempoIgnoredInPreMeasure
import ui.strings.Strings.ImportWarningTempoIgnoredInTrack
import ui.strings.Strings.ImportWarningTempoNotFound
import ui.strings.Strings.ImportWarningTimeSignatureIgnoredInPreMeasure
import ui.strings.Strings.ImportWarningTimeSignatureIgnoredInTrack
import ui.strings.Strings.ImportWarningTimeSignatureNotFound
import ui.strings.Strings.ImportWarningTitle
import ui.strings.Strings.MusicXmlFormatDescription
import ui.strings.Strings.S5PFormatDescription
import ui.strings.Strings.SVPFormatDescription
import ui.strings.Strings.SelectOutputFormatCaption
import ui.strings.Strings.USTFormatDescription
import ui.strings.Strings.VPRFormatDescription
import ui.strings.Strings.VSQXFormatDescription
import ui.strings.string

class OutputFormatSelector : RComponent<OutputFormatSelectorProps, RState>() {

    override fun RBuilder.render() {
        title(SelectOutputFormatCaption)
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
            alertTitle { +(string(ImportWarningTitle)) }
            importWarnings
                .map { it.text }
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
                        onClick = {
                            props.onSelected(format)
                        }
                        style = Style(padding = "24px")
                    }
                    avatar {
                        attrs {
                            style = Style(width = "96px", height = "96px")
                            src = format.iconPath
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
            is ImportWarning.TempoNotFound -> string(ImportWarningTempoNotFound)
            is ImportWarning.TempoIgnoredInFile -> string(
                ImportWarningTempoIgnoredInFile,
                "bpm" to tempo.bpm.toString(),
                "tick" to tempo.tickPosition.toString(),
                "file" to file.name
            )
            is ImportWarning.TempoIgnoredInTrack -> string(
                ImportWarningTempoIgnoredInTrack,
                "bpm" to tempo.bpm.toString(),
                "tick" to tempo.tickPosition.toString(),
                "number" to (track.id + 1).toString(),
                "name" to track.name
            )
            is ImportWarning.TempoIgnoredInPreMeasure -> string(
                ImportWarningTempoIgnoredInPreMeasure,
                "bpm" to tempo.bpm.toString()
            )
            is ImportWarning.TimeSignatureNotFound -> string(ImportWarningTimeSignatureNotFound)
            is ImportWarning.TimeSignatureIgnoredInTrack -> string(
                ImportWarningTimeSignatureIgnoredInTrack,
                "timeSignature" to timeSignature.displayValue,
                "measure" to timeSignature.measurePosition.toString(),
                "number" to (track.id + 1).toString(),
                "name" to track.name
            )
            is ImportWarning.TimeSignatureIgnoredInPreMeasure -> string(
                ImportWarningTimeSignatureIgnoredInPreMeasure,
                "timeSignature" to timeSignature.displayValue
            )
        }

    private val Format.description: String
        get() = string(
            when (this) {
                VSQX -> VSQXFormatDescription
                VPR -> VPRFormatDescription
                UST -> USTFormatDescription
                CCS -> CCSFormatDescription
                SVP -> SVPFormatDescription
                S5P -> S5PFormatDescription
                MUSIC_XML -> MusicXmlFormatDescription
            }
        )

    private val Format.iconPath: String
        get() = when (this) {
            VSQX -> Resources.vsqxIcon
            VPR -> Resources.vprIcon
            UST -> Resources.ustIcon
            CCS -> Resources.ccsIcon
            SVP -> Resources.svpIcon
            S5P -> Resources.s5pIcon
            MUSIC_XML -> Resources.ccsIcon
        }
}

external interface OutputFormatSelectorProps : RProps {
    var project: Project
    var formats: List<Format>
    var onSelected: (Format) -> Unit
}

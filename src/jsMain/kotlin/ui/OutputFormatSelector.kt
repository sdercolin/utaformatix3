package ui

import core.model.Format
import core.model.Format.Ccs
import core.model.Format.Dv
import core.model.Format.MusicXml
import core.model.Format.Ppsf
import core.model.Format.S5p
import core.model.Format.StandardMid
import core.model.Format.Svp
import core.model.Format.Tssln
import core.model.Format.UfData
import core.model.Format.Ust
import core.model.Format.Ustx
import core.model.Format.VocaloidMid
import core.model.Format.Vpr
import core.model.Format.Vsq
import core.model.Format.Vsqx
import core.model.ImportWarning
import core.model.Project
import csstype.FontWeight
import csstype.px
import emotion.react.css
import kotlinx.browser.window
import kotlinx.js.jso
import mui.material.Alert
import mui.material.AlertColor
import mui.material.AlertTitle
import mui.material.Avatar
import mui.material.ListItem
import mui.material.ListItemButton
import mui.material.ListItemText
import mui.material.Typography
import mui.material.styles.TypographyVariant
import react.ChildrenBuilder
import react.FC
import react.Props
import react.ReactNode
import react.create
import react.dom.html.ReactHTML.div
import ui.common.title
import ui.strings.Strings
import ui.strings.string

val OutputFormatSelector = FC<OutputFormatSelectorProps> { props ->
    title(Strings.SelectOutputFormatCaption)
    buildImportWarnings(props)
    buildFormatList(props)
}

private const val MAX_WARNINGS = 20

private fun ChildrenBuilder.buildImportWarnings(props: OutputFormatSelectorProps) {
    val importWarnings = props.projects.flatMap { project: Project ->
        project.importWarnings.map { project.inputFiles[0].name to it }
    }
    if (importWarnings.isEmpty()) return
    val multipleMode = props.projects.size > 1

    Alert {
        severity = AlertColor.warning
        AlertTitle { +string(Strings.ImportWarningTitle) }
        importWarnings.take(MAX_WARNINGS).map {
            if (multipleMode) {
                val (fileName, warning) = it
                "($fileName) ${warning.text}"
            } else {
                it.second.text
            }
        }
            .forEach {
                div { +it }
            }
        if (importWarnings.size > MAX_WARNINGS) {
            div { +"..." }
        }
    }
}

private fun ChildrenBuilder.buildFormatList(props: OutputFormatSelectorProps) {
    mui.material.List {
        for (format in props.formats) {
            ListItem {
                ListItemButton {
                    onClick = {
                        // reset configs when opening ConfigurationEditor from this screen
                        window.localStorage.removeItem("currentConfigs")
                        props.onSelected(format)
                    }
                    style = jso { padding = 24.px }
                    Avatar {
                        style = jso {
                            width = 96.px
                            height = 96.px
                        }
                        src = format.iconPath.orEmpty()
                    }
                    div {
                        css {
                            marginLeft = 24.px
                        }
                        ListItemText {
                            primary = Typography.create {
                                variant = TypographyVariant.h4
                                style = jso { fontWeight = FontWeight.lighter }

                                +format.displayName
                            }
                            secondary = ReactNode(format.description.orEmpty())
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
            "file" to file.name,
        )
        is ImportWarning.TempoIgnoredInTrack -> string(
            Strings.ImportWarningTempoIgnoredInTrack,
            "bpm" to tempo.bpm.toString(),
            "tick" to tempo.tickPosition.toString(),
            "number" to (track.id + 1).toString(),
            "name" to track.name,
        )
        is ImportWarning.TempoIgnoredInPreMeasure -> string(
            Strings.ImportWarningTempoIgnoredInPreMeasure,
            "bpm" to tempo.bpm.toString(),
        )
        is ImportWarning.DefaultTempoFixed -> string(
            Strings.ImportWarningDefaultTempoFixed,
            "bpm" to originalBpm.toString(),
        )
        is ImportWarning.TimeSignatureNotFound -> string(Strings.ImportWarningTimeSignatureNotFound)
        is ImportWarning.TimeSignatureIgnoredInTrack -> string(
            Strings.ImportWarningTimeSignatureIgnoredInTrack,
            "timeSignature" to timeSignature.displayValue,
            "measure" to timeSignature.measurePosition.toString(),
            "number" to (track.id + 1).toString(),
            "name" to track.name,
        )
        is ImportWarning.TimeSignatureIgnoredInPreMeasure -> string(
            Strings.ImportWarningTimeSignatureIgnoredInPreMeasure,
            "timeSignature" to timeSignature.displayValue,
        )
        is ImportWarning.IncompatibleFormatSerializationVersion -> string(
            Strings.ImportWarningIncompatibleFormatSerializationVersion,
            "dataVersion" to dataVersion,
            "currentVersion" to currentVersion,
        )
    }

private val Format.description: String?
    get() = when (this) {
        VocaloidMid -> Strings.VocaloidMidiFormatDescription
        Vsqx -> Strings.VsqxFormatDescription
        Vpr -> Strings.VprFormatDescription
        Ust -> Strings.UstFormatDescription
        Ustx -> Strings.UstxFormatDescription
        Ccs -> Strings.CcsFormatDescription
        Svp -> Strings.SvpFormatDescription
        S5p -> Strings.S5pFormatDescription
        MusicXml -> Strings.MusicXmlFormatDescription
        Dv -> Strings.DvFormatDescription
        Vsq -> Strings.VsqFormatDescription
        Ppsf -> null
        StandardMid -> Strings.StandardMidDescription
        UfData -> Strings.UfDataFormatDescription
        Tssln -> Strings.VoiSonaFormatDescription
    }?.let { string(it) }

private val Format.iconPath: String?
    get() = when (this) {
        VocaloidMid -> Resources.vocaloidMidIcon
        Vsqx -> Resources.vsqxIcon
        Vpr -> Resources.vprIcon
        Ust -> Resources.ustIcon
        Ustx -> Resources.ustxIcon
        Ccs -> Resources.ccsIcon
        Svp -> Resources.svpIcon
        S5p -> Resources.s5pIcon
        MusicXml -> Resources.ccsIcon
        Dv -> Resources.dvIcon
        Vsq -> Resources.vsqIcon
        Ppsf -> null
        StandardMid -> Resources.standardMidiIcon
        UfData -> Resources.ufdataIcon
        Tssln -> Resources.tsslnIcon
    }

external interface OutputFormatSelectorProps : Props {
    var projects: List<Project>
    var formats: List<Format>
    var onSelected: (Format) -> Unit
}

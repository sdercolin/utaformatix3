package ui

import csstype.AlignSelf
import csstype.Display
import csstype.Length
import csstype.Margin
import csstype.VerticalAlign
import csstype.em
import csstype.px
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.js.jso
import model.ExportResult
import model.Feature
import model.Format
import model.LyricsType
import model.LyricsType.KanaCv
import model.LyricsType.KanaVcv
import model.LyricsType.RomajiCv
import model.LyricsType.RomajiVcv
import model.LyricsType.Unknown
import model.Project
import model.TICKS_IN_FULL_NOTE
import mui.icons.material.AddCircle
import mui.icons.material.ArrowDownward
import mui.icons.material.ArrowUpward
import mui.icons.material.ErrorOutline
import mui.icons.material.HelpOutline
import mui.icons.material.RemoveCircle
import mui.material.BaseTextFieldProps
import mui.material.Box
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import mui.material.FormControl
import mui.material.FormControlLabel
import mui.material.FormControlMargin
import mui.material.FormControlVariant
import mui.material.FormGroup
import mui.material.FormLabel
import mui.material.IconButton
import mui.material.IconButtonColor
import mui.material.LabelPlacement
import mui.material.MenuItem
import mui.material.Paper
import mui.material.Radio
import mui.material.RadioColor
import mui.material.RadioGroup
import mui.material.StandardTextFieldProps
import mui.material.Switch
import mui.material.SwitchColor
import mui.material.TextField
import mui.material.Tooltip
import mui.material.TooltipPlacement
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import process.RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
import process.evalFractionOrNull
import process.fillRests
import process.lyrics.LyricsReplacementRequest
import process.lyrics.convert
import process.lyrics.replaceLyrics
import process.needWarningZoom
import process.projectZoomFactorOptions
import process.restsFillingMaxLengthDenominatorOptions
import process.zoom
import react.ChildrenBuilder
import react.ElementType
import react.FC
import react.Props
import react.ReactNode
import react.StateSetter
import react.create
import react.css.css
import react.dom.html.ReactHTML.div
import react.useState
import ui.strings.Strings
import ui.strings.string
import util.runCatchingCancellable

val ConfigurationEditor = scopedFC<ConfigurationEditorProps> { props, scope ->
    var isProcessing by useState(false)
    val (lyricsConversion, setLyricsConversion) = useState {
        val analysedType = props.project.lyricsType
        val doLyricsConversion = analysedType != Unknown
        val fromLyricsType: LyricsType?
        val toLyricsType: LyricsType?

        if (doLyricsConversion) {
            fromLyricsType = analysedType
            toLyricsType = analysedType.findBestConversionTargetIn(props.outputFormat)
        } else {
            fromLyricsType = null
            toLyricsType = null
        }
        LyricsConversionState(
            doLyricsConversion,
            fromLyricsType,
            toLyricsType
        )
    }
    val (lyricsReplacement, setLyricsReplacement) = useState {
        val preset = LyricsReplacementRequest.getPreset(props.outputFormat)
        if (preset == null) {
            LyricsReplacementState(false, LyricsReplacementRequest())
        } else {
            LyricsReplacementState(true, preset)
        }
    }
    val (slightRestsFilling, setSlightRestsFilling) = useState {
        SlightRestsFillingState(
            true,
            RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
        )
    }
    val (pitchConversion, setPitchConversion) = useState {
        val hasPitchData = Feature.ConvertPitch.isAvailable(props.project)
        val isPitchConversionAvailable = hasPitchData &&
            props.outputFormat.availableFeaturesForGeneration.contains(Feature.ConvertPitch)
        PitchConversionState(
            isAvailable = isPitchConversionAvailable,
            isOn = isPitchConversionAvailable
        )
    }
    val (projectZoom, setProjectZoom) = useState {
        ProjectZoomState(
            isOn = false,
            factor = projectZoomFactorOptions.first(),
            hasWarning = false
        )
    }
    var dialogError by useState(DialogErrorState())

    fun isReady() = lyricsConversion.isReady && lyricsReplacement.isReady

    fun closeErrorDialog() {
        dialogError = dialogError.copy(isShowing = false)
    }

    title(Strings.ConfigurationEditorCaption)
    LyricsConversionBlock {
        this.project = props.project
        this.outputFormat = props.outputFormat
        initialState = lyricsConversion
        submitState = setLyricsConversion
    }
    LyricsReplacementBlock {
        initialState = lyricsReplacement
        submitState = setLyricsReplacement
    }
    SlightRestsFillingBlock {
        initialState = slightRestsFilling
        submitState = setSlightRestsFilling
    }
    if (pitchConversion.isAvailable) PitchConversionBlock {
        initialState = pitchConversion
        submitState = setPitchConversion
    }
    ProjectZoomBlock {
        this.project = props.project
        initialState = projectZoom
        submitState = setProjectZoom
    }
    buildNextButton(
        scope,
        props,
        isEnabled = isReady(),
        lyricsConversion,
        lyricsReplacement,
        slightRestsFilling,
        pitchConversion,
        projectZoom,
        setProcessing = { isProcessing = it },
        onDialogError = { dialogError = it }
    )

    errorDialog(
        isShowing = dialogError.isShowing,
        title = dialogError.title,
        errorMessage = dialogError.message,
        close = { closeErrorDialog() }
    )

    progress(isShowing = isProcessing)
}

external interface LyricsConversionProps : SubProps<LyricsConversionState> {
    var project: Project
    var outputFormat: Format
}

private val LyricsConversionBlock = subFC<LyricsConversionProps, LyricsConversionState> { props, state, editState ->
    FormGroup {
        buildFeatureSwitch(
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
                        lyricTypeOptions = listOf(RomajiCv, RomajiVcv, KanaCv, KanaVcv)
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

external interface LyricsReplacementProps : SubProps<LyricsReplacementState>

private val LyricsReplacementBlock = subFC<LyricsReplacementProps, LyricsReplacementState> { _, state, editState ->
    FormGroup {
        buildFeatureSwitch(
            isOn = state.isOn,
            onSwitched = { editState { copy(isOn = it) } },
            labelStrings = Strings.LyricsReplacement
        )
    }

    if (state.isOn) buildLyricsReplacementDetail(state, editState)
}

private fun ChildrenBuilder.buildLyricsReplacementDetail(
    state: LyricsReplacementState,
    editState: (LyricsReplacementState.() -> LyricsReplacementState) -> Unit
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
                        bottom = 24.px
                    )
                    paddingTop = 8.px
                    paddingBottom = 8.px
                }
                state.request.items.forEachIndexed { index, item ->
                    buildLyricsReplacementItem(index, item, editState)
                }
                div {
                    Button {
                        color = ButtonColor.secondary
                        variant = ButtonVariant.text
                        AddCircle()
                        onClick = { editState { copy(request = request.add()) } }
                        div {
                            css { padding = 8.px }
                            +string(Strings.LyricsReplacementAddItemButton)
                        }
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.buildLyricsReplacementItem(
    index: Int,
    item: LyricsReplacementRequest.Item,
    editState: (LyricsReplacementState.() -> LyricsReplacementState) -> Unit
) {
    fun editRequest(block: LyricsReplacementRequest.() -> LyricsReplacementRequest) {
        editState { copy(request = request.block()) }
    }

    fun editItem(block: LyricsReplacementRequest.Item.() -> LyricsReplacementRequest.Item) {
        editRequest { copy(items = items.mapIndexed { i, it -> if (i == index) block(it) else it }) }
    }
    div {
        css {
            display = Display.flex
            marginBottom = 16.px
        }
        Typography {
            css {
                color = appTheme.palette.secondary.main
                alignSelf = AlignSelf.center
            }
            variant = TypographyVariant.subtitle2
            component = "span".asDynamic().unsafeCast<ElementType<*>>()
            +string(Strings.LyricsReplacementItemLabel, "number" to (index + 1).toString())
        }
        FormControl {
            style = jso {
                marginLeft = 2.em
                marginTop = 8.px
                marginBottom = 8.px
            }
            variant = FormControlVariant.standard
            focused = false
            FormLabel {
                focused = false
                Typography {
                    variant = TypographyVariant.caption
                    +string(Strings.LyricsReplacementFilterTypeLabel)
                }
            }
            TextField {
                sx { minWidth = 5.em }
                select = true
                value = item.filterType.unsafeCast<Nothing?>()
                (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                    val value = event.target.asDynamic().value as String
                    editItem { copy(filterType = LyricsReplacementRequest.FilterType.valueOf(value)) }
                }
                LyricsReplacementRequest.FilterType.values().forEach { type ->
                    MenuItem {
                        value = type.toString()
                        +string(type.strings)
                    }
                }
            }
        }
        FormControl {
            style = jso {
                marginLeft = 2.em
                marginTop = 8.px
                marginBottom = 8.px
            }

            focused = false
            FormLabel {
                focused = false
                Typography {
                    variant = TypographyVariant.caption
                    +string(Strings.LyricsReplacementFilterTextLabel)
                }
            }
            TextField {
                sx { width = 8.em }
                value = item.filter
                disabled = item.filterType.needsFilter().not()
                (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                    val value = event.target.asDynamic().value as String
                    editItem { copy(filter = value) }
                }
            }
        }
        FormControl {
            style = jso {
                marginLeft = 2.em
                marginTop = 8.px
                marginBottom = 8.px
            }
            variant = FormControlVariant.standard
            focused = false
            FormLabel {
                focused = false
                Typography {
                    variant = TypographyVariant.caption
                    +string(Strings.LyricsReplacementMatchTypeLabel)
                }
            }
            TextField {
                sx { minWidth = 5.em }
                select = true
                value = item.matchType.unsafeCast<Nothing?>()
                (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                    val value = event.target.asDynamic().value as String
                    editItem { copy(matchType = LyricsReplacementRequest.MatchType.valueOf(value)) }
                }
                LyricsReplacementRequest.MatchType.values().forEach { type ->
                    MenuItem {
                        value = type.toString()
                        +string(type.strings)
                    }
                }
            }
        }
        FormControl {
            style = jso {
                marginLeft = 2.em
                marginTop = 8.px
                marginBottom = 8.px
            }
            focused = false
            FormLabel {
                focused = false
                Typography {
                    variant = TypographyVariant.caption
                    +string(Strings.LyricsReplacementFromTextLabel)
                }
            }
            TextField {
                sx { width = 8.em }
                value = item.from
                disabled = item.matchType.needsFrom().not()
                (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                    val value = event.target.asDynamic().value as String
                    editItem { copy(from = value) }
                }
            }
        }
        FormControl {
            style = jso {
                marginLeft = 2.em
                marginTop = 8.px
                marginBottom = 8.px
            }
            focused = false
            FormLabel {
                focused = false
                Typography {
                    variant = TypographyVariant.caption
                    +string(Strings.LyricsReplacementToTextLabel)
                }
            }
            TextField {
                sx { width = 8.em }
                value = item.to
                (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                    val value = event.target.asDynamic().value as String
                    editItem { copy(to = value) }
                }
            }
        }
        IconButton {
            color = IconButtonColor.inherit
            style = jso {
                margin = 5.px
                marginLeft = 20.px
                height = Length.fitContent
                alignSelf = AlignSelf.center
            }
            onClick = {
                editRequest { moveUp(index) }
            }
            ArrowUpward()
        }
        IconButton {
            color = IconButtonColor.inherit
            style = jso {
                margin = 5.px
                height = Length.fitContent
                alignSelf = AlignSelf.center
            }
            onClick = {
                editRequest { moveDown(index) }
            }
            ArrowDownward()
        }
        IconButton {
            color = IconButtonColor.secondary
            style = jso {
                margin = 5.px
                height = Length.fitContent
                alignSelf = AlignSelf.center
            }
            onClick = {
                editRequest { remove(index) }
            }
            RemoveCircle()
        }
    }
}

external interface SlightRestsFillingProps : SubProps<SlightRestsFillingState>

private val SlightRestsFillingBlock = subFC<SlightRestsFillingProps, SlightRestsFillingState> { _, state, editState ->
    FormGroup {
        div {
            buildFeatureSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.SlightRestsFilling
            )
            Tooltip {
                title = ReactNode(string(Strings.SlightRestsFillingDescription))
                placement = TooltipPlacement.right
                disableInteractive = false
                HelpOutline {
                    style = jso {
                        verticalAlign = VerticalAlign.middle
                    }
                }
            }
        }
    }

    if (state.isOn) buildRestsFillingDetail(state, editState)
}

private fun ChildrenBuilder.buildRestsFillingDetail(
    state: SlightRestsFillingState,
    editState: (SlightRestsFillingState.() -> SlightRestsFillingState) -> Unit
) {
    div {
        css {
            margin = Margin(horizontal = 40.px, vertical = 0.px)
            width = Length.maxContent
        }
        Paper {
            elevation = 0
            Box {
                style = jso {
                    margin = Margin(
                        left = 24.px,
                        right = 48.px,
                        top = 16.px,
                        bottom = 16.px
                    )
                    paddingBottom = 8.px
                }
                sx { minWidth = 15.em }
                FormControl {
                    margin = FormControlMargin.normal
                    variant = FormControlVariant.standard
                    focused = false
                    FormLabel {
                        focused = false
                        Typography {
                            variant = TypographyVariant.caption
                            +string(Strings.SlightRestsFillingThresholdLabel)
                        }
                    }
                    TextField {
                        style = jso { minWidth = 5.em }
                        select = true
                        value = state.excludedMaxLengthDenominator.toString().unsafeCast<Nothing?>()
                        (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                        (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                            val value = event.target.asDynamic().value as String
                            editState { copy(excludedMaxLengthDenominator = value.toInt()) }
                        }
                        restsFillingMaxLengthDenominatorOptions.forEach { denominator ->
                            MenuItem {
                                value = denominator.toString()
                                +string(
                                    Strings.SlightRestsFillingThresholdItem,
                                    "denominator" to denominator.toString()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

external interface PitchConversionProps : SubProps<PitchConversionState>

private val PitchConversionBlock = subFC<PitchConversionProps, PitchConversionState> { _, state, editState ->
    FormGroup {
        div {
            buildFeatureSwitch(
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

external interface ProjectZoomProps : SubProps<ProjectZoomState> {
    var project: Project
}

private val ProjectZoomBlock = subFC<ProjectZoomProps, ProjectZoomState> { props, state, editState ->
    FormGroup {
        div {
            buildFeatureSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.ProjectZoom
            )
            Tooltip {
                title = ReactNode(string(Strings.ProjectZoomDescription))
                placement = TooltipPlacement.right
                disableInteractive = false
                HelpOutline {
                    style = jso {
                        verticalAlign = VerticalAlign.middle
                    }
                }
            }
            if (props.project.needWarningZoom(state.factorValue)) {
                Tooltip {
                    title = ReactNode(string(Strings.ProjectZoomWarning))
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
    if (state.isOn) buildProjectZoomDetail(state, editState)
}

private fun ChildrenBuilder.buildProjectZoomDetail(
    state: ProjectZoomState,
    editState: (ProjectZoomState.() -> ProjectZoomState) -> Unit
) {
    div {
        css {
            margin = Margin(horizontal = 40.px, vertical = 0.px)
            width = Length.maxContent
        }
        Paper {
            elevation = 0
            Box {
                style = jso {
                    margin = Margin(
                        left = 24.px,
                        right = 48.px,
                        top = 16.px,
                        bottom = 16.px
                    )
                    paddingBottom = 8.px
                }
                sx { minWidth = 15.em }
                FormControl {
                    FormLabel {
                        focused = false
                        Typography {
                            variant = TypographyVariant.caption
                            +string(Strings.ProjectZooLabel)
                        }
                    }
                    margin = FormControlMargin.normal
                    variant = FormControlVariant.standard
                    focused = false
                    TextField {
                        style = jso { minWidth = 5.em }
                        select = true
                        value = state.factor.unsafeCast<Nothing?>()
                        (this.unsafeCast<BaseTextFieldProps>()).variant = FormControlVariant.standard
                        (this.unsafeCast<StandardTextFieldProps>()).onChange = { event ->
                            val value = event.target.asDynamic().value as String
                            editState { copy(factor = value) }
                        }
                        projectZoomFactorOptions.forEach { factor ->
                            MenuItem {
                                value = factor
                                +(factor)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.buildFeatureSwitch(
    isOn: Boolean,
    onSwitched: (Boolean) -> Unit,
    labelStrings: Strings
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

private fun ChildrenBuilder.buildNextButton(
    scope: CoroutineScope,
    props: ConfigurationEditorProps,
    isEnabled: Boolean,
    lyricsConversion: LyricsConversionState,
    lyricsReplacement: LyricsReplacementState,
    slightRestsFilling: SlightRestsFillingState,
    pitchConversion: PitchConversionState,
    projectZoom: ProjectZoomState,
    setProcessing: (Boolean) -> Unit,
    onDialogError: (DialogErrorState) -> Unit
) {
    div {
        css {
            marginTop = 48.px
        }
        Button {
            color = ButtonColor.primary
            variant = ButtonVariant.contained
            disabled = !isEnabled
            onClick = {
                process(
                    scope,
                    props,
                    lyricsConversion,
                    lyricsReplacement,
                    slightRestsFilling,
                    pitchConversion,
                    projectZoom,
                    setProcessing,
                    onDialogError
                )
            }
            +string(Strings.NextButton)
        }
    }
}

private fun process(
    scope: CoroutineScope,
    props: ConfigurationEditorProps,
    lyricsConversion: LyricsConversionState,
    lyricsReplacement: LyricsReplacementState,
    slightRestsFilling: SlightRestsFillingState,
    pitchConversion: PitchConversionState,
    projectZoom: ProjectZoomState,
    setProcessing: (Boolean) -> Unit,
    onDialogError: (DialogErrorState) -> Unit
) {
    setProcessing(true)
    scope.launch {
        runCatchingCancellable {
            val format = props.outputFormat
            val fromType = lyricsConversion.fromType
            val toType = lyricsConversion.toType

            val project = props.project
                .let {
                    if (lyricsConversion.isOn && fromType != null && toType != null) {
                        convert(it.copy(lyricsType = fromType), toType, format)
                    } else it
                }
                .let {
                    if (lyricsReplacement.isOn) {
                        it.replaceLyrics(lyricsReplacement.request)
                    } else it
                }
                .let {
                    if (slightRestsFilling.isOn) {
                        it.copy(
                            tracks = it.tracks.map { track ->
                                track.fillRests(slightRestsFilling.excludedMaxLength)
                            }
                        )
                    } else it
                }
                .let {
                    if (projectZoom.isOn) {
                        it.zoom(projectZoom.factorValue)
                    } else it
                }

            val availableFeatures = Feature.values()
                .filter { it.isAvailable.invoke(project) && format.availableFeaturesForGeneration.contains(it) }
                .filter {
                    when (it) {
                        Feature.ConvertPitch -> pitchConversion.isOn
                    }
                }

            delay(100)
            val result = format.generator.invoke(project, availableFeatures)
            console.log(result.blob)
            props.onFinished.invoke(result, format)
        }.onFailure { t ->
            console.log(t)
            setProcessing(false)
            onDialogError(
                DialogErrorState(
                    isShowing = true,
                    title = string(Strings.ProcessErrorDialogTitle),
                    message = t.stackTraceToString()
                )
            )
        }
    }
}

external interface ConfigurationEditorProps : Props {
    var project: Project
    var outputFormat: Format
    var onFinished: (ExportResult, Format) -> Unit
}

external interface SubProps<T : SubState> : Props {
    var initialState: T
    var submitState: StateSetter<T>
}

interface SubState

fun <P : SubProps<T>, T : SubState> subFC(
    block: ChildrenBuilder.(props: P, state: T, editState: (T.() -> T) -> Unit) -> Unit,
) = FC<P> { props ->
    var state by useState(props.initialState)
    fun editState(editor: T.() -> T) {
        val newState = editor(state)
        state = newState
        props.submitState(newState)
    }
    block(props, state, ::editState)
}

data class LyricsConversionState(
    val isOn: Boolean,
    val fromType: LyricsType?,
    val toType: LyricsType?
) : SubState {
    val isReady: Boolean = if (isOn) fromType != null && toType != null else true
}

data class LyricsReplacementState(
    val isOn: Boolean,
    val request: LyricsReplacementRequest
) : SubState {
    val isReady: Boolean = if (isOn) request.isValid else true
}

data class SlightRestsFillingState(
    val isOn: Boolean,
    val excludedMaxLengthDenominator: Int
) : SubState {

    val excludedMaxLength: Long
        get() = (TICKS_IN_FULL_NOTE / excludedMaxLengthDenominator).toLong()
}

data class PitchConversionState(
    val isAvailable: Boolean,
    val isOn: Boolean
) : SubState

data class ProjectZoomState(
    val isOn: Boolean,
    val factor: String,
    val hasWarning: Boolean
) : SubState {
    val factorValue: Double
        get() = factor.evalFractionOrNull()!!
}

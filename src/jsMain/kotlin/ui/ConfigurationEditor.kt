package ui

import csstype.px
import emotion.react.css
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import model.ExportResult
import model.Feature
import model.FeatureConfig
import model.Format
import model.JapaneseLyricsType
import model.Project
import model.TICKS_IN_FULL_NOTE
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import org.w3c.dom.get
import process.RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT
import process.evalFractionOrNull
import process.fillRests
import process.lyrics.LyricsMappingRequest
import process.lyrics.LyricsReplacementRequest
import process.lyrics.chinese.convertChineseLyricsToPinyin
import process.lyrics.japanese.convertJapaneseLyrics
import process.lyrics.mapLyrics
import process.lyrics.replaceLyrics
import process.projectZoomFactorOptions
import process.zoom
import react.ChildrenBuilder
import react.Props
import react.dom.html.ReactHTML.div
import react.useMemo
import react.useState
import ui.common.DialogErrorState
import ui.common.DialogWarningState
import ui.common.ProgressProps
import ui.common.SubState
import ui.common.errorDialog
import ui.common.progress
import ui.common.scopedFC
import ui.common.title
import ui.common.warningDialog
import ui.configuration.ChinesePinyinConversionBlock
import ui.configuration.JapaneseLyricsConversionBlock
import ui.configuration.LyricsMappingBlock
import ui.configuration.LyricsReplacementBlock
import ui.configuration.PitchConversionBlock
import ui.configuration.ProjectSplitBlock
import ui.configuration.ProjectZoomBlock
import ui.configuration.SlightRestsFillingBlock
import ui.strings.Strings
import ui.strings.string
import util.runCatchingCancellable
import util.runIf
import util.runIfAllNotNull

val ConfigurationEditor = scopedFC<ConfigurationEditorProps> { props, scope ->
    var progress by useState(ProgressProps.Initial)

    val (japaneseLyricsConversion, setJapaneseLyricsConversion) = useState {
        getStateFromLocalStorage<JapaneseLyricsConversionState>("japaneseLyricsConversion")?.let {
            return@useState it
        }

        val japaneseLyricsAnalysedType =
            props.projects.map { it.japaneseLyricsType }.distinct().singleOrNull() ?: JapaneseLyricsType.Unknown
        val doJapaneseLyricsConversion = japaneseLyricsAnalysedType != JapaneseLyricsType.Unknown
        val fromLyricsType: JapaneseLyricsType?
        val toLyricsType: JapaneseLyricsType?

        if (doJapaneseLyricsConversion) {
            fromLyricsType = japaneseLyricsAnalysedType
            toLyricsType = japaneseLyricsAnalysedType.findBestConversionTargetIn(props.outputFormat)
        } else {
            fromLyricsType = null
            toLyricsType = null
        }
        JapaneseLyricsConversionState(
            doJapaneseLyricsConversion,
            japaneseLyricsAnalysedType,
            fromLyricsType,
            toLyricsType,
        )
    }
    val (chinesePinyinConversion, setChinesePinyinConversion) = useState {
        getStateFromLocalStorage<ChinesePinyinConversionState>("chinesePinyinConversion")?.let {
            return@useState it
        }

        ChinesePinyinConversionState(
            isOn = false,
        )
    }
    val (lyricsReplacement, setLyricsReplacement) = useState {
        getStateFromLocalStorage<LyricsReplacementState>("lyricsReplacement")?.let {
            return@useState it
        }

        val preset = LyricsReplacementRequest.getPreset(props.projects.first().format, props.outputFormat)
        if (preset == null) {
            LyricsReplacementState(false, LyricsReplacementRequest())
        } else {
            LyricsReplacementState(true, preset)
        }
    }
    val (lyricsMapping, setLyricsMapping) = useState {
        getStateFromLocalStorage<LyricsMappingState>("lyricsMapping")?.let {
            return@useState it
        }
        LyricsMappingState(
            isOn = false,
            presetName = null,
            request = LyricsMappingRequest(),
        )
    }
    val (slightRestsFilling, setSlightRestsFilling) = useState {
        getStateFromLocalStorage<SlightRestsFillingState>("slightRestsFilling")?.let {
            return@useState it
        }
        SlightRestsFillingState(
            true,
            RESTS_FILLING_MAX_LENGTH_DENOMINATOR_DEFAULT,
        )
    }
    val (pitchConversion, setPitchConversion) = useState {
        getStateFromLocalStorage<PitchConversionState>("pitchConversion")?.let {
            return@useState it
        }
        val hasPitchData = props.projects.any { Feature.ConvertPitch.isAvailable(it) }
        val isPitchConversionAvailable = hasPitchData &&
            props.outputFormat.availableFeaturesForGeneration.contains(Feature.ConvertPitch)
        PitchConversionState(
            isAvailable = isPitchConversionAvailable,
            isOn = isPitchConversionAvailable,
        )
    }
    val (projectZoom, setProjectZoom) = useState {
        getStateFromLocalStorage<ProjectZoomState>("projectZoom")?.let {
            return@useState it
        }
        ProjectZoomState(
            isOn = false,
            factor = projectZoomFactorOptions.first(),
            hasWarning = false,
        )
    }
    val (projectSplit, setProjectSplit) = useState {
        getStateFromLocalStorage<ProjectSplitState>("projectSplit")?.let {
            return@useState it
        }
        ProjectSplitState(
            isAvailable = props.outputFormat.availableFeaturesForGeneration.contains(Feature.SplitProject),
            isOn = false,
            maxTrackCountInput = FeatureConfig.SplitProject.getDefault(props.outputFormat).maxTrackCount.toString(),
        )
    }
    var dialogError by useState(DialogErrorState())

    fun isReady() = listOf(
        japaneseLyricsConversion,
        chinesePinyinConversion,
        lyricsReplacement,
        lyricsMapping,
        slightRestsFilling,
        pitchConversion,
        projectZoom,
        projectSplit,
    ).all { it.isReady }

    fun closeErrorDialog() {
        dialogError = dialogError.copy(isShowing = false)
    }

    title(Strings.ConfigurationEditorCaption)
    JapaneseLyricsConversionBlock {
        this.projects = props.projects
        this.outputFormat = props.outputFormat
        initialState = japaneseLyricsConversion
        submitState = setJapaneseLyricsConversion
    }
    ChinesePinyinConversionBlock {
        initialState = chinesePinyinConversion
        submitState = setChinesePinyinConversion
    }
    LyricsReplacementBlock {
        initialState = lyricsReplacement
        submitState = setLyricsReplacement
    }
    LyricsMappingBlock {
        initialState = lyricsMapping
        submitState = setLyricsMapping
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
        this.projects = props.projects
        initialState = projectZoom
        submitState = setProjectZoom
    }
    if (projectSplit.isAvailable) ProjectSplitBlock {
        initialState = projectSplit
        submitState = setProjectSplit
    }
    buildNextButton(
        scope,
        props,
        isEnabled = isReady(),
        japaneseLyricsConversion,
        chinesePinyinConversion,
        lyricsReplacement,
        lyricsMapping,
        slightRestsFilling,
        pitchConversion,
        projectZoom,
        projectSplit,
        setProgress = { progress = it },
        onDialogError = { dialogError = it },
    )

    errorDialog(
        state = dialogError,
        close = { closeErrorDialog() },
    )

    progress(progress)

    multipleModeWarning(props.projects, props.outputFormat)
}

private inline fun <reified T> ChildrenBuilder.getStateFromLocalStorage(name: String): T? {
    runCatching {
        window.localStorage[name]?.let {
            return json.decodeFromString(it)
        }
    }.onFailure {
        console.log(it)
    }
    return null
}

private fun ChildrenBuilder.buildNextButton(
    scope: CoroutineScope,
    props: ConfigurationEditorProps,
    isEnabled: Boolean,
    japaneseLyricsConversion: JapaneseLyricsConversionState,
    chinesePinyinConversion: ChinesePinyinConversionState,
    lyricsReplacement: LyricsReplacementState,
    lyricsMapping: LyricsMappingState,
    slightRestsFilling: SlightRestsFillingState,
    pitchConversion: PitchConversionState,
    projectZoom: ProjectZoomState,
    projectSplit: ProjectSplitState,
    setProgress: (ProgressProps) -> Unit,
    onDialogError: (DialogErrorState) -> Unit,
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
                    japaneseLyricsConversion,
                    chinesePinyinConversion,
                    lyricsReplacement,
                    lyricsMapping,
                    slightRestsFilling,
                    pitchConversion,
                    projectZoom,
                    projectSplit,
                    setProgress,
                    onDialogError,
                )
            }
            +string(Strings.NextButton)
        }
    }
}

private fun process(
    scope: CoroutineScope,
    props: ConfigurationEditorProps,
    japaneseLyricsConversion: JapaneseLyricsConversionState,
    chinesePinyinConversion: ChinesePinyinConversionState,
    lyricsReplacement: LyricsReplacementState,
    lyricsMapping: LyricsMappingState,
    slightRestsFilling: SlightRestsFillingState,
    pitchConversion: PitchConversionState,
    projectZoom: ProjectZoomState,
    projectSplit: ProjectSplitState,
    setProgress: (ProgressProps) -> Unit,
    onDialogError: (DialogErrorState) -> Unit,
) {
    scope.launch {
        runCatchingCancellable {
            val format = props.outputFormat
            val results = props.projects.mapIndexed { index, inputProject ->
                setProgress(ProgressProps(isShowing = true, total = props.projects.size, current = index + 1))
                val project = inputProject
                    .runIf(japaneseLyricsConversion.isOn) {
                        val fromType = japaneseLyricsConversion.fromType
                        val toType = japaneseLyricsConversion.toType
                        runIfAllNotNull(fromType, toType) { nonNullFromType, nonNullToType ->
                            convertJapaneseLyrics(copy(japaneseLyricsType = nonNullFromType), nonNullToType, format)
                        }
                    }
                    .runIf(chinesePinyinConversion.isOn) {
                        convertChineseLyricsToPinyin(this)
                    }
                    .runIf(lyricsReplacement.isOn) {
                        replaceLyrics(lyricsReplacement.request)
                    }
                    .runIf(lyricsMapping.isOn) {
                        mapLyrics(lyricsMapping.request)
                    }
                    .runIf(slightRestsFilling.isOn) {
                        fillRests(slightRestsFilling.excludedMaxLength)
                    }
                    .runIf(projectZoom.isOn) {
                        zoom(projectZoom.factorValue)
                    }

                val featureConfigs = buildList {
                    if (pitchConversion.isOn) add(FeatureConfig.ConvertPitch)
                    if (projectSplit.isOn) add(FeatureConfig.SplitProject(projectSplit.maxTrackCountInput.toInt()))
                }.filter {
                    it.type.isAvailable.invoke(project) &&
                        format.availableFeaturesForGeneration.contains(it.type)
                }

                delay(100)
                val result = format.generator.invoke(project, featureConfigs)
                console.log("Finished processing project ${project.name}. ${index + 1}/${props.projects.size}")
                result
            }
            val currentConfigs = mapOf(
                "japaneseLyricsConversion" to japaneseLyricsConversion,
                "chinesePinyinConversion" to chinesePinyinConversion,
                "lyricsReplacement" to lyricsReplacement,
                "lyricsMapping" to lyricsMapping,
                "slightRestsFilling" to slightRestsFilling,
                "pitchConversion" to pitchConversion,
                "projectZoom" to projectZoom,
                "projectSplit" to projectSplit,
            )
            window.localStorage.setItem("currentConfigs", json.encodeToString(currentConfigs))

            // adjust results to make every fileName unique
            val adjustedResults = results.mapIndexed { index, result ->
                val fileName = result.fileName
                val adjustedFileName = if (results.any { it != result && it.fileName == fileName }) {
                    val extension = fileName.substringAfterLast(".")
                    val name = fileName.substringBeforeLast(".")
                    "$name-$index.$extension"
                } else {
                    fileName
                }
                ExportResult(blob = result.blob, fileName = adjustedFileName, notifications = result.notifications)
            }

            props.onFinished.invoke(adjustedResults, format)
        }.onFailure { t ->
            console.log(t)
            setProgress(ProgressProps.Initial)
            onDialogError(
                DialogErrorState(
                    isShowing = true,
                    title = string(Strings.ProcessErrorDialogTitle),
                    message = t.stackTraceToString(),
                ),
            )
        }
    }
}

private fun ChildrenBuilder.multipleModeWarning(projects: List<Project>, outputFormat: Format) {
    val shouldWarn = useMemo(projects, outputFormat) {
        projects.size > 1 && projects.first().format.multipleFile && outputFormat.multipleFile.not()
    }
    val (closed, setClosed) = useState(false)
    warningDialog(
        id = "isMultipleModeForSingleFileFormat",
        state = DialogWarningState(
            isShowing = shouldWarn && !closed,
            title = string(Strings.MultipleModeForMultipleFileFormatWarningTitle),
            message = string(Strings.MultipleModeForMultipleFileFormatWarningDescription),
        ),
        close = { setClosed(true) },
    )
}

private val json = Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        polymorphic(SubState::class, JapaneseLyricsConversionState::class, JapaneseLyricsConversionState.serializer())
        polymorphic(SubState::class, ChinesePinyinConversionState::class, ChinesePinyinConversionState.serializer())
        polymorphic(SubState::class, LyricsReplacementState::class, LyricsReplacementState.serializer())
        polymorphic(SubState::class, LyricsMappingState::class, LyricsMappingState.serializer())
        polymorphic(SubState::class, SlightRestsFillingState::class, SlightRestsFillingState.serializer())
        polymorphic(SubState::class, PitchConversionState::class, PitchConversionState.serializer())
        polymorphic(SubState::class, ProjectZoomState::class, ProjectZoomState.serializer())
        polymorphic(SubState::class, ProjectSplitState::class, ProjectSplitState.serializer())
    }
}

external interface ConfigurationEditorProps : Props {
    var projects: List<Project>
    var outputFormat: Format
    var onFinished: (List<ExportResult>, Format) -> Unit
}

@Serializable
data class JapaneseLyricsConversionState(
    val isOn: Boolean,
    val detectedType: JapaneseLyricsType,
    val fromType: JapaneseLyricsType?,
    val toType: JapaneseLyricsType?,
) : SubState() {
    override val isReady: Boolean get() = if (isOn) fromType != null && toType != null else true
}

@Serializable
data class ChinesePinyinConversionState(
    val isOn: Boolean,
) : SubState()

@Serializable
data class LyricsReplacementState(
    val isOn: Boolean,
    val request: LyricsReplacementRequest,
) : SubState() {
    override val isReady: Boolean get() = if (isOn) request.isValid else true
}

@Serializable
data class LyricsMappingState(
    val isOn: Boolean,
    val presetName: String?,
    val request: LyricsMappingRequest,
) : SubState() {
    override val isReady: Boolean get() = if (isOn) request.isValid else true

    fun updatePresetName() = when {
        presetName == null -> this
        LyricsMappingRequest.findPreset(presetName) == request -> this
        else -> copy(presetName = null)
    }
}

@Serializable
data class SlightRestsFillingState(
    val isOn: Boolean,
    val excludedMaxLengthDenominator: Int,
) : SubState() {

    val excludedMaxLength: Long
        get() = (TICKS_IN_FULL_NOTE / excludedMaxLengthDenominator).toLong()
}

@Serializable
data class PitchConversionState(
    val isAvailable: Boolean,
    val isOn: Boolean,
) : SubState()

@Serializable
data class ProjectZoomState(
    val isOn: Boolean,
    val factor: String,
    val hasWarning: Boolean,
) : SubState() {
    val factorValue: Double
        get() = factor.evalFractionOrNull()!!
}

@Serializable
data class ProjectSplitState(
    val isAvailable: Boolean,
    val isOn: Boolean,
    val maxTrackCountInput: String,
) : SubState() {
    override val isReady: Boolean
        get() = if (isOn) {
            val maxTrackCount = maxTrackCountInput.toIntOrNull()
            maxTrackCount != null && maxTrackCount > 0
        } else true
}
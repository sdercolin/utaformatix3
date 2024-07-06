package ui.configuration

import core.process.lyrics.LyricsReplacementRequest
import csstype.AlignSelf
import csstype.Display
import csstype.Length
import csstype.Margin
import csstype.VerticalAlign
import csstype.WhiteSpace
import csstype.em
import csstype.px
import emotion.react.css
import kotlinx.js.jso
import mui.icons.material.AddCircle
import mui.icons.material.ArrowDownward
import mui.icons.material.ArrowUpward
import mui.icons.material.HelpOutline
import mui.icons.material.RemoveCircle
import mui.material.BaseTextFieldProps
import mui.material.Button
import mui.material.ButtonColor
import mui.material.ButtonVariant
import mui.material.FormControl
import mui.material.FormControlVariant
import mui.material.FormGroup
import mui.material.FormLabel
import mui.material.IconButton
import mui.material.IconButtonColor
import mui.material.MenuItem
import mui.material.Paper
import mui.material.StandardTextFieldProps
import mui.material.TextField
import mui.material.Tooltip
import mui.material.TooltipPlacement
import mui.material.Typography
import mui.material.styles.TypographyVariant
import mui.system.sx
import react.ChildrenBuilder
import react.ElementType
import react.create
import react.dom.html.ReactHTML.div
import ui.LyricsReplacementState
import ui.appTheme
import ui.common.SubProps
import ui.common.configurationSwitch
import ui.common.subFC
import ui.strings.Strings
import ui.strings.string

external interface LyricsReplacementProps : SubProps<LyricsReplacementState>

val LyricsReplacementBlock = subFC<LyricsReplacementProps, LyricsReplacementState> { _, state, editState ->
    FormGroup {
        div {
            configurationSwitch(
                isOn = state.isOn,
                onSwitched = { editState { copy(isOn = it) } },
                labelStrings = Strings.LyricsReplacement,
            )
            Tooltip {
                val text = string(
                    Strings.LyricsReplacementDescription,
                    "regex" to string(Strings.LyricsReplacementMatchTypeRegex),
                    "matchType" to string(Strings.LyricsReplacementMatchTypeLabel),
                    "to" to string(Strings.LyricsReplacementToTextLabel),
                )
                title = div.create {
                    css { whiteSpace = WhiteSpace.preLine }
                    +text
                }
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

    if (state.isOn) buildLyricsReplacementDetail(state, editState)
}

private fun ChildrenBuilder.buildLyricsReplacementDetail(
    state: LyricsReplacementState,
    editState: (LyricsReplacementState.() -> LyricsReplacementState) -> Unit,
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
                    paddingTop = 8.px
                    paddingBottom = 8.px
                }
                state.request.items.indices.forEach { index ->
                    buildLyricsReplacementItem(index, state.request, editState)
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
    request: LyricsReplacementRequest,
    editState: (LyricsReplacementState.() -> LyricsReplacementState) -> Unit,
) {
    val item = request.items[index]
    fun editRequest(block: LyricsReplacementRequest.() -> LyricsReplacementRequest) {
        editState { copy(request = request.block()) }
    }

    fun editItem(block: LyricsReplacementRequest.Item.() -> LyricsReplacementRequest.Item) {
        editRequest { update(index, block) }
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
                sx { minWidth = 8.em }
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
                sx { minWidth = 8.em }
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
            disabled = index == 0
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
            disabled = index == request.items.size - 1
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

private val LyricsReplacementRequest.FilterType.strings
    get() = when (this) {
        LyricsReplacementRequest.FilterType.None -> Strings.LyricsReplacementFilterTypeNone
        LyricsReplacementRequest.FilterType.Exact -> Strings.LyricsReplacementFilterTypeExact
        LyricsReplacementRequest.FilterType.Containing -> Strings.LyricsReplacementFilterTypeContaining
        LyricsReplacementRequest.FilterType.Prefix -> Strings.LyricsReplacementFilterTypePrefix
        LyricsReplacementRequest.FilterType.Suffix -> Strings.LyricsReplacementFilterTypeSuffix
        LyricsReplacementRequest.FilterType.Regex -> Strings.LyricsReplacementFilterTypeRegex
    }

private val LyricsReplacementRequest.MatchType.strings
    get() = when (this) {
        LyricsReplacementRequest.MatchType.All -> Strings.LyricsReplacementMatchTypeAll
        LyricsReplacementRequest.MatchType.Exact -> Strings.LyricsReplacementMatchTypeExact
        LyricsReplacementRequest.MatchType.Regex -> Strings.LyricsReplacementMatchTypeRegex
    }

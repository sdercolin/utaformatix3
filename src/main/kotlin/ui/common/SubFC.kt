package ui.common

import kotlinx.serialization.Serializable
import react.ChildrenBuilder
import react.FC
import react.Props
import react.StateSetter
import react.useState

external interface SubProps<T : SubState> : Props {
    var initialState: T
    var submitState: StateSetter<T>
}

@Serializable
abstract class SubState

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

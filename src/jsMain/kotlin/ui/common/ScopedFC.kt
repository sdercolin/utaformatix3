package ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import react.ChildrenBuilder
import react.FC
import react.Props
import react.useEffectOnce

fun <P : Props> scopedFC(
    block: ChildrenBuilder.(props: P, scope: CoroutineScope) -> Unit,
) = FC<P> { props ->
    val scope = CoroutineScope(Dispatchers.Default)

    useEffectOnce {
        cleanup { scope.cancel() }
    }

    block(props, scope)
}

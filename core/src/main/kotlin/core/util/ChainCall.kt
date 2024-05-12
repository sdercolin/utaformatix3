package core.util

inline fun <T : Any> T.runIf(
    condition: Boolean,
    block: T.() -> T,
): T = if (condition) block(this) else this

inline fun <T : Any> T.runIf(
    condition: T.() -> Boolean,
    block: T.() -> T,
): T = if (condition(this)) block(this) else this

inline fun <T : Any, R1 : Any, R2 : Any> T.runIfAllNotNull(
    parameter1: R1?,
    parameter2: R2?,
    block: T.(R1, R2) -> T,
): T = if (parameter1 != null && parameter2 != null) block(parameter1, parameter2) else this

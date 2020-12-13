package process.pitch

import model.KEY_CENTER_C
import model.LOG_FRQ_CENTER_C
import model.LOG_FRQ_DIFF_ONE_KEY

fun Double.loggedFrequencyToKey() = KEY_CENTER_C + (this - LOG_FRQ_CENTER_C) / LOG_FRQ_DIFF_ONE_KEY
fun Double.keyToLoggedFrequency() = (this - KEY_CENTER_C) * LOG_FRQ_DIFF_ONE_KEY + LOG_FRQ_CENTER_C

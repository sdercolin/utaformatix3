package core.process

import core.model.Project
import core.model.TickCounter
import core.model.TimeSignature
import core.model.Track

fun Project.lengthLimited(maxLength: Long): Project {
    val tracks = this.tracks.map { it.lengthLimited(maxLength) }
    val tickCounter = TickCounter()
    val timeSignatures = mutableListOf<TimeSignature>()
    this.timeSignatures.forEach {
        tickCounter.goToMeasure(it)
        if (tickCounter.tick <= maxLength) timeSignatures.add(it)
    }
    val tempos = this.tempos.filter { it.tickPosition <= maxLength }
    return copy(
        tracks = tracks,
        timeSignatures = timeSignatures,
        tempos = tempos,
    )
}

private fun Track.lengthLimited(maxLength: Long): Track {
    val notes = this.notes
        .filter { it.tickOff <= maxLength }
        .mapIndexed { index, note -> note.copy(id = index) }
    val pitch = this.pitch?.copy(data = this.pitch.data.filter { it.first <= maxLength })

    return copy(
        notes = notes,
        pitch = pitch,
    )
}

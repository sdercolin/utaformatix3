package io

import external.JsZip
import external.JsZipOption
import kotlinx.coroutines.await
import model.DEFAULT_METER_HIGH
import model.DEFAULT_METER_LOW
import model.ExportNotification
import model.ExportResult
import model.Feature
import model.Format
import model.Project
import model.Track
import org.khronos.webgl.Uint8Array
import org.w3c.files.Blob
import process.pitch.UtauMode1NotePitchData
import process.pitch.UtauMode1TrackPitchData
import process.pitch.pitchToUtauMode1Track
import util.encode
import util.getSafeFileName
import util.padStartZero
import util.toFixed

//Only contains generator, as Mode2 generator has not been implemented.
object UstMode1 {

    private fun makePitchDataString(pitches : UtauMode1NotePitchData?): String?{
        pitches ?: return null
        return pitches.pitchData?.data?.joinToString(separator = ",") { (it.second ?: 0.0).toInt().toString() }
    }

    suspend fun generate(project: Project, features: List<Feature>): ExportResult {
        val zip = JsZip()
        for (track in project.tracks) {
            val content = generateTrackContent(project, track, features)
            val contentEncodedArray = content.encode("SJIS")
            val trackNameUrlSafe = getSafeFileName(track.name)
            val trackFileName = "${project.name}_${track.id + 1}_$trackNameUrlSafe${Format.UST.extension}"
            zip.file(trackFileName, Uint8Array(contentEncodedArray))
        }
        val option = JsZipOption().also { it.type = "blob" }
        val blob = zip.generateAsync(option).await() as Blob
        val name = project.name + ".zip"
        val notifications = mutableListOf<ExportNotification>()
        if (project.tempos.distinctBy { it.bpm }.count() > 1) {
            notifications.add(ExportNotification.TempoChangeIgnored)
        }
        if (project.timeSignatures.any { it.numerator != DEFAULT_METER_HIGH || it.denominator != DEFAULT_METER_LOW }) {
            notifications.add(ExportNotification.TimeSignatureIgnored)
        }
        if (features.contains(Feature.CONVERT_PITCH)) {
            notifications.add(ExportNotification.PitchDataExported)
        }
        return ExportResult(blob, name, notifications)
    }

    private fun generateTrackContent(project: Project, track: Track, features: List<Feature>): String {
        val builder = object {
            var content = ""
                private set

            fun appendLine(line: String) {
                content += "$line${LINE_SEPARATOR}"
            }
        }
        builder.appendLine("[#VERSION]")
        builder.appendLine("UST Version1.2")
        builder.appendLine("[#SETTING]")
        val bpm = project.tempos.first().bpm.toFixed(2)
        builder.appendLine("Tempo=$bpm")
        builder.appendLine("Tracks=1")
        builder.appendLine("ProjectName=${track.name}")
        var tickPos = 0L
        var restCount = 0
        var pitchData: UtauMode1TrackPitchData? = null
        if (features.contains(Feature.CONVERT_PITCH))
        {
            pitchData = pitchToUtauMode1Track(track.pitch, track.notes)
        }
        for ((index, note) in track.notes.withIndex()) {
            if (tickPos < note.tickOn) {
                val restNoteNumber = (note.id + restCount).padStartZero(4)
                builder.appendLine("[#$restNoteNumber]")
                builder.appendLine("Length=${note.tickOn - tickPos}")
                builder.appendLine("Lyric=R")
                builder.appendLine("NoteNum=60")
                restCount++
            }
            val noteNumber = (note.id + restCount).padStartZero(4)
            builder.appendLine("[#$noteNumber]")
            builder.appendLine("Length=${note.length}")
            builder.appendLine("Lyric=${note.lyric}")
            builder.appendLine("NoteNum=${note.key}")
            builder.appendLine("PreUtterance=")
            if (features.contains(Feature.CONVERT_PITCH)){
                builder.appendLine("PBType=5")
                val pitchString = makePitchDataString(pitchData?.notes?.get(index))
                builder.appendLine("Piches=${pitchString}")
                builder.appendLine("Pitches=${pitchString}")
                builder.appendLine("PitchBend=${pitchString}")
                builder.appendLine("PBStart=0")
            }
            tickPos = note.tickOff
        }
        builder.appendLine("[#TRACKEND]")
        return builder.content
    }

    private const val LINE_SEPARATOR = "\r\n"
    const val PITCH_SAMPLING_INTERVAL_TICK = 5L
}

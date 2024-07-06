package core.model

import core.process.phonemes.PhonemesMappingRequest

class PhonemesMappingPreset(
    val sourceFormats: List<Format>,
    val targetFormats: List<Format>,
    val name: String,
    val phonemesMap: PhonemesMappingRequest,
)

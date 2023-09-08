package external

object Resources {
    val vsqxTemplate: String
        get() = require("format_templates/template.vsqx").default as String

    val vprTemplate: String
        get() = require("format_templates/template.vprjson").default as String

    val ccsTemplate: String
        get() = require("format_templates/template.ccs").default as String

    val musicXmlTemplate: String
        get() = require("format_templates/template.musicxml").default as String

    val svpTemplate: String
        get() = require("format_templates/template.svp").default as String

    val s5pTemplate: String
        get() = require("format_templates/template.s5p").default as String

    val ustxTemplate: String
        get() = require("format_templates/template.ustx").default as String

    val vocaloidMidIcon: String
        get() = require("images/vocaloid1.png").default as String

    val vsqIcon: String
        get() = require("images/vocaloid2.png").default as String

    val vsqxIcon: String
        get() = require("images/vocaloid4.png").default as String

    val vprIcon: String
        get() = require("images/vocaloid5.png").default as String

    val ustIcon: String
        get() = require("images/utau.png").default as String

    val ustxIcon: String
        get() = require("images/openutau.png").default as String

    val ccsIcon: String
        get() = require("images/cevio.png").default as String

    val svpIcon: String
        get() = require("images/svr2.png").default as String

    val s5pIcon: String
        get() = require("images/svr1.png").default as String

    val dvIcon: String
        get() = require("images/dv.png").default as String

    val standardMidiIcon: String
        get() = require("images/midi.png").default as String

    val ufdataIcon: String
        get() = require("images/ufdata.png").default as String

    val chineseLyricsDictionaryText: String
        get() = require("texts/mandarin-pinyin-dict.txt").default as String

    val lyricsMappingVxBetaJaText: String
        get() = require("texts/vxbeta-japanese-mapping.txt").default as String
}

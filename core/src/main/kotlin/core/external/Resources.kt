package core.external

object Resources {
    val vsqxTemplate: String
        get() = require("./format_templates/template.vsqx").default as String

    val vprTemplate: String
        get() = require("./format_templates/template.vprjson").default as String

    val ccsTemplate: String
        get() = require("./format_templates/template.ccs").default as String

    val musicXmlTemplate: String
        get() = require("./format_templates/template.musicxml").default as String

    val tsslnTemplate: Array<Byte>
        get() =
            require("./format_templates/template.tssln.json") as Array<Byte>
    val svpTemplate: String
        get() = require("./format_templates/template.svp").default as String

    val s5pTemplate: String
        get() = require("./format_templates/template.s5p").default as String

    val ustxTemplate: String
        get() = require("./format_templates/template.ustx").default as String

    val chineseLyricsDictionaryText: String
        get() = require("./texts/mandarin-pinyin-dict.txt").default as String

    val lyricsMappingVxBetaJaText: String
        get() = require("./texts/vxbeta-japanese-mapping.txt").default as String
}

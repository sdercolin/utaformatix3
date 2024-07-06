package ui

object Resources {

    val vocaloidMidIcon: String
        get() = core.external.require("./images/vocaloid1.png").default as String

    val vsqIcon: String
        get() = core.external.require("./images/vocaloid2.png").default as String

    val vsqxIcon: String
        get() = core.external.require("./images/vocaloid4.png").default as String

    val vprIcon: String
        get() = core.external.require("./images/vocaloid5.png").default as String

    val ustIcon: String
        get() = core.external.require("./images/utau.png").default as String

    val ustxIcon: String
        get() = core.external.require("./images/openutau.png").default as String

    val ccsIcon: String
        get() = core.external.require("./images/cevio.png").default as String

    val svpIcon: String
        get() = core.external.require("./images/svr2.png").default as String

    val s5pIcon: String
        get() = core.external.require("./images/svr1.png").default as String

    val dvIcon: String
        get() = core.external.require("./images/dv.png").default as String

    val standardMidiIcon: String
        get() = core.external.require("./images/midi.png").default as String

    val ufdataIcon: String
        get() = core.external.require("./images/ufdata.png").default as String

    val tsslnIcon: String
        get() = core.external.require("./images/voisona.png").default as String
}

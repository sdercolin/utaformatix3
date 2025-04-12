package ui

import kotlinx.js.jso
import mui.material.PaletteMode
import mui.material.styles.createTheme

val appTheme =
    createTheme(
        jso {
            palette =
                jso {
                    mode = PaletteMode.dark
                    primary =
                        jso {
                            main = "#3b3b3b"
                            light = "#757575"
                            dark = "#212121"
                            contrastText = "#e8e8e8"
                        }
                    secondary =
                        jso {
                            main = "#f48fb1"
                            light = "#f0afc5"
                            dark = "#fff"
                        }
                    background =
                        jso {
                            default = "#303030"
                            paper = "#3b3b3b"
                        }
                }
        },
    )

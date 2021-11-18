# UtaFormatix3

UtaFormatix is an application for converting projects among singing voice synthesizer softwares.

The current version `3.x` is built with [Kotlin for JavaScript](https://kotlinlang.org/docs/reference/js-overview.html) and [React](https://github.com/facebook/react).

## Features

- Supported importing formats: `.vsqx(3/4)`, `.vpr`, `.vsq`, `.mid(VOCALOID)`, `.ust`, `.ccs`,`.xml(MusicXML)`, `.musicxml`, `.svp`, `.s5p`, `.dv`, `.ppsf(NT)`
- Supported exporting formats: `.vsqx(4)`, `.vpr`, `.vsq`, `.mid(VOCALOID)`, `.ust`, `.ccs`, `.xml(MusicXML)`, `.svp`, `.s5p`, `.dv`
- Keep information including: tracks, notes, tempo labels, time signatures
- Detect and convert Japanese lyrics types
  - between CV and VCV
  - between Kana and Romaji
- Convert pitch for the following supported formats
  
  |        Format        | Pitch import | Vibrato import | Pitch export | 
  | -------------------- | ------------ | -------------- | ------------ |
  | VSQ/VSQX/VPR/MID(V1) |       ✓      |                |       ✓      |
  |      UST(mode2)      |       ✓      |        ✓       |       ✓      |
  |      UST(mode1)      |       ✓      |       N/A      |       ✓      |
  |         CCS          |       ✓      |                |       ✓      |
  |         SVP          |       ✓      |        ✓       |       ✓      |
  |         S5P          |       ✓      |                |       ✓      |
  |         DV           |       ✓      |        ✓       |       ✓      |
  
## Contributors

[sdercolin](https://github.com/sdercolin), [ghosrt](https://github.com/ghosrt), [shine5402](https://github.com/shine5402), [時雨ゆん](https://twitter.com/Yun_Shigure)

## Get started for development
1. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/)
2. Clone and import as a Gradle project
3. Configure IDEA's Gradle settings with `JDK11+` and `Use Gradle from: gradle-wrapper.properties file`
4. Run by `./gradlew run` or Gradle Task `other/run`

## License
[Apache License, Version 2.0](https://github.com/sdercolin/utaformatix3/blob/master/LICENSE.md)

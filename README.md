# UtaFormatix3

UtaFormatix is an application for converting projects among singing voice synthesizer softwares.

The current version `3.x` is built with [Kotlin for JavaScript](https://kotlinlang.org/docs/reference/js-overview.html) and [React](https://github.com/facebook/react).

## Features

- Supported importing formats: `.vsqx(3/4)`, `.vpr`, `.ust`, `.ccs`, `.svp`, `.s5p`
- Supported exporting formats: `.vsqx(4)`, `.vpr`, `.ust`, `.ccs`, `.xml(MusicXML)`, `.svp`, `.s5p`
- Keep information including: tracks, notes, tempo labels, time signatures
- Detect and convert Japanese lyrics types
  - between CV and VCV
  - between Kana and Romaji
  
## Contributors

[sdercolin](https://github.com/sdercolin), [ghosrt](https://github.com/ghosrt), [時雨ゆん](https://twitter.com/Yun_Shigure)

## Get started for development
1. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/)
2. Clone and import as a Gradle project
3. Run by `./gradlew run`

## License
[Apache License, Version 2.0](https://github.com/sdercolin/utaformatix3/blob/master/LICENSE.md)

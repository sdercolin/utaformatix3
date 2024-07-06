# UtaFormatix3

[![Discord](https://img.shields.io/discord/984044285584359444?style=for-the-badge&label=discord&logo=discord&logoColor=ffffff&color=7389D8&labelColor=6A7EC2)](https://discord.gg/TyEcQ6P73y)

UtaFormatix is an application for converting projects among singing voice synthesizer softwares.

The current version `3.x` is built with [Kotlin for JavaScript](https://kotlinlang.org/docs/js-overview.html)
and [React](https://github.com/facebook/react).

## Features

- Supported importing
  formats: `.vsqx(3/4)`, `.vpr`, `.vsq`, `.mid(VOCALOID)`, `.mid(standard)`, `.ust`, `.ustx`, `.ccs`,`.xml(MusicXML)`
  , `.musicxml`, `.svp`, `.s5p`, `.dv`, `.ppsf(NT)`, `.tssln`, `.ufdata`
- Supported exporting
  formats: `.vsqx(4)`, `.vpr`, `.vsq`, `.mid(VOCALOID)`, `.mid(standard)`, `.ust`, `.ustx`, `.ccs`, `.xml(MusicXML)`
  , `.svp`, `.s5p`, `.dv`, `.tssln`, `.ufdata`
- Keep information including: tracks, notes, tempo labels, time signatures
- Detect and convert Japanese lyrics types
    - between CV and VCV
    - between Kana and Romaji
- Find/Replace texts in the lyrics
- Lyrics mapping with customized dictionaries
- Convert phonemes of the notes with or without mapping with customized dictionaries (only available for VOCALOID
  projects, `.ustx`, `.svp` and `.ufdata`.)
- Project zooming, changing tempo and time signatures without changing the actual time duration of the contents
- Project splitting with a max track count in each project for SVP export
- Convert pitch for the following supported formats

| Format               | Pitch import | Vibrato import | Pitch export |
|----------------------|--------------|----------------|--------------|
| VSQ/VSQX/VPR/MID(V1) | ✓            |                | ✓            |
| UST(mode2)           | ✓            | ✓              | ✓            |
| UST(mode1)           | ✓            | N/A            | ✓            |
| USTX                 | ✓            | ✓              | ✓            |
| CCS                  | ✓            |                | ✓            |
| SVP                  | ✓            | ✓              | ✓            |
| S5P                  | ✓            |                | ✓            |
| DV                   | ✓            | ✓              | ✓            |

## Open format published (.ufdata)

We have published the internal data format of UtaFormatix
to [UtaFormatix Data](https://github.com/sdercolin/utaformatix-data).

If you are developing OSS projects related to singing voice synthesis, you may find it useful.

## Contributors

[sdercolin](https://github.com/sdercolin), [ghosrt](https://github.com/ghosrt), [shine5402](https://github.com/shine5402), [adlez27](https://github.com/adlez27), [
General Nuisance](https://github.com/GeneralNuisance0), [sevenc-nanashi](https://github.com/sevenc-nanashi)

## Localization help

[時雨ゆん](https://twitter.com/Yun_Shigure), [KagamineP](https://github.com/KagamineP)
, [Exorcism0666](https://github.com/Exorcism0666)

## Get started for development

1. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/)
2. Clone and import as a Gradle project
3. Configure IDEA's Gradle settings with `JDK 17` and `Use Gradle Wrapper`
4. Run by `./gradlew jsRun` or Gradle Task `other/jsRun`
5. Optionally, run by `./gradlew jsRun --continuous` with live reloading enabled

## Contribution

Code contribution is welcomed. Basically, please cut your branch from `develop` and make Pull Requests towards `develop`
branch.

#### Adding a format support

Please check [Format.kt](src/jsMain/kotlin/model/Format.kt) and its usages.

#### Adding a Language

Please check [Strings.kt](src/jsMain/kotlin/ui/strings/Strings.kt).

#### Adding a configurable process

Please check [ConfigurationEditor.kt](src/jsMain/kotlin/ui/ConfigurationEditor.kt)
about how the existing processes work.

#### Build/Format check

Pull requests require build check to be merged. Besides normal building of the project, a format check is conducted.
Please confirm that the `build` and `ktlintCheck` Gradle tasks pass before submitting your code.

You may find `ktlintFormat` task helpful, which helps fix most format problems.

If your IDE's formatter is conflicting with `ktlint`, please import format settings
from [.editorconfig](.editorconfig) (IntelliJ IDEA uses it by default).

## License

[Apache License, Version 2.0](LICENSE.md)

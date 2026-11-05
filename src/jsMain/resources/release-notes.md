# UtaFormatix - Release Notes

#### 3.25 (2026.11.5)
- Add TuneLab file (.tlp) support (thanks @Krono159)
- Fix USTX import failing on files without some optional fields (e.g. phonemizer)
- Fix USTX import for files written by newer OpenUtau versions
- Fix tempo changes in the middle of a measure being lost on MusicXML import
- Speed up pitch conversion, especially for UTAU and VOCALOID formats

#### 3.24 (2025.5.19)
- Add phoneme support for CCS
- Optimize (speedup) pitch import from SVP

#### 3.23 (2025.4.13)
- Framework upgrade
- Fix some issues about Standard MIDI import
- Fix some issues about Ufdata (created by other services) import

#### 3.22.1 (2024.7.29)
- Fix some issues about UST/USTX pitch import
 
#### 3.22 (2024.7.6)
- Add Tssln (Voisona) file basic support (without pitch conversion)
- Fix the Japanese Lyrics Conversion: No longer remove `っ` by default
- Update some texts and error messages

#### 3.21.2 (2024.4.27)
- Fix some phoneme conversion presets

#### 3.21.1 (2024.4.24)
- Fix phoneme output for VOCALOID formats

#### 3.21 (2024.4.24)
- Support phoneme conversion.

#### 3.20.2 (2024.1.3)
- Support track name conversion with new versions of USTX.

#### 3.20.1 (2024.1.2)
- Fix issues around conversion configs.

#### 3.20 (2023.12.20)
- Add project split feature for SVP export (to be used in SV basic).
- Fix some minor issues.

#### 3.19 (2023.9.9)
- Add lyrics mapping feature.

#### 3.18.5 (2023.8.1)
- Fix a minor issue when importing UST with incomplete VBR values.

#### 3.18.4 (2023.6.15)
- Fix a minor issue when importing UST with tempo settings.

#### 3.18.3 (2023.6.6)
- Fix import from MusicXml when there's no tempo or time signature.

#### 3.18.2 (2023.5.10)
- Fix output file name for VSQ and MID(V1)

#### 3.18.1 (2023.5.2)
- Fix format matching for VSQ

#### 3.18 (2023.4.28)
- Add support for Standard MIDI file (.mid)

#### 3.17.1 (2023.3.1)
- Fix pitch import from ust mode2

#### 3.17 (2023.1.13)
- Add `Batch Conversion` mode in the import screen
- Fix unset tempo in ust to default (120bpm)

#### 3.16.2 (2022.12.15)
- Fix pitch import from ust mode2

#### 3.16.1 (2022.10.19)
- Add `About Usage of Google Analytcis` in the footer

#### 3.16 (2022.9.29)
- Add `Convert lyrics in Chinese characters to Pinyin` option
- Fix some issues in pitch converion to ccs
- Add some presets for lyrics replacement

#### 3.15.2（2022.9.10）
- Fix some pitch parsing issues, especially from ustx

#### 3.15.1（2022.9.8）
- Fix import of vsq/mid(VOCALOID)

#### 3.15（2022.9.7）
- Support multiple tempos and time signatures for ustx 0.6
- Write multiple tempos to ust
- Fix some parsing issues of svp

#### 3.14 (2022.8.20)
- Add UtaFormatix Data format support (.ufdata)

#### 3.13 (2022.8.4)
- Add lyrics replacement feature
- Add French Language
- Fix a minor issue in pitch conversion to ccs

#### 3.12 (2022.7.21)
- Fix pitch conversion from ccs to ustx
- Adjust UI slightly
- Codebase overhaul

#### 3.11.1 (2022.7.13)
- Add Russian language

#### 3.11 (2022.6.21)
- Add OpenUtau project (.ustx) support
- Add project zoom in/out feature

#### 3.10.4 (2022.6.9)
- Fix issues about `ust` vibrato import

#### 3.10.3 (2022.6.7)
- Fix an issue about `ust` pitch import since `3.10.1`

#### 3.10.2 (2022.6.6)
- Fix an issue when importing unpitched notes from `musicxml`

#### 3.10.1 (2022.3.30)
- Fix an issue about `ust` pitch import

#### 3.10 (2022.3.26)
- Add `Simple Import` option to import screen for skipping pitch import
- Fix an issue about about pitch conversion to `ccs`
- Fix an issue about `dv` import

#### 3.9.2 (2021.11.22)
- Fix an issue about `s5p` import

#### 3.9.1 (2021.11.18)
- Fix issues about pitch export to `ust` pitch mode2

#### 3.9 (2021.11.18)
- Add pitch conversion to UTAU project (.ust) using pitch mode2
- Fix some minor issues

#### 3.8.1 (2021.9.21)
- Fix an issue when importing `ust` files with certain locale/languages

#### 3.8 (2021.8.12)
- Add VOCALOID1 project (.mid) support
- Add pitch conversion from/to DeepVocal project (.dv)
- Add pitch conversion to UTAU project (.ust) using pitch mode1

#### 3.7.1 (2021.7.18)
- Fix lyrics encoding for `vsq` import 

#### 3.7 (2021.7.9)
- Convert "-" and "ー" in lyrics when exporting `ust`
- Change `musicxml` exported extension to `.musicxml`
- Fix some issues for `musicxml` import
- Limit output tick length for `vsq` export, to filter irregular input

#### 3.6.4 (2021.5.14)
- Fix issue for vibrato import from `ust`

#### 3.6.3 (2021.3.31)
- Fix issue for vibrato import from `svp`

#### 3.6.2 (2021.3.14)
- Improve compatibilities for handling `vpr`, `svp`, `s5p`, `ppsf`

#### 3.6.1 (2021.3.13)
- Fix issue for ouput `vpr` files while editing customized voice color, breath, etc.

#### 3.6 (2021.2.14)
- Add MusicXML (.xml/.musicxml) import support
- Fix issue for "-" in lyrics output to CeVIO Project

#### 3.5 (2021.2.2)
- Add vibrato import from Synthesizer V Studio project (.svp)
- Add pitch import from UTAU project (.ust) which use pitch mode1
- Add FAQ and release notes

#### 3.4.2 (2021.1.20)
- Fix issue about pitch import from UTAU project (.ust)

#### 3.4.1 (2021.1.16)
- Fix issue about pitch import from UTAU project (.ust)

#### 3.4 (2021.1.15)
- Add new feature: pitch conversion among VOCALOID, UTAU, CeVIO and Synthesizer V Studio projects

#### 3.3.2 (2020.12.20)
- Fix issue: output `.dv` file's vibrato settings cannot be edited 

#### 3.3.1 (2020.12.9)
- Keep x-sampa data while converting among VOCALOID projects (.vsq/.vsqx/.vpr)

#### 3.3 (2020.11.29)
- Add Piapro Studio NT project (.ppsf) import support

#### 3.2 (2020.10.20)
- Add VOCALOID2 project (.vsq) support
- Add new feature: filling short rests

#### 3.1 (2020.8.12)
- Add DeepVocal project (.dv) support

#### 3.0.1 (2020.8.5)
- Fix issue about VOCALOID5 project (.vpr) import

#### 3.0 (2020.7.19)
- First release of `UtaFormatix3`

package ui.strings

import io.MusicXml.MUSIC_XML_VERSION
import ui.strings.Language.English
import ui.strings.Language.Japanese
import ui.strings.Language.SimplifiedChinese

enum class Strings(val en: String, val ja: String, val zhCN: String) {
    LanguageDisplayName(
        en = English.displayName,
        ja = Japanese.displayName,
        zhCN = SimplifiedChinese.displayName
    ),
    ReportFeedbackTooltip(
        en = "Send feedback",
        ja = "フィードバックを送信",
        zhCN = "提交反馈"
    ),
    FrequentlyAskedQuestionTooltip(
        en = "Frequently Asked Questions",
        ja = "よくある質問",
        zhCN = "常见问题解答"
    ),
    ImportProjectCaption(
        en = "Import Project",
        ja = "プロジェクトをインポート",
        zhCN = "导入工程"
    ),
    SelectOutputFormatCaption(
        en = "Select Output Format",
        ja = "出力形式を選ぶ",
        zhCN = "选择输出格式"
    ),
    ConfigurationEditorCaption(
        en = "Configuration",
        ja = "設定",
        zhCN = "设置"
    ),
    ExportCaption(
        en = "Export",
        ja = "エクスポート",
        zhCN = "导出"
    ),
    ExporterTitleSuccess(
        en = "Process finished successfully.",
        ja = "処理に成功しました。",
        zhCN = "处理已完成。"
    ),
    LyricsTypeUnknown(
        en = "Unknown",
        ja = "不明",
        zhCN = "未知"
    ),
    LyricsTypeRomajiCV(
        en = "Romaji CV",
        ja = "単独音（ローマ字）",
        zhCN = "罗马字单独音"
    ),
    LyricsTypeRomajiVCV(
        en = "Romaji VCV",
        ja = "連続音（ローマ字）",
        zhCN = "罗马字连续音"
    ),
    LyricsTypeKanaCV(
        en = "Kana CV",
        ja = "単独音（ひらがな）",
        zhCN = "假名单独音"
    ),
    LyricsTypeKanaVCV(
        en = "Kana VCV",
        ja = "連続音（ひらがな）",
        zhCN = "假名连续音"
    ),
    JapaneseLyricsConversionSwitchLabel(
        en = "Cleanup and convert lyrics (only for Japanese lyrics)",
        ja = "歌詞のSuffix除去・変換（日本語歌詞のみ対応）",
        zhCN = "清理并转换歌词（仅日语）"
    ),
    FromLyricsTypeLabel(
        en = "Original lyrics type (analysis result: {{type}})",
        ja = "変換元の歌詞タイプを選択（分析結果：{{type}}）",
        zhCN = "原歌词类型（分析结果为：{{type}}）"
    ),
    ToLyricsTypeLabel(
        en = "Target lyrics type",
        ja = "変換先の歌詞タイプを選択",
        zhCN = "目标歌词类型"
    ),
    ConvertPitchData(
        en = "Convert pitch parameters",
        ja = "ピッチパラメータを変換",
        zhCN = "转换音高参数"
    ),
    ConvertPitchDataDescription(
        en = "It may take some time to process with this option.",
        ja = "処理に時間がかかることがあります。",
        zhCN = "该选项可能会增加处理时间。"
    ),
    NextButton(
        en = "Next",
        ja = "次へ",
        zhCN = "下一步"
    ),
    CancelButton(
        en = "Cancel",
        ja = "キャンセル",
        zhCN = "取消"
    ),
    ReportButton(
        en = "Report",
        ja = "問題を報告",
        zhCN = "提交报告"
    ),
    ImportFileDescription(
        en = "Drop files or Click to import",
        ja = "ファイルをドロップするか、クリックしてインポート",
        zhCN = "拖放文件或点击导入"
    ),
    ImportFileSubDescription(
        en = "Supported file types: VSQX, VPR, VSQ, MID(VOCALOID), USTs," +
                " USTX, CCS, MUSICXML, XML, SVP, S5P, DV, PPSF(NT)",
        ja = "サポートされているファイル形式：VSQX、VPR、VSQ、MID（VOCALOID）、UST（複数可）、" +
                "USTX、CCS、MUSICXML、XML、SVP、S5P、DV、PPSF（NT）",
        zhCN = "支持的文件类型：VSQX、VPR、VSQ、MID（VOCALOID）、UST（允许复数个）、" +
                "USTX、CCS、MUSICXML、XML、SVP、S5P、DV、PPSF（NT）"
    ),
    UnsupportedFileTypeImportError(
        en = "Unsupported file type",
        ja = "サポートされていないファイル形式です",
        zhCN = "不支持的文件类型"
    ),
    MultipleFileImportError(
        en = "Multiple files of {{format}} could not be imported in one go",
        ja = "複数の{{format}}ファイルを一度にインポートすることはできません",
        zhCN = "无法同时导入多个{{format}}文件"
    ),
    ImportErrorDialogTitle(
        en = "Failed to import the project",
        ja = "プロジェクトのインポートに失敗しました",
        zhCN = "无法导入该工程"
    ),
    ProcessErrorDialogTitle(
        en = "An error happened during the process",
        ja = "処理中に下記のエラーが発生しました",
        zhCN = "在处理中发生了以下错误"
    ),
    ErrorDialogDescription(
        en = "If you find any problems, please help us collect error information" +
                " for better performance of this application by submitting a feedback report.",
        ja = "サービス向上のためにエラー情報を収集しております。問題を発見した場合、フィードバックにご協力をお願いします。",
        zhCN = "如您在使用中发现问题，您可以向提交反馈表单。感谢您对本应用的改善所提供的帮助！"
    ),
    ReportUrl(
        en = "https://forms.gle/3Es3ZomcYKNHWBvp6",
        ja = "https://forms.gle/kDY9chZBjGATXqpE8",
        zhCN = "https://forms.gle/nJVdrsfwMhbNXEYUA"
    ),
    FaqUrl(
        en = "https://gist.githubusercontent.com/sdercolin/4d835e7e201a39504f5321f67d254209/raw",
        ja = "https://gist.githubusercontent.com/sdercolin/f1de7c1f7a894f1fc8f77b17f3e8f77d/raw",
        zhCN = "https://gist.githubusercontent.com/sdercolin/1a940a1357e2a6a5c10561482536bdba/raw"
    ),
    ReleaseNotesUrl(
        en = "https://gist.githubusercontent.com/sdercolin/512db280480072f22cf1d462401eb1a0/raw",
        ja = "https://gist.githubusercontent.com/sdercolin/512db280480072f22cf1d462401eb1a0/raw",
        zhCN = "https://gist.githubusercontent.com/sdercolin/512db280480072f22cf1d462401eb1a0/raw"
    ),
    ImportWarningTitle(
        en = "The following exceptions happened during the import process.",
        ja = "インポート中に下記の例外が発生しました。",
        zhCN = "导入过程中出现了下列异常。"
    ),
    ImportWarningTempoNotFound(
        en = "- No tempo labels found in the imported project.",
        ja = "- テンポ記号が見つかりませんでした。",
        zhCN = "- 在导入的工程中未找到速度记号。"
    ),
    ImportWarningTempoIgnoredInFile(
        en = "- Tempo label ({{bpm}}) at tick {{tick}} in file [{{file}}] was ignored.",
        ja = "- ファイル[{{file}}]の tick {{tick}} にあるテンポ記号（{{bpm}}）を読み込めませんでした。",
        zhCN = "- 未能读取文件[{{file}}]中 tick {{tick}} 处的速度记号（{{bpm}}）。"
    ),
    ImportWarningTempoIgnoredInTrack(
        en = "- Tempo label ({{bpm}}) at tick {{tick}} in Track {{number}}: [{{name}}] was ignored.",
        ja = "- トラック{{number}}：[{{name}}]のtick {{tick}}にあるテンポ記号（{{bpm}}）を読み込めませんでした。",
        zhCN = "- 未能读取音轨{{number}}：[{{name}}]中tick {{tick}}处的速度记号（{{bpm}}）。"
    ),
    ImportWarningTempoIgnoredInPreMeasure(
        en = "- Tempo label ({{bpm}}) in pre-measures was ignored.",
        ja = "- プリメジャーにあるテンポ記号（{{bpm}}）を読み込めませんでした。",
        zhCN = "- 未能读取前置小节中的速度记号（{{bpm}}）。"
    ),
    ImportWarningTimeSignatureNotFound(
        en = "- No time signature labels found in the imported project.",
        ja = "- 拍子記号が見つかりませんでした。",
        zhCN = "- 在导入的工程中未找到节拍记号。"
    ),
    ImportWarningTimeSignatureIgnoredInTrack(
        en = "- Time signature label ({{timeSignature}}) at measure {{measure}}" +
                " in Track {{number}}: [{{name}}] was ignored.",
        ja = "- トラック{{number}}：[{{name}}]の小節{{measure}}にある拍子記号（{{timeSignature}}）を読み込めませんでした。",
        zhCN = "- 未能读取音轨{{number}}: [{{name}}]中小节{{measure}}处的节拍记号（{{timeSignature}}）。"
    ),
    ImportWarningTimeSignatureIgnoredInPreMeasure(
        en = "- Time signature label ({{timeSignature}}) in pre-measures was ignored.",
        ja = "- プリメジャーにある拍子記号（{{timeSignature}}）を読み込めませんでした。",
        zhCN = "- 未能读取前置小节中的节拍记号（{{timeSignature}}）。"
    ),
    VsqxFormatDescription(
        en = "Project for VOCALOID4",
        ja = "VOCALOID4 プロジェクト",
        zhCN = "VOCALOID4 工程"
    ),
    VprFormatDescription(
        en = "Project for VOCALOID5",
        ja = "VOCALOID5 プロジェクト",
        zhCN = "VOCALOID5 工程"
    ),
    UstFormatDescription(
        en = "Project for UTAU",
        ja = "UTAU プロジェクト",
        zhCN = "UTAU 工程"
    ),
    UstxFormatDescription(
        en = "Project for OpenUtau",
        ja = "OpenUtau プロジェクト",
        zhCN = "OpenUtau 工程"
    ),
    CcsFormatDescription(
        en = "Project for CeVIO Creative Studio",
        ja = "CeVIO Creative Studio プロジェクト",
        zhCN = "CeVIO Creative Studio 工程"
    ),
    SvpFormatDescription(
        en = "Project for Synthesizer V Studio",
        ja = "Synthesizer V Studio プロジェクト",
        zhCN = "Synthesizer V Studio 工程"
    ),
    S5pFormatDescription(
        en = "Project for Synthesizer V",
        ja = "Synthesizer V プロジェクト",
        zhCN = "Synthesizer V 工程"
    ),
    MusicXmlFormatDescription(
        en = "MusicXML $MUSIC_XML_VERSION (CeVIO style)",
        ja = "MusicXML $MUSIC_XML_VERSION （CeVIO基準に準じる）",
        zhCN = "MusicXML $MUSIC_XML_VERSION（参照CeVIO标准）"
    ),
    DvFormatDescription(
        en = "Project for DeepVocal",
        ja = "DeepVocal プロジェクト",
        zhCN = "DeepVocal 工程"
    ),
    VsqFormatDescription(
        en = "Project for VOCALOID2",
        ja = "VOCALOID2 プロジェクト",
        zhCN = "VOCALOID2 工程"
    ),
    VocaloidMidiFormatDescription(
        en = "Project for VOCALOID1",
        ja = "VOCALOID1 プロジェクト",
        zhCN = "VOCALOID1 工程"
    ),
    ExportButton(
        en = "Export",
        ja = "エクスポート",
        zhCN = "导出"
    ),
    RestartButton(
        en = "Back to the beginning",
        ja = "プロジェクトインポート画面に戻る",
        zhCN = "回到初始页面"
    ),
    ExportNotificationPhonemeResetRequiredVSQ(
        en = "Phonemes of all notes were set to \"a\". Please reset them to make it sound correctly.",
        ja = "全てのノートの発音記号が\"a\"に設定されました。正確に発音させるには発音記号をリセットしてください。",
        zhCN = "所有音符的音素都被设为了\"a\"。请重置音素以使其正确发音。"
    ),
    ExportNotificationPhonemeResetRequiredV4(
        en = "Phonemes of all notes were set to \"a\"." +
                " Please use \"Lyrics\" -> \"Convert Phonemes\" in the menu of VOCALOID4 to reset them.",
        ja = "全てのノートの発音記号が\"a\"に設定されました。" +
                "VOCALOID4のメニューから「歌詞」->「発音記号変換」機能で発音記号をリセットしてください。",
        zhCN = "所有音符的音素都被设为了\"a\"。请使用VOCALOID4菜单中的「歌词」->「音位变换」功能来重置音素。"
    ),
    ExportNotificationPhonemeResetRequiredV5(
        en = "Phonemes of all notes were set to \"a\". Please use \"Job\" -> \"Convert Phonemes to Match Languages\"" +
                " in the menu of VOCALOID5 to reset them.",
        ja = "全てのノートの発音記号が\"a\"に設定されました。" +
                "VOCALOID5のメニューから「ジョブ」->「発音記号を言語に合わせて変換」機能で発音記号をリセットしてください。",
        zhCN = "所有音符的音素都被设为了\"a\"。请使用VOCALOID5菜单中的「任务」->「发音符号匹配」功能来重置音素。"
    ),
    ExportNotificationTempoChangeIgnored(
        en = "Could not convert tempo changes to the target format.",
        ja = "テンポの変更を出力することができませんでした。",
        zhCN = "未能将速度的变化导出到目标格式。"
    ),
    ExportNotificationTimeSignatureIgnored(
        en = "Could not convert time signatures to the target format.",
        ja = "拍子記号を出力することができませんでした。",
        zhCN = "未能将节拍记号导出到目标格式。"
    ),
    ExportNotificationPitchDataExported(
        en = "Some pitch parameters were exported. For a higher reproduction accuracy," +
                " you may need to turn off pitch transition and vibrato settings in the target editor.",
        ja = "ピッチパラメータが出力されました。出力側のエディターでピッチ推移やビブラートなどの設定を削除することで、より高い精度でピッチを" +
                "再現できる場合があります。",
        zhCN = "生成的文件中带有音高参数。您可能需要在对象编辑器中关闭音高平滑设置及颤音设置以达到更高的重现精度。"
    ),
    ExportNotificationDataOverLengthLimitIgnored(
        en = "Data exceeding length limit ignored. Please check if your output includes all your intended data.",
        ja = "一部のデータが長さ制限を超えているため、すべてのデータを出力することができませんでした。意図したデータが出力結果にすべて" +
                "入っているかどうかを確認してください。",
        zhCN = "部分数据超过了长度限制而被忽略，请检查生成的文件是否完整包含您所需要的数据。"
    ),
    SlightRestsFillingSwitchLabel(
        en = "Fill short rests",
        ja = "短い休符を埋める",
        zhCN = "填充短休止符"
    ),
    SlightRestsFillingDescription(
        en = "Extend note to fill the short rest between it and its next note",
        ja = "ノート同士の間に短い休符が挟まっている場合、前のノートを伸ばして隙間を埋めます",
        zhCN = "当音符之间存在较短的休止符时，将前一个音符延长来填充休止符"
    ),
    SlightRestsFillingThresholdLabel(
        en = "Max length to be processed (exclusive)",
        ja = "長さが入力値未満の場合処理",
        zhCN = "适用该处理的最大长度（不含）"
    ),
    SlightRestsFillingThresholdItem(
        en = "1/{{denominator}} note",
        ja = "{{denominator}}分音符",
        zhCN = "{{denominator}}分音符"
    ),
    ProcessingOverlay(
        en = "Processing…",
        ja = "処理中…",
        zhCN = "正在处理…"
    ),
    UseSimpleImport(
        en = "Simple Import",
        ja = "シンプルインポート",
        zhCN = "简单导入"
    ),
    UseSimpleImportDescription(
        en = "Ignore detail parameters to accelerate importing",
        ja = "パラメータをインポートしないことで、より早くインポートする",
        zhCN = "忽略参数，使导入更快"
    );

    fun get(language: Language): String = when (language) {
        English -> en
        Japanese -> ja
        SimplifiedChinese -> zhCN
    }
}

fun string(key: Strings, vararg params: Pair<String, String>): String {
    val options = object {}.asDynamic()
    params.forEach { (key, value) ->
        options[key] = value
    }
    return i18next.t(key.name, options) as String
}

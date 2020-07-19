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
    ConfigureLyricsCaption(
        en = "Configure Lyrics",
        ja = "歌詞設定",
        zhCN = "设置歌词"
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
        en = "Supported file types: VSQX, VPR, USTs, CCS, SVP, S5P",
        ja = "サポートされているファイル形式：VSQX、VPR、UST（複数可）、CCS、SVP、S5P",
        zhCN = "支持的文件类型：VSQX、VPR、UST（允许复数个）、CCS、SVP、S5P"
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
        en = "If you find any problems, please help us collect error information for better performance of this application by submitting a feedback report.",
        ja = "サービス向上のためにエラー情報を収集しております。問題を発見した場合、フィードバックにご協力をお願いします。",
        zhCN = "如您在使用中发现问题，您可以向提交反馈表单。感谢您对本应用的改善所提供的帮助！"
    ),
    ReportUrl(
        en = "https://forms.gle/3Es3ZomcYKNHWBvp6",
        ja = "https://forms.gle/kDY9chZBjGATXqpE8",
        zhCN = "https://forms.gle/nJVdrsfwMhbNXEYUA"
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
        en = "- Time signature label ({{timeSignature}}) at measure {{measure}} in Track {{number}}: [{{name}}] was ignored.",
        ja = "- トラック{{number}}：[{{name}}]の小節{{measure}}にある拍子記号（{{timeSignature}}）を読み込めませんでした。",
        zhCN = "- 未能读取音轨{{number}}: [{{name}}]中小节{{measure}}处的节拍记号（{{timeSignature}}）。"
    ),
    ImportWarningTimeSignatureIgnoredInPreMeasure(
        en = "- Time signature label ({{timeSignature}}) in pre-measures was ignored.",
        ja = "- プリメジャーにある拍子記号（{{timeSignature}}）を読み込めませんでした。",
        zhCN = "- 未能读取前置小节中的节拍记号（{{timeSignature}}）。"
    ),
    VSQXFormatDescription(
        en = "Project for VOCALOID4",
        ja = "VOCALOID4 プロジェクト",
        zhCN = "VOCALOID4 工程"
    ),
    VPRFormatDescription(
        en = "Project for VOCALOID5",
        ja = "VOCALOID5 プロジェクト",
        zhCN = "VOCALOID5 工程"
    ),
    USTFormatDescription(
        en = "Project for UTAU",
        ja = "UTAU プロジェクト",
        zhCN = "UTAU 工程"
    ),
    CCSFormatDescription(
        en = "Project for CeVIO Creative Studio",
        ja = "CeVIO Creative Studio プロジェクト",
        zhCN = "CeVIO Creative Studio 工程"
    ),
    SVPFormatDescription(
        en = "Project for Synthesizer V Studio",
        ja = "Synthesizer V Studio プロジェクト",
        zhCN = "Synthesizer V Studio 工程"
    ),
    S5PFormatDescription(
        en = "Project for Synthesizer V",
        ja = "Synthesizer V プロジェクト",
        zhCN = "Synthesizer V 工程"
    ),
    MusicXmlFormatDescription(
        en = "MusicXML $MUSIC_XML_VERSION (CeVIO style)",
        ja = "MusicXML $MUSIC_XML_VERSION （CeVIO基準に準じる）",
        zhCN = "MusicXML $MUSIC_XML_VERSION（参照CeVIO标准）"
    ),
    DownloadButton(
        en = "Download",
        ja = "ダウンロード",
        zhCN = "下载"
    ),
    RestartButton(
        en = "Back to the beginning",
        ja = "ファイルのインポート画面に戻る",
        zhCN = "回到初始页面"
    ),
    ExportNotificationPhonemeResetRequiredV4(
        en = "Phonemes of all notes were set to \"a\"." +
                " Please use \"Lyrics\" -> \"Convert Phonemes\" in the menu of VOCALOID4 to reset them.",
        ja = "全てのノートの発音記号が\"a\"に設定されました。" +
                "VOCALOID4のメニューから「歌詞」->「発音記号変換」機能で発音記号をリセットしてください。",
        zhCN = "所有音符的音素都被设为了\"a\"。请使用VOCALOID4菜单中的「歌词」->「音位变换」功能来重置音素。"
    ),
    ExportNotificationPhonemeResetRequiredV5(
        en = "Phonemes of all notes were set to \"a\"." +
                " Please use \"Job\" -> \"Convert Phonemes to Match Languages\" in the menu of VOCALOID5 to reset them.",
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

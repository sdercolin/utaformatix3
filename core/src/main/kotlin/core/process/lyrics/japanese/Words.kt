package core.process.lyrics.japanese

val String.isKana get() = findKanaIndex(this) != null
val String.isRomaji get() = findRomajiIndex(this) != null

fun findKanaIndex(text: String) = kanas.indexOf(text).takeIf { it >= 0 }
fun findRomajiIndex(text: String) = romajis.indexOf(text).takeIf { it >= 0 }

fun findVowelKana(kana: String): String? {
    val romaji = kanaToRomaji.find { it.first == kana }?.second ?: return null
    val vowelRomaji = romaji.takeLast(1)
    return kanaToRomaji.find { it.second == vowelRomaji }?.first
}

val kanaToRomaji = listOf(
    "あ" to "a",
    "い" to "i",
    "いぇ" to "ye",
    "う" to "u",
    "わ" to "wa",
    "うぁ" to "wa",
    "うぁ" to "ua",
    "うぃ" to "wi",
    "うぃ" to "ui",
    "うぇ" to "we",
    "え" to "e",
    "お" to "o",
    "か" to "ka",
    "が" to "ga",
    "き" to "ki",
    "きぇ" to "kye",
    "きゃ" to "kya",
    "きゅ" to "kyu",
    "きょ" to "kyo",
    "ぎ" to "gi",
    "ぎぇ" to "gye",
    "ぎゃ" to "gya",
    "ぎゅ" to "gyu",
    "ぎょ" to "gyo",
    "く" to "ku",
    "くぁ" to "kua",
    "くぃ" to "kui",
    "くぇ" to "kue",
    "くぉ" to "kuo",
    "ぐ" to "gu",
    "ぐぁ" to "gua",
    "ぐぃ" to "gui",
    "ぐぇ" to "gue",
    "ぐぉ" to "guo",
    "け" to "ke",
    "げ" to "ge",
    "こ" to "ko",
    "ご" to "go",
    "さ" to "sa",
    "ざ" to "za",
    "し" to "shi",
    "し" to "si",
    "しぇ" to "she",
    "しぇ" to "sye",
    "しゃ" to "sha",
    "しゃ" to "sya",
    "しゅ" to "shu",
    "しゅ" to "syu",
    "しょ" to "sho",
    "しょ" to "syo",
    "じ" to "ji",
    "じぇ" to "je",
    "じぇ" to "jye",
    "じゃ" to "ja",
    "じゃ" to "jya",
    "じゅ" to "ju",
    "じゅ" to "jyu",
    "じょ" to "jo",
    "じょ" to "jyo",
    "す" to "su",
    "すぁ" to "sua",
    "すぃ" to "sui",
    "すぇ" to "sue",
    "すぉ" to "suo",
    "ず" to "zu",
    "ずぁ" to "zua",
    "ずぃ" to "zui",
    "ずぇ" to "zue",
    "ずぉ" to "zuo",
    "せ" to "se",
    "ぜ" to "ze",
    "そ" to "so",
    "ぞ" to "zo",
    "た" to "ta",
    "だ" to "da",
    "ち" to "chi",
    "ちぇ" to "che",
    "ちゃ" to "cha",
    "ちゅ" to "chu",
    "ちょ" to "cho",
    "つ" to "tsu",
    "つ" to "tu",
    "つぁ" to "tsa",
    "つぁ" to "tua",
    "つぃ" to "tsi",
    "つぃ" to "tui",
    "つぇ" to "tse",
    "つぇ" to "tue",
    "つぉ" to "tso",
    "つぉ" to "tuo",
    "て" to "te",
    "てぃ" to "ti",
    "てゅ" to "tyu",
    "で" to "de",
    "でぃ" to "di",
    "でゅ" to "dyu",
    "と" to "to",
    "とぅ" to "tu",
    "とぅ" to "twu",
    "ど" to "do",
    "どぅ" to "du",
    "どぅ" to "dwu",
    "な" to "na",
    "に" to "ni",
    "にぇ" to "nye",
    "にゃ" to "nya",
    "にゅ" to "nyu",
    "にょ" to "nyo",
    "ぬ" to "nu",
    "ぬぁ" to "nua",
    "ぬぃ" to "nui",
    "ぬぇ" to "nue",
    "ぬぉ" to "nuo",
    "ね" to "ne",
    "の" to "no",
    "は" to "ha",
    "ば" to "ba",
    "ぱ" to "pa",
    "ひ" to "hi",
    "ひぇ" to "hye",
    "ひゃ" to "hya",
    "ひゅ" to "hyu",
    "ひょ" to "hyo",
    "び" to "bi",
    "びぇ" to "bye",
    "びゃ" to "bya",
    "びゅ" to "byu",
    "びょ" to "byo",
    "ぴ" to "pi",
    "ぴぇ" to "pye",
    "ぴゃ" to "pya",
    "ぴゅ" to "pyu",
    "ぴょ" to "pyo",
    "ふ" to "fu",
    "ふぁ" to "fa",
    "ふぃ" to "fi",
    "ふぇ" to "fe",
    "ふぉ" to "fo",
    "ぶ" to "bu",
    "ぶぁ" to "bua",
    "ぶぃ" to "bui",
    "ぶぇ" to "bue",
    "ぶぉ" to "buo",
    "ぷ" to "pu",
    "ぷぁ" to "pua",
    "ぷぃ" to "pui",
    "ぷぇ" to "pue",
    "ぷぉ" to "puo",
    "へ" to "he",
    "べ" to "be",
    "ぺ" to "pe",
    "ほ" to "ho",
    "ぼ" to "bo",
    "ぽ" to "po",
    "ま" to "ma",
    "み" to "mi",
    "みぇ" to "mye",
    "みゃ" to "mya",
    "みゅ" to "myu",
    "みょ" to "myo",
    "む" to "mu",
    "むぁ" to "mua",
    "むぃ" to "mui",
    "むぇ" to "mue",
    "むぉ" to "muo",
    "め" to "me",
    "も" to "mo",
    "や" to "ya",
    "ゆ" to "yu",
    "よ" to "yo",
    "ら" to "ra",
    "り" to "ri",
    "りぇ" to "rye",
    "りゃ" to "rya",
    "りゅ" to "ryu",
    "りょ" to "ryo",
    "る" to "ru",
    "るぁ" to "rua",
    "るぃ" to "rui",
    "るぇ" to "rue",
    "るぉ" to "ruo",
    "れ" to "re",
    "ろ" to "ro",
    "わ" to "wa",
    "を" to "o",
    "うぉ" to "wo",
    "ん" to "n",
    "ー" to "-",
)

val kanas = kanaToRomaji.map { it.first }
val romajis = kanaToRomaji.map { it.second }
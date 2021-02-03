package ui.strings

import external.require
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import ui.strings.Strings.LanguageDisplayName

val i18next = require("i18next").default

suspend fun initializeI18n(defaultLanguage: Language) = suspendCoroutine<dynamic> { cont ->
    val reactI18next = require("react-i18next").initReactI18next
    val languageDetector = require("i18next-browser-languagedetector").default
    val options = object {}.asDynamic()
    options["detection"] = object {}.also {
        val detection = it.asDynamic()
        detection["order"] = arrayOf("cookie", "localStorage", "navigator")
        detection["caches"] = arrayOf("cookie", "localStorage")
        detection["cookieMinutes"] = 60 * 24 * 90
        detection["lookupCookie"] = "i18next"
        detection["lookupLocalStorage"] = "i18nextLng"
    }
    options["fallbackLng"] = defaultLanguage.code
    options["interpolation"] = object {}.also { it.asDynamic()["escapeValue"] = false }
    options["resources"] = object {}.also { resources ->
        Language.values().forEach { language ->
            val translation = object {}.asDynamic()
            Strings.values().forEach {
                translation[it.name] = it.get(language)
            }
            resources.asDynamic()[language.code] = object {}.also { it.asDynamic()["translation"] = translation }
        }
    }
    i18next
        .use(reactI18next)
        .use(languageDetector)
        .init(options).then {
            val languageName = i18next.t(LanguageDisplayName.name)
            console.log("i18n is initialized with language: $languageName")
            cont.resume(i18next)
        }
}

suspend fun changeLanguage(code: String) = suspendCoroutine<Unit> { cont ->
    i18next.changeLanguage(code)
        .then {
            val languageName = i18next.t(LanguageDisplayName.name)
            console.log("i18n has changed language to: $languageName")
            cont.resume(Unit)
        }
}

package core.io

import core.model.ImportParams
import kotlinx.serialization.json.Json

object ImportParamsJson {
    fun parse(content: String): ImportParams {
        return jsonSerializer.decodeFromString(ImportParams.serializer(), content)
    }

    fun generate(config: ImportParams): String {
        return jsonSerializer.encodeToString(ImportParams.serializer(), config)
    }

    private val jsonSerializer = Json {
        encodeDefaults = true
        isLenient = true
        ignoreUnknownKeys = true
    }
}

package core.model

import com.sdercolin.utaformatix.data.Document

// Container for a Document object for better typing (otherwise Document will be any in .d.ts)
@OptIn(ExperimentalJsExport::class)
@JsExport
class DocumentContainer(@Suppress("NON_EXPORTABLE_TYPE") internal val document: Document)

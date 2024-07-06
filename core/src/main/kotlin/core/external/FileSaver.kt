package core.external

import org.w3c.files.Blob

@JsModule("file-saver")
@JsNonModule
external fun saveAs(blob: Blob, name: String)

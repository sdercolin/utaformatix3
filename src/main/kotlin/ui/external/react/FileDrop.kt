@file:JsModule("react-file-drop")
@file:JsNonModule

package ui.external.react

import org.w3c.dom.events.Event
import org.w3c.files.FileList
import react.RClass
import react.RProps

@JsName("FileDrop")
external val fileDrop: RClass<FileDropProps>

external interface FileDropProps : RProps {
    var onDrop: (FileList, Event) -> Unit
}

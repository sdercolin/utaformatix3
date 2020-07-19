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
    var onFrameDragEnter: (Event) -> Unit
    var onFrameDragLeave: (Event) -> Unit
    var onFrameDrop: (Event) -> Unit
    var onDragOver: (Event) -> Unit
    var onDragLeave: (Event) -> Unit
    var onDrop: (FileList, Event) -> Unit
}

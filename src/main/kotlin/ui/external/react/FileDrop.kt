@file:JsModule("react-file-drop")
@file:JsNonModule

package ui.external.react

import org.w3c.dom.events.Event
import org.w3c.files.FileList
import react.ComponentClass
import react.Props

@JsName("FileDrop")
external val FileDrop: ComponentClass<FileDropProps>

external interface FileDropProps : Props {
    var onDrop: (FileList, Event) -> Unit
}

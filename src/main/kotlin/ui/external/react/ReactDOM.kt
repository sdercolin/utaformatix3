@file:JsModule("react-dom")
@file:JsNonModule

package ui.external.react

import org.w3c.dom.Element

external fun findDOMNode(component: dynamic): Element

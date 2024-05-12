package core.util

import core.exception.IllegalFileException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.get

fun Element.getElementListByTagName(name: String, allowEmpty: Boolean = true) =
    getElementsByTagName(name).let {
        if (!allowEmpty && it.length == 0) throw IllegalFileException.XmlElementNotFound(name)
        else (0 until it.length).map { index -> it[index]!! }
    }

fun Element.getSingleElementByTagName(name: String) =
    getElementsByTagName(name)[0] ?: throw IllegalFileException.XmlElementNotFound(name)

fun Element.getSingleElementByTagNameOrNull(name: String) =
    getElementsByTagName(name)[0]

fun Element.getRequiredAttributeAsInteger(attribute: String) =
    getAttribute(attribute)?.toIntOrNull()
        ?: throw IllegalFileException.XmlElementAttributeValueIllegal(attribute, tagName)

fun Element.getRequiredAttributeAsLong(attribute: String) =
    getAttribute(attribute)?.toLongOrNull()
        ?: throw IllegalFileException.XmlElementAttributeValueIllegal(attribute, tagName)

fun Element.getRequiredAttribute(attribute: String) =
    getAttribute(attribute) ?: throw IllegalFileException.XmlElementAttributeValueIllegal(attribute, tagName)

val Element.innerValue
    get() = try {
        firstChild!!.nodeValue!!
    } catch (t: Throwable) {
        throw IllegalFileException.XmlElementValueIllegal(this.tagName)
    }

val Element.innerValueOrNull get() = firstChild?.nodeValue

fun Element.setSingleChildValue(name: String, value: Any) {
    getSingleElementByTagName(name).firstChild!!.nodeValue = value.toString()
}

fun Element.insertAfterThis(child: Element) = insertAdjacentElement("afterend", child)

fun Element.clone() = cloneNode(true) as Element

fun Document.appendNewChildTo(node: Node, localName: String, handler: (Element) -> Unit) = node.appendChild(
    createElement(localName).also(handler),
)

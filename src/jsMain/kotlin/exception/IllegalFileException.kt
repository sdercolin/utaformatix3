package exception

sealed class IllegalFileException(message: String) : Throwable(message) {
    class UnknownVsqVersion : IllegalFileException("Cannot identify the version of the loaded vsqx file.")
    class XmlRootNotFound : IllegalFileException("The root element is not found in the xml file.")
    class XmlElementNotFound(name: String) :
        IllegalFileException("The required element <$name> is not found in the xml file.")

    class XmlElementValueIllegal(name: String) :
        IllegalFileException("The required element <$name> has an illegal value.")

    class XmlElementAttributeValueIllegal(attribute: String, elementName: String) :
        IllegalFileException(
            "The required attribute \"$attribute\" in element <$elementName> is missing or has in illegal value.",
        )

    class IllegalMidiFile : IllegalFileException("Cannot parse this file as a MIDI file.")
}

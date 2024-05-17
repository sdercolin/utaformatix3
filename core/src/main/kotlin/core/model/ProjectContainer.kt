package core.model

// Container for a Document object for better typing (otherwise Document will be any in .d.ts)
@OptIn(ExperimentalJsExport::class)
@JsExport
class ProjectContainer(@Suppress("NON_EXPORTABLE_TYPE") internal val project: Project)

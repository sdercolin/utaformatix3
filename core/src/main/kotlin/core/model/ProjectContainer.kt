package core.model

/**
 * Container for a Project object for better typing (otherwise Document will be any in .d.ts)
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
class ProjectContainer internal constructor(internal val project: Project)

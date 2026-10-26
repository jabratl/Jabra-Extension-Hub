package com.jabra.hub

/** Single source of truth for the public extension API v1 compatibility contract. */
object JabraApiContract {
    const val API_VERSION = 1
    const val MIN_SUPPORTED_API_VERSION = 1
    const val MAX_PLUGIN_ID_LENGTH = 80

    val capabilities: List<String> = listOf(
        "storage",
        "fileExport",
        "fileShare",
        "fileImportPicker",
        "clipboardWrite",
        "textShare",
        "toast",
        "diagnostics",
        "chapterIntake",
        "clipboardScan",
        "floater",
        "browser",
        "mediaDownload",
        "mediaPlayer",
        "downloadManager",
        "apiVersionInfo"
    )
}

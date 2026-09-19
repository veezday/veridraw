rootProject.name = "veridraw"

fun includeIfExists(projectName: String) {
    val projectDir = file(projectName)
    if (projectDir.exists() && projectDir.isDirectory) {
        include(projectName)
    }
}

includeIfExists("shared")
includeIfExists("gateway")
includeIfExists("draw-core")
includeIfExists("notify-worker")
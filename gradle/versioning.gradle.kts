// x.y.z is user-managed; only the Git hash and original project's date are automatic.
val semanticVersion = providers.gradleProperty("crticket.version").orNull
check(semanticVersion != null && semanticVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
    "Set crticket.version to x.y.z in gradle.properties."
}
val upstreamPath = checkNotNull(providers.gradleProperty("crticket.upstreamMainView").orNull) {
    "Set crticket.upstreamMainView to the original project's MainView.vue."
}
val upstreamSource = checkNotNull(providers.fileContents(
    rootProject.layout.projectDirectory.file(upstreamPath)
).asText.orNull) {
    "Cannot read the original project's MainView.vue: $upstreamPath"
}
val dateMatches = Regex(
    """<h3\b[^>]*>\s*Version\s+([0-9]{6})\s*</h3>""",
    RegexOption.IGNORE_CASE,
).findAll(upstreamSource).toList()
check(dateMatches.size == 1) {
    "Expected one <h3>Version YYMMDD</h3> in $upstreamPath."
}
val upstreamDate = dateMatches.single().groupValues[1]
val previousAndroidVersionCode = 6

fun gitOutput(vararg arguments: String): String? {
    val output = providers.exec {
        workingDir(rootDir)
        commandLine("git", *arguments)
        isIgnoreExitValue = true
    }
    return if (output.result.get().exitValue == 0) {
        output.standardOutput.asText.get().trim().ifEmpty { null }
    } else {
        null
    }
}

check(gitOutput("rev-parse", "--is-inside-work-tree") == "true") {
    "Versioning requires a Git checkout."
}
check(gitOutput("rev-parse", "--is-shallow-repository") == "false") {
    "Versioning requires full Git history; run git fetch --unshallow first."
}

val commitHash = gitOutput("rev-parse", "--short=7", "--verify", "HEAD")
val commitCount = if (commitHash == null) {
    0
} else {
    checkNotNull(gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull()) {
        "Unable to count Git commits."
    }
}
val buildCount = commitCount.coerceAtLeast(1)
val androidVersionName = "$semanticVersion-" +
    "${commitHash ?: "uncommitted"}-$upstreamDate"
// Preserve upgrade compatibility with previously distributed versionCode 6.
val androidVersionCode = previousAndroidVersionCode + buildCount

extra["androidVersionName"] = androidVersionName
extra["androidVersionCode"] = androidVersionCode
version = androidVersionName

tasks.register("printAndroidVersion") {
    group = "help"
    description = "Print the manual version, Git hash and original project's version date."
    val report = listOf(
        "versionName=$androidVersionName",
        "versionCode=$androidVersionCode",
        "commitCount=$commitCount",
    ).joinToString("\n")
    doLast {
        println(report)
    }
}

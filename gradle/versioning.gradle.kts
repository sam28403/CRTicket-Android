// The first commit is 0.3.3; each subsequent commit increments the patch version.
val majorVersion = 0
val minorVersion = 3
val firstPatchVersion = 3
val upstreamDate = "260930"
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
val patchVersion = firstPatchVersion + buildCount - 1
val androidVersionName = "$majorVersion.$minorVersion.$patchVersion-" +
    "${commitHash ?: "uncommitted"}-$upstreamDate"
// Preserve upgrade compatibility with previously distributed versionCode 6.
val androidVersionCode = previousAndroidVersionCode + buildCount

extra["androidVersionName"] = androidVersionName
extra["androidVersionCode"] = androidVersionCode
version = androidVersionName

tasks.register("printAndroidVersion") {
    group = "help"
    description = "Print the Android version derived from Git history."
    val report = listOf(
        "versionName=$androidVersionName",
        "versionCode=$androidVersionCode",
        "commitCount=$commitCount",
    ).joinToString("\n")
    doLast {
        println(report)
    }
}

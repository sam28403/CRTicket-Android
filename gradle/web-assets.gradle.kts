// All Android variants and lint consume the same generated web assets.
val buildWebAssets = tasks.register<Exec>("buildWebAssets") {
    group = "build"
    description = "Build the bundled web resources used by the Android app."
    workingDir(rootProject.file("web"))
    if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        commandLine("cmd.exe", "/d", "/c", "npm.cmd", "run", "build")
    } else {
        commandLine("npm", "run", "build")
    }
    inputs.dir(rootProject.file("web/src"))
    inputs.dir(rootProject.file("web/public"))
    inputs.files(
        rootProject.file("web/package.json"),
        rootProject.file("web/package-lock.json"),
        rootProject.file("web/vite.config.js"),
        rootProject.file("tools/package-web-assets.mjs"),
        rootProject.file("tools/package-arrival-stations.mjs"),
        rootProject.file("tools/package-speedometer-assets.mjs"),
    )
    outputs.dir(project.file("src/main/assets/web"))
    outputs.file(project.file("src/main/assets/arrival-stations.json"))
    outputs.dir(project.file("src/main/assets/speedometer-lib"))
}

tasks.matching { it.name == "preBuild" || it.name.contains("lint", ignoreCase = true) }.configureEach {
    dependsOn(buildWebAssets)
}

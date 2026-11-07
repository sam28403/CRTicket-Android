// Check both upstreams once per build invocation, even when local inputs are unchanged.
val prepareOfflineAssets = tasks.register<Exec>("prepareOfflineAssets") {
    workingDir(rootDir)
    commandLine("node", "tools/offline-snapshot.mjs", "package")
    inputs.file(rootProject.file("offline-data/manifest.json"))
    inputs.file(rootProject.file("tools/offline-snapshot.mjs"))
    inputs.files(fileTree(rootProject.file("web/src/android/railway")) { include("*.js") })
    outputs.dir(layout.buildDirectory.dir("generated/offline-assets"))
    outputs.upToDateWhen { false }
}

tasks.matching {
    it.name == "preCiOfflineBuild" || it.name == "preReleaseOfflineBuild" ||
        it.name.contains("lint", ignoreCase = true) && it.name.contains("Offline")
}.configureEach { dependsOn(prepareOfflineAssets) }

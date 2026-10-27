package com.vincenthzr.locationspoofer.data.model

data class RootSetupTestResult(
    val hasRoot: Boolean,
    val idOutput: String,
    val solution: RootSolution,
    val frameworkConnected: Boolean,
    val remoteConfigAccessible: Boolean,
    val configPublished: Boolean,
    val frameworkDetail: String
) {
    val overallVerified: Boolean get() = hasRoot && frameworkConnected && remoteConfigAccessible
}

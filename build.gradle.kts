plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.jetbrains.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
}

fun git(vararg args: String): String = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrDefault("")

val isCi = System.getenv("GITHUB_ACTIONS") == "true"
val commitSha = git("rev-parse", "HEAD").ifBlank { "unknown" }
val commitCount = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val isDirty = !isCi && git("status", "--porcelain").isNotEmpty()

val branch = when {
    !System.getenv("GITHUB_HEAD_REF").isNullOrBlank() ->
        "pr" + System.getenv("GITHUB_REF").orEmpty().split('/').getOrElse(2) { "" }
    isCi -> System.getenv("GITHUB_REF_NAME").orEmpty()
    else -> git("rev-parse", "--abbrev-ref", "HEAD")
}.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')

val branchPrefix = branch.takeUnless { it.isEmpty() || it == "main" || it == "HEAD" }

extra["appVersionCode"] = commitCount
extra["appVersionName"] =
    listOfNotNull(branchPrefix, commitSha.take(7), "dirty".takeIf { isDirty }).joinToString("-")
extra["appCommitSha"] = commitSha

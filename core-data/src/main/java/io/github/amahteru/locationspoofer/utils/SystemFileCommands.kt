package io.github.amahteru.locationspoofer.utils

internal object SystemFileCommands {
    private const val ROOT = "/proc/1/root"
    private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"

    fun read(paths: List<String>): String {
        require(paths.isNotEmpty() && paths.all { it.startsWith("/data/") && ".." !in it.split('/') })
        return paths.joinToString(" || ") { "cat ${quote(ROOT + it)} 2>/dev/null" }
    }
}

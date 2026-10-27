package com.vincenthzr.locationspoofer.xposed.utils

internal class CallScope<T> {
    private val value = ThreadLocal<T>()
    val current: T? get() = value.get()

    fun <R> withValue(current: T?, block: () -> R): R {
        val previous = value.get()
        if (current == null) value.remove() else value.set(current)
        try {
            return block()
        } finally {
            if (previous == null) value.remove() else value.set(previous)
        }
    }
}

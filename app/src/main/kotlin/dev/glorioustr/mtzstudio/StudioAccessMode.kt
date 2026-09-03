package dev.glorioustr.mtzstudio

/**
 * The strongest capability currently available to MTZ Studio.
 *
 * Shizuku started through wireless debugging is deliberately distinct from root: it runs with
 * Android's shell identity, which can help with intent/package operations but cannot access
 * another application's private data directory.
 */
enum class StudioAccessMode {
    STANDARD,
    SHIZUKU,
    ROOT,
}

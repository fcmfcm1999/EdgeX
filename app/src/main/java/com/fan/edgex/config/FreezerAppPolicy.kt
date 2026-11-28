package com.fan.edgex.config

import java.util.Locale

internal object FreezerAppPolicy {

    fun parseFreezerPackages(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())
    }

    fun <T> mergeLaunchableApps(
        enabledApps: List<T>,
        disabledCandidates: List<T>,
        freezerPackages: Set<String>,
        packageNameOf: (T) -> String,
        enabledOf: (T) -> Boolean,
        labelOf: (T) -> String,
    ): List<T> {
        val seen = linkedSetOf<String>()
        val merged = mutableListOf<T>()

        for (app in enabledApps) {
            val pkg = packageNameOf(app)
            if (pkg.isNotEmpty() && seen.add(pkg)) {
                merged.add(app)
            }
        }

        if (freezerPackages.isNotEmpty()) {
            for (app in disabledCandidates) {
                val pkg = packageNameOf(app)
                if (pkg.isNotEmpty() && pkg in freezerPackages && !enabledOf(app) && seen.add(pkg)) {
                    merged.add(app)
                }
            }
        }

        return merged.sortedBy { labelOf(it).lowercase(Locale.getDefault()) }
    }

    fun packagesToRefreeze(
        freezerPackages: Set<String>,
        enabledStateByPackage: Map<String, Boolean>,
    ): List<String> =
        freezerPackages.filter { pkg -> enabledStateByPackage[pkg] == true }
}

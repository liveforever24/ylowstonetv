package com.ylowstone.tv.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.ylowstone.tv.model.AppIconOption

object AppIconManager {
    fun applyIcon(context: Context, target: AppIconOption) {
        val pm = context.packageManager
        val pkg = context.packageName

        // 1. Enable the chosen alias first so launcher always sees an active entry
        runCatching {
            pm.setComponentEnabledSetting(
                ComponentName(pkg, target.aliasClassName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }

        // 2. Disable all other 11 aliases
        val others = AppIconOption.entries.filter { it != target }
        others.forEachIndexed { idx, opt ->
            val flags = if (idx == others.lastIndex) 0 else PackageManager.DONT_KILL_APP
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, opt.aliasClassName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    flags
                )
            }
        }
    }
}

package com.xalitoria.referrals.spigot.tracking.afk

import com.Zrips.CMI.Modules.Portals.CMIVector3D

class AfkInfo {
    var type: AfkType = AfkType.AUTO

    var afkAt: CMIVector3D? = null

    var afkFrom: Long = 0L

    var kicksPerformed: Int = 0

    var playtimeAtAfkStart: Long = 0L

    var kickOutInSec: Int = 0

    var reason: String? = null
}
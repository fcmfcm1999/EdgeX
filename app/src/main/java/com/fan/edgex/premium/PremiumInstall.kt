package com.fan.edgex.premium

object PremiumInstall {
    const val DIR_PATH = "/data/system/edgex"
    const val DEX_PATH = "$DIR_PATH/premium.dex"
    const val META_PATH = "$DIR_PATH/premium.meta"
    // Legacy path from before Keystore binding; cleaned up on deactivation.
    const val LEGACY_DEVICE_ID_PATH = "$DIR_PATH/device_id"
    const val SUPPORTED_API_VERSION = 2
}

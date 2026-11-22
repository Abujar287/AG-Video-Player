package com.example.util

import android.os.Build

object PreviewEnvironment {

    fun isEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val device = Build.DEVICE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val board = Build.BOARD.lowercase()

        return fp.startsWith("generic") ||
            fp.startsWith("unknown") ||
            fp.contains("emulator") ||
            fp.contains("sdk_gphone") ||
            fp.contains("aosp_cf") ||
            fp.contains("vsoc") ||
            (fp.contains("test-keys") && (
                brand.equals("aosp", ignoreCase = true) ||
                    brand.equals("android", ignoreCase = true) ||
                    model.contains("emulator") ||
                    model.contains("cuttlefish") ||
                    model.contains("gce") ||
                    product.contains("sdk") ||
                    product.contains("cf_") ||
                    hardware.contains("cutf") ||
                    device.contains("vsoc")
                )) ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            model.contains("cuttlefish") ||
            model.contains("gce") ||
            manufacturer.contains("genymotion") ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product.contains("sdk_gphone") ||
            product.contains("sdk_google_phone") ||
            product.contains("google_sdk") ||
            product.contains("sdk_x86") ||
            product.contains("vbox86p") ||
            product.contains("emulator") ||
            product.contains("simulator") ||
            product.contains("cf_x86") ||
            product.contains("cf_arm64") ||
            product.contains("aosp_cf") ||
            product.contains("gce_x86") ||
            device.contains("vsoc") ||
            device.contains("emulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            hardware.contains("cutf_cvm") ||
            hardware.contains("vbox86") ||
            hardware.contains("android_x86") ||
            board.contains("goldfish") ||
            board.contains("ranchu") ||
            board.contains("cutf") ||
            runCatching {
                java.io.File("/dev/qemu_pipe").exists() ||
                    java.io.File("/dev/goldfish_pipe").exists()
            }.getOrDefault(false)
    }
}

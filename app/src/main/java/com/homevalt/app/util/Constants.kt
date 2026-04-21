package com.homevalt.app.util

object Constants {
    const val HEALTH_CHECK_TIMEOUT_MS = 2000L
    const val UPLOAD_DOWNLOAD_TIMEOUT_S = 30L
    const val POLL_INTERVAL_MS = 2000L
    const val MAX_POLL_ATTEMPTS = 10
    const val LOCAL_FAIL_COOLDOWN_MS = 5 * 60 * 1000L
    const val NOTIFICATION_CHANNEL_ID = "transfer_channel"
    const val NOTIFICATION_CHANNEL_NAME = "File Transfers"
    const val UPLOAD_NOTIFICATION_ID = 1001
    const val DOWNLOAD_NOTIFICATION_ID = 1002
    const val SESSION_EXPIRED_NOTIFICATION_ID = 1003
    const val DEFAULT_PUBLIC_URL = "http://10.0.2.2:8080"
    const val DEFAULT_LOCAL_URL = ""
    const val PAGE_SIZE = 20
}

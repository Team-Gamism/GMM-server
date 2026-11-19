package com.gmm.global.support

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/** API dates use millisecond precision (`2026-11-15T12:04:00.000Z`). */
fun Clock.nowMillis(): Instant = instant().truncatedTo(ChronoUnit.MILLIS)

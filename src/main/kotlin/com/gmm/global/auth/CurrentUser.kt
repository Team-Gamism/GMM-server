package com.gmm.global.auth

/**
 * Injects the signed-in [AuthUser]. With [required] = true (default) a missing session gives 401.
 * With [required] = false the parameter must be nullable and receives null for guests.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentUser(val required: Boolean = true)

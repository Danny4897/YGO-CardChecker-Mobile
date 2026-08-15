package com.ygochecker.core.common

sealed interface AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>
    data class Err(val error: AppError) : AppResult<Nothing>
}

data class AppError(val errorKey: String, val detail: String? = null)

val <T> AppResult<T>.isOk get() = this is AppResult.Ok
val <T> AppResult<T>.valueOrNull get() = (this as? AppResult.Ok)?.value
val <T> AppResult<T>.errorOrNull get() = (this as? AppResult.Err)?.error

package com.habit1.app.di

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dependency container interface.
 * Defines dependencies available to ViewModels and services.
 * Implemented manually without reflection or code generation per ADR-01.
 */
interface AppContainer {
    val context: Context
    val ioDispatcher: CoroutineDispatcher
    val defaultDispatcher: CoroutineDispatcher
    val mainDispatcher: CoroutineDispatcher
}

/**
 * Default implementation of AppContainer.
 */
class DefaultAppContainer(
    override val context: Context,
    override val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    override val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    override val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) : AppContainer

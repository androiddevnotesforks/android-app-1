package com.kelsos.mbrc.mvp

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlin.coroutines.CoroutineContext

open class BasePresenter<T : BaseView>(
  private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) : Presenter<T>, LifecycleOwner {
  @Suppress("LeakingThis")
  private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
  override fun getLifecycle(): Lifecycle = this.lifecycleRegistry
  private var view: T? = null
  private lateinit var job: Job
  protected lateinit var scope: CoroutineScope

  private val coroutineContext: CoroutineContext
    get() = job + dispatcher

  override val isAttached: Boolean
    get() = view != null

  override fun attach(view: T) {
    this.view = view
    job = SupervisorJob()
    scope = CoroutineScope(coroutineContext)
    lifecycleRegistry.currentState = Lifecycle.State.CREATED
    lifecycleRegistry.currentState = Lifecycle.State.STARTED
  }

  override fun detach() {
    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    this.view = null
    job.cancelChildren()
  }

  fun view(): T {
    return view ?: throw ViewNotAttachedException()
  }

  fun checkIfAttached() {
    if (!isAttached) {
      throw ViewNotAttachedException()
    }
  }

  protected class ViewNotAttachedException : RuntimeException(
    "Please call Presenter.attach(BaseView) before calling a method on the presenter"
  )
}

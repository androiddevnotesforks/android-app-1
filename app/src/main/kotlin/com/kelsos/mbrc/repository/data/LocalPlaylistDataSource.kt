package com.kelsos.mbrc.repository.data

import com.kelsos.mbrc.data.Playlist
import com.raizlabs.android.dbflow.kotlinextensions.database
import com.raizlabs.android.dbflow.kotlinextensions.delete
import com.raizlabs.android.dbflow.kotlinextensions.from
import com.raizlabs.android.dbflow.kotlinextensions.modelAdapter
import com.raizlabs.android.dbflow.kotlinextensions.select
import com.raizlabs.android.dbflow.list.FlowCursorList
import com.raizlabs.android.dbflow.structure.database.transaction.FastStoreModelTransaction
import rx.Emitter
import rx.Observable
import javax.inject.Inject

class LocalPlaylistDataSource
@Inject constructor(): LocalDataSource<Playlist> {
  override fun deleteAll() {
    delete(Playlist::class).execute()
  }

  override fun saveAll(list: List<Playlist>) {
    val adapter = modelAdapter<Playlist>()

    val transaction = FastStoreModelTransaction.insertBuilder(adapter)
        .addAll(list)
        .build()

    database<Playlist>().executeTransaction(transaction)
  }

  override fun loadAllCursor(): Observable<FlowCursorList<Playlist>> {
    return Observable.fromEmitter({
      val modelQueriable = (select from Playlist::class)
      val cursor = FlowCursorList.Builder(Playlist::class.java).modelQueriable(modelQueriable).build()
      it.onNext(cursor)
      it.onCompleted()
    }, Emitter.BackpressureMode.LATEST)
  }
}
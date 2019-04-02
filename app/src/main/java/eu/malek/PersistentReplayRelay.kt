package eu.malek

import com.jakewharton.rxrelay2.PublishRelay
import com.jakewharton.rxrelay2.ReplayRelay


import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.ReplaySubject
import java.util.HashMap

fun <T> persistentReplay(
    storageId: String,
    storedItemsMaxCount: Int = INFINITE_SIZE,
    scopeMap: HashMap<String, Any>
): (Observable<T>) -> Observable<T> =
    { from ->
        val relay: ReplayRelay<T> = getRelay(scopeMap, storageId, storedItemsMaxCount)
        from.subscribe(relay)
        relay
    }

const val INFINITE_SIZE = -1

/**run.num
 * 1st  -   restore from disk -> restoreState, record emitted items
 * 2nd+ -   restore from cache -> -||-
 * last -   save to disk
 */
fun <T> persistState(
    scopeMap: HashMap<String, Any>,
    storageId: String,
    storedItemsMaxCount: Int = INFINITE_SIZE,
    restoreState: ((Observable<T>) -> Unit)? = null
): (Observable<T>) -> Observable<T> =
    { from: Observable<T> ->
        val restored: ReplayRelay<T> = getRelay(scopeMap, storageId, storedItemsMaxCount)

        var restoredDisposable: Disposable? = null
        if (restoreState != null) {

            restoreState.invoke((
                    if (restored.values.isNotEmpty())
                        ReplaySubject.fromArray<T>(*restored.values as Array<T>)
                    else Observable.empty<T>()
                    )
                .doOnComplete {
                    restoredDisposable = from.subscribe(restored)
                }
            )

        } else restoredDisposable = from.subscribe(restored)

//        val stream = getStream(scopeMap, storageId) { restored }
//
////        val stream = restored
//        stream.doOnDispose { restoredDisposable?.dispose() }


        restored.doOnDispose { restoredDisposable?.dispose() }

    }


fun <T> getRelay(
    scopeMap: HashMap<String, Any>,
    storageId: String,
    storedItemsMaxCount: Int
): ReplayRelay<T> {
    val stored = scopeMap.get(storageId)
    var relay: ReplayRelay<T>? = if (stored is ReplayRelay<*>) {
        stored as ReplayRelay<T>
    } else {
        null
    }
    if (relay == null) {
        relay = createRelay(storedItemsMaxCount)
        scopeMap.put(storageId, relay)
    }
    return relay
}


fun <R, T> getStream(
    scopeMap: HashMap<String, Any>,
    storageId: String,
    transformer: ObservableTransformer<in T, out R>,
    relay: ReplayRelay<T>,
    disposable: Disposable
): Observable<R> {
    val storageKey = storageId + "stream"
    val stored = scopeMap.get(storageKey)
    var stream: Observable<R>? = if (stored is Observable<*>) {
        stored as Observable<R>
    } else {
        null
    }
    if (stream == null) {
        stream = relay.compose(transformer).doOnDispose { disposable.dispose() }
        scopeMap.put(storageKey, stream)
    }
    return stream!!
}

fun <T> getStream(
    scopeMap: HashMap<String, Any>,
    storageId: String,
    factory: () -> Observable<T>
): Observable<T> {
    val storageKey = storageId + "stream"
    val stored = scopeMap.get(storageKey)
    var stream: Observable<T>? = if (stored is Observable<*>) {
        stored as Observable<T>
    } else {
        null
    }
    if (stream == null) {
        stream = factory.invoke()
        scopeMap.put(storageKey, stream)
    }
    return stream!!
}

private fun <T> createRelay(storedItemsMaxCount: Int): ReplayRelay<T> {
    return if (storedItemsMaxCount > 0)
        ReplayRelay.createWithSize<T>(storedItemsMaxCount)
    else
        ReplayRelay.create()
}


val NO_DEFAULT = null
/**
 * Persist state of android view represented by single value (EditText, Button Click, Radio .ie not lists)
 */
fun <T : Any> persistStateOfSingleValueView(
    scopeMap: HashMap<String, Any>,
    storageId: String,
    restoreValue: (T) -> Unit,
    defaultItem: T? = NO_DEFAULT,
    scheduler: Scheduler = AndroidSchedulers.mainThread(),
    storedItemsMaxCount: Int = 1
): (Observable<T>) -> Observable<T> {
    return persistState(
        scopeMap = scopeMap,
        storageId = storageId,
        storedItemsMaxCount = storedItemsMaxCount,
        restoreState = { observable ->
            observable
                .compose({
                    if (defaultItem == NO_DEFAULT) it.lastElement().toObservable() else it.last(defaultItem).toObservable()
                })
                .observeOn(scheduler)
                .subscribeBy(onNext = restoreValue)
        }
    )
}

fun <T> getOrCreateStream(
    appScopeMap: HashMap<String, Any>,
    storageId: String,
    factory: () -> Observable<T>
): Observable<T> {
    return appScopeMap
        .getOrPut(storageId, {
            val published = factory.invoke().publish()
            published.connect()
            published
        }) as Observable<T>

}

/**
 * Return reconnectable observable which inputs are relays
 *
 * usage:
 * val reconnectable:ReconnectableObservableWithInputs  =  getOrCreateStreamWithInput(...)
 *
 * observableInput0.subscribe(reconnectable.input0)
 * observableInput1.subscribe(reconnectable.input1)
 *
 * InputStream.subscribe()
 */
fun getOrCreateStreamWithInput(
    appScopeMap: HashMap<String, Any>,
    storageId: String,
    outputFactory: (ArrayList<*>) -> Observable<*>,
    input0Factory: () -> PublishRelay<*> = {PublishRelay.create<Any>()},
    input1Factory: () -> PublishRelay<*> = {PublishRelay.create<Any>()}
): ReconnectableObservableWithInputs {
    return appScopeMap
        .getOrPut(storageId,
            {
                val input0 = input0Factory.invoke()
                val input1 = input1Factory.invoke()
                val published = outputFactory.invoke(
                    arrayListOf(
                        input0,
                        input1
                    )
                ).publish()

                published.connect()
                ReconnectableObservableWithInputs(published, input0, input1)
            }
        ) as ReconnectableObservableWithInputs

}

/**
 * Observable that can reconnect from both sides - input and output.
 * @param input0 relay which should observe input stream
 */
data class ReconnectableObservableWithInputs(
    val outputStream: Observable<*>,
    val input0: PublishRelay<*>,
    val input1: PublishRelay<*>
)
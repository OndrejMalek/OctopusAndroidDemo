package eu.malek

import com.jakewharton.rxrelay2.ReplayRelay
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.ReplaySubject
import java.util.HashMap


const val INFINITE_SIZE = -1

fun <T> persistentReplay(
    storageId: String,
    storedItemsMaxCount: Int = 1,
    scopeMap: HashMap<String, Any>
): (Observable<T>) -> Observable<T> =
    { from ->
        val relay: ReplayRelay<T> = getRelay(scopeMap, storageId, storedItemsMaxCount)
        from.subscribe(relay)
        relay
    }


fun <T> persistState(
    scopeMap: HashMap<String, Any>,
    storageId: String,
    storedItemsMaxCount: Int = 1,
    restoreState: ((Observable<T>) -> Unit)? = null
): (Observable<T>) -> Observable<T> =
    { from: Observable<T> ->
        val restored: ReplayRelay<T> = getRelay(scopeMap, storageId, storedItemsMaxCount)

        var disposable: Disposable? = null
        if (restoreState != null) {

            restoreState.invoke((
                    if (restored.values.isNotEmpty())
                        ReplaySubject.fromArray<T>(*restored.values as Array<T>)
                    else Observable.empty<T>()
                    )
                .doOnComplete { disposable = from.subscribe(restored) }
            )

        } else disposable = from.subscribe(restored)

//        val stream = getStream(scopeMap, storageId) { restored }
        val stream = restored
        stream.doOnDispose { disposable?.dispose() }
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


/**
 * Persist state of android view represented by single value (EditText, Button Click, Radio .ie not lists)
 */
fun <T : Any> persistSingleStateOfView(
    scopeMap: HashMap<String, Any>,
    storageId: String,
    setValue: (T) -> Unit,
    defaultItem: T? = null,
    scheduler: Scheduler = AndroidSchedulers.mainThread(),
    storedItemsMaxCount:Int = 1
): (Observable<T>) -> Observable<T> {
    return persistState(
        scopeMap = scopeMap,
        storageId = storageId,
        storedItemsMaxCount = storedItemsMaxCount,
        restoreState = { observable ->
            observable
                .compose({
                    if (defaultItem == null) it.lastElement().toObservable() else it.last(defaultItem).toObservable()
                })
                .observeOn(scheduler)
                .subscribeBy(onNext = setValue)
        }
    )
}


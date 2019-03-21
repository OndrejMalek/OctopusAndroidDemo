package eu.malek

import com.jakewharton.rxrelay2.ReplayRelay
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.disposables.Disposable
import java.util.HashMap


const val INFINITE = -1

fun <T> persistentReplay(
    storageId: String,
    storedItemsMaxCount: Int = INFINITE,
    scopeMap: HashMap<String, Any>
): (Observable<T>) -> Observable<T> =
    { from ->
        val relay: ReplayRelay<T> = getRelay(scopeMap, storageId, storedItemsMaxCount)
        from.subscribe(relay)
        relay
    }

fun attachToReplayStream(
    scopeMap: HashMap<String, Any>,
    observableIn: Observable<Int>,
    storageId: String,
    storedItemsMaxCount: Int = INFINITE
): Observable<Int> {
    val relay: ReplayRelay<Int> = getRelay(scopeMap, storageId, storedItemsMaxCount)
    val disposable = observableIn
        .subscribe(relay)

    return getStream(scopeMap, storageId, relay, disposable)
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
    relay: ReplayRelay<T>,
    disposable: Disposable
): Observable<T> {
    val storageKey = storageId + "stream"
    val stored = scopeMap.get(storageKey)
    var stream: Observable<T>? = if (stored is Observable<*>) {
        stored as Observable<T>
    } else {
        null
    }
    if (stream == null) {
        stream = relay.doOnDispose { disposable.dispose() }
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

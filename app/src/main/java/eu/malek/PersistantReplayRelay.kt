package eu.malek

import com.jakewharton.rxrelay2.ReplayRelay
import io.reactivex.Observable
import io.reactivex.functions.Function
import java.util.HashMap


fun <T> persistentReplay(
    storageId: String,
    storedItemsMaxCount: Int = -1,
    scopeMap: HashMap<String, Any>
): (Observable<T>) -> Observable<T> =
    { from ->
        val relay: ReplayRelay<T> = getRelay(scopeMap, storageId, storedItemsMaxCount)
        from.subscribe(relay)
            relay
    }

private fun <T> getRelay(
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

private fun <T> createRelay(storedItemsMaxCount: Int): ReplayRelay<T> {
    return if (storedItemsMaxCount > 0)
        ReplayRelay.createWithSize<T>(storedItemsMaxCount)
    else
        ReplayRelay.create()
}

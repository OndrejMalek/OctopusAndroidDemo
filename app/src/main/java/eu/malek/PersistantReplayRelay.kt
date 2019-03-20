package eu.malek

import com.jakewharton.rxrelay2.ReplayRelay
import io.reactivex.Observable
import java.util.HashMap


fun <T> persistentReplay(
    storageId: String,
    storedItemsMaxCount: Int = -1,
    scopeMap: HashMap<String, Any>
): (Observable<T>) -> Observable<T> =
    { from ->
        val scope = scopeMap

        var relay = scope.get(storageId) as ReplayRelay<T>
        if (relay == null) {
            relay = if (storedItemsMaxCount > 0)
                ReplayRelay.createWithSize<T>(storedItemsMaxCount)
            else
                ReplayRelay.create()

            scope.put(storageId, relay)
        }

        from.subscribe(relay)

        relay
    }

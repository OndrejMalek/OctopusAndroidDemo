package eu.malek

import com.google.common.truth.Truth
import com.google.common.truth.Truth.*
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivecache2.Provider
import io.reactivecache2.ReactiveCache
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.victoralbertos.jolyglot.GsonSpeaker
import org.junit.Ignore
import org.junit.Test

import java.io.File

class PersistentReplayRelayKtTest {

    @Test
    fun testGetOrCreateStreamWithInput() {

        val map = HashMap<String, Any>()
        val (outputStream, input0, input1) = getOrCreateStreamWithInput(
            map,
            "test",
            { inputs: ArrayList<*> ->
                Observable.range(0,5)
                    .mergeWith(inputs[0] as Observable<Int>)
                    .mergeWith(inputs[1] as Observable<Int>)
            },
            { PublishRelay.create<Int>() },
            { PublishRelay.create<Int>() }
        )

        val test: TestObserver<Int> = outputStream.test() as TestObserver<Int>
        val testInput0 = input0.test()
        val testInput1 = input1.test()
        Observable.range(0,3).subscribe(input0 as PublishRelay<Int>)
        Observable.range(10,3).subscribe(input1 as PublishRelay<Int>)

        println(map)
        testInput0.assertValueCount(3)
        testInput1.assertValueCount(3)
        test.assertValues(0, 1, 2, 10, 11, 12)


    }

    @Test
    fun testPersistSingleStateOfView_whenValuesEmitted_SetDefault() {
        var restoredState: Int = 999
        val appScopeMap = HashMap<String, Any>()
        val LAST_VALUE = 4

        Observable.range(0, 5)
            .compose(
                persistStateOfSingleValueView(
                    appScopeMap,
                    "test",
                    { restoredState = it },
                    888,
                    Schedulers.trampoline()
                )
            )
            .test()
            .assertValues(LAST_VALUE)
            .assertOf({
                assertThat(restoredState).isEqualTo(888)
            })
    }

    @Test
    fun testPersistSingleStateOfView_whenValuesNotEmitted_SetDefault() {
        var restoredState: Int = 999
        val appScopeMap = HashMap<String, Any>()

        Observable.empty<Int>()
            .compose(
                persistStateOfSingleValueView(
                    appScopeMap,
                    "test",
                    { restoredState = it },
                    888,
                    Schedulers.trampoline()
                )
            )
            .test()

        assertThat(restoredState).isEqualTo(888)
    }


    @Test
    fun persistState_r() {
        val scopeMap = HashMap<String, Any>()

        var stateFirst: Int? = 999
        val disposable = Observable.range(0, 5)
            .compose(
                persistState(
                    scopeMap = scopeMap,
                    storageId = "test1",
                    restoreState = { restore -> restore.subscribeBy { stateFirst = it } })
            ).subscribe()
        disposable.dispose()

        val disposableRestores = Observable.range(5, 5)
            .compose(
                persistState(
                    scopeMap = scopeMap,
                    storageId = "test1",
                    restoreState = { restore -> restore.subscribeBy { stateFirst = it } }
                )
            )
            .test()
            .assertOf({ assertThat(stateFirst).isEqualTo(4) })

        disposableRestores.dispose()
    }

    @Test
    fun persistState_restoresState_called() {
        val scopeMap = HashMap<String, Any>()

        var stateFirst: Int? = 999
        val disposable = Observable.range(0, 5)
            .compose(
                persistState(
                    scopeMap = scopeMap,
                    storageId = "test1",
                    restoreState = { restore -> restore.subscribeBy { stateFirst = it } })
            ).subscribe()
        disposable.dispose()

        val disposableRestores = Observable.range(5, 5)
            .compose(
                persistState(
                    scopeMap = scopeMap,
                    storageId = "test1",
                    restoreState = { restore -> restore.subscribeBy { stateFirst = it } }
                )
            )
            .test()
            .assertOf({ assertThat(stateFirst).isEqualTo(4) })
    }

    @Test
    fun persistState_restoresState_keepsValues() {
        val scopeMap = HashMap<String, Any>()

        val disposable = Observable.range(0, 5)
            .compose(
                persistState(
                    scopeMap = scopeMap,
                    storageId = "test1",
                    restoreState = { restore -> restore.subscribe() })
            ).subscribe()
        disposable.dispose()

        val disposableRestores = Observable.range(5, 5)
            .compose(
                persistState(
                    scopeMap = scopeMap,
                    storageId = "test1",
                    restoreState = { restore -> restore.subscribe() }
                )
            )
            .test()
            .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

        disposableRestores.dispose()
    }

    @Test
    fun persistState_storesItemsMaxCountDoesNotChange() {
        val scopeMap = HashMap<String, Any>()


        val observableOut = Observable.range(0, 5)
            .compose(persistState(scopeMap, "test1", 1))
        val observableOut2 = Observable.range(5, 5)
            .compose(persistState(scopeMap, "test1", 2))


        observableOut
            .test()
            .assertValues(9)
        observableOut2
            .test()
            .assertValues(9)
    }

    @Test
    fun persistState_keepsValues() {
        val scopeMap = HashMap<String, Any>()


        val observableOut = Observable.range(0, 5)
            .compose(persistState(scopeMap, "test1"))
        val observableOut2 = Observable.range(5, 5)
            .compose(persistState(scopeMap, "test1"))


        observableOut
            .test()
            .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        observableOut2
            .test()
            .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
    }


    /**
     * Dunno how to sofar
     */
    @Ignore
    fun persistState_worksAfterOnCompleteOnError() {
        val scopeMap = HashMap<String, Any>()


        val observableOut = Observable.error<Throwable>(Throwable("Error"))
            .compose(persistState(scopeMap, "test1"))
        observableOut
            .subscribeBy(onError = {})
            .dispose()
        val observableOut2 = Observable.range(5, 5)
            .compose(persistState(scopeMap, "test1"))

        assertThat(observableOut).isSameAs(observableOut2)
        observableOut2
            .test()
            .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

    }


    @Test
    fun persistState_worksAfterDifferentSubscriberDisposed() {
        val scopeMap = HashMap<String, Any>()


        val observableOut = Observable.range(0, 5)
            .compose(persistState(scopeMap, "test1"))
        observableOut
            .subscribe().dispose()
        val observableOut2 = Observable.range(5, 5)
            .compose(persistState(scopeMap, "test1"))

        observableOut2
            .test()
            .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

    }

    @Ignore
    fun persistState_reconnectsToStream() {

    }

    @Test
    fun persistState_keepsRelay() {
        val scopeMap = HashMap<String, Any>()

        val observableOut = Observable.range(0, 5)
            .compose(persistState(scopeMap, "test1"))
        val relay = getRelay<Int>(scopeMap, "test1", 0)
        val observableOut2 = Observable.range(5, 5)
            .compose(persistState(scopeMap, "test1"))
        val relay2 = getRelay<Int>(scopeMap, "test1", 0)


        assertThat(relay).isSameAs(relay2)
    }

    @Test
    fun persistState_passesValues() {
        val scopeMap = HashMap<String, Any>()

        val observableOut = Observable.range(0, 5)
            .compose(persistState(scopeMap, "test1"))

        observableOut
            .test()
            .assertValues(0, 1, 2, 3, 4)
    }

    @Test
    fun persistentReplay_passesValues() {
        val scopeMap = HashMap<String, Any>()

        Observable.range(0, 5)
            .compose(
                persistentReplay(
                    storageId = "test1",
                    scopeMap = scopeMap
                )
            ).test()
            .assertValueCount(5)
    }

    @Test
    fun persistentReplay_replaysValuesMultipleTimes() {
        val scopeMap = HashMap<String, Any>()

        Observable.range(0, 5)
            .compose(
                persistentReplay(
                    storageId = "test1",
                    scopeMap = scopeMap
                )
            ).subscribe()

        Observable.range(0, 5)
            .compose(
                persistentReplay(
                    storageId = "test1",
                    scopeMap = scopeMap
                )
            ).test()
            .assertValueCount(10)
    }


    @Test
    fun reactiveCache_perservesData() {
        val cacheDir = File("./build/test/reactiveCache")
        cacheDir.mkdirs()

        val cache = ReactiveCache.Builder()
            .using(cacheDir, GsonSpeaker())
        cache.evictAll().subscribe()

        val cacheProvider: Provider<List<Int>> = cache.provider<List<Int>>().withKey("testInt1")

        val rangeDisposable1 =
            Single.just<List<Int>>(arrayListOf(1))
                .compose(cacheProvider.readWithLoader())
                .toObservable()
                .flatMap { Observable.fromIterable(it) }
                .concatWith(Observable.range(0, 5))
                .toList()
                .compose(cacheProvider.replace())
                .subscribe()

        val rangeDisposable2 =
            cacheProvider.read()
                .toObservable()
                .flatMap { Observable.fromIterable(it) }
                .concatWith(Observable.range(0, 5))
                .toList()
                .compose(cacheProvider.replace())
                .test()
                .assertValue(arrayListOf(1, 0, 1, 2, 3, 4, 0, 1, 2, 3, 4))
                .assertValueCount(1)

        rangeDisposable1.dispose()
        rangeDisposable2.dispose()
    }

    @Test
    fun evictAll_erasesWholeDir() {
        val cacheDir = File("./build/test/reactiveCache")
        cacheDir.mkdirs()
        val testFile = File(cacheDir, "test_evictAll_erasesWholeDir")
        testFile.createNewFile()
        testFile.writeText("ReactiveCache erases all files within selected directory. This should be erased!")
        val cache = ReactiveCache.Builder()
            .using(cacheDir, GsonSpeaker())


        cache.evictAll().subscribe()


        assertThat(testFile.exists()).isFalse()


        if (testFile.exists()) testFile.delete()
    }


}
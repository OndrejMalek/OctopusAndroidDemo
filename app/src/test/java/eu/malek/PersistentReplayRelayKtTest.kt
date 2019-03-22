package eu.malek

import com.google.common.truth.Truth.*
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.ReplayRelay
import io.reactivecache2.Provider
import io.reactivecache2.ReactiveCache
import io.reactivex.Observable
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.ReplaySubject
import io.victoralbertos.jolyglot.GsonSpeaker
import org.junit.Ignore
import org.junit.Test

import java.io.File

class PersistentReplayRelayKtTest {

    @Test
    fun attachToReplayStream_keepsValues() {
        val scopeMap = HashMap<String, Any>()

        val observableOut = attachToReplayStream<Int,Int>(scopeMap, Observable.range(0, 5), "test1")
        val observableOut2 = attachToReplayStream<Int,Int>(scopeMap, Observable.range(5, 5), "test1")

        observableOut2
            .test()
            .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        observableOut
            .test()
            .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

    }

    @Ignore
    fun rxtest() {
        val subject = ReplaySubject.create<Int>()

        Observable
            .range(0,4)
            .observeOn(Schedulers.computation())
            .mergeWith(subject)
            .map {if (it != 666) subject.onNext(666)
                print(" $it ")
                it}
//            .concatWith(Observable.error(Throwable()))
            .mergeWith(Observable.just(3,4))
            .test()
            .assertValueCount(60)
        //.assertError(Throwable())


    }

    @Test
    fun scan() {

        Observable.range(0,4)
            .scan(0, BiFunction { t1, t2 -> t1 + 1 })
            .test()
            .assertValues(1)
    }

    /**
     * Dunno how to sofar
     */
    @Ignore
    fun attachToReplayStream_worksAfterOnCompleteOnError() {
        val scopeMap = HashMap<String, Any>()


        val observableOut = attachToReplayStream<Int,Int>(scopeMap, Observable.error(Throwable("Error")), "test1")
        observableOut
            .subscribeBy(onError = {})
            .dispose()
        val observableOut2 = attachToReplayStream<Int,Int>(scopeMap, Observable.range(5, 5), "test1")

        assertThat(observableOut).isSameAs(observableOut2)
        observableOut2
            .test()
            .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

    }


    @Test
    fun attachToReplayStream_worksAfterDifferentSubscriberDisposed() {
        val scopeMap = HashMap<String, Any>()


        val observableOut = attachToReplayStream<Int,Int>(scopeMap, Observable.range(0, 5), "test1")
        observableOut
            .subscribe().dispose()
        val observableOut2 = attachToReplayStream<Int,Int>(scopeMap, Observable.range(5, 5), "test1")

        assertThat(observableOut).isSameAs(observableOut2)
        observableOut2
            .test()
            .assertValues(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)

    }

    @Test
    fun attachToReplayStream_keepsStream() {
        val scopeMap = HashMap<String, Any>()

        val observableOut = attachToReplayStream<Int,Int>(scopeMap, Observable.range(0, 5), "test1")
        val observableOut2 = attachToReplayStream<Int,Int>(scopeMap, Observable.range(5, 5), "test1")


        assertThat(observableOut).isSameAs(observableOut2)
    }

    @Test
    fun attachToReplayStream_keepsRelay() {
        val scopeMap = HashMap<String, Any>()

        val observableOut = attachToReplayStream<Int,Int>(scopeMap, Observable.range(0, 5), "test1")
        val relay = getRelay<Int>(scopeMap, "test1", 0)
        val observableOut2 = attachToReplayStream<Int,Int>(scopeMap, Observable.range(5, 5), "test1")
        val relay2 = getRelay<Int>(scopeMap, "test1", 0)


        assertThat(relay).isSameAs(relay2)
    }

    @Test
    fun attachToReplayStream_passesValues() {
        val scopeMap = HashMap<String, Any>()

        val observableOut = attachToReplayStream<Int,Int>(scopeMap, Observable.range(0, 5), "test1")

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
    fun perRep() {

        Observable.range(0, 4)
            .subscribe()


        Observable.range(0, 4)
            .subscribe()

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
                .doOnSuccess { println(it) }
                .subscribe()

        val rangeDisposable2 =
            cacheProvider.read()
                .toObservable()
                .flatMap { Observable.fromIterable(it) }
                .concatWith(Observable.range(0, 5))
                .toList()
                .compose(cacheProvider.replace())
                .doOnSuccess { println(it) }
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
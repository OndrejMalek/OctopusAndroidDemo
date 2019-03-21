package eu.malek

import com.google.common.truth.ExpectFailure
import com.google.common.truth.Truth
import io.reactivecache2.Provider
import io.reactivecache2.ReactiveCache
import io.reactivex.Observable
import io.reactivex.Single
import io.victoralbertos.jolyglot.GsonSpeaker
import org.junit.Test

import java.io.File

class PersistentReplayRelayKtTest {

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
    fun persistentReplay_replaysValues() {
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

        Observable.range(0,4)
            .subscribe()


        Observable.range(0,4)
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
                .doOnSuccess{ println(it) }
                .subscribe()

        val rangeDisposable2 =
            cacheProvider.read()
                .toObservable()
                .flatMap { Observable.fromIterable(it) }
                .concatWith(Observable.range(0, 5))
                .toList()
                .compose(cacheProvider.replace())
                .doOnSuccess{ println(it) }
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


        Truth.assertThat(testFile.exists()).isFalse()


        if (testFile.exists()) testFile.delete()
    }



}
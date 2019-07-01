package org.oelab.octopusengine.octolabapp.ui.rgb

import com.jakewharton.rxrelay2.PublishRelay
import com.jakewharton.rxrelay2.ReplayRelay
import io.reactivex.Observable
import io.reactivex.subjects.ReplaySubject
import org.junit.Test

class RxTest {


    @Test
    fun replayRelay_ReplaysAfterSourceCompleteOrDispose() {
        val replay = ReplayRelay.create<Int>()
        val range = Observable.range(1, 3)
        val range2 = Observable.range(4, 4)
        val rangeDisposable = range.subscribe(replay)

        val disposable1 = replay.doOnNext { println("1: $it") }.subscribe()
        rangeDisposable.dispose()
        val disposable2 = replay.doOnNext { println("2: $it") }.subscribe()
        val range2Disposable = range2.subscribe(replay)
        range2Disposable.dispose()

        replay.test().assertValueCount(3 + 4)

        disposable1.dispose()
        disposable2.dispose()
    }

    @Test
    fun replaySubject_notReplaysAfterSourceComplete() {
        val replay = ReplaySubject.create<Int>()
        val range = Observable.range(1, 3)
        val range2 = Observable.range(4, 4)

        range.subscribe(replay)
        val disposable1 = replay.doOnNext { println("1: $it") }.subscribe()
        val disposable2 = replay.doOnNext { println("2: $it") }.subscribe()
        range2.subscribe(replay)

        replay.test().assertValueCount(3)

        disposable1.dispose()
    }

    @Test
    fun testReconnectToObservable() {
        val subject = ReplaySubject.create<Int>()

        subject.onNext(0)
        val publish = subject.publish()
        subject.onNext(0)
        subject.onNext(0)

        val test = publish.test()
        publish.connect()
        publish.subscribe { println(it) }


        subject.onNext(2)
        test.assertValueCount(4)
    }


    @Test
    fun testReconnectWithRelays() {
        val relay = PublishRelay.create<Int>()
        val publish = Observable.never<Int>()
            .mergeWith(relay)
            .map { it + 100 }
            .publish()
        publish.connect()

        val observableWithInputRelayTest = publish
            .test()
        val testRelay = relay.test()


        Observable.range(0, 5).subscribe(relay)

        testRelay.assertValueCount(5)
        observableWithInputRelayTest
            .assertValues(100, 101, 102, 103, 104)
            .assertValueCount(5)
    }
}


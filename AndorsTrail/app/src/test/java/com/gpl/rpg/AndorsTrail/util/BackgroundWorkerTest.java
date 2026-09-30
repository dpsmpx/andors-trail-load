package com.gpl.rpg.AndorsTrail.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Every background operation must end with exactly one result, so that the progress dialog is
 * always closed and the caller learns about failures (audit finding M6).
 */
public final class BackgroundWorkerTest {

	@Test
	public void completedTaskReportsItsResult() throws Exception {
		List<String> results = run(callback -> callback.onComplete(true));
		assertEquals(Collections.singletonList("complete true"), results);
	}

	@Test
	public void exceptionThrownByTheTaskIsReported() throws Exception {
		RuntimeException thrown = new IllegalArgumentException("broken");
		Recorder recorder = new Recorder();
		start(callback -> { throw thrown; }, recorder);
		assertEquals(Collections.singletonList("failure IllegalArgumentException"), recorder.await());
		assertSame(thrown, recorder.failure);
	}

	@Test
	public void taskEndingWithoutResultIsReportedAsFailure() throws Exception {
		// For example a task that catches an exception and does not report it.
		List<String> results = run(callback -> { });
		assertEquals(Collections.singletonList("failure IllegalStateException"), results);
	}

	@Test
	public void onlyTheFirstResultIsReported() throws Exception {
		List<String> results = run(callback -> {
			callback.onComplete(true);
			callback.onFailure(new Exception("late"));
			throw new IllegalStateException("after the result");
		});
		assertEquals(Collections.singletonList("complete true"), results);
	}

	private static List<String> run(BackgroundWorker.worker<Boolean> task) throws Exception {
		Recorder recorder = new Recorder();
		start(task, recorder);
		return recorder.await();
	}

	private static void start(BackgroundWorker.worker<Boolean> task, Recorder recorder) {
		BackgroundWorker<Boolean> worker = new BackgroundWorker<>();
		worker.setTask(task);
		worker.setCallback(recorder);
		worker.run();
	}

	private static final class Recorder implements BackgroundWorker.BackgroundWorkerCallback<Boolean> {
		private final List<String> results = new ArrayList<>();
		private final CountDownLatch firstResult = new CountDownLatch(1);
		Exception failure;

		@Override public void onInitialize() { }

		@Override
		public synchronized void onFailure(Exception e) {
			failure = e;
			results.add("failure " + e.getClass().getSimpleName());
			firstResult.countDown();
		}

		@Override
		public synchronized void onComplete(Boolean result) {
			results.add("complete " + result);
			firstResult.countDown();
		}

		List<String> await() throws InterruptedException {
			assertTrue("no result reported", firstResult.await(5, TimeUnit.SECONDS));
			Thread.sleep(100); // A second result would arrive right after the first one.
			synchronized (this) {
				return new ArrayList<>(results);
			}
		}
	}
}

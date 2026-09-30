package com.gpl.rpg.AndorsTrail.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BackgroundWorker<T> {
	// Shared by all workers; idle threads end after a minute.
	private static final ExecutorService executor = Executors.newCachedThreadPool();

	volatile boolean cancelled = false;
	worker<T> task;
	BackgroundWorkerCallback<T> callback;

	public void setTask(worker<T> task) {
		this.task = task;
	}

	public void setCallback(BackgroundWorkerCallback<T> callback) {
		this.callback = callback;
	}

	public void cancel() {
		cancelled = true;
	}

	interface worker<T> {
		// Must report the result with onComplete or onFailure before returning.
		void doWork(BackgroundWorkerCallback<T> callback);
	}

	interface BackgroundWorkerCallback<T> {
		void onInitialize();

		default void onProgress(float progress) {
		}

		void onFailure(Exception e);

		void onComplete(T result);
	}

	// The callback gets exactly one result, also when the task throws or reports nothing,
	// so that the caller can always close its progress dialog.
	public void run() {
		final SingleResultCallback<T> singleResult = new SingleResultCallback<T>(callback);
		executor.execute(() -> {
			try {
				task.doWork(singleResult);
			} catch (RuntimeException e) {
				singleResult.onFailure(e);
			}
			singleResult.onFailure(new IllegalStateException("Background task ended without a result"));
		});
	}

	public boolean isCancelled() {
		return cancelled;
	}

	private static final class SingleResultCallback<T> implements BackgroundWorkerCallback<T> {
		private final BackgroundWorkerCallback<T> callback;
		private boolean hasResult = false;

		SingleResultCallback(BackgroundWorkerCallback<T> callback) {
			this.callback = callback;
		}

		@Override
		public void onInitialize() {
			callback.onInitialize();
		}

		@Override
		public void onProgress(float progress) {
			callback.onProgress(progress);
		}

		@Override
		public synchronized void onFailure(Exception e) {
			if (hasResult) return;
			hasResult = true;
			callback.onFailure(e);
		}

		@Override
		public synchronized void onComplete(T result) {
			if (hasResult) return;
			hasResult = true;
			callback.onComplete(result);
		}
	}
}

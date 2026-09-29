package com.gpl.rpg.AndorsTrail.activity;

import android.app.Activity;
import android.os.Build;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.RequiresApi;

/**
 * Routes the system Back action to an activity's own Back handling.
 *
 * <p>For apps that target Android 16 (API 36), predictive back is enabled by default on Android 16:
 * the system no longer calls {@code Activity.onBackPressed()} and no longer dispatches
 * {@code KEYCODE_BACK}, it only invokes the registered {@link OnBackInvokedCallback}s and otherwise
 * finishes the activity. On Android 13 to 15 the callbacks are only used by apps that opt in to
 * predictive back, which this app does not do, so {@code onBackPressed()} keeps being called there
 * and the callback registered here stays unused.</p>
 *
 * <p>Every activity that overrides {@code onBackPressed()} or uses an {@code OnBackPressedCallback}
 * must register here (see BackNavigationTest).</p>
 */
final class BackNavigation {
	private BackNavigation() {}

	static void register(Activity activity, Runnable onBack) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
		Api33.register(activity, onBack);
	}

	@RequiresApi(Build.VERSION_CODES.TIRAMISU)
	private static final class Api33 {
		static void register(Activity activity, final Runnable onBack) {
			activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
					OnBackInvokedDispatcher.PRIORITY_DEFAULT,
					new OnBackInvokedCallback() {
						@Override
						public void onBackInvoked() {
							onBack.run();
						}
					});
		}
	}
}

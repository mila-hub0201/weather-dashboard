package jp.milahub.weatherwidget;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 更新ボタンを押したときの即時更新。
 *
 * <p>JobScheduler 経由にすると Doze (省電力) やアプリスタンバイで実行が延期され、
 * 「更新中…」の表示のまま何時間も止まってしまう。ユーザー操作による更新は
 * ブロードキャストを受けたその場で実行する。ランチャーから送られた
 * PendingIntent を受け取った直後はアプリが一時的に Doze 除外されるため、
 * この経路なら画面消灯中でも通信できる。
 *
 * <p>成功・失敗・時間切れのどれであっても必ず最後に再描画するので、
 * 「更新中…」が残り続けることはない。
 */
final class WidgetRefresher {
    private static final String TAG = "WeatherWidgetUpdate";

    /** ブロードキャストが打ち切られる前に必ず結果を出すための持ち時間。 */
    private static final long BUDGET_MS = 8_000L;
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 3_500;
    private static final long RETRY_DELAY_MS = 400L;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private WidgetRefresher() {}

    static void refreshNow(Context context, BroadcastReceiver.PendingResult pending) {
        Context appContext = context.getApplicationContext();
        AtomicBoolean settled = new AtomicBoolean(false);
        Handler handler = new Handler(Looper.getMainLooper());

        // 通信が持ち時間内に終わらなくても、必ず結果を表示して待ち状態を終わらせる。
        // ブロードキャストの後始末は settled を取れた側だけが行う (finish は一度だけ)。
        Runnable onOutOfTime = () -> {
            if (!settled.compareAndSet(false, true)) return;
            Log.w(TAG, "Immediate refresh ran out of time; leaving it to the fallback job");
            fail(appContext, "Immediate refresh timed out");
            if (pending != null) pending.finish();
        };
        handler.postDelayed(onOutOfTime, BUDGET_MS);

        new WidgetStore(appContext).markUpdateStarted(System.currentTimeMillis());

        EXECUTOR.execute(() -> {
            String failure = null;
            int hours = 0;
            // 受信直後は通信の解禁 (ファイアウォール規則の反映) が間に合わず
            // 名前解決に失敗することがあるため、一度だけ間を置いて試し直す。
            for (int attempt = 0; attempt < 2; attempt++) {
                if (attempt > 0) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                try {
                    WidgetStore store = new WidgetStore(appContext);
                    List<ForecastHour> forecast = new ForecastRepository().fetch(
                            store.getLatitude(),
                            store.getLongitude(),
                            CONNECT_TIMEOUT_MS,
                            READ_TIMEOUT_MS
                    );
                    hours = forecast.size();
                    store.saveForecast(forecast, System.currentTimeMillis());
                    failure = null;
                    break;
                } catch (Exception error) {
                    failure = error.getClass().getSimpleName() + ": " + error.getMessage();
                    Log.e(TAG, "Immediate refresh failed (attempt " + (attempt + 1) + ")", error);
                }
            }

            if (!settled.compareAndSet(false, true)) {
                // 時間切れ側が後始末済み。取得できていれば保存内容は次の描画で活きる。
                if (failure == null) WeatherWidgetProvider.renderAll(appContext, false, false);
                return;
            }
            handler.removeCallbacks(onOutOfTime);

            if (failure == null) {
                Log.i(TAG, "Immediate refresh updated " + hours + " hours");
                WeatherWidgetProvider.renderAll(appContext, false, false);
            } else {
                fail(appContext, failure);
            }
            if (pending != null) pending.finish();
        });
    }

    /** 失敗を記録して表示し、通信できるようになったら拾い直せるようジョブを予約する。 */
    private static void fail(Context appContext, String reason) {
        boolean powerBlocked = PowerRestrictions.isBlockingNetwork(appContext);
        if (powerBlocked) Log.w(TAG, "Network is blocked by battery saver; app is not exempt");
        new WidgetStore(appContext).markUpdateFailed(System.currentTimeMillis(), reason, powerBlocked);
        WeatherWidgetProvider.renderAll(appContext, false, true);
        // 省電力で遮断されている間はジョブの通信条件も満たされないので予約しない。
        if (!powerBlocked) WeatherUpdateJobService.enqueueImmediate(appContext);
    }
}

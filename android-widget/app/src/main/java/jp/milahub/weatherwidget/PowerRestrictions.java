package jp.milahub.weatherwidget;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * 省電力モードによる通信遮断の判定。
 *
 * <p>省電力モード中、電池最適化の対象アプリはバックグラウンドでの通信を
 * ファイアウォールで遮断される。この状態では名前解決すら通らないため、
 * ウィジェットは更新できない。充電を始めると省電力モードが解除されるので
 * 「PCに繋いだときだけ更新される」という症状になる。
 */
final class PowerRestrictions {

    private PowerRestrictions() {}

    /** 電池最適化の除外を受けているか (除外されていれば省電力中でも通信できる)。 */
    static boolean isExempt(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        if (power == null) return true;
        return power.isIgnoringBatteryOptimizations(context.getPackageName());
    }

    /** 省電力モードで通信を止められている状態か。 */
    static boolean isBlockingNetwork(Context context) {
        PowerManager power = context.getSystemService(PowerManager.class);
        if (power == null) return false;
        return power.isPowerSaveMode() && !isExempt(context);
    }

    /** 電池最適化の除外を求める標準ダイアログ。可否の判断はユーザーが行う。 */
    static Intent requestExemptionIntent(Context context) {
        return new Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + context.getPackageName())
        );
    }
}

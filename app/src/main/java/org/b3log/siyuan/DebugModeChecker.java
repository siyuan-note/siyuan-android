package org.b3log.siyuan;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

public class DebugModeChecker {

  /**
   * Checks if the current package name contains ".debug" and if debug mode is enabled.
   *
   * @param context The Android context used to retrieve the package information.
   * @return true if the package name contains ".debug" and debug mode is enabled, false otherwise.
   */
  public static boolean isDebugPackageAndMode(Context context) {
    PackageManager packageManager = context.getPackageManager();
    ApplicationInfo appInfo = null;
    try {
      appInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }

    // Check if the package name contains ".debug"
    boolean isDebugPackage = context.getPackageName() != null && context.getPackageName().contains(".debug");
    boolean isDebugMode = appInfo != null && (appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    return isDebugPackage && isDebugMode;
  }

}

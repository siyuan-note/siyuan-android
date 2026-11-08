package org.b3log.siyuan;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public final class NotificationSettingsTest {
    @Test
    public void notificationSettingsUsesPackageExtraOnUiThread() {
        final Activity activity = mock(Activity.class);
        when(activity.getPackageName()).thenReturn("org.b3log.siyuan.debug");
        final AtomicReference<Runnable> pending = new AtomicReference<>();
        doAnswer(call -> {
            pending.set(call.getArgument(0));
            return null;
        }).when(activity).runOnUiThread(any(Runnable.class));
        final List<List<?>> arguments = new ArrayList<>();
        try (MockedConstruction<Intent> intents = mockConstruction(Intent.class,
                (intent, context) -> arguments.add(context.arguments()))) {
            JSAndroid.openNotificationSettings(activity);
            assertTrue(intents.constructed().isEmpty());
            verify(activity, never()).startActivity(any());
            assertNotNull(pending.get());
            pending.get().run();
            assertEquals(1, intents.constructed().size());
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, arguments.get(0).get(0));
            final Intent intent = intents.constructed().get(0);
            verify(intent).putExtra(Settings.EXTRA_APP_PACKAGE, "org.b3log.siyuan.debug");
            verify(intent, never()).setData(any());
            verify(activity).startActivity(intent);
        }
    }

    @Test
    public void missingNotificationSettingsFallsBackToApplicationDetails() {
        checkFallback(mock(ActivityNotFoundException.class), false);
    }

    @Test
    public void inaccessibleNotificationSettingsFallsBackToApplicationDetails() {
        checkFallback(new SecurityException("restricted"), false);
    }

    @Test
    public void unavailableSettingsPagesDoNotThrow() {
        checkFallback(mock(ActivityNotFoundException.class), true);
    }

    private void checkFallback(final RuntimeException failure, final boolean fallbackFails) {
        final Activity activity = mock(Activity.class);
        when(activity.getPackageName()).thenReturn("org.b3log.siyuan.debug");
        doAnswer(call -> {
            call.<Runnable>getArgument(0).run();
            return null;
        }).when(activity).runOnUiThread(any(Runnable.class));
        if (fallbackFails) {
            doThrow(failure).when(activity).startActivity(any());
        } else {
            doThrow(failure).doNothing().when(activity).startActivity(any());
        }
        final Uri packageUri = mock(Uri.class);
        final List<List<?>> arguments = new ArrayList<>();
        try (MockedStatic<Uri> uris = mockStatic(Uri.class);
             MockedStatic<Utils> utils = mockStatic(Utils.class);
             MockedConstruction<Intent> intents = mockConstruction(Intent.class,
                     (intent, context) -> arguments.add(context.arguments()))) {
            uris.when(() -> Uri.parse("package:org.b3log.siyuan.debug")).thenReturn(packageUri);
            JSAndroid.openNotificationSettings(activity);
            assertEquals(2, intents.constructed().size());
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, arguments.get(1).get(0));
            final Intent fallback = intents.constructed().get(1);
            verify(fallback).setData(packageUri);
            verify(activity).startActivity(fallback);
            if (fallbackFails) {
                utils.verify(() -> Utils.logError("notification", "open notification settings failed", failure));
            } else {
                utils.verifyNoInteractions();
            }
        }
    }
}

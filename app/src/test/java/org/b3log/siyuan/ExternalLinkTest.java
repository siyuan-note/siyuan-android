package org.b3log.siyuan;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public final class ExternalLinkTest {
    @Test
    public void opensTheUriWithActionView() {
        final Activity activity = mock(Activity.class);
        final Uri uri = mock(Uri.class);
        try (MockedConstruction<Intent> intents = mockConstruction(Intent.class,
                (intent, context) -> assertEquals(List.of(Intent.ACTION_VIEW, uri), context.arguments()))) {
            Utils.openExternalUri(uri, activity);

            assertEquals(1, intents.constructed().size());
            verify(activity).startActivity(intents.constructed().get(0));
            verify(activity, never()).runOnUiThread(any());
        }
    }

    @Test
    public void showsMessageOnUiThreadWhenNoAppCanOpenTheUri() {
        final Activity activity = mock(Activity.class);
        final Uri uri = mock(Uri.class);
        doThrow(mock(ActivityNotFoundException.class)).when(activity).startActivity(any());
        try (MockedConstruction<Intent> intents = mockConstruction(Intent.class);
             MockedStatic<Utils> utils = mockStatic(Utils.class)) {
            utils.when(() -> Utils.openExternalUri(uri, activity)).thenCallRealMethod();
            Utils.openExternalUri(uri, activity);

            assertEquals(1, intents.constructed().size());
            verify(activity).startActivity(intents.constructed().get(0));
            final ArgumentCaptor<Runnable> pendingToast = ArgumentCaptor.forClass(Runnable.class);
            verify(activity).runOnUiThread(pendingToast.capture());
            utils.verify(() -> Utils.showToast(activity, R.string.no_app_to_open_link), never());
            pendingToast.getValue().run();
            utils.verify(() -> Utils.showToast(activity, R.string.no_app_to_open_link));
        }
    }
}

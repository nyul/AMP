package hu.bme.andh.tabplayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;

public class MediaButtonIntentReceiver extends BroadcastReceiver {

public MediaButtonIntentReceiver() {
    super();
}

@Override
public void onReceive(Context context, Intent intent) {
    String intentAction = intent.getAction();
    if (Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
        KeyEvent event = (KeyEvent) intent
                .getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            System.out.println("adsad     adsds");
        }

    }

    if (isOrderedBroadcast()) {
        abortBroadcast();
    }
}
}
package com.tietie.calendar;
import android.content.*;
public class WidgetDateReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){StickerWidgets.refresh(context);}
}

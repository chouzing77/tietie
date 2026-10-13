package com.tietie.calendar;
import android.content.*;
import android.widget.Toast;
public class WidgetPinReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String request=intent.getStringExtra("request");if(request==null)return;
        context.getSharedPreferences("widget-pinning",Context.MODE_PRIVATE).edit().putString("completed",request).apply();
        StickerWidgets.refresh(context);
        Toast.makeText(context,"组件已添加到桌面",Toast.LENGTH_SHORT).show();
    }
}

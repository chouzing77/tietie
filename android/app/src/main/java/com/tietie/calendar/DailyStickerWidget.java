package com.tietie.calendar;
import android.appwidget.*;
import android.content.Context;
import android.os.Bundle;
public class DailyStickerWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)StickerWidgets.update(c,m,id,false);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager m,int id,Bundle options){StickerWidgets.update(c,m,id,false);}
}

package com.tietie.calendar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.util.AtomicFile;
import android.util.Base64;
import android.widget.RemoteViews;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

final class StickerWidgets {
    private static Typeface font;
    static synchronized void save(Context context, String json) throws Exception {
        JSONObject days = new JSONObject(json);
        // Only reduced public day snapshots are accepted; original images never leave WebView storage.
        for (java.util.Iterator<String> keys=days.keys(); keys.hasNext();) {
            String key=keys.next();
            if (!key.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
            JSONObject day=days.getJSONObject(key);
            if (day.getInt("count")<0 || day.getJSONArray("images").length()>2) throw new IllegalArgumentException();
        }
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"widget-days.json"));
        FileOutputStream out=null;
        try { out=file.startWrite(); out.write(json.getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch(Exception e) { if(out!=null)file.failWrite(out); throw e; }
        refresh(context);
    }
    static synchronized JSONObject read(Context context) {
        try { AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"widget-days.json"));
            return new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
        } catch(Exception e) { return new JSONObject(); }
    }
    static void refresh(Context context) {
        AppWidgetManager manager=AppWidgetManager.getInstance(context);
        for(Class<?> type:new Class<?>[]{DailyStickerWidget.class,MonthlyStickerWidget.class})
            for(int id:manager.getAppWidgetIds(new ComponentName(context,type))) {
                try {update(context,manager,id,type==MonthlyStickerWidget.class);}
                catch(Exception error){android.util.Log.w("TietieWidget","Widget refresh failed",error);}
            }
    }
    static void update(Context context, AppWidgetManager manager, int id, boolean monthly) {
        Bundle options=manager.getAppWidgetOptions(id);
        int width=Math.max(120,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,monthly?320:160));
        int height=Math.max(monthly?220:120,options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,monthly?320:160));
        // Keep RemoteViews bitmap comfortably below the launcher IPC size limit.
        int side=Math.max(120,Math.min(450,Math.min(width,height)*2));
        Bitmap bitmap=draw(context,monthly,side,side,read(context));
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.sticker_widget);
        views.setImageViewBitmap(R.id.widget_image,bitmap);
        views.setContentDescription(R.id.widget_image,monthly?"贴贴月历，点击打开日历":"今日贴纸，点击打开日历");
        Intent open=new Intent(context,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        views.setOnClickPendingIntent(R.id.widget_image,PendingIntent.getActivity(context,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(id,views);
    }
    static RemoteViews preview(Context context,boolean monthly) {
        RemoteViews views=new RemoteViews(context.getPackageName(),R.layout.sticker_widget);
        views.setImageViewBitmap(R.id.widget_image,draw(context,monthly,monthly?450:280,monthly?450:280,read(context)));
        return views;
    }
    static String key(Calendar day) {return String.format(Locale.ROOT,"%04d-%02d-%02d",day.get(Calendar.YEAR),day.get(Calendar.MONTH)+1,day.get(Calendar.DAY_OF_MONTH));}
    static Paint text(Context context,float size,int color) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setTextSize(size);
        if(font==null)try{font=Typeface.createFromAsset(context.getAssets(),"参考字体/Huiwen-mincho.ttf");}catch(Exception ignored){font=Typeface.SERIF;}
        p.setTypeface(font);return p;
    }
    static void label(Canvas c,String label,float x,float y,Paint p,boolean centered) {
        p.setTextAlign(centered?Paint.Align.CENTER:Paint.Align.LEFT);c.drawText(label,x,y,p);
    }
    static void image(Canvas c,String data,RectF area,float angle) {
        try { byte[] bytes=Base64.decode(data.substring(data.indexOf(',')+1),Base64.DEFAULT);
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null)return;
            float scale=Math.min(area.width()/bitmap.getWidth(),area.height()/bitmap.getHeight());
            float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
            RectF dest=new RectF(area.centerX()-w/2,area.centerY()-h/2,area.centerX()+w/2,area.centerY()+h/2);
            c.save();c.rotate(angle,area.centerX(),area.centerY());c.drawBitmap(bitmap,null,dest,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));c.restore();bitmap.recycle();
        }catch(Exception ignored){}
    }
    static Bitmap draw(Context context,boolean monthly,int width,int height,JSONObject days) {
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);
        float w=width,h=height;Paint background=new Paint(Paint.ANTI_ALIAS_FLAG);background.setColor(Color.rgb(247,244,235));
        c.drawRoundRect(new RectF(0,0,w,h),w*.085f,w*.085f,background);
        Calendar now=Calendar.getInstance();int ink=Color.rgb(48,42,35),muted=Color.rgb(135,125,113);
        if(!monthly){
            JSONObject day=days.optJSONObject(key(now));JSONArray imgs=day==null?new JSONArray():day.optJSONArray("images");int count=day==null?0:day.optInt("count");
            if(imgs!=null&&imgs.length()>0) {
                image(c,imgs.optString(0),new RectF(w*.09f,h*.07f,w*.80f,h*.71f),-9);
                if(imgs.length()>1)image(c,imgs.optString(1),new RectF(w*.32f,h*.12f,w*.92f,h*.74f),8);
            }else label(c,"暂无贴纸",w/2,h*.43f,text(context,w*.09f,muted),true);
            label(c,new String[]{"周日","周一","周二","周三","周四","周五","周六"}[now.get(Calendar.DAY_OF_WEEK)-1],w*.09f,h*.81f,text(context,w*.075f,ink),false);
            label(c,""+now.get(Calendar.DAY_OF_MONTH),w*.09f,h*.94f,text(context,w*.16f,ink),false);
            if(count>0){background.setColor(ink);c.drawRoundRect(new RectF(w*.70f,h*.79f,w*.93f,h*.94f),w*.06f,w*.06f,background);
                label(c,count>2?"+"+(count-2):count+"张",w*.815f,h*.895f,text(context,w*.07f,Color.WHITE),true);}
            return bitmap;
        }
        label(c,(now.get(Calendar.MONTH)+1)+"月",w*.09f,h*.07f,text(context,w*.045f,ink),false);
        label(c,""+now.get(Calendar.YEAR),w*.88f,h*.07f,text(context,w*.032f,muted),true);
        float left=w*.035f,cw=w*.93f/7,top=h*.17f;
        String[] week={"一","二","三","四","五","六","日"};
        for(int col=0;col<7;col++)label(c,week[col],left+cw*(col+.5f),h*.13f,text(context,w*.027f,muted),true);
        Calendar day=(Calendar)now.clone();day.set(Calendar.DAY_OF_MONTH,1);int offset=(day.get(Calendar.DAY_OF_WEEK)+5)%7,last=day.getActualMaximum(Calendar.DAY_OF_MONTH),rows=(offset+last+6)/7;
        float rh=(h*.80f)/rows;
        for(int d=1;d<=last;d++){
            day.set(Calendar.DAY_OF_MONTH,d);int pos=offset+d-1,col=pos%7,row=pos/7;float x=left+cw*col,y=top+rh*row;
            boolean today=d==now.get(Calendar.DAY_OF_MONTH);
            if(today){background.setColor(Color.WHITE);c.drawRoundRect(new RectF(x+1,y+1,x+cw-1,y+rh-2),cw*.25f,cw*.25f,background);}
            label(c,""+d,x+cw/2,y+rh*.18f,text(context,w*.026f,ink),true);
            JSONObject data=days.optJSONObject(key(day));if(data==null)continue;JSONArray imgs=data.optJSONArray("images");
            if(imgs!=null&&imgs.length()>0) image(c,imgs.optString(0),new RectF(x+cw*.08f,y+rh*.24f,x+cw*.90f,y+rh*.87f),-4);
            if(data.optInt("count")>1){background.setColor(Color.rgb(225,219,207));c.drawRoundRect(new RectF(x+cw*.50f,y+rh*.73f,x+cw*.96f,y+rh*.97f),5,5,background);
                label(c,"+"+(data.optInt("count")-1),x+cw*.73f,y+rh*.91f,text(context,w*.021f,ink),true);}
        }
        return bitmap;
    }
}

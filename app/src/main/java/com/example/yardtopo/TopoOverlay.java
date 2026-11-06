package com.example.yardtopo;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.Locale;

/** On-screen estimates from ARCore hit tests; no survey-grade claim. */
final class TopoOverlay extends View {
    static final int COLS=13, ROWS=19;
    static final class Data {
        final float[][] elevation=new float[ROWS][COLS];
        final boolean[][] valid=new boolean[ROWS][COLS];
        int count; float min,max, reference; boolean hasReference;
    }
    private volatile Data data=new Data();
    private final Paint paint=new Paint(3);
    private boolean contours=true, heatmap=true;
    TopoOverlay(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    void update(Data d){data=d;postInvalidate();}
    void setContours(boolean b){contours=b;invalidate();}
    void setHeatmap(boolean b){heatmap=b;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas); Data d=data;
        float left=getWidth()*.06f, top=getHeight()*.17f, gw=getWidth()*.88f, gh=getHeight()*.65f;
        float dx=gw/(COLS-1),dy=gh/(ROWS-1);
        if(heatmap) for(int r=0;r<ROWS;r++)for(int c=0;c<COLS;c++)if(d.valid[r][c]){
            float z=d.elevation[r][c]-d.reference;
            float t=Math.max(0,Math.min(1,(z+0.3f)/0.6f));
            int color=Color.HSVToColor(110,new float[]{(1-t)*230,0.85f,1});
            paint.setColor(color);paint.setAlpha(105);paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(left+c*dx,top+r*dy,Math.max(dx,dy)*.68f,paint);
        }
        if(contours && d.count>5){
            float low=(float)Math.floor((d.min-d.reference)/.05f)*.05f;
            float high=(float)Math.ceil((d.max-d.reference)/.05f)*.05f;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.4f);paint.setColor(Color.WHITE);paint.setAlpha(230);
            for(float level=low;level<=high && level<low+2;level+=.05f){
                for(int r=0;r<ROWS-1;r++)for(int c=0;c<COLS-1;c++){
                    if(!d.valid[r][c]||!d.valid[r][c+1]||!d.valid[r+1][c]||!d.valid[r+1][c+1])continue;
                    float x=left+c*dx,y=top+r*dy;
                    float[] z={d.elevation[r][c]-d.reference,d.elevation[r][c+1]-d.reference,d.elevation[r+1][c+1]-d.reference,d.elevation[r+1][c]-d.reference};
                    float[] px={x,x+dx,x+dx,x},py={y,y,y+dy,y+dy};
                    float[] crossX=new float[4],crossY=new float[4];int n=0;
                    for(int k=0;k<4;k++){int j=(k+1)%4; if((z[k]<level)!=(z[j]<level)){
                        float t=(level-z[k])/(z[j]-z[k]);crossX[n]=px[k]+t*(px[j]-px[k]);crossY[n]=py[k]+t*(py[j]-py[k]);n++;}}
                    if(n>=2)canvas.drawLine(crossX[0],crossY[0],crossX[1],crossY[1],paint);
                    if(n==4)canvas.drawLine(crossX[2],crossY[2],crossX[3],crossY[3],paint);
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setColor(Color.WHITE);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));paint.setTextSize(34);
        canvas.drawText(String.format(Locale.US,"Ground points: %d | Range: %.1f in",d.count,(d.max-d.min)*39.3701f),18,getHeight()-150,paint);
        paint.setTextSize(25);canvas.drawText("Contours: 2 in • green=lower, red=higher",18,getHeight()-110,paint);
        canvas.drawText("ESTIMATES ONLY — NOT FOR GRADING CONTROL",18,getHeight()-72,paint);
    }
}

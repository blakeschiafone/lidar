package com.example.yardtopo;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.opengl.GLSurfaceView;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import com.google.ar.core.*;
import java.io.OutputStream;

public final class MainActivity extends Activity {
    private GLSurfaceView glView;
    private CameraRenderer renderer;
    private TopoOverlay overlay;
    private Session session;
    private boolean installRequested;
    private boolean depthSupported;
    private volatile float baseline=Float.NaN;
    private long lastSample;
    private TextView status;
    private String lastStatus="";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this);
        glView=new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8,8,8,8,16,0);
        glView.setPreserveEGLContextOnPause(true);
        renderer=new CameraRenderer(this);
        glView.setRenderer(renderer);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(glView,new FrameLayout.LayoutParams(-1,-1));
        overlay=new TopoOverlay(this);
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(16,32,16,12);
        status=new TextView(this);
        status.setTextColor(0xffffffff);
        status.setTextSize(14);
        status.setPadding(8,6,8,6);
        status.setBackgroundColor(0xaa101820);
        status.setText("Starting AR camera…");
        controls.addView(status);
        LinearLayout buttons=new LinearLayout(this);
        Button reset=new Button(this); reset.setText("Reset baseline");
        reset.setOnClickListener(v->{baseline=Float.NaN;overlay.update(new TopoOverlay.Data());});
        buttons.addView(reset);
        Button save=new Button(this); save.setText("Save photo"); save.setOnClickListener(v->savePhoto());
        buttons.addView(save);
        controls.addView(buttons);
        root.addView(controls,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            showScanStatus("Camera permission required");
            requestPermissions(new String[]{Manifest.permission.CAMERA},1);
            return;
        }
        try {
            if(session==null) {
                ArCoreApk.InstallStatus result=ArCoreApk.getInstance().requestInstall(this,!installRequested);
                if(result==ArCoreApk.InstallStatus.INSTALL_REQUESTED) { installRequested=true; showScanStatus("Installing Google Play Services for AR…"); return; }
                session=new Session(this);
                Config config=new Config(session);
                depthSupported=session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
                config.setDepthMode(depthSupported?Config.DepthMode.AUTOMATIC:Config.DepthMode.DISABLED);
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL);
                session.configure(config);
                renderer.setSession(session);
            }
            session.resume();
            renderer.setRotation(getWindowManager().getDefaultDisplay().getRotation());
            glView.onResume();
            showScanStatus("Camera initializing • " + getDepthLabel());
        } catch(Exception e) { showScanStatus("ARCore start failed: " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
    }
    @Override protected void onPause(){ glView.onPause(); if(session!=null)session.pause(); super.onPause(); }
    @Override protected void onDestroy(){ if(session!=null){session.close();session=null;} super.onDestroy(); }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==1 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) onResume();
        else showScanStatus("Camera permission denied. Enable it in Android app settings.");
    }
    String getDepthLabel(){ return depthSupported ? "ARCore depth enabled" : "No depth support (planes only)"; }
    void showScanStatus(String message){
        if(message.equals(lastStatus))return;
        lastStatus=message;
        runOnUiThread(()->status.setText(message));
    }
    void onArError(String error){showScanStatus("AR ERROR: " + error);}

    // DepthPoint hits are measurements from ARCore depth; horizontal plane hits are fallbacks.
    // This is a live view, not a persistent terrain survey. Never interpret values as graded elevations.
    void onArFrame(Frame frame,int width,int height){
        if(frame.getCamera().getTrackingState()!=TrackingState.TRACKING) return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastSample<450) return;
        lastSample=now;
        TopoOverlay.Data data=new TopoOverlay.Data();
        float min=Float.POSITIVE_INFINITY,max=Float.NEGATIVE_INFINITY;
        for(int r=0;r<TopoOverlay.ROWS;r++) for(int c=0;c<TopoOverlay.COLS;c++) {
            float x=width*(.06f+.88f*c/(TopoOverlay.COLS-1f));
            float y=height*(.17f+.65f*r/(TopoOverlay.ROWS-1f));
            HitResult best=null;
            for(HitResult hit:frame.hitTest(x,y)) {
                Trackable t=hit.getTrackable();
                if(t instanceof DepthPoint) {best=hit;break;}
                if(best==null && t instanceof Plane) {
                    Plane p=(Plane)t;
                    if(p.getType()==Plane.Type.HORIZONTAL_UPWARD_FACING && p.isPoseInPolygon(hit.getHitPose())) best=hit;
                }
            }
            if(best==null)continue;
            float elevation=best.getHitPose().ty();
            if(Float.isNaN(baseline))baseline=elevation;
            data.elevation[r][c]=elevation;
            data.valid[r][c]=true;
            data.count++;
            min=Math.min(min,elevation);max=Math.max(max,elevation);
        }
        data.min=data.count==0?0:min;
        data.max=data.count==0?0:max;
        data.reference=Float.isNaN(baseline)?0:baseline;
        data.hasReference=!Float.isNaN(baseline);
        overlay.update(data);
        if(data.count==0)showScanStatus("Camera live, tracking • no ground hits yet. Walk slowly and aim at textured ground.");
        else showScanStatus("Camera live • " + data.count + " ground samples • " + getDepthLabel());
    }
    private void savePhoto(){
        if(glView.getWidth()<=0 || glView.getHeight()<=0)return;
        // Capture the entire window, including the camera and elevation overlay.
        View decor=getWindow().getDecorView();
        Bitmap image=Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),Bitmap.Config.ARGB_8888);
        android.view.PixelCopy.request(getWindow(),new Rect(0,0,decor.getWidth(),decor.getHeight()),image,result->{
            if(result!=android.view.PixelCopy.SUCCESS){Toast.makeText(this,"Capture failed: "+result,Toast.LENGTH_LONG).show();return;}
            try {
                ContentValues values=new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,"yardtopo_"+System.currentTimeMillis()+".png");
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
                android.net.Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(uri==null)throw new Exception("MediaStore returned no destination");
                try(OutputStream out=getContentResolver().openOutputStream(uri)){image.compress(Bitmap.CompressFormat.PNG,100,out);}
                Toast.makeText(this,"Saved to Photos",Toast.LENGTH_SHORT).show();
            } catch(Exception e){Toast.makeText(this,"Save failed: "+e.getMessage(),Toast.LENGTH_LONG).show();}
        },new Handler(Looper.getMainLooper()));
    }
}

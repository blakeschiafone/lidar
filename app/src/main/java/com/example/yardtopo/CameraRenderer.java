package com.example.yardtopo;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Camera is rendered on the GL thread; Android-view hit tests use the same display geometry. */
final class CameraRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "YardTopo";
    private static FloatBuffer buffer(float[] values) {
        FloatBuffer b = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(values).position(0);
        return b;
    }
    private final MainActivity activity;
    private final FloatBuffer vertices = buffer(new float[] {-1,-1, 1,-1, -1,1, 1,1});
    private final FloatBuffer uvs = buffer(new float[8]);
    private final float[] transformed = new float[8];
    private volatile Session session;
    private int texture, program, aPosition, aUv, uTexture;
    private int width, height, rotation = Surface.ROTATION_0;
    private boolean geometryDirty = true;
    private long lastReportMs;
    private String previousError = "";

    CameraRenderer(MainActivity activity) { this.activity = activity; }
    void setSession(Session value) { session = value; geometryDirty = true; }
    void setRotation(int value) { rotation = value; geometryDirty = true; }

    private int compile(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1]; GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("Shader: " + GLES20.glGetShaderInfoLog(shader));
        return shader;
    }
    @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        int[] ids = new int[1]; GLES20.glGenTextures(1, ids, 0); texture = ids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        int vert = compile(GLES20.GL_VERTEX_SHADER, "attribute vec2 aPosition; attribute vec2 aUv; varying vec2 vUv; void main(){ gl_Position=vec4(aPosition,0.,1.); vUv=aUv; }");
        int frag = compile(GLES20.GL_FRAGMENT_SHADER, "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vUv; uniform samplerExternalOES uTexture; void main(){ gl_FragColor=texture2D(uTexture,vUv); }");
        program = GLES20.glCreateProgram(); GLES20.glAttachShader(program,vert); GLES20.glAttachShader(program,frag); GLES20.glLinkProgram(program);
        int[] ok = new int[1]; GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("Program: " + GLES20.glGetProgramInfoLog(program));
        aPosition=GLES20.glGetAttribLocation(program,"aPosition");
        aUv=GLES20.glGetAttribLocation(program,"aUv");
        uTexture=GLES20.glGetUniformLocation(program,"uTexture");
        geometryDirty=true;
    }
    @Override public void onSurfaceChanged(GL10 unused, int w, int h) {
        width=w; height=h; geometryDirty=true; GLES20.glViewport(0,0,w,h);
    }
    @Override public void onDrawFrame(GL10 unused) {
        GLES20.glClearColor(0.08f,0.10f,0.13f,1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        Session s = session;
        if(s == null || width <= 0 || height <= 0 || texture == 0) return;
        try {
            // Critical: setDisplayGeometry is required for camera UVs AND frame.hitTest(x,y).
            s.setDisplayGeometry(rotation,width,height);
            geometryDirty=false;
            s.setCameraTextureName(texture);
            Frame frame = s.update();
            if(frame.getTimestamp() == 0) { activity.showScanStatus("Waiting for first camera frame…"); return; }
            // Transform every frame to avoid uninitialized UVs on the initial frame or GL recreation.
            vertices.position(0);
            FloatBuffer out=buffer(new float[8]);
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,vertices,Coordinates2d.TEXTURE_NORMALIZED,out);
            out.position(0); out.get(transformed);
            uvs.position(0); uvs.put(transformed); uvs.position(0);
            GLES20.glDisable(GLES20.GL_DEPTH_TEST);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
            GLES20.glUniform1i(uTexture,0);
            vertices.position(0); uvs.position(0);
            GLES20.glEnableVertexAttribArray(aPosition);
            GLES20.glVertexAttribPointer(aPosition,2,GLES20.GL_FLOAT,false,0,vertices);
            GLES20.glEnableVertexAttribArray(aUv);
            GLES20.glVertexAttribPointer(aUv,2,GLES20.GL_FLOAT,false,0,uvs);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glDisableVertexAttribArray(aPosition);
            GLES20.glDisableVertexAttribArray(aUv);
            long now=android.os.SystemClock.elapsedRealtime();
            if(now-lastReportMs>1000) {
                lastReportMs=now;
                activity.showScanStatus("Camera live • " + frame.getCamera().getTrackingState() + " • " + activity.getDepthLabel());
            }
            activity.onArFrame(frame,width,height);
        } catch(Exception ex) {
            String msg=ex.getClass().getSimpleName() + ": " + ex.getMessage();
            if(!msg.equals(previousError)) { Log.e(TAG,"AR render failed",ex); previousError=msg; }
            activity.onArError(msg);
        }
    }
}

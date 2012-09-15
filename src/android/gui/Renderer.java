package android.gui;

import java.util.*;
import java.util.concurrent.*;
import java.nio.*;
import java.security.MessageDigest;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.os.SystemClock;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.*;

import static netmash.lib.Utils.*;

public class Renderer implements GLSurfaceView.Renderer {

    private NetMash netmash;
    private Mesh mesh;

    private int texture0Loc;
    private int texture1Loc;
    private int mvvmLoc;
    private int mvpmLoc;
    private int lightPosLoc;
    private int posLoc;
    private int norLoc;
    private int texLoc;

    private float[] matrixPrj = new float[16];
    private float[] matrixRtx = new float[16];
    private float[] matrixRty = new float[16];
    private float[] matrixRtz = new float[16];
    private float[] matrixRxy = new float[16];
    private float[] matrixRot = new float[16];
    private float[] matrixScl = new float[16];
    private float[] matrixRos = new float[16];
    private float[] matrixTrn = new float[16];
    private float[] matrixLgt = new float[16];
    private float[] matrixMSR = new float[16];
    private float[] matrixVVV = new float[16];
    private float[] matrixMVV = new float[16];
    private float[] matrixMVP = new float[16];
    private float[] matrixNor = new float[16];

    private float[] lightPosInModelSpace = new float[] {0.0f, 0.0f, 0.0f, 1.0f};
    private float[] lightPosInWorldSpace = new float[4];
    private float[] lightPos = new float[4];

    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private float seeX;
    private float seeY;
    private float seeZ;

    private float direction=0;

    public Renderer(NetMash netmash, LinkedHashMap hm) {
        this.netmash=netmash;
        this.mesh=new Mesh(hm);
        resetCoordsAndView(0,1.0f,-0.5f);
    }

    public void newMesh(LinkedHashMap hm){
        this.mesh=new Mesh(hm);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.6f, 0.8f, 0.9f, 1.0f);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float r = ((float)width)/height;
        Matrix.frustumM(matrixPrj, 0, -r, r, -1.0f, 1.0f, 1.0f, 100.0f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        drawLightAndCamera();
        drawMeshAndSubs(mesh, 0,0,0);
    }

    private void drawLightAndCamera(){
        Matrix.setLookAtM(matrixVVV, 0, eyeX,eyeY,eyeZ, seeX,seeY,seeZ, 0f,1f,0f);

        long time = SystemClock.uptimeMillis() % 10000L;
        float angle = (360.0f / 10000.0f) * ((int) time);

        Matrix.setIdentityM(matrixLgt, 0);
        Matrix.translateM(  matrixLgt, 0, 0.0f, 0.0f, -5.0f);
        Matrix.rotateM(     matrixLgt, 0, angle, 0.0f, 1.0f, 0.0f);
        Matrix.translateM(  matrixLgt, 0, 0.0f, 0.0f, 2.0f);
        Matrix.multiplyMV(lightPosInWorldSpace, 0, matrixLgt, 0, lightPosInModelSpace, 0);
        Matrix.multiplyMV(lightPos, 0, matrixVVV, 0, lightPosInWorldSpace, 0);

        String pointVertexShaderSource = "uniform mat4 mvpm; attribute vec4 pos; void main(){ gl_Position = mvpm * pos; gl_PointSize = 4.0; }";
        String pointFragmentShaderSource = "precision mediump float; void main(){ gl_FragColor = vec4(1.0, 1.0, 0.8, 1.0); }";
        getProgramLocs(getProgram(pointVertexShaderSource, pointFragmentShaderSource));

        GLES20.glVertexAttrib3f(posLoc, lightPosInModelSpace[0], lightPosInModelSpace[1], lightPosInModelSpace[2]);
        GLES20.glDisableVertexAttribArray(posLoc);

        Matrix.multiplyMM(matrixMVV, 0, matrixVVV, 0, matrixLgt, 0);
        Matrix.multiplyMM(matrixMVP, 0, matrixPrj, 0, matrixMVV, 0);

        GLES20.glUniformMatrix4fv(mvpmLoc, 1, false, matrixMVP, 0);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);
    }

    public ConcurrentHashMap<Object,Mesh> meshes = new ConcurrentHashMap<Object,Mesh>();

    private void drawMeshAndSubs(Mesh m, float tx, float ty, float tz){

        try{
            getProgramLocs(getProgram(m));
            setupTextures(m);
            setVariables(m, tx,ty,tz);
            uploadVBO(m);
            drawMesh(m);

        }catch(Throwable t){ Log.e("drawMeshAndSubs main",""+t); t.printStackTrace();
            log("Apparently I now have to set posLoc.. (fixme!)");
            posLoc = GLES20.glGetAttribLocation(getProgram(m), "pos");
            GLES20.glEnableVertexAttribArray(posLoc);
        }
        for(Object o: m.subObjects){ try{
            LinkedHashMap subob=(LinkedHashMap)o;
            Object subobuid=subob.get("object");
            Object subobcrd=subob.get("coords");
            LinkedHashMap sm=(LinkedHashMap)netmash.user.glElements.get(subobuid);
            if(sm==null) continue;
            Mesh ms=meshes.get(subobuid);
            if(ms==null){ ms=new Mesh(sm); meshes.put(subobuid,ms); }
            drawMeshAndSubs(ms, tx+Mesh.getFloatFromList(subobcrd,0,0), ty+Mesh.getFloatFromList(subobcrd,1,0), tz+Mesh.getFloatFromList(subobcrd,2,0));

        }catch(Throwable t){ Log.e("drawMeshAndSubs subs",""+t); t.printStackTrace(); } }
    }

    private void setVariables(Mesh m, float tx, float ty, float tz){

        Matrix.setIdentityM(matrixRtx, 0);
        Matrix.setIdentityM(matrixRty, 0);
        Matrix.setIdentityM(matrixRtz, 0);
        Matrix.setIdentityM(matrixScl, 0);
        Matrix.setIdentityM(matrixTrn, 0);

        Matrix.setRotateM(  matrixRtx, 0, m.rotationX, -1.0f, 0.0f, 0.0f);
        Matrix.setRotateM(  matrixRty, 0, m.rotationY,  0.0f, 1.0f, 0.0f);
        Matrix.setRotateM(  matrixRtz, 0, m.rotationZ,  0.0f, 0.0f, 1.0f);
        Matrix.scaleM(      matrixScl, 0, m.scaleX, m.scaleY, m.scaleZ);
        Matrix.translateM(  matrixTrn, 0, tx, ty, tz);

        Matrix.multiplyMM(  matrixRxy, 0, matrixRty, 0, matrixRtx, 0);
        Matrix.multiplyMM(  matrixRot, 0, matrixRtz, 0, matrixRxy, 0);
        Matrix.multiplyMM(  matrixRos, 0, matrixRot, 0, matrixScl, 0);
        Matrix.multiplyMM(  matrixMSR, 0, matrixTrn, 0, matrixRos, 0);

        Matrix.multiplyMM(  matrixMVV, 0, matrixVVV, 0, matrixMSR, 0);
        Matrix.multiplyMM(  matrixMVP, 0, matrixPrj, 0, matrixMVV, 0);

        GLES20.glUniformMatrix4fv(mvvmLoc, 1, false, matrixMVV, 0);
        GLES20.glUniformMatrix4fv(mvpmLoc, 1, false, matrixMVP, 0);

        GLES20.glUniform3f(lightPosLoc, lightPos[0], lightPos[1], lightPos[2]);

        throwAnyGLException("setting variables");
    }

    public ConcurrentHashMap<Mesh,Integer> meshIDs = new ConcurrentHashMap<Mesh,Integer>();

    private void uploadVBO(Mesh m){
        if(meshIDs.get(m)!=null) return;
        int vbo[] = new int[1];
        GLES20.glGenBuffers(1, vbo, 0);
        log("GPU: sending VBO "+vbo[0]+" "+m);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, m.vb.position(0).capacity()*4, m.vb, GLES20.GL_STATIC_DRAW);
        meshIDs.put(m,vbo[0]);
    }

    private void drawMesh(Mesh m){

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, meshIDs.get(m));

        GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, 32, 0);
        GLES20.glEnableVertexAttribArray(posLoc);

        GLES20.glVertexAttribPointer(norLoc, 3, GLES20.GL_FLOAT, false, 32, 12);
        GLES20.glEnableVertexAttribArray(norLoc);

        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 32, 24);
        GLES20.glEnableVertexAttribArray(texLoc);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        throwAnyGLException("VBOs");

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.il, GLES20.GL_UNSIGNED_SHORT, m.ib);

        throwAnyGLException("glDrawElements");
    }

    // -------------------------------------------------------------

    public void resetCoordsAndView(float x, float y, float z){
        direction=0;
        eyeX=x;
        eyeY=y;
        eyeZ=z;
        seeX=eyeX;
        seeY=eyeY;
        seeZ=eyeZ-4.5f;
    }

    public void stroke(float dx, float dy){
        direction -= dx/50f;
        if(direction> 2*Math.PI) direction-=2*Math.PI;
        if(direction<-2*Math.PI) direction+=2*Math.PI;
        seeX=eyeX-4.5f*FloatMath.sin(direction);
        seeZ=eyeZ-4.5f*FloatMath.cos(direction);
        eyeX+=dy/7f*FloatMath.sin(direction);
        eyeZ+=dy/7f*FloatMath.cos(direction);
        this.netmash.user.onNewCoords(eyeX, eyeY, eyeZ);
    }

    // -------------------------------------------------------------

    public void showGPULimit(){
        int[] x=new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_VARYING_VECTORS,x,0);
        log("GL_MAX_VARYING_VECTORS: "+x[0]);
    }

    // -------------------------------------------------------------

    public ConcurrentHashMap<String,Bitmap>  textureBMs = new ConcurrentHashMap<String,Bitmap>();
    public ConcurrentHashMap<String,Integer> textureIDs = new ConcurrentHashMap<String,Integer>();

    // use ETC compression; mipmaps
    private void setupTextures(Mesh m){
        for(int i=0; i< m.textures.size(); i++) {
            String url=m.textures.get(i).toString();
            Bitmap bm=netmash.getBitmap(url);
            if(bm==null) continue;
            if(bm!=textureBMs.get(url)){
                int[] texID = new int[1];
                GLES20.glGenTextures(1, texID, 0);
                sendTexture(texID[0],bm);
                textureBMs.put(url, bm);
                textureIDs.put(url, Integer.valueOf(texID[0]));
            }
            bindTexture(textureIDs.get(url),i);
        }
    }

    private void sendTexture(int texID, Bitmap bm){
        log("GPU: sending texture "+texID+","+bm);
        GLES20.glBindTexture(  GLES20.GL_TEXTURE_2D, texID);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,     GLES20.GL_REPEAT);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,     GLES20.GL_REPEAT);
        GLUtils.texImage2D(    GLES20.GL_TEXTURE_2D, 0, bm, 0);
        throwAnyGLException("sendTexture");
    }

    private void bindTexture(int texID, int i){
        GLES20.glBindTexture(  GLES20.GL_TEXTURE_2D, texID);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
        if(i==0) GLES20.glUniform1i(texture0Loc, i);
        if(i==1) GLES20.glUniform1i(texture1Loc, i);
        throwAnyGLException("bindTexture: "+texID+","+i);
    }

    // -------------------------------------------------------------

    static public MessageDigest SHA1;
    public ConcurrentHashMap<String,Integer> shaders = new ConcurrentHashMap<String,Integer>();

    private String sha1(String s){
        try{
            if(SHA1==null) SHA1=MessageDigest.getInstance("SHA-1");
            return new String(SHA1.digest(s.getBytes("UTF-8")),"UTF-8");
        }catch(Throwable t){ return s; }
    }

    private int getProgram(Mesh m) {
        String vertshad=(String)netmash.user.glElements.get(m.vertexShader);
        String fragshad=(String)netmash.user.glElements.get(m.fragmentShader);
        return getProgram(vertshad, fragshad);
    }

    private int getProgram(String vertshad, String fragshad){

        int program;

        String shadkey=sha1(vertshad+fragshad);
        Integer prog=shaders.get(shadkey);
        if(prog!=null){
            program=prog.intValue();
            GLES20.glUseProgram(program);
            throwAnyGLException("glUseProgram existing");
            return program;
        }
        log("GPU: sending program \n"+vertshad+"\n"+fragshad);

        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertshad);
        if(vertexShader==0) throw new RuntimeException("Could not compile vertexShader\n"+vertshad);

        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragshad);
        if(fragmentShader==0) throw new RuntimeException("Could not compile fragmentShader\n"+fragshad);

        program = GLES20.glCreateProgram();
        if(program==0) throw new RuntimeException("Could not create program");

        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if(linkStatus[0]==0) {
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("Could not link program " + GLES20.glGetProgramInfoLog(program)+" "+program+"\n"+vertshad+"\n"+fragshad);
        }
        GLES20.glUseProgram(program);
        throwAnyGLException("glUseProgram new: "+program+"\n"+vertshad+"\n"+fragshad);
        shaders.put(shadkey,program);
        return program;
    }

    void getProgramLocs(int program){
        if(program==0) return;
        texture0Loc = GLES20.glGetUniformLocation(program, "texture0");
        texture1Loc = GLES20.glGetUniformLocation(program, "texture1");
        mvvmLoc =     GLES20.glGetUniformLocation(program, "mvvm");
        mvpmLoc =     GLES20.glGetUniformLocation(program, "mvpm");
        lightPosLoc = GLES20.glGetUniformLocation(program, "lightPos");
        posLoc =      GLES20.glGetAttribLocation( program, "pos");
        texLoc =      GLES20.glGetAttribLocation( program, "tex");
        norLoc =      GLES20.glGetAttribLocation( program, "nor");
    }

    private int compileShader(int shaderType, String source){
        int shader = GLES20.glCreateShader(shaderType);
        if(shader==0) throw new RuntimeException("Error creating shader "+shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if(compiled[0]!=0) return shader;
        GLES20.glDeleteShader(shader);
        throw new RuntimeException("Could not compile "+shaderType+" shader:\n"+source+"\n"+GLES20.glGetShaderInfoLog(shader));
    }

    // -------------------------------------------------------------

    private void throwAnyGLException(String fn) {
        int e; while((e=GLES20.glGetError())!=GLES20.GL_NO_ERROR){ throw new RuntimeException(fn+": glError "+e); }
    }
}

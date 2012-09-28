package android.gui;

import java.util.*;
import java.util.concurrent.*;
import java.nio.*;

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
    private int touchColLoc;
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

    private float[] touchCol = new float[4];
    private float[] lightPosInModelSpace = new float[]{0.0f, 0.0f, 0.0f, 1.0f};
    private float[] lightPosInWorldSpace = new float[4];
    private float[] lightPos = new float[4];

    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private float seeX;
    private float seeY;
    private float seeZ;

    private float direction=0;

    private boolean touchDetecting=false;
    private boolean touchShift=false;
    private int     touchX,touchY;
    private float   touchDX,touchDY;

    static String pointVertexShaderSource       = "uniform mat4 mvpm; attribute vec4 pos; void main(){ gl_Position = mvpm * pos; gl_PointSize = 4.0; }";
    static String pointFragmentShaderSource     = "precision mediump float; void main(){ gl_FragColor = vec4(1.0, 1.0, 0.8, 1.0); }";
    static String grayscaleVertexShaderSource   = "uniform mat4 mvpm; attribute vec4 pos; void main(){ gl_Position=mvpm*pos; }";
    static String grayscaleFragmentShaderSource = "precision mediump float; uniform vec4 touchCol; void main(){ gl_FragColor = touchCol; }";
    static String basicVertexShaderSource       = "uniform mat4 mvpm, mvvm; attribute vec4 pos; attribute vec2 tex; attribute vec3 nor; varying vec3 mvvp; varying vec2 texturePt; varying vec3 mvvn; void main(){ texturePt = tex; mvvp = vec3(mvvm*pos); mvvn = vec3(mvvm*vec4(nor,0.0)); gl_Position = mvpm*pos; }";
    static String basicFragmentShaderSource     = "precision mediump float; uniform vec3 lightPos; uniform sampler2D texture0; varying vec3 mvvp; varying vec2 texturePt; varying vec3 mvvn; void main(){ float lgtd=length(lightPos-mvvp); vec3 lgtv=normalize(lightPos-mvvp); float dffus=max(dot(mvvn, lgtv), 0.1)*(1.0/(1.0+(0.25*lgtd*lgtd))); gl_FragColor=vec4(1.0,1.0,1.0,1.0)*(0.30+0.85*dffus)*texture2D(texture0,texturePt); }";

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
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float r = ((float)width)/height;
        Matrix.frustumM(matrixPrj, 0, -r, r, -1.0f, 1.0f, 1.0f, 100.0f);
    }

    int currentGrey;
    public ConcurrentHashMap<String,Mesh> touchables = new ConcurrentHashMap<String,Mesh>();

    @Override
    public void onDrawFrame(GL10 gl) {
        if(touchDetecting){
            currentGrey=0;
            touchables.clear();
            drawFrame();
            ByteBuffer b=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(touchX,touchY, 1,1, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, b);
            int touchedGrey=flipAndRound(((int)b.get(0)+b.get(1)+b.get(2))/3);
            Mesh m=touchables.get(""+touchedGrey);
            if(m!=null) this.netmash.user.onObjectTouched(m.mesh,touchShift,touchDX,touchDY);
            touchDetecting=false;
        }
        drawFrame();
    }

    private int flipAndRound(int n){
        if(n<0) n+=256;
        return ((n+8)/16)*16;
    }

    private void drawFrame(){
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        drawCamera();
        drawLight(true);
        drawMeshAndSubs(mesh);
    }

    private void drawCamera(){
        Matrix.setLookAtM(matrixVVV, 0, eyeX,eyeY,eyeZ, seeX,seeY,seeZ, 0f,1f,0f);
    }

    private void drawLight(boolean showPoint){

        if(touchDetecting) return;

        long time = SystemClock.uptimeMillis() % 10000L;
        float angle = (360.0f / 10000.0f) * ((int) time);

        Matrix.setIdentityM(matrixLgt, 0);
        Matrix.translateM(  matrixLgt, 0, 0.0f, 0.0f, -5.0f);
        Matrix.rotateM(     matrixLgt, 0, angle, 0.0f, 1.0f, 0.0f);
        Matrix.translateM(  matrixLgt, 0, 0.0f, 0.0f, 2.0f);
        Matrix.multiplyMV(lightPosInWorldSpace, 0, matrixLgt, 0, lightPosInModelSpace, 0);
        Matrix.multiplyMV(lightPos, 0, matrixVVV, 0, lightPosInWorldSpace, 0);

        if(showPoint) drawLightPoint();
    }

    private void drawLightPoint(){

        int program=getProgram(pointVertexShaderSource, pointFragmentShaderSource);
        if(program==0) return;

        mvpmLoc = GLES20.glGetUniformLocation(program, "mvpm");
        posLoc =  GLES20.glGetAttribLocation( program, "pos");

        GLES20.glVertexAttrib3f(posLoc, lightPosInModelSpace[0], lightPosInModelSpace[1], lightPosInModelSpace[2]);

        Matrix.multiplyMM(matrixMVV, 0, matrixVVV, 0, matrixLgt, 0);
        Matrix.multiplyMM(matrixMVP, 0, matrixPrj, 0, matrixMVV, 0);

        GLES20.glUniformMatrix4fv(mvpmLoc, 1, false, matrixMVP, 0);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);
    }

    public ConcurrentHashMap<LinkedHashMap,Mesh> meshes = new ConcurrentHashMap<LinkedHashMap,Mesh>();

    private void drawMeshAndSubs(Mesh m){

        drawAMesh(m,0,0,0);

        for(Object o: m.subObjects){
            LinkedHashMap subob=(LinkedHashMap)o;
            Object subobobj=subob.get("object");
            if(!(subobobj instanceof LinkedHashMap)) continue;
            LinkedHashMap sm=(LinkedHashMap)subobobj;
            Mesh ms=meshes.get(sm);
            if(ms==null){ ms=new Mesh(sm); meshes.put(sm,ms); }
            Object subobcrd=subob.get("coords");
            if(subobcrd!=null) drawAMesh(ms, Mesh.getFloatFromList(subobcrd,0,0), Mesh.getFloatFromList(subobcrd,1,0), Mesh.getFloatFromList(subobcrd,2,0));
            else               drawEditMesh(ms);
        }
    }

    private void drawAMesh(Mesh m, float tx, float ty, float tz){
        int program=0;
        if(!touchDetecting){
            program=getProgram(m);
            getProgramLocs(program);
            setupTextures(m);
        }else{
            program=getProgram(grayscaleVertexShaderSource, grayscaleFragmentShaderSource);
            getProgramLocs(program);
        }
        setVariables(m, tx,ty,tz);
        uploadVBO(m);
        drawMesh(m);
    }

    private void drawEditMesh(Mesh m){
        if(!touchDetecting){
            int program=getProgram(m);
            getProgramLocs(program);
            setupTextures(m);
        }else{
            int program=getProgram(grayscaleVertexShaderSource, grayscaleFragmentShaderSource);
            getProgramLocs(program);
        }
        setVariablesForEdit(m);
        uploadVBO(m);
        drawMesh(m);
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

        if(!touchDetecting)
        GLES20.glUniformMatrix4fv(mvvmLoc, 1, false, matrixMVV, 0);
        GLES20.glUniformMatrix4fv(mvpmLoc, 1, false, matrixMVP, 0);
        if(touchDetecting){
            currentGrey+=16;
            touchCol[0]=currentGrey/256.0f;
            touchCol[1]=currentGrey/256.0f;
            touchCol[2]=currentGrey/256.0f;
            GLES20.glUniform4fv(touchColLoc, 1, touchCol, 0);
            touchables.put(""+currentGrey,m);
        }
        else
        GLES20.glUniform3f(lightPosLoc, lightPos[0], lightPos[1], lightPos[2]);

        throwAnyGLException("setting variables");
    }

    private void setVariablesForEdit(Mesh m){

        Matrix.setIdentityM(matrixMVV, 0);
        matrixMVV[12]= 0.0f; matrixMVV[13]= 0.5f; matrixMVV[14]= -1.6f;
        Matrix.multiplyMM(  matrixMVP, 0, matrixPrj, 0, matrixMVV, 0);

        if(!touchDetecting)
        GLES20.glUniformMatrix4fv(mvvmLoc, 1, false, matrixMVV, 0);
        GLES20.glUniformMatrix4fv(mvpmLoc, 1, false, matrixMVP, 0);
        if(touchDetecting){
            currentGrey+=16;
            touchCol[0]=currentGrey/256.0f;
            touchCol[1]=currentGrey/256.0f;
            touchCol[2]=currentGrey/256.0f;
            GLES20.glUniform4fv(touchColLoc, 1, touchCol, 0);
            touchables.put(""+currentGrey,m);
        }
        else
        GLES20.glUniform3f(lightPosLoc, 0f, 1f, -2f);

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
        if(!touchDetecting){
        GLES20.glVertexAttribPointer(norLoc, 3, GLES20.GL_FLOAT, false, 32, 12);
        GLES20.glEnableVertexAttribArray(norLoc);

        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 32, 24);
        GLES20.glEnableVertexAttribArray(texLoc);
        }

        throwAnyGLException("VBOs");

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.il, GLES20.GL_UNSIGNED_SHORT, m.ib);

        GLES20.glDisableVertexAttribArray(posLoc);
        GLES20.glDisableVertexAttribArray(norLoc);
        GLES20.glDisableVertexAttribArray(texLoc);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

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

    public void swipeIn(float dx, float dy){
        direction += dx/50f;
        if(direction> 2*Math.PI) direction-=2*Math.PI;
        if(direction<-2*Math.PI) direction+=2*Math.PI;
        seeX=eyeX-4.5f*FloatMath.sin(direction);
        seeZ=eyeZ-4.5f*FloatMath.cos(direction);
        eyeX-=dy/7f*FloatMath.sin(direction);
        eyeZ-=dy/7f*FloatMath.cos(direction);
        this.netmash.user.onNewCoords(eyeX, eyeY, eyeZ);
    }

    public void swipeOn(boolean shift, int x, int y, float dx, float dy){
        if(touchDetecting) return;
        touchDetecting=true;
        touchShift=shift;
        touchX=x; touchY=y;
        touchDX=dx; touchDY=dy;
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
            Bitmap       bm=netmash.user.textBitmaps.get(url);
            if(bm==null) bm=netmash.getBitmap(url);
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

    public ConcurrentHashMap<String,Integer> shaders = new ConcurrentHashMap<String,Integer>();

    private int getProgram(Mesh m) {
        String vertshad=join(m.vertexShader," ");
        String fragshad=join(m.fragmentShader," ");
        if(vertshad.length()==0 || fragshad.length()==0){ vertshad=basicVertexShaderSource; fragshad=basicFragmentShaderSource; }
        return getProgram(vertshad, fragshad);
    }

    private int getProgram(String vertshad, String fragshad){

        int program;

        String shadkey=String.format("%d%d",vertshad.hashCode(),fragshad.hashCode());
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
        if(!touchDetecting){
        texture0Loc = GLES20.glGetUniformLocation(program, "texture0");
        texture1Loc = GLES20.glGetUniformLocation(program, "texture1");
        mvvmLoc =     GLES20.glGetUniformLocation(program, "mvvm");
        lightPosLoc = GLES20.glGetUniformLocation(program, "lightPos");
        texLoc =      GLES20.glGetAttribLocation( program, "tex");
        norLoc =      GLES20.glGetAttribLocation( program, "nor");
        }
        mvpmLoc =     GLES20.glGetUniformLocation(program, "mvpm");
        posLoc =      GLES20.glGetAttribLocation( program, "pos");
        if(touchDetecting)
        touchColLoc = GLES20.glGetUniformLocation(program, "touchCol");
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

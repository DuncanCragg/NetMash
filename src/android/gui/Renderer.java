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
    private int mvvmLoc;
    private int mvpmLoc;
    private int touchColLoc;
    private int lightPosLoc;
    private int lightColLoc;
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
    private float[] lightPosWorld = new float[]{0f,0f,0f,1f};
    private float[] lightPos = new float[4];
    private float[] lightCol = new float[]{1f,1f,1f,1f};

    private float eyeX;
    private float eyeY;
    private float eyeZ;
    private float seeX;
    private float seeY;
    private float seeZ;

    private float direction=0;

    private boolean touchDetecting=false;
    private boolean touchEdit=false;
    private int     touchX,touchY;
    private float   touchDX,touchDY;
    private boolean lightObject=false;
    // touchDetecting => mvpm; pos; touchCol
    // lightObject    => mvpm; pos; tex; lightCol; texture0

    static String basicVertexShaderSource       = "uniform mat4 mvpm, mvvm; attribute vec4 pos; attribute vec2 tex; attribute vec3 nor; varying vec3 mvvp; varying vec2 texturePt; varying vec3 mvvn; void main(){ texturePt = tex; mvvp = vec3(mvvm*pos); mvvn = vec3(mvvm*vec4(nor,0.0)); gl_Position = mvpm*pos; }";
    static String basicFragmentShaderSource     = "precision mediump float; uniform vec3 lightPos; uniform vec3 lightCol; uniform sampler2D texture0; varying vec3 mvvp; varying vec2 texturePt; varying vec3 mvvn; void main(){ float lgtd=length(lightPos-mvvp); vec3 lgtv=normalize(lightPos-mvvp); float dffus=max(dot(mvvn, lgtv), 0.1)*(1.0/(1.0+(0.25*lgtd*lgtd))); gl_FragColor=vec4(lightCol,1.0)*(0.30+0.85*dffus)*texture2D(texture0,texturePt); }";
    static String grayscaleVertexShaderSource   = "uniform mat4 mvpm; attribute vec4 pos; void main(){ gl_Position=mvpm*pos; }";
    static String grayscaleFragmentShaderSource = "precision mediump float; uniform vec4 touchCol; void main(){ gl_FragColor = touchCol; }";
    static String lightVertexShaderSource       = "uniform mat4 mvpm; attribute vec4 pos; attribute vec2 tex; varying vec2 texturePt; void main(){ texturePt = tex; gl_Position = mvpm*pos; }";
    static String lightFragmentShaderSource     = "precision mediump float; uniform vec3 lightCol; uniform sampler2D texture0; varying vec2 texturePt; void main(){ gl_FragColor=vec4(lightCol,1.0)*texture2D(texture0,texturePt); }";

    public Renderer(NetMash netmash, LinkedHashMap hm) {
        this.netmash=netmash;
        this.mesh=new Mesh(hm,netmash.user);
        resetCoordsAndView(0,1.0f,-0.5f);
    }

    synchronized public void newMesh(LinkedHashMap hm){
        this.mesh=meshes.get(System.identityHashCode(hm));
        if(this.mesh!=null) return;
        this.mesh=new Mesh(hm,netmash.user);
        meshes.put(System.identityHashCode(hm),this.mesh);
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
    public void onDrawFrame(GL10 gl){ onDrawFrame(); }

    synchronized private void onDrawFrame(){
        if(touchDetecting){try{
            currentGrey=0;
            touchables.clear();
            drawFrame();
            touchDetecting=false;
            ByteBuffer b=ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(touchX,touchY, 1,1, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, b);
            throwAnyGLException("glReadPixels ",touchX,touchY,b);
            int touchedGrey=flipAndRound(((int)b.get(0)+b.get(1)+b.get(2))/3);
            Mesh m=touchables.get(""+touchedGrey);
            if(m!=null) netmash.user.onObjectTouched(m.mesh,touchEdit,touchDX,touchDY);
        }catch(Throwable t){ log(t); }}
        drawFrame();
    }

    private int flipAndRound(int n){
        if(n<0) n+=256;
        return ((n+8)/16)*16;
    }

    private void drawFrame(){
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        drawCamera();
        drawMeshAndSubs(mesh);
    }

    private void drawCamera(){
        Matrix.setLookAtM(matrixVVV, 0, eyeX,eyeY,eyeZ, seeX,seeY,seeZ, 0f,1f,0f);
    }

    public ConcurrentHashMap<Integer,Mesh> meshes = new ConcurrentHashMap<Integer,Mesh>();

    private void drawMeshAndSubs(Mesh m){

        drawAMesh(m,0,0,0);

        for(Object o: m.subObjects){
            LinkedHashMap subob=(LinkedHashMap)o;
            Object subobobj=subob.get("object");
            if(!(subobobj instanceof LinkedHashMap)) continue;
            LinkedHashMap hm=(LinkedHashMap)subobobj;
            Mesh ms=meshes.get(System.identityHashCode(hm));
            if(ms==null){ ms=new Mesh(hm,netmash.user); meshes.put(System.identityHashCode(hm),ms); }
            Object subobcrd=subob.get("coords");
            if(subobcrd!=null) drawAMesh(ms, Mesh.getFloatFromList(subobcrd,0,0), Mesh.getFloatFromList(subobcrd,1,0), Mesh.getFloatFromList(subobcrd,2,0));
            else               drawEditMesh(ms);
        }
    }

    private void drawAMesh(Mesh m, float tx, float ty, float tz){
        int program=0;
        if(!touchDetecting){
            lightObject=(m.lightR+m.lightG+m.lightB >0);
            if(lightObject){
                lightPosWorld[0]=tx; lightPosWorld[1]=ty; lightPosWorld[2]=tz;
                lightCol[0]=clamp(m.lightR); lightCol[1]=clamp(m.lightG); lightCol[2]=clamp(m.lightB);
                program=getProgram(lightVertexShaderSource, lightFragmentShaderSource);
            }
            else program=getProgram(m);
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

        if(!touchDetecting && !lightObject)
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
        else{
            if(!lightObject){
                Matrix.multiplyMV(lightPos, 0, matrixVVV, 0, lightPosWorld, 0);
                GLES20.glUniform3f(lightPosLoc, lightPos[0], lightPos[1], lightPos[2]);
            }
            GLES20.glUniform3f(lightColLoc, lightCol[0], lightCol[1], lightCol[2]);
        }
        throwAnyGLException("Setting variables");
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
        else{
            GLES20.glUniform3f(lightPosLoc, 0f, 1f, -2f);
            GLES20.glUniform3f(lightColLoc, 1f, 1f, 1f);
        }

        throwAnyGLException("Setting variables for edit");
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
        GLES20.glEnableVertexAttribArray(posLoc); throwAnyGLException("VBOs pos",posLoc,m);
        if(!touchDetecting){ if(!lightObject){
        GLES20.glVertexAttribPointer(norLoc, 3, GLES20.GL_FLOAT, false, 32, 12);
        GLES20.glEnableVertexAttribArray(norLoc); throwAnyGLException("VBOs nor",norLoc,m);
        }
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 32, 24);
        GLES20.glEnableVertexAttribArray(texLoc); throwAnyGLException("VBOs tex",texLoc,m);
        }

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, m.il, GLES20.GL_UNSIGNED_SHORT, m.ib);
        throwAnyGLException("glDrawElements");

        GLES20.glDisableVertexAttribArray(posLoc);
        if(!touchDetecting){ if(!lightObject){
        GLES20.glDisableVertexAttribArray(norLoc);
        }
        GLES20.glDisableVertexAttribArray(texLoc);
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        throwAnyGLException("drawMesh disable/unbind",posLoc,norLoc,texLoc,m);
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

    public void swipe(boolean shift, int edge, int x, int y, float dx, float dy){
        if(!shift){
            if(edge!=2){
                direction += dx/50f;
                if(direction> 2*Math.PI) direction-=2*Math.PI;
                if(direction<-2*Math.PI) direction+=2*Math.PI;
                seeX=eyeX-4.5f*FloatMath.sin(direction);
                seeZ=eyeZ-4.5f*FloatMath.cos(direction);
                eyeX-=dy/7f*FloatMath.sin(direction);
                eyeZ-=dy/7f*FloatMath.cos(direction);
                netmash.user.onNewCoords(eyeX, eyeY, eyeZ);
            }
            else{
                eyeX-=dx/7f*FloatMath.cos(direction)+dy/7f*FloatMath.sin(direction);
                eyeZ+=dx/7f*FloatMath.sin(direction)-dy/7f*FloatMath.cos(direction);
                seeX=eyeX-4.5f*FloatMath.sin(direction);
                seeZ=eyeZ-4.5f*FloatMath.cos(direction);
                netmash.user.onNewCoords(eyeX, eyeY, eyeZ);
            }
        }else{
            if(touchDetecting) return;
            touchDetecting=true;
            touchEdit=false;
            touchX=x; touchY=y;
            touchDX=dx; touchDY=dy;
        }
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
            Bitmap       bm=null;
            if(url.equals("placeholder")) bm=netmash.getPlaceHolderBitmap();
            if(bm==null)                  bm=netmash.user.textBitmaps.get(url);
            if(bm==null)                  bm=netmash.getBitmap(url);
            if(bm==null) continue;
            if(bm!=textureBMs.get(url)){
                int[] texID = new int[1];
                GLES20.glGenTextures(1, texID, 0);
                log("GPU: sending texture "+url+","+texID[0]+","+bm);
                sendTexture(texID[0],bm);
                textureBMs.put(url, bm);
                textureIDs.put(url, texID[0]);
            }
            bindTexture(textureIDs.get(url),i);
        }
    }

    private void sendTexture(int texID, Bitmap bm){
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
        throwAnyGLException("bindTexture: ",texID,",",i);
    }

    // -------------------------------------------------------------

    public ConcurrentHashMap<Integer,Integer> shaders = new ConcurrentHashMap<Integer,Integer>();

    private int getProgram(Mesh m) {
        String vertshad=m.vertexShader;
        String fragshad=m.fragmentShader;
        if(vertshad.length()==0 || fragshad.length()==0){ vertshad=basicVertexShaderSource; fragshad=basicFragmentShaderSource; }
        return getProgram(vertshad, fragshad);
    }

    private int getProgram(String vertshad, String fragshad){

        int program;

        int shadkey=vertshad.hashCode()+fragshad.hashCode();
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
        throwAnyGLException("glCreateProgram: ",program);

        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        throwAnyGLException("glAttachShader: ",program,"\n",vertshad,"\n",fragshad);

        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if(linkStatus[0]==0) {
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("Could not link program " + GLES20.glGetProgramInfoLog(program)+" "+program+"\n"+vertshad+"\n"+fragshad);
        }
        throwAnyGLException("glLinkProgram: ",program,"\n",vertshad,"\n",fragshad);

        GLES20.glUseProgram(program);
        throwAnyGLException("glUseProgram new: ",program,"\n",vertshad,"\n",fragshad);
        shaders.put(shadkey,program);
        return program;
    }

    void getProgramLocs(int program){
        if(program==0) return;
        mvpmLoc =     GLES20.glGetUniformLocation(program, "mvpm");
        posLoc =      GLES20.glGetAttribLocation( program, "pos");
        if(touchDetecting){
            touchColLoc = GLES20.glGetUniformLocation(program, "touchCol");
            return;
        }
        texLoc =      GLES20.glGetAttribLocation( program, "tex");
        lightColLoc = GLES20.glGetUniformLocation(program, "lightCol");
        texture0Loc = GLES20.glGetUniformLocation(program, "texture0");
        if(lightObject) return;
        mvvmLoc =     GLES20.glGetUniformLocation(program, "mvvm");
        norLoc =      GLES20.glGetAttribLocation( program, "nor");
        lightPosLoc = GLES20.glGetUniformLocation(program, "lightPos");
    }

    private int compileShader(int shaderType, String source){
        int shader = GLES20.glCreateShader(shaderType);
        if(shader==0) throw new RuntimeException("Error creating shader "+shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        throwAnyGLException("compileShader: ",shaderType,"\n",source);
        if(compiled[0]!=0) return shader;
        GLES20.glDeleteShader(shader);
        throw new RuntimeException("Could not compile "+shaderType+" shader:\n"+source+"\n"+GLES20.glGetShaderInfoLog(shader));
    }

    // -------------------------------------------------------------

    private float clamp(float x){ if(x>1.0) return 1; if(x<0) return 0; return x; }

    private void throwAnyGLException(Object...strings) {
        int e; while((e=GLES20.glGetError())!=GLES20.GL_NO_ERROR){ throw new RuntimeException(Arrays.asList(strings)+": glError "+e); }
    }
}

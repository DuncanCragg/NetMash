
package android.gui;

import java.util.*;
import java.nio.*;

import android.util.Log;

public class Mesh {

    FloatBuffer vb;
    ShortBuffer ib;
    int         il;
    LinkedList  textures;
    String      vertexShader;
    String      fragmentShader;
    LinkedList  subObjects;
    float       rotationX;
    float       rotationY;
    float       rotationZ;
    float       scaleX;
    float       scaleY;
    float       scaleZ;

    @SuppressWarnings("unchecked")
    public Mesh(LinkedHashMap mesh) {

        try {
            ArrayList<Float> vs = new ArrayList<Float>(256);
            ArrayList<Float> ns = new ArrayList<Float>(256);
            ArrayList<Float> tp = new ArrayList<Float>(256);

            int numVertices=0;
            LinkedList verts=getListFromHash(mesh,"vertices");
            for(Object vert: verts){
                vs.add(getFloatFromList(vert,0,0f));
                vs.add(getFloatFromList(vert,1,0f));
                vs.add(getFloatFromList(vert,2,0f));
                numVertices++;
            }
            LinkedList norms=getListFromHash(mesh,"normals");
            for(Object norm: norms){
                ns.add(getFloatFromList(norm,0,0f));
                ns.add(getFloatFromList(norm,1,0f));
                ns.add(getFloatFromList(norm,2,0f));
            }
            int numTexPoints=0;
            LinkedList texts=getListFromHash(mesh,"texturepoints");
            for(Object text: texts){
                tp.add(getFloatFromList(text,0,0f));
                tp.add(getFloatFromList(text,1,0f));
                numTexPoints++;
            }

            float[] v = new float[numVertices * 3];
            float[] n = new float[numVertices * 3];
            float[] t = new float[numTexPoints * 2];

            for(int i=0; i< numVertices; i++){

                v[i*3]   =  vs.get(i*3);
                v[i*3+1] =  vs.get(i*3+1);
                v[i*3+2] =  vs.get(i*3+2);

                n[i*3]   = -ns.get(i*3);
                n[i*3+1] = -ns.get(i*3+1);
                n[i*3+2] = -ns.get(i*3+2);

                if(i>=numTexPoints) continue;
                t[i*2]   =  tp.get(i*2);
                t[i*2+1] =  tp.get(i*2+1);
            }

            ArrayList<Short> ind = new ArrayList<Short>(numVertices * 3);
            ArrayList<Float> vnt = new ArrayList<Float>(numVertices * 6);
            short index = 0;
            LinkedList faces=getListFromHash(mesh,"faces");
            for(Object face: faces){
                for(int i=0; i<3; i++) {
                    String f=getStringFromList(face,i,"1/1/2");
                    StringTokenizer st=new StringTokenizer(f, "/");
                    int fv=Integer.parseInt(st.nextToken())-1;
                    int ft=Integer.parseInt(st.nextToken())-1;
                    int fn=Integer.parseInt(st.nextToken())-1;

                    vnt.add(v[fv*3  ]);
                    vnt.add(v[fv*3+1]);
                    vnt.add(v[fv*3+2]);

                    vnt.add(n[fn*3  ]);
                    vnt.add(n[fn*3+1]);
                    vnt.add(n[fn*3+2]);

                    vnt.add(t[ft*2  ]);
                    vnt.add(t[ft*2+1]);

                    ind.add(index++);
                }
            }
            vnt.trimToSize();
            float[] va = new float[vnt.size()];
            for(int i=0; i< vnt.size(); i++) va[i]=vnt.get(i);

            ind.trimToSize();
            short[] ia = new short[ind.size()];
            for(int i=0; i< ind.size(); i++) ia[i]=ind.get(i);

            vb = ByteBuffer.allocateDirect(va.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            vb.put(va).position(0);

            ib = ByteBuffer.allocateDirect(ia.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();
            ib.put(ia).position(0);

            il=ia.length;

            textures       = getListFromHash(  mesh,"textures");
            vertexShader   = getStringFromHash(mesh,"vertexShader",  "basicVert");
            fragmentShader = getStringFromHash(mesh,"fragmentShader","basicFrag");
            subObjects     = getListFromHash(  mesh,"subObjects");

            rotationX      = getFloatFromList(getListFromHash(mesh,"rotation"), 0, 0f);
            rotationY      = getFloatFromList(getListFromHash(mesh,"rotation"), 1, 0f);
            rotationZ      = getFloatFromList(getListFromHash(mesh,"rotation"), 2, 0f);
            scaleX         = getFloatFromList(getListFromHash(mesh,"scale"   ), 0, 1f);
            scaleY         = getFloatFromList(getListFromHash(mesh,"scale"   ), 1, 1f);
            scaleZ         = getFloatFromList(getListFromHash(mesh,"scale"   ), 2, 1f);

        } catch (Exception e) { e.printStackTrace(); Log.e("ERROR", e.getLocalizedMessage()); return; }
    }

    static public LinkedList getListFromHash(LinkedHashMap hm, String tag){
        Object o=hm.get(tag);
        if(o==null || !(o instanceof LinkedList)) return new LinkedList();
        return (LinkedList)o;
    }

    static public LinkedHashMap getHashFromHash(LinkedHashMap hm, String tag){
        Object o=hm.get(tag);
        if(o==null || !(o instanceof LinkedHashMap)) return new LinkedHashMap();
        return (LinkedHashMap)o;
    }

    static public String getStringFromHash(LinkedHashMap hm, String tag, String d){
        Object o=hm.get(tag);
        if(o==null || !(o instanceof String) || ((String)o).length()==0) return d;
        return (String)o;
    }

    static public Float getFloatFromList(Object o, int i, float d){
        if(o==null || !(o instanceof LinkedList)) return d;
        LinkedList ll=((LinkedList)o);
        if(i>=ll.size()) return d;
        Object f=ll.get(i);
        if(f==null) return d;
        if(f instanceof Number) return ((Number)f).floatValue();
        if(f instanceof String) return Float.parseFloat((String)f);
        return d;
    }

    static public String getStringFromList(Object ll, int i, String d){
        if(!(ll instanceof LinkedList)) return d;
        Object o=((LinkedList)ll).get(i);
        if(o==null || !(o instanceof String)) return d;
        return (String)o;
    }
}



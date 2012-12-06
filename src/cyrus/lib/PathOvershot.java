
package cyrus.lib;

public class PathOvershot extends Exception{

    private Object leaf;
    private String[] parts;
    private int i;
    private String path=null;

    PathOvershot(Object leaf, String[] parts, int i){
        this.leaf = leaf;
        this.parts = parts;
        this.i = i;
    }

    public Object leaf(){ return leaf; }

    public String path(){
        if(path==null){
            path = parts[++i];
            for(i++ ; i<parts.length; i++) path+=":"+parts[i];
        }
        return path;
    }
}


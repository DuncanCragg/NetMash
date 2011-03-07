
package netmash.lib;

public class PathOvershot extends Exception{

    public Object leaf;
    public String path;

    PathOvershot(Object leaf, String path){
        this.leaf = leaf;
        this.path = path;
    }

    public String toString(){
        return leaf+" / "+path;
    }
}


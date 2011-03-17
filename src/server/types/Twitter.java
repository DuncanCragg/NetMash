
package server.types;

import java.util.*;
import java.util.regex.*;

import netmash.lib.JSON;
import netmash.forest.WebObject;

/** Twitter API driver.
  */
public class Twitter extends WebObject {

    public Twitter(){}

    public void evaluate(){
        if(contentListContains("is", "twitter")){
            answerFollowersQuery();
            answerPostsQuery();
        }
        else
        if(contentListContains("is", "results")){
            notifyResults();
        }
    }

    private void answerFollowersQuery(){
        for(String queryuid: alerted()){
            content("query", queryuid);
            if(contentListContains("query:is", "query")){
                if(contentListContainsAll("query:query:is", list("twitter", "followers"))){ logrule();
                    String userid = content("query:query:user:id");
                    if(userid==null) return;
                    String indpath = "indexes:followers:user-id:"+userid;
                    if(contentHash(indpath)==null){
                        httpGETJSON("http://api.twitter.com/1/followers/ids/"+userid+".json", queryuid);
                        contentHash(indpath, hash("status", "loading..."));
                    }
                    else{
                        spawn(new Twitter(queryuid, uid, indpath));
                    }
                }
            }
        }
    }

    private void answerPostsQuery(){
        for(String queryuid: alerted()){
            content("query", queryuid);
            if(contentListContains("query:is", "query")){
                if(contentListContainsAll("query:query:is", list("twitter", "post"))){ logrule();
                    String usernum = content("query:query:user:id2");
                    if(usernum==null) return; logrule();
                    String indpath = "indexes:posts:user-num:"+usernum;
                    if(contentHash(indpath)==null){
                        httpGETJSON("http://api.twitter.com/1/statuses/user_timeline/"+usernum+".json?trim_user=true", queryuid);
                        contentList(indpath, list("status", "loading..."));
                    }
                    else{
                        spawn(new Twitter(queryuid, uid, indpath));
                    }
                }
            }
        }
    }

    static public final JSON Twitter2NetMashMap = new JSON("{ \"id2\":      \"id\","+
                                                             "\"text\":     \"text\","+
                                                             "\"created\":  \"created_at\","+
                                                             "\"location\": \"geo\""+
                                                           "}");
    @Override
    @SuppressWarnings("unchecked")
    public void httpNotifyJSON(final JSON json, final String queryuid){
        new Evaluator(this){
            public void evaluate(){ logrule();
                content("query", queryuid);
                LinkedList list = json.listPathN("list");
                if(list.size()==0){
                    spawn(new Twitter(queryuid, null, null));
                }
                else
                if(list.get(0) instanceof Integer){
                    String userid = content("query:query:user:id");
                    String indpath = "indexes:followers:user-id:"+userid;
                    JSON results = new JSON("{ \"is\": [ \"twitter\", \"followers\" ] }");
                    results.listPath("list", spawnUsers((LinkedList<Integer>)list));
                    contentHash(indpath, results);
                    spawn(new Twitter(queryuid, uid, indpath));
                }
                else{
                    String usernum = content("query:query:user:id2");
                    String indpath = "indexes:posts:user-num:"+usernum;
                    contentList(indpath, json.mapList("list", Twitter2NetMashMap));
                    spawn(new Twitter(queryuid, uid, indpath));
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    private LinkedList spawnUsers(LinkedList<Integer> followeridnums){
        LinkedList l=new LinkedList();
        for(Integer followeridnum: followeridnums){
            l.add(spawn(new Twitter(followeridnum)));
        }
        return l;
    }

    public Twitter(Integer followeridnum){
        super("{ \"is\": [ \"twitter\", \"user\" ], \n"+
              "  \"id2\": \""+followeridnum+"\"\n"+
              "}");
    }

    public Twitter(String queryuid, String topuid, String indpath){
        super("{ \"is\": [ \"query\", \"results\" ],"+
               " \"query\": \""+queryuid+"\","+
               " \"twitter\": \""+topuid+"\","+
               " \"indexpath\": \""+indpath+"\" }");
    }

    private void notifyResults(){ logrule();
        if(contentHash("results")==null){ logrule();
            String indpath=content("indexpath");
            if(indpath==null){
                content("results", "none");
            }
            else
            if(indpath.startsWith("indexes:followers:user-id:")){
                LinkedHashMap results=contentHash("twitter:"+indpath);
                if(results!=null){
                   contentHash("results", results);
                   contentRemove("indexpath");
                }
            }
            else
            if(indpath.startsWith("indexes:posts:user-num:")){
                LinkedList results=contentList("twitter:"+indpath);
                if(results!=null){
                   contentList("results", results);
                   contentRemove("indexpath");
                }
            }
            notifying(content("query"));
        }
    }
}


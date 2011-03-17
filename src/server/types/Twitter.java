
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
            answerTimelineQuery();
        }
        else
        if(contentListContains("is", "results")){
            notifyResults();
        }
    }

    private void answerFollowersQuery(){
        for(String queryuid: alerted()){
            if(contentListOfContains(queryuid, "is", "query")){
               if(contentListOfContainsAll(queryuid, "query:is", list("twitter", "followers"))){ logrule();
                    String userid = contentOf(queryuid, "query:user:id");
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

    private void answerTimelineQuery(){ /*
        for(String queryuid: alerted()){
            if(contentListOfContainsAll(queryuid, "is", list("twitter", "query"))){
                String query=contentOf(queryuid, "query");  // or content("query:/users:.*:followers/)!=null
                if(query!=null){ logrule();
                    Matcher m = TIMEQPA.matcher(query);
                    if(m.matches()){
                        if(contentList(query)==null){
                            String user = m.group(1);
                            httpGETJSON("http://api.twitter.com/1/statuses/user_timeline/"+user+".json?trim_user=true", queryuid);
                            contentHash("users:"+user, hash("timeline", "loading..."));
                        }
                        else{
                            spawn(new Twitter(uid, queryuid));
                        }
                    }
                }
            }
        }
    */}

    static public final JSON Twitter2MicroBlogMap=new JSON("{ \"id\":      \"id\","+
                                                             "\"text\":    \"text\","+
                                                             "\"created\": \"created_at\","+
                                                             "\"geo\":     \"geo\""+
                                                           "}");
                //json.mapList("list", Twitter2MicroBlogMap));
    @Override
    @SuppressWarnings("unchecked")
    public void httpNotifyJSON(final JSON json, final String queryuid){
        new Evaluator(this){
            public void evaluate(){ logrule();
                LinkedList<Integer> followeridnums = json.listPathN("list");
                JSON result = new JSON("{ \"is\": [ \"twitter\", \"followers\" ] }");
                result.listPath("list", spawnUsers(followeridnums));
                String userid = contentOf(queryuid, "query:user:id");
                String indpath = "indexes:followers:user-id:"+userid;
                contentHash(indpath, result);
                spawn(new Twitter(queryuid, uid, indpath));
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
        if(contentHash("result")==null){
            LinkedHashMap result=contentHash("twitter:"+content("indexpath"));
            if(result!=null){
               contentHash("result", result);
               contentRemove("indexpath");
               notifying(content("query"));
            }
        }
    }
}



import java.util.*;
import java.util.regex.*;

import jungle.lib.JSON;
import jungle.forest.WebObject;

/** Twitter API driver.
  */
public class Twitter extends WebObject {

    public Twitter(){}

    public Twitter(String topuid, String queryuid){
        super("{ \"is\": [ \"twitter\", \"results\" ], \n"+
              "  \"top\": \""+topuid+"\",\n"+
              "  \"query\": \""+queryuid+"\"\n"+
              "}");
    }

    public void evaluate(){
        if(contentListContains("is", "top")){
            answerQuery();
        }
        else
        if(contentListContains("is", "results")){
            notifyResults();
        }
    }

    static public final String  FOLLQRE = "users:(.*):followers";
    static public final Pattern FOLLQPA = Pattern.compile(FOLLQRE);

    private void answerQuery(){
        for(String queryuid: alerted()){
            if(contentListOfContainsAll(queryuid, "is", list("twitter", "query"))){
                String query=contentOf(queryuid, "query");  // or content("query:/users:.*:followers/)!=null
                if(query!=null){
                    Matcher m = FOLLQPA.matcher(query);
                    if(m.matches()){
                        if(contentList(query)==null){
                            String user = m.group(1);
                            httpGETJSON("http://api.twitter.com/1/followers/ids/"+user+".json", queryuid);
                            contentHash("users:"+user, hash("followers", "loading.."));
                        }
                        else{
                            spawn(new Twitter(uid, queryuid));
                        }
                    }
                }
            }
        }
    }

    @Override
    public void httpNotifyJSON(final JSON json, final String queryuid){
        new Evaluator(this){
            public void evaluate(){
                String query=contentOf(queryuid, "query");
                contentList(query, json.listPathN("list"));
                spawn(new Twitter(uid, queryuid));
            }
        };
    }

    private void notifyResults(){
        String query=content("query:query");
        LinkedList results=contentList("top:"+query);
        contentList("results", results);
        notifying(content("query"));
    }
}



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
    static public final String  TIMEQRE = "users:(.*):timeline";
    static public final Pattern TIMEQPA = Pattern.compile(TIMEQRE);

    private void answerQuery(){
        for(String queryuid: alerted()){
            if(contentListOfContainsAll(queryuid, "is", list("twitter", "query"))){
                String query=contentOf(queryuid, "query");  // or content("query:/users:.*:followers/)!=null
                if(query!=null){ logrule();
                    Matcher m = FOLLQPA.matcher(query);
                    if(m.matches()){
                        if(contentList(query)==null){
                            String user = m.group(1);
                            httpGETJSON("http://api.twitter.com/1/followers/ids/"+user+".json", queryuid);
                            contentHash("users:"+user, hash("followers", "loading..."));
                        }
                        else{
                            spawn(new Twitter(uid, queryuid));
                        }
                    }
                    else{
                    m = TIMEQPA.matcher(query);
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
        }
    }

    @Override
    public void httpNotifyJSON(final JSON json, final String queryuid){
        new Evaluator(this){
            public void evaluate(){ logrule();
                String query=contentOf(queryuid, "query");
                contentList(query, json.listPathN("list"));
                spawn(new Twitter(uid, queryuid));
            }
        };
    }

    private void notifyResults(){ logrule();
        String query=content("query:query");
        if(query!=null){
            LinkedList results=contentList("top:"+query);
            if(results!=null){
                contentList("results", results);
                notifying(content("query"));
           }
       }
    }
}


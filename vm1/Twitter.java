
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
            replyOrFetch();
        }
    }

    private void answerQuery(){
        for(String queryuid: alerted()){
            if(contentListOfContainsAll(queryuid, "is", list("twitter", "query"))){
                spawn(new Twitter(uid, queryuid));
            }
        }
    }

    static public final String  FOLLQRE = "users:(.*):followers";
    static public final Pattern FOLLQPA = Pattern.compile(FOLLQRE);

    private void replyOrFetch(){
        if(content("results")!=null) return;
        String query=content("query:query");  // or content("query:query:/users:.*:followers/)!=null
        if(query!=null){
            Matcher m = FOLLQPA.matcher(query);
            if(m.matches()){
                LinkedList followers=contentList("top:"+query);
                if(followers!=null){
                    contentList("results", followers);
                    notifying(content("query"));
                }
                else{
                    String user = m.group(1);
                    content("results", "fetching "+user);
                    httpGETJSON("http://twitter.com/statuses/followers/"+user+".json");
                }
            }
        }
    }

    @Override
    public void httpNotifyJSON(final JSON json){
        new Evaluator(this){
            public void evaluate(){
log("Twitter callback: "+json.stringPathN("list:0:name"));
            }
        };
    }
}


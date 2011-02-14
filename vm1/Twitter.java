
import java.util.*;
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

    private void replyOrFetch(){
        String query=content("query:query");
        if(query!=null){
            if(query.endsWith(":followers")){  // or content("query:query:/.*:followers/)!=null
                LinkedList followers=contentList("top:"+query);
                if(followers!=null){
                    contentList("results", followers);
                    notifying(content("query"));
                }
                else{
                }
            }
        }
    }

}


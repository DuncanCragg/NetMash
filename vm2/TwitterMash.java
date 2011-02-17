
import java.util.*;
import java.util.regex.*;

import jungle.lib.JSON;
import jungle.forest.WebObject;

/** Declarative, Concurrent Twitter Mashup in the Object Web Cloud with a NoSQL database
  * and Map-Reduce!.
  */
public class TwitterMash extends WebObject {

    public TwitterMash(){}

    public TwitterMash(String userid, String topuid, String twitter){
        super("{ \"is\": [ \"twittermash\", \"masher\" ], \n"+
              "  \"userid\":  \""+userid+"\",\n"+
              "  \"topuid\":  \""+topuid +"\",\n"+
              "  \"twitter\": \""+twitter+"\"\n"+
              "}");
    }

    public void evaluate(){
        if(contentListContains("is", "top")){
            getFollowers();
            addFollowers();
            distributeWork();
        }
        else
        if(contentListContains("is", "masher")){
            getTimeline();
            addTimeline();
        }
    }

    private void getFollowers(){
        if(contentList("followers")==null && !contentListContainsAll("is", list("twitter", "query"))){ logrule();
            contentListAddAll("is", list("twitter", "query")); // Quick change of type
            content("query", "users:"+content("topuser")+":followers" );
            notifying(content("twitter"));
        }
    }

    private void addFollowers(){
        for(String resultsuid: alerted()){ logrule();
            contentList("followers", contentListOf(resultsuid, "results"));
            contentListRemoveAll("is", list("twitter", "query"));
            content("query", null);
            unnotifying(content("twitter"));
        }
    }

    private void distributeWork(){
        while(contentList("mashers")==null ||
              contentList("mashers").size()< 2 ){

            String follower = content("followers:0");
            if(follower!=null){ logrule();
                contentListAdd("mashers", spawn(new TwitterMash(follower, uid, content("twitter"))));
                contentRemove("followers:0");
            } else break;
        }
    }

    private void getTimeline(){
        if(content("timeline")==null && !contentListContainsAll("is", list("twitter", "query"))){ logrule();
            contentListAddAll("is", list("twitter", "query"));
            content("query", "users:"+content("userid")+":timeline" );
            notifying(content("twitter"));
        }
    }

    private void addTimeline(){
        for(String resultsuid: alerted()){ logrule();
            contentList("timeline", contentListOf(resultsuid, "results"));
            contentListRemoveAll("is", list("twitter", "query"));
            content("query", null);
            unnotifying(content("twitter"));
        }
    }
}


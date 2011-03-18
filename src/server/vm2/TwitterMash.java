
import java.util.*;
import java.util.regex.*;

import netmash.lib.JSON;
import netmash.forest.UID;
import netmash.forest.WebObject;

/** Declarative, Concurrent Twitter Mashup in the Object Web Cloud with a NoSQL database
  * and Map-Reduce!.
  */
public class TwitterMash extends WebObject {

    public TwitterMash(){}

    public void evaluate(){
        if(contentListContains("is", "top")){
            searchByGeoAndKeyword();
            addGeoPosts();
            getFollowers();
            addFollowers();
            distributeWork();
        }
        else
        if(contentListContains("is", "masher")){
            getPosts();
            addPosts();
        }
    }

    private void searchByGeoAndKeyword(){
        if(!contentSet("geokey") && !contentListContains("is", "query")){ logrule();
            contentListAdd("is", "query");
            contentHash("query", new JSON("{ \"is\": [ \"twitter\", \"post\" ],"+
                                            "\"text\": \"tsunami\","+
                                            "\"location\": { \"lat\": 36.8, \"lon\": -122.75, \"range\": \"50mi\" }}" ));
            notifying(content("twitter"));
        }
    }

    private void addGeoPosts(){
        for(String resultsuid: alerted()){ logrule();
            content("results", resultsuid);
            LinkedList ll=contentList("results:results");
            if(ll==null) return;
            contentList("geokey", ll);
            unnotifying(content("twitter"));
            contentRemove("query");
            contentListRemove("is", "query");
        }
        alerted().clear();
    }

    private void getFollowers(){
        if(contentList("followers")==null && !contentListContains("is", "query")){ logrule();
            contentListAdd("is", "query");
            contentHash("query", new JSON("{ \"is\": [ \"twitter\", \"followers\" ], \"user\": { \"id\": \""+content("topuser")+"\" }}" ));
            notifying(content("results"));
        }
    }

    private void addFollowers(){
        for(String resultsuid: alerted()){ logrule();
            content("results", resultsuid);
            contentList("followers", contentList("results:results:list"));
            unnotifying(content("twitter"));
            contentRemove("query");
            contentListRemove("is", "query");
        }
    }

    private void distributeWork(){
        while(contentList("mashers")==null ||
              contentList("mashers").size()< 3 ){

            String follower = content("followers:0");
            if(follower!=null){ logrule();
                contentListAdd("mashers", spawn(new TwitterMash(UID.toURLfromBaseURL(content("twitter"), follower), uid, content("twitter"))));
                contentRemove("followers:0");
            } else break;
        }
    }

    public TwitterMash(String user, String topuid, String twitter){
        super("{ \"is\": [ \"twittermash\", \"masher\" ], \n"+
              "  \"user\": \""+user+"\",\n"+
              "  \"topuid\":  \""+topuid +"\",\n"+
              "  \"twitter\": \""+twitter+"\"\n"+
              "}");
    }

    private void getPosts(){
        if(contentList("posts")==null && !contentListContains("is", "query")){ logrule();
            contentListAdd("is", "query");
            contentHash("query", new JSON("{ \"is\": [ \"twitter\", \"post\" ], \"user\": \""+content("user")+"\" }" ));
            notifying(content("twitter"));
        }
    }

    private void addPosts(){
        for(String resultsuid: alerted()){ logrule();
            content("results", resultsuid);
            contentList("posts", contentList("results:results"));
            unnotifying(content("twitter"));
            contentRemove("query");
            contentListRemove("is", "query");
        }
    }
}


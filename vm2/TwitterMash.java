
import jungle.forest.WebObject;

/** Declarative, Concurrent Twitter Mashup in the Object Web Cloud with a NoSQL database
  * and Map-Reduce!.
  */
public class TwitterMash extends WebObject {

    public void evaluate(){
        if(contentListContains("is", "top")){
            getFollowersList();
            addFollowers();
        }
    }

    private void getFollowersList(){
        if(content("followers")==null && !contentListContainsAll("is", list("twitter", "query"))){
            contentListAddAll("is", list("twitter", "query")); // Quick change of type
            content("query", "users:"+content("topuser")+":followers" );
            notifying(content("twitter"));
        }
    }

    private void addFollowers(){
        for(String resultsuid: alerted()){
            contentList("followers", contentListOf(resultsuid, "results"));
            contentListRemoveAll("is", list("twitter", "query"));
            content("query", null);
            unnotifying(content("twitter"));
        }
    }
}



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
            answerPostQuery();
            answerFollowersQuery();
            answerPostsQuery();
        }
        else
        if(contentListContains("is", "results")){
            notifyResults();
        }
    }

    private void answerPostQuery(){
        for(String queryuid: alerted()){
            content("query", queryuid);
            if(contentListContains("query:is", "query")){
                if(contentListContainsAll("query:query:is", list("twitter", "post"))){ logrule();
                    String search = searchFromQuery();
                    if(search==null) return;
                    String indpath = "indexes:posts:search:"+search;
                    if(contentHash(indpath)==null){
                        httpGETJSON("http://search.twitter.com/search.json?"+search, queryuid);
                    }
                    else{
                        spawn(new Twitter(queryuid, uid, indpath));
                    }
                }
            }
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

    @Override
    @SuppressWarnings("unchecked")
    public void httpNotifyJSON(final JSON json, final String queryuid){
        new Evaluator(this){
            public void evaluate(){ logrule();
                if(json==null){
                    spawn(new Twitter(queryuid, null, null));
                    return;
                }
                LinkedList list = json.listPathN("list");
                if(list==null || list.size()==0){
                list = json.listPathN("results");
                if(list==null || list.size()==0){
                    spawn(new Twitter(queryuid, null, null));
                    return;
                }}
                content("query", queryuid);
                String indpath=null;
                if(list.get(0) instanceof Integer){
                    String userid = content("query:query:user:id");
                    indpath = "indexes:followers:user-id:"+userid;
                    contentList(indpath, spawnUsers((LinkedList<Integer>)list));
                }
                else{
                    String usernum = content("query:query:user:id2");
                    if(usernum!=null){
                        indpath = "indexes:posts:user-num:"+usernum;
                        contentList(indpath, spawnPosts((LinkedList<LinkedHashMap>)list, content("query:query:user")));
                    }
                    else{
                        indpath = "indexes:posts:search:"+searchFromQuery();
                        contentList(indpath, spawnPosts((LinkedList<LinkedHashMap>)list, null));
                    }
                }
                if(indpath!=null) spawn(new Twitter(queryuid, uid, indpath));
            }
        };
    }

    private String searchFromQuery(){
        String keywords = content(      "query:query:text");
        double lat      = contentDouble("query:query:location:lat");
        double lon      = contentDouble("query:query:location:lon");
        String range    = content(      "query:query:location:range");
        if(keywords==null || range==null) return null;
        return "q="+keywords+"&geocode="+lat+","+lon+","+range;
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

    @SuppressWarnings("unchecked")
    private LinkedList spawnPosts(LinkedList<LinkedHashMap> postinfolist, String user){
        LinkedList l=new LinkedList();
        for(LinkedHashMap postinfo: postinfolist){
            l.add(spawn(new Twitter(postinfo, user)));
        }
        return l;
    }

    public Twitter(LinkedHashMap postinfo, String user){
        super("{ \"is\": [ \"twitter\", \"post\" ],\n"+
              "  \"user\": \""+user+"\",\n"+
              "  \"id2\": \""+postinfo.get("id")+"\",\n"+
              "  \"text\": \""+JSON.replaceEscapableChars(postinfo.get("text").toString())+"\",\n"+
              "  \"created\": \""+postinfo.get("created_at")+"\",\n"+
              "  \"location\": \""+postinfo.get("geo")+"\"\n"+
              "}");
    }

    public Twitter(String queryuid, String topuid, String indpath){
        super("{ \"is\": [ \"query\", \"results\" ],"+
                                                 " \"query\": \""+queryuid+"\","+
         (topuid==null?  " \"twitter\": null,":  " \"twitter\": \""+topuid+"\",")+
         (indpath==null? " \"indexpath\": null": " \"indexpath\": \""+indpath+"\"")+" }");
    }

    private void notifyResults(){ logrule();
        if(!contentSet("results")){ logrule();
            String indpath=content("indexpath");
            if(indpath==null){
                content("results", "none");
            }
            else{
                LinkedList results=contentList("twitter:"+indpath);
                if(results!=null){
                    if(indpath.startsWith("indexes:followers:user-id:")){
                        contentHash("type", contentHash("twitter:types:twitter-followers"));
                        JSON tf = new JSON("{ \"is\": [ \"twitter\", \"followers\" ] }");
                        tf.listPath("list", results);
                        contentHash("results", tf);
                    }
                    else
                    if(indpath.startsWith("indexes:posts:user-num:")){
                        contentHash("type", contentHash("twitter:types:twitter-post"));
                        contentList("results", results);
                    }
                    else
                    if(indpath.startsWith("indexes:posts:search:")){
                        contentHash("type", contentHash("twitter:types:twitter-post"));
                        contentList("results", results);
                    }
                }
            }
            contentRemove("twitter");
            contentRemove("indexpath");
            notifying(content("query"));
        }
    }
}


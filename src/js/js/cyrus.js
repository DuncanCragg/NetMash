
// }-------------- Networking ------------------------------{

function Network(){

    var useLocalStorage = false;//typeof(localStorage)!=='undefined';
    var getting={};
    var localObjects = {};
    var localVersions = {};
    var outstandingRequests = 0;
    var cacheNotify = null;

    var me = {
        getJSON: function(url,creds,ok,err){
            var isCN=url.indexOf('/c-n-')!= -1;
            if(!isCN) me.updateProgress(1);
            var obj=null;
            if(useLocalStorage){
                var objstr = localStorage.getItem('objects:'+url);
                if(objstr) obj=JSON.parse(objstr);
            }
            //else obj=localObjects[url];
            if(obj){
                me.updateProgress(-1);
                ok(obj,'from-cache',null);
            }else{
                if(getting[url]) return;
                getting[url] = true;
                var headers = { 'Cache-Notify': me.getCacheNotify() };
                if(creds) headers.Authorization = me.buildAuth(creds,'GET',url);
                if(url.endethWith('.json')||url.indexOf('c-n-')!=-1) $.ajax({
                    url: url,
                    headers: headers,
                    dataType: 'json',
                    success: function(obj,s,x){
                        delete getting[url];
                        if(isCN) url = x && x.getResponseHeader('Content-Location');
                        var etag = x && x.getResponseHeader('ETag');
                        delete obj.Notify;
                        if(useLocalStorage){ try{ localStorage.setItem('objects:'+url, JSON.stringify(obj));
                                                  if(etag) localStorage.setItem('versions:'+getUID(url), etag);
                                            }catch(e){ if(e==QUOTA_EXCEEDED_ERR){ console.log('Local Storage quota exceeded'); } }
                        } else { localObjects[url]=obj; localVersions[url]=etag; }
if(etag && typeof(localStorage)!=='undefined') localStorage.setItem('versions:'+getUID(url), etag);
                        if(!isCN) me.updateProgress(-1);
                        ok(url,obj,s,x);
                    },
                    error: function(x,s,e){
                        delete getting[url];
                        if(!isCN) me.updateProgress(-1);
                        err(url,e,s,x);
                    }
                });
                else if(url.endethWith('.cyr')) $.ajax({
                    url: url,
                    headers: headers,
                    success: function(obj,s,x){
                        delete getting[url];
                        if(isCN) url = x && x.getResponseHeader('Content-Location');
                        var etag = x && x.getResponseHeader('ETag');
if(etag && typeof(localStorage)!=='undefined') localStorage.setItem('versions:'+getUID(url), etag);
                        if(!isCN) me.updateProgress(-1);
                        ok(url,obj,s,x);
                    },
                    error: function(x,s,e){
                        delete getting[url];
                        if(!isCN) me.updateProgress(-1);
                        err(url,e,s,x);
                    }
                });
            }
        },
        postJSON: function(url,json,cyrus,creds,ok,err){
            var headers = { 'Cache-Notify': me.getCacheNotify() };
            if(creds) headers.Authorization = me.buildAuth(creds,'POST',url,json);
            if(!cyrus) $.ajax({
                type: 'POST',
                url: url,
                headers: headers,
                data: json,
                contentType: 'application/json',
                dataType: 'json',
                success: ok,
                error: err
            });
            else $.ajax({
                type: 'POST',
                url: url,
                headers: headers,
                data: json,
                contentType: 'text/cyrus',
                success: ok,
                error: err
            });
        },
        longGetJSON: function(cn,creds,ok,err){
            for(uid in getting) if(getting[uid]) return;
            me.getJSON(cn,creds,ok,err);
        },
        getCacheNotify: function(){
            if(cacheNotify) return cacheNotify;
            cacheNotify=localStorage.getItem('Cache-Notify');
            if(cacheNotify) return cacheNotify;
            cacheNotify=generateUID('c-n');
            localStorage.setItem('Cache-Notify', cacheNotify);
            return cacheNotify;
        },
        updateProgress: function(i){
            outstandingRequests+=i;
            $('#progress').width(5*outstandingRequests);
        },
        buildAuth: function(creds, method, url, json){
            var expires = 1340000000000;
            var agentid = 56781234;
            return 'Cyrus username='+creds.username+', agentid='+agentid+', scope='+method+'/'+url+', expires='+expires+', hash='+
                        me.buildHash(creds.userpass,             agentid,           method+'/'+url,             expires, json);
        },
        buildHash: function(userpass, agentid, methodurl, expires, json){
            return '[ '+userpass+' '+agentid+' '+methodurl+' '+expires+(json? ' '+json: '')+' ]';
        }
    };
    return me;
};

// }-------------- JSON->HTML ------------------------------{

function JSON2HTML(url){

    var currentObjectBasePath = url;

    var linkre=/\[([^\[]+?)\]\[([^ ]+?)\]/g;
    var boldre=/!\[(.+?)\]!/g;
    var italre=/\/\[(.+?)\]\//g;
    var prefre=/^\|\[(.+)\]\|$/g;
    var codere=/\|\[(.+?)\]\|/g;

    return {
        getHTML: function(url,json,closed){
            if(!json) return '<div><div>No object!</div><div>'+'<a href="'+url+'">'+url+'</a></div></div>';
            if(json.constructor===String){
                if(url.endethWith('.cyr'))      return this.getCyrusTextHTML(url,json,closed);
                return '<div><div>Not Cyrus text!</div><div>'+'<a href="'+url+'">'+url+'</a></div><div>'+json+'</div></div>';
            }
            if(json.constructor!==Object) return '<div><div>Not an object!</div><div>'+'<a href="'+url+'">'+url+'</a></div><div>'+json+'</div></div>';
            if(this.isA('gui',     json))       return this.getGUIHTML(url,json,closed);
            if(this.isA('contact', json))       return this.getContactHTML(url,json,closed);
            if(this.isA('event',   json))       return this.getEventHTML(url,json,closed);
            if(this.isA('article', json))       return this.getArticleHTML(url,json,closed);
            if(this.isA('chapter', json))       return this.getArticleHTML(url,json,closed);
            if(this.isA('list',    json))       return this.getPlainListHTML(url,json,closed);
            if(this.isA('article', json, true)) return this.getDocumentListHTML(url,json,closed);
            if(this.isA('document',json, true)) return this.getDocumentListHTML(url,json,closed);
            if(this.isA('media',   json, true)) return this.getMediaListHTML(url,json,closed);
            return this.getObjectHTML(url,json,closed);
        },
        getAnyHTML: function(a,closed){
            if(!a) return '';
            if(closed===undefined) closed=true;
            if(a.constructor===String) return this.getStringHTML(a);
            if(a.constructor===Array)  return this.getListHTML(a);
            if(a.constructor===Object) return this.getHTML(a.URL||a.More,a,closed);
            return a!=null? ''+a: '-';
        },
        getObjectHTML: function(url,json,closed,title){
            if(this.isA('editable', json))
                 return this.getObjectHeadHTML(this.getTitle(json,title),url,false,closed)+
                        '<form class="cyrus-form">\n'+
                        '<input class="cyrus-target" type="hidden" value="'+url+'" />\n'+
                        '<textarea class="cyrus-raw" rows="14">\n'+JSON.stringify(json)+'\n</textarea>\n'+
                        '<input class="submit" type="submit" value="Update" />\n'+
                        '</form>';
            else return this.getObjectHeadHTML(this.getTitle(json,title),url,false,closed)+
                        '<pre class="cyrus">\n'+JSON.stringify(json)+'\n</pre>';
        },
        getCyrusTextHTML: function(url,item,closed){
            if(item.indexOf('editable')!= -1)
                 return this.getObjectHeadHTML('Cyrus Code',url,false,closed)+
                        '<form class="cyrus-form">\n'+
                        '<input class="cyrus-target" type="hidden" value="'+url+'" />\n'+
                        '<textarea class="cyrus-raw" rows="14">\n'+item+'\n</textarea>\n'+
                        '<input class="submit" type="submit" value="Update" />\n'+
                        '</form>';
            else return this.getObjectHeadHTML('Cyrus Code',url,false,closed)+
                        '<pre class="cyrus">\n'+item+'\n</pre>';
        },
        getListHTML: function(l){
            var that = this;
            var rows = [];
            $.each(l, function(key,val){ rows.push(that.getAnyHTML(val)); });
            if(rows.length >5) return '<div class="list"><p class="list">'+rows.join('</p>\n<p class="list">')+'</p></div>\n';
            return rows.join(', ');
        },
        getObjectListHTML: function(header,itemclass,list,closed){
            var rows=[];
            if(header) rows.push('<h3>'+header+'</h3>');
            var that = this;
            if(list.constructor===String) list = [ list ];
            if(list.constructor!==Array) return this.getAnyHTML(list);
            if(list.length){
            rows.push('<ul>');
            $.each(list, function(key,item){
                rows.push('<li class="'+itemclass+'">');
                if(that.isONLink(item)) rows.push(that.getObjectHeadHTML(null, item, true, closed));
                else                    rows.push(that.getAnyHTML(item,closed));
                rows.push('</li>');
            });
            rows.push('</ul>');
            }
            return rows.join('\n')+'\n';
        },
        getStringHTML: function(s){
            if(this.isONLink(s))    return '<a class="new-state" href="'+getMashURL(this.fullURL(s).htmlEscape())+'"> &gt;&gt; </a>';
            if(this.isImageLink(s)) return '<img src="'+s.htmlEscape()+'" />';
            if(this.isLink(s))      return '<a href="'+s.htmlEscape()+'"> &gt;&gt; </a>';
            return this.ONMLString2HTML(s);
        },
        // ------------------------------------------------
        getContactHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML('Contact: '+this.getTitle(json), url, false, closed));
            rows.push('<div class="vcard">');
            if(json.fullName     !== undefined) rows.push('<h2 class="fn">'+this.getAnyHTML(json.fullName)+'</h2>');
            if(json.address      !== undefined) rows.push(this.getContactAddressHTML(json.address));
            if(json.phone        !== undefined) rows.push(this.getContactPhoneHTML(json.phone));
            if(json.email        !== undefined) rows.push('<div class="info-item">Email: <span class="email">'+this.getAnyHTML(json.email)+'</span></div>');
            if(json['web-view']  !== undefined) rows.push('<div class="info-item">Website: '+this.getAnyHTML(json['web-view'])+'</div>');
            if(json.publications !== undefined) rows.push(this.getObjectListHTML('Publications', 'publication', json.publications, true));
            if(json.bio          !== undefined) rows.push('<div class="info-item">Bio: '+this.getAnyHTML(json.bio)+'</div>');
            if(json.photo        !== undefined) rows.push('<div class="photo">'+this.getAnyHTML(json.photo)+'</div>');
            if(json.parents      !== undefined) rows.push(this.getObjectListHTML('Parents', 'parent', json.parents, true));
            if(json.inspirations !== undefined) rows.push(this.getObjectListHTML('Inspired by', 'inspirations', json.inspirations, true));
            if(json.following    !== undefined) rows.push(this.getObjectListHTML('Following', 'following', json.following, true));
            if(json.More         !== undefined) rows.push(this.getObjectListHTML('More', 'more', json.More, true));
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        getContactAddressHTML: function(addresses){
            if(addresses.constructor!==Array) addresses = [ addresses ];
            var rows=[];
            for(i in addresses){ var address = addresses[i];
            rows.push('<div class="adr">');
            if(address.constructor===String)     rows.push('<p class="address">'         +this.getStringHTML(address)+'</p>'); else{
            if(address.postbox    !== undefined) rows.push('<p class="post-office-box">' +this.getAnyHTML(address.postbox)+'</p>');
            if(address.extended   !== undefined) rows.push('<p class="extended-address">'+this.getAnyHTML(address.extended)+'</p>');
            if(address.street     !== undefined) rows.push('<p class="street-address">'  +this.getAnyHTML(address.street)+'</p>');
            if(address.locality   !== undefined) rows.push('<p class="locality">'        +this.getAnyHTML(address.locality)+'</p>');
            if(address.region     !== undefined) rows.push('<p class="region">'          +this.getAnyHTML(address.region)+'</p>');
            if(address.postalCode !== undefined) rows.push('<p class="postal-code">'     +this.getAnyHTML(address.postalCode)+'</p>');
            if(address.country    !== undefined) rows.push('<p class="country-name">'    +this.getAnyHTML(address.country)+'</p>'); }
            rows.push('</div>');
            }
            return rows.join('\n')+'\n';
        },
        getContactPhoneHTML: function(phone){
            var rows=[];
            if(phone.constructor!==Object) rows.push('<div class="info-item phone">Tel:    <span class="tel">'+this.getAnyHTML(phone)+'</span></div>');
            else{
            if(phone.mobile !== undefined) rows.push('<div class="info-item phone">Mobile: <span class="tel">'+this.getAnyHTML(phone.mobile)+'</span></div>');
            if(phone.home   !== undefined) rows.push('<div class="info-item phone">Home:   <span class="tel">'+this.getAnyHTML(phone.home)+'</span></div>');
            if(phone.work   !== undefined) rows.push('<div class="info-item phone">Work:   <span class="tel">'+this.getAnyHTML(phone.work)+'</span></div>');
            }
            return rows.join('\n')+'\n';
        },
        // ------------------------------------------------
        getEventHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML('Event: '+this.getTitle(json), url, false, closed));
            rows.push('<div class="vevent">');
            if(json.title     !== undefined) rows.push('<h2 class="summary">'+this.getAnyHTML(json.title)+'</h2>');
            if(json.text      !== undefined) rows.push('<p class="description">'+this.getAnyHTML(json.text)+'</p>');
            if(json.start     !== undefined) rows.push('<div class="info-item">From: ' +this.getDateSpan('dtstart', json.start)+'</div>');
            if(json.end       !== undefined) rows.push('<div class="info-item">Until: '+this.getDateSpan('dtend',   json.end)  +'</div>');
            if(json.list      !== undefined) rows.push(this.getObjectListHTML('Sub-Events', 'sub-event', json.list, true));
            if(json.location  !== undefined) rows.push(this.getEventLocationHTML(json.location, true));
            if(json.attendees !== undefined) rows.push(this.getObjectListHTML('Attendees:', 'attendee', json.attendees, true));
            if(json.reviews   !== undefined) rows.push(this.getObjectListHTML('Reviews:', 'review', json.reviews, true));
            if(json.More      !== undefined) rows.push(this.getObjectListHTML('More', 'more', json.More, true));
            if(this.isA('attendable', json)){
                rows.push('<form class="rsvp-form">');
                rows.push('<input class="rsvp-type"   type="hidden" value="attendable" />');
                rows.push('<input class="rsvp-target" type="hidden" value="'+url+'" />');
                rows.push('<label for="rsvp">Attending?</label>');
                rows.push('<input class="rsvp-attending" type="checkbox" />');
                rows.push('<input class="submit" type="submit" value="Update" />');
                rows.push('</form>');
            }
            if(this.isA('reviewable', json)){
                rows.push('<form class="rsvp-form">');
                rows.push('<input class="rsvp-type"   type="hidden" value="reviewable" />');
                rows.push('<input class="rsvp-target" type="hidden" value="'+url+'" />');
                rows.push('<input class="rsvp-within" type="hidden" value="'+json.within+'" />');
                rows.push('<table class="grid">');
                this.createGUI(json['review-template'],rows);
                rows.push('</table>');
                rows.push('<input class="submit" type="submit" value="Update" />');
                rows.push('</form>');
            }
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        getEventLocationHTML: function(locurl,closed){
            var rows=[];
            rows.push('<h3>Location:</h3>');
            rows.push('<div class="location">');
            rows.push(this.getObjectHeadHTML(null, locurl, true, closed));
            rows.push('</div>');
            return rows.join('\n')+'\n';
        },
        // ------------------------------------------------
        getGUIHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML(this.getTitle(json), url, false, closed));
            rows.push('<form class="gui-form">');
            rows.push('<input class="form-target" type="hidden" value="'+url+'" />');
            rows.push('<table class="grid">');
            this.createGUI(json.view,rows);
            rows.push('</table>');
            rows.push('<input class="submit" type="submit" value="Submit" />');
            rows.push('</form>');
            return rows.join('\n')+'\n';
        },
        createGUI: function(guilist,rows){
            if(guilist.constructor===String){ rows.push(guilist); return; }
            var horizontal=false;
            for(i in guilist){ var item=guilist[i];
                if(item.constructor===Object && item.is=='style') horizontal=(item.direction=='horizontal');
            }
            var tagged=(guilist.constructor===Object);
            if(horizontal) rows.push('<tr>');
            for(i in guilist){
                var tag=tagged? i: null;
                var item=guilist[i];
                if(item.constructor===Object){
                    if(item.input){
                        input=item.input;
                        label=item.label;
                        if(!horizontal) rows.push('<tr>');
                        if(input=='textfield'){
                            rows.push('<td><label for="'+tag+'">'+label+'</label></td>');
                            rows.push('<td><input id="'+tag+'" class="form-field" type="text" /></td>');
                        }
                        else
                        if(input=='rating'){
                            rows.push('<td><label for="'+tag+'">'+label+'</label></td>');
                            rows.push('<td><input id="'+tag+'" class="form-field" type="text" /></td>');
                        }
                        if(!horizontal) rows.push('</tr>');
                    }
                    else
                    if(this.isOrContains(item.view, 'raw')){
                        if(!horizontal) rows.push('<tr>');
                        rows.push('<td class="grid-col">'+this.getObjectHeadHTML(null, item.item, true, !this.isOrContains(item.view,'open'))+'</td>');
                        if(!horizontal) rows.push('</tr>');
                    }
                    else
                    if(item.view){
                        if(!horizontal) rows.push('<tr>');
                        rows.push('<td class="grid-col">'+this.getObjectHeadHTML(null, item.item, true, !this.isOrContains(item.view,'open'))+'</td>');
                        if(!horizontal) rows.push('</tr>');
                    }
                }
                else
                if(this.isONLink(item)){
                    if(!horizontal) rows.push('<tr>');
                    rows.push('<td class="grid-col">'+this.getObjectHeadHTML(null, item, true, true)+'</td>');
                    if(!horizontal) rows.push('</tr>');
                }
                else
                if(item.constructor===String){
                    if(!horizontal) rows.push('<tr>');
                    rows.push('<td class="grid-col">'+item+'</td>');
                    if(!horizontal) rows.push('</tr>');
                }
                else{
                    if(!horizontal) rows.push('<tr>');
                    rows.push('<td class="grid-col">'+item+'</td>');
                    if(!horizontal) rows.push('</tr>');
                }
            }
            if(horizontal) rows.push('</tr>');
        },
        // ------------------------------------------------
        getArticleHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML(this.getTitle(json), url, false, closed));
            rows.push('<div class="document left">');
            if(json.title        !== undefined) rows.push('<h2 class="summary">'+this.getAnyHTML(json.title)+'</h2>');
            if(json.publisher    !== undefined) rows.push('<div class="info-item">Publisher: '+this.getAnyHTML(json.publisher)+'</div>');
            if(json.journalTitle !== undefined) rows.push('<div class="info-item">Journal: '+this.getAnyHTML(json.journalTitle)+'</div>');
            if(json.volume       !== undefined) rows.push('<div class="info-item">Volume: '+this.getAnyHTML(json.volume)+'</div>');
            if(json.issue        !== undefined) rows.push('<div class="info-item">Issue: '+this.getAnyHTML(json.issue)+'</div>');
            if(json.published    !== undefined) rows.push('<div class="info-item">Published: '+this.getDateSpan('published', json.published)+'</div>');
            if(json['web-view']  !== undefined) rows.push('<div class="info-item">Website: '+this.getAnyHTML(json['web-view'])+'</div>');
            if(json.collection   !== undefined) rows.push('<div class="info-item">'+this.getObjectHeadHTML(null, json.collection, true, false)+'</div>');
            if(json.authors      !== undefined) rows.push(this.getObjectListHTML('Authors:', 'author', json.authors, true));
            rows.push('</div><div class="document right">');
            if(json.text         !== undefined) rows.push('<div class="text">'+this.getAnyHTML(json.text)+'</div>');
            if(json.More         !== undefined) rows.push(this.getObjectListHTML('More', 'more', json.More, true));
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        // ------------------------------------------------
        getPlainListHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML(this.getTitle(json,'Documents'), url, false, closed, json.icon));
            rows.push('<div class="plain-list">');
            if(json.logo         !== undefined) rows.push('<div class="logo">'+this.getAnyHTML(json.logo)+'</div>');
            if(json['web-view']  !== undefined) rows.push('<div class="info-item">Website: '+this.getAnyHTML(json['web-view'])+'</div>');
            if(json.contentCount !== undefined) rows.push('<div class="info-item">'+this.getObjectHTML(null,json.contentCount,false,'Documents Available')+'</div>');
            if(json.list         !== undefined) rows.push(this.getObjectListHTML(null, 'document', json.list, false));
            if(json.collection   !== undefined) rows.push('<div class="info-item">'+this.getObjectHeadHTML(null, json.collection, true, false)+'</div>');
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        getDocumentListHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML(this.getTitle(json,'Documents'), url, false, closed, json.icon));
            rows.push('<div class="document-list">');
            if(json.logo         !== undefined) rows.push('<div class="logo">'+this.getAnyHTML(json.logo)+'</div>');
            if(json['web-view']  !== undefined) rows.push('<div class="info-item">Website: '+this.getAnyHTML(json['web-view'])+'</div>');
            if(json.contentCount !== undefined) rows.push('<div class="info-item">'+this.getObjectHTML(null,json.contentCount,false,'Documents Available')+'</div>');
            if(this.isA('searchable', json, true)){
            rows.push('<form id="query-form">');
            rows.push('<label for="query">Search these documents:</label>');
            rows.push('<input id="query" class="text" type="text" />');
            rows.push('<input class="submit" type="submit" value="Search" />');
            rows.push('</form>');
            }
            if(this.isA('personalisable', json, true)){
            rows.push('<form id="login-form">');
            rows.push('<label for="username" class="login-label">Name</label>');
            rows.push('<input id="username" class="text" type="text" />');
            rows.push('<label for="userpass" class="login-label">Password</label>');
            rows.push('<input id="userpass" class="text" type="text" />');
            rows.push('<input class="submit" type="submit" value="Log in" />');
            rows.push('</form>');
            }
            if(json.list         !== undefined) rows.push(this.getObjectListHTML(null, 'document', json.list, false));
            if(json.collection   !== undefined) rows.push('<div class="info-item">'+this.getObjectHeadHTML(null, json.collection, true, false)+'</div>');
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        // ------------------------------------------------
        getMediaListHTML: function(url,json,closed){
            var list = json.list;
            if(!list) return '';
            if(list.constructor===String) list = [ list ];
            if(list.constructor!==Array) return this.getAnyHTML(list);
            var rows=[];
            rows.push(this.getObjectHeadHTML('Media', url, false, closed));
            rows.push('<div class="media-list">');
            var that = this;
            $.each(list, function(key,item){ rows.push(that.getMediaHTML(item)); });
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        getMediaHTML: function(json){
            return ' <div class="media">\n'+
                   '  <img class="media-img" src="'+json.url+'" />\n'+
                   '  <div class="media-text"><p>\n'+this.ONMLString2HTML(json.text)+'</p>\n</div>\n'+
                   ' </div>\n';
        },
        // ------------------------------------------------
        ONMLString2HTML: function(text){
            if(!text) return '';
            text=text.replace(/&#39;/g,'\'');
            text=text.replace(/&quot;/g,'"');
            text=text.htmlEscape();
            text=text.replace(linkre, '<a href="$2">$1</a>');
            text=text.replace(boldre, '<b>$1</b>');
            text=text.replace(italre, '<i>$1</i>');
            if(text.startethWith('|[') && text.endethWith(']|')){
                 text='<pre>'+text.substring(2, text.length-2)+'</pre>';
            }
            text=text.replace(codere, '<code>$1</code>');
            return text;
        },
        // ---------------------------------------------------
        getTitle: function(json,elsedefault){
            if(!json || json.constructor!==Object) return '';
            if(json.fullName !== undefined) return this.getAnyHTML(json.fullName);
            if(json.title    !== undefined) return this.getAnyHTML(json.title);
            return elsedefault? elsedefault: deCameliseList(json.is);
        },
        getDateSpan: function(clss, date){
            return '<span class="'+clss+'" title="'+makeISODate(date)+'">'+makeNiceDate(date)+'</span>';
        },
        getObjectHeadHTML: function(title, url, place, closed, icon){
            if(!this.isObjectURL(url) && place) return this.getAnyHTML(url);
            return '<div class="object-head'+(closed? '':' open')+'">'+
                                                    this.getAnyHTML(url)+
                                                  ' <span class="open-close">+/-</span>'+
                                             (url?' <a href="'+url+'" class="object'+(place? '-place': '')+'">{..}</a>':'')+
                                            (icon? '<span class="icon">'+this.getAnyHTML(icon)+'</span>':'')+
                                                   '<span class="object-title">'+(title? title: '...')+'&nbsp;</span>'+
                   '</div>'+(!place? '<div class="object-body" '+(closed? 'style="display: none"':'')+'>': '');
        },
        isA: function(type, json, list){
            if(!json.is) return false;
            if(json.is.constructor===String && json.is==type) return !list;
            if(json.is.constructor!==Array) return false;
            var islist=$.inArray('list', json.is) >= 0;
            if(!!list!=islist) return false;
            return $.inArray(type, json.is) >= 0;
        },
        isLink: function(s){
            return s && s.startethWith('http://');
        },
        isONLink: function(s){
            return s && (s.constructor===String) && ((this.isLink(s) && (s.endethWith('.json') || s.endethWith('.cyr'))) || s.startethWith('uid-'));
        },
        isImageLink: function(s){
            return this.isLink(s) && (s.endethWith('.png' )||
                                      s.endethWith('.gif' )||
                                      s.endethWith('.jpg' )||
                                      s.endethWith('.jpeg')||
                                      s.endethWith('.ico' ));
        },
        fullURL: function(s){
            if(this.isLink(s)) return s;
            return currentObjectBasePath+s;
        },
        isObjectURL: function(s){
            if(!s) return false;
            if( s.constructor!==String) return false;
            if(!s.startethWith('http:')) return false;
            if(!s.endethWith('.json') && !s.endethWith('.cyr')) return false;
            return true;
        },
        isOrContains: function(x,s){
            if(!x) return false;
            if(x.constructor===String && x==s) return true;
            if(x.constructor===Array  && x.indexOf(s)!= -1) return true;
            return false;
        }
    };
};

// }-------------- Viewer Application ----------------------{

function Cyrus(){

    var useHistory = typeof(history.pushState)==='function';
    var useLocalStorage = typeof(localStorage)!=='undefined';
    var network = new Network();
    var json2html;
    var topObjectURL = null;
    var windowWidth = $(window).width();
    var moreOf = {};
    var retryDelay=100;

    var me = {
        init: function(){
            me.getTopObject(window.location);
            if(!useHistory) setInterval(function(){ me.getTopObject(window.location); }, 200);
        },
        topObjectIn: function(url,obj,s,x){
            var newURL = x && x.getResponseHeader('Content-Location');
            if(false && newURL && newURL!=topObjectURL){
                topObjectURL = newURL;
                json2html = new JSON2HTML(topObjectURL.substring(0,topObjectURL.lastIndexOf('/')+1));
                var mashURL = getMashURL(topObjectURL);
                if(useHistory) history.pushState(null,null,mashURL);
                else { /* $('#content').html('Reloading  '+mashURL); window.location = mashURL; return; */ }
            }
            if(false && s!='mored'){
                var objMore=obj.More;
                if(objMore){
                    if(objMore.constructor===String) moreOf[objMore]=obj;
                    else for(i in objMore) moreOf[objMore[i]]=obj;
                }
            }
            document.title = json2html.getTitle(obj).htmlUnEscape();
            window.scrollTo(0,0);
            $('#content').html(json2html.getHTML(topObjectURL, obj, false));
            me.setUpHTMLEvents();
            setTimeout(function(){
                me.ensureVisibleAndReflow($('#content'));
                var cn = x && x.getResponseHeader('Cache-Notify');
                if(cn) network.longGetJSON(cn,me.getCreds(cn),me.someObjectIn,me.objectFail);
            }, 50);
            retryDelay=100;
        },
        topObjectFail: function(url,err,s,x){
            $('#content').html('<div>topObjectFail: <a href="'+topObjectURL+'">'+topObjectURL+'</a></div><div>'+s+'; '+err+'</div>');
        },
        objectIn: function(url,obj,s,x){
            if(!obj){ this.objectFail(url,null,'object empty; status='+s,null); return; }
            var moreofobj=moreOf[url];
            if(false && moreofobj){
                this.mergeHashes(moreofobj,obj);
                this.topObjectIn(moreofobj,'mored',null);
                moreOf[url]=undefined;
                return;
            }
            var htmlopen;
            var htmlclosed;
            $('a.object-place, a.object').each(function(n,ae){ var a=$(ae);
                if(a.attr('href')!=url) return;
                var open=a.parent().hasClass('open');
                if(open){ if(!htmlopen)   htmlopen   = json2html.getHTML(url, obj, false); }
                else    { if(!htmlclosed) htmlclosed = json2html.getHTML(url, obj, true); }
                var objhead = a.parent();
                objhead.next().remove();
                objhead.replaceWith(open? htmlopen: htmlclosed);
            });
            me.setUpHTMLEvents();
            var cn = x && x.getResponseHeader('Cache-Notify');
            if(cn) network.longGetJSON(cn,me.getCreds(cn),me.someObjectIn,me.objectFail);
            retryDelay=100;
        },
        objectFail: function(url,err,s,x){
            console.log('objectFail '+url+' '+err+' '+s+' '+(x && x.getResponseHeader('Cache-Notify')));
            var isCN=url.indexOf('/c-n-')!= -1;
            retryDelay*=2;
            if(isCN) setTimeout(function(){ network.longGetJSON(url,me.getCreds(url),me.someObjectIn,me.objectFail); }, retryDelay);
        },
        someObjectIn: function(url,obj,s,x){
            if(url==topObjectURL) me.topObjectIn(url,obj,s,x);
            else                     me.objectIn(url,obj,s,x);
        },
        setUpHTMLEvents: function(){
            $(window).resize(function(e){
                if($(window).width() != windowWidth){
                    windowWidth = $(window).width();
                    me.reflowIfWidthChanged($('#content'));
                }
            });
            $('.open-close').unbind().click(function(e){
                var objhead = $(this).parent();
                var panel=objhead.next();
                if(panel.css('display')=='none'){ panel.show('fast', function(){ me.ensureVisibleAndReflow(panel); }); objhead.addClass('open'); }
                else                            { panel.hide('fast'); objhead.removeClass('open'); }
                e.preventDefault();
                return false;
            });
            $('.media-img').unbind().click(function(e){
                var mediaList = $(this).parent().parent();
                var numSlides = mediaList.children().length;
                if(typeof mediaIndex==='undefined') mediaIndex=1;
                mediaList.find(':nth-child('+mediaIndex+')').hide();
                if(e.clientX-this.offsetLeft<this.width/2) mediaIndex--;
                else mediaIndex++;
                if(mediaIndex==0) mediaIndex=1;
                if(mediaIndex==numSlides+1) mediaIndex=numSlides;
                mediaList.find(':nth-child('+mediaIndex+')').show();
                mediaList.find(':nth-child('+mediaIndex+')').children().show();
                mediaList.find(':nth-child('+mediaIndex+')').children().children().show();
            });
            $('.cyrus-form').unbind().submit(function(e){
                if(!useLocalStorage){ e.preventDefault(); alert('your browser is not new enough to run Cyrus reliably'); return; }
                var targetURL=$(this).find('.cyrus-target').val();
                var cyrus=targetURL.endethWith('.cyr');
                var cytext=$(this).find('.cyrus-raw').val().trim();
                var cy; try{ cy=cyrus? cytext: JSON.parse(cytext); } catch(e){ alert('Syntax: '+e); }
                if(!cy){ e.preventDefault(); return; }
                if(!cyrus){
                    if(cy.is.constructor==String) cy.is=[ cy.is, 'editable' ];
                    if(cy.is.constructor==Array && cy.is.indexOf('editable')== -1) cy.is.push('editable');
                }
                var ver=localStorage.getItem('versions:'+getUID(targetURL));
                if(ver) ver=JSON.parse('{ "ver": '+ver.substring(1,ver.length-1)+' }').ver;
                var uidver=me.getUIDandVer(targetURL,cyrus);
                if(cyrus){
                    var cyr = '{ '+uidver+'\n  is: editable rule\n  when: edited\n  editable: '+targetURL+'\n  user: ""\n  ';
                    network.postJSON(targetURL, me.makeCyrusEditRule(cyr,ver,cy), true, me.getCreds(targetURL), null, null);
                }else{
                    var json = '{ '+uidver+', "is": [ "editable", "rule" ], "when": "edited", "editable": "'+targetURL+'", "user": "" }';
                    network.postJSON(targetURL, me.makeJSONEditRule(json,ver,cy), false, me.getCreds(targetURL), null, null);
                }
                e.preventDefault();
            });
            $('.gui-form').unbind().submit(function(e){
                if(!useLocalStorage){ e.preventDefault(); alert('your browser is not new enough to run Cyrus reliably'); return; }
                var targetURL=$(this).find('.form-target').val();
                var uidver=me.getUIDandVer(targetURL);
                var json = '{ '+uidver+',\n  "is": "form", "gui": "'+targetURL+'", "user": "",\n  "form": {\n   ';
                var fields = [];
                $(this).find('.form-field').each(function(n,i){ fields.push('"'+i.getAttribute('id')+'": "'+$(i).val()+'"'); });
                json+=fields.join(',\n   ');
                json+='\n }\n}';
                network.postJSON(targetURL, json, false, me.getCreds(targetURL), null, null);
                e.preventDefault();
            });
            $('.rsvp-form').unbind().submit(function(e){
                if($(this).find('.rsvp-type').val()=='attendable'){
                    if(!useLocalStorage){ e.preventDefault(); alert('your browser is not new enough to run Cyrus reliably'); return; }
                    var targetURL=$(this).find('.rsvp-target').val();
                    var uidver=me.getUIDandVer(targetURL);
                    var q=$(this).find('.rsvp-attending').is(':checked');
                    var json = '{ '+uidver+', "is": "rsvp", "event": "'+targetURL+'", "user": "", "attending": "'+(q? 'yes': 'no')+'" }';
                    network.postJSON(targetURL, json, false, me.getCreds(targetURL), null, null);
                    e.preventDefault();
                }
                if($(this).find('.rsvp-type').val()=='reviewable'){
                    if(!useLocalStorage){ e.preventDefault(); alert('your browser is not new enough to run Cyrus reliably'); return; }
                    var withinURL=$(this).find('.rsvp-within').val();
                    var within=me.getUIDOnly(withinURL);
                    if(!within){ e.preventDefault(); alert('please mark your attendance before reviewing'); return; }
                    var targetURL=$(this).find('.rsvp-target').val();
                    var uidver=me.getUIDandVer(targetURL);
                    var json = '{ '+uidver+', "is": "rsvp", "event": "'+targetURL+'", "user": "", "within": "'+within+'"';
                    $(this).find('.form-field').each(function(n,i){ json+=', "'+i.getAttribute('id')+'": "'+$(i).val()+'"'; });
                    json+=' }';
                    network.postJSON(targetURL, json, false, me.getCreds(targetURL), null, null);
                    e.preventDefault();
                }
            });
            $('#query').focus();
            $('#query-form').unbind().submit(function(e){
                var q=$('#query').val();
                var json = '{ "is": [ "document", "query" ], "text": "<hasWords('+q.jsonEscape()+')>" }';
                network.postJSON(topObjectURL, json, false, me.getCreds(topObjectURL), me.topObjectIn, me.topObjectFail);
                e.preventDefault();
            });
            $('#login-form').unbind().submit(function(e){
                var creds = { 'username': $('#username').val(), 'userpass': $('#userpass').val() };
                me.setCreds(topObjectURL, creds);
                e.preventDefault();
            });
            if(!useHistory) return;
            $('.new-state').unbind().click(function(e){
                var mashURL = $(this).attr('href');
                me.getTopObject(mashURL);
                history.pushState(null,null,mashURL);
                e.preventDefault();
                return false;
            });
            $(window).bind('popstate', function() {
                me.getTopObject(window.location);
            });
        },
        getTopObject: function(mashURL){
            var previousObjectURL = topObjectURL;
            topObjectURL = me.getFullObjectURL(mashURL);
            if(!topObjectURL || topObjectURL==previousObjectURL) return;
            json2html = new JSON2HTML(topObjectURL.substring(0,topObjectURL.lastIndexOf('/')+1));
            network.getJSON(topObjectURL, me.getCreds(topObjectURL), me.topObjectIn, me.topObjectFail);
        },
        getUIDandVer: function(url,cyrus){
            var uidver=localStorage.getItem('responses:'+url);
            if(!uidver){
                if(cyrus) uidver= 'UID: '  +generateUID('uid')+' Version: ' +1;
                else      uidver='"UID": "'+generateUID('uid')+'", "Version": '+1;
            }else{
                var re;
                if(cyrus) re=RegExp('(.*Version: )(.*)').exec(uidver);
                else      re=RegExp('(.*"Version": )(.*)').exec(uidver);
                uidver=re[1]+(parseInt(re[2])+1);
            }
            localStorage.setItem('responses:'+url, uidver);
            return uidver;
        },
        getUIDOnly: function(url){
            var uidver=localStorage.getItem('responses:'+url);
            if(!uidver) return null;
            var re=RegExp('"UID": "([^"]*)"').exec(uidver);
            if(!re) return null;
            return re[1];
        },
        ensureVisibleAndReflow: function(panel){
            me.ensureVisibleObjectsIn(panel);
            me.reflowIfWidthChanged(panel);
        },
        ensureVisibleObjectsIn: function(panel){
            $(panel).find('a.object-place').each(function(n,a){
                if(!$(a).is(':visible')) return;
                var url = a.getAttribute('href');
                network.getJSON(url, me.getCreds(url), me.objectIn, me.objectFail);
                $(a).next().html('Loading...');
            });
        },
        reflowIfWidthChanged: function(panel){
            $(panel).find('.document.right').each(function(n,r){
                if(!$(r).is(':visible')) return;
                if($(r).parent().width() > 400) $(r).addClass('wide');
                else                            $(r).removeClass('wide');
            });
        },
        getFullObjectURL: function(mashURL){
            url=getObjectURL(mashURL);
            if(!url) return null;
            if(!url.startethWith('http://')) url = getRootURL()+url;
            if(!url.endethWith('.json') && !url.endethWith('.cyr')) url = url+'.json';
            return url;
        },
        setCreds: function(siteURL, creds){
            var domain = getDomain(siteURL);
            if(useLocalStorage) localStorage.setItem('credsOfSite:'+domain, JSON.stringify(creds));
        },
        getCreds: function(requestURL){
            var domain = getDomain(requestURL);
            if(useLocalStorage) return JSON.parse(localStorage.getItem('credsOfSite:'+domain));
            return '';
        },
        makeCyrusEditRule: function(cyr,ver,val){
            return cyr+': { Version: '+ver+' } => as-is\n'+val+'\n}';
        },
        makeJSONEditRule: function(json,ver,val){
            var j=JSON.parse(json);
            var v={ 'Version': ver };
            j['']=[ v, '=>', 'as-is', val ];
            return JSON.stringify(j);
        },
        mergeHashes: function(a, b){
            for(x in b){

                if(!b[x]) continue;
                typebx=b[x].constructor;
                var bx;

                if(typebx===String) bx=b[x]; else
                if(typebx===Array)  bx=clone(b[x]); else
                if(typebx===Object) bx=clone(b[x]);

                if(!a[x]){ a[x]=bx; continue; }
                typeax=a[x].constructor;
                var ax=a[x];

                if(typeax===String){
                    if(typebx===String){
                        if(ax!==bx){
                            a[x]=[];
                            a[x].push(ax);
                            a[x].push(bx);
                        }
                    }
                    else
                    if(typebx===Array){
                        a[x]=bx;
                        if(a[x].indexOf(ax)== -1) a[x].unshift(ax);
                    }
                    else
                    if(typebx===Object){ }
                }
                else
                if(typeax===Array){
                    if(ax.indexOf(bx)== -1) ax.push(bx);
                }
                else
                if(typeax===Object){
                    if(typebx===String){ }
                    else
                    if(typebx===Array){
                        a[x]=bx;
                        if(a[x].indexOf(ax)== -1) a[x].unshift(ax);
                    }
                    else
                    if(typebx===Object){
                        this.mergeHashes(ax,bx);
                    }
                }
            }
        },
        mergeLists: function(a, b){
            for(x in b){
                typebx=b[x].constructor;
                if(typebx===String) if(a.indexOf(b[x])== -1) a.push(b[x]); else
                if(typebx===Array)  if(a.indexOf(b[x])== -1) a.push(clone(b[x])); else
                if(typebx===Object) if(a.indexOf(b[x])== -1) a.push(clone(b[x]));
            }
        }
    };
    return me;
};

// }-------------- Utilities -------------------------------{

String.prototype.startethWith = function(str){ return this.slice(0, str.length)==str; };
String.prototype.endethWith   = function(str){ return this.slice(  -str.length)==str; };
String.prototype.jsonEscape = function(){
    return this.replace(/\\/g, '\\\\')
               .replace(/"/g, '\\"');
};
String.prototype.htmlEscape = function(){
    return this.replace(/&/g,'&amp;')
               .replace(/</g,'&lt;')
               .replace(/>/g,'&gt;')
               .replace(/"/g,'&quot;');
};
String.prototype.htmlUnEscape = function(){
    return this.replace(/&amp;/g, '&')
               .replace(/&lt;/g,  '<')
               .replace(/&gt;/g,  '>')
               .replace(/&quot;/g,'"');
};

// --------------------

function clone(o){
  var o = (this instanceof Array)? []: {};
  for(i in this){
    if(!(this[i] && this.hasOwnProperty(i))) continue;
    if(typeof this[i]=='object') o[i]=clone(this[i]);
    else o[i]=this[i];
  }
  return o;
}

function getRootURL(){
    return window.location.protocol + '//' + window.location.host + '/';
}

function getDirURL(){
    return window.location.protocol + '//' + window.location.host + window.location.pathname;
}

function getMashURL(url){
    return window.location.protocol + '//' + window.location.host + window.location.pathname + '#' + url;
}

function getObjectURL(mashURL){
    return RegExp('#(.*)').exec(mashURL)[1];
}

function getDomain(url){
    return RegExp('http://([^/]*)/').exec(url)[1];
}

function getUID(url){
    var parts=RegExp('(uid-[-0-9a-zA-Z]*)').exec(url);
    return parts && parts[1];
}

var daysLookupTable   = [ 'Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday' ];
var monthsLookupTable = [ 'January','February','March','April','May', 'June','July','August','September','October','November','December'];

function makeISODate(date){
    var d = new Date(date);
    if(d.toString()=='Invalid Date') return '[not a valid date]';
    return d.toISOString();
}

function makeNiceDate(date){
    var d = new Date(date)
    if(d.toString()=='Invalid Date') return '[not a valid date]';
    var day = daysLookupTable[d.getDay()];
    var mon = monthsLookupTable[d.getMonth()];
    return day + ', ' + d.getDate() + ' ' + mon + ' ' + d.getFullYear() + ' at '+d.toLocaleTimeString();
}

function deCameliseList(is){
    if(!is) return '';
    if(is.constructor===String) return deCamelise(is);
    if(is.constructor!==Array) return deCamelise(''+is);
    r=''; $.each(is, function(k,s){ r+=deCamelise(s)+' '; });
    return r;
}

function deCamelise(s){
    return s.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/\b([A-Z]+)([A-Z])([a-z])/, '$1 $2$3').replace(/^./, function(str){ return str.toUpperCase(); });
}

function generateUID(prefix){
    return prefix+'-'+fourHex()+'-'+fourHex()+'-'+fourHex()+'-'+fourHex();
}

function fourHex(){
    var h= '000'+Math.floor(Math.random()*65536).toString(16);
    return h.substring(h.length-4);
}

// }--------------------------------------------------------{


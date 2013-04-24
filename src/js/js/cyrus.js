
// }-------------- Networking ------------------------------{

function Network(){

    var getting={};
    var pendingCN={};
    var cacheNotify = null;

    var me = {
        getJSON: function(url,creds,ok,err){
            var isCN=url.indexOf('/c-n-')!= -1;
            var obj=null;
        /*  var objstr = localStorage.getItem('objects:'+getUID(url));
            if(objstr) obj=JSON.parse(objstr); */
            if(obj){
                ok(obj,'from-cache',null);
            }else{
                if(me.isGetting(true,url,isCN)) return;
                var headers = { 'Cache-Notify': me.getCacheNotify() };
                if(creds) headers.Authorization = me.buildAuth(creds,'GET',url);
                if(url.endethWith('.json')||url.indexOf('/c-n-')!=-1) $.ajax({
                    url: url,
                    headers: headers,
                    dataType: 'json',
                    success: function(obj,s,x){
                        me.isGetting(false,url,isCN);
                        if(isCN) url = x && x.getResponseHeader('Content-Location');
                        var etag = x && x.getResponseHeader('ETag');
                        if(url){ try{
                            if(obj)  localStorage.setItem('objects:'+getUID(url), JSON.stringify(obj));
                            if(etag) localStorage.setItem('versions:'+getUID(url), etag);
                        }catch(e){ if(e==QUOTA_EXCEEDED_ERR){ console.log('Local Storage quota exceeded'); }}}
                        ok(url,obj,s,x);
                    },
                    error: function(x,s,e){
                        me.isGetting(false,url,isCN);
                        err(url,e,s,x);
                    }
                });
                else if(url.endethWith('.cyr')) $.ajax({
                    url: url,
                    headers: headers,
                    success: function(obj,s,x){
                        me.isGetting(false,url,isCN);
                        if(isCN) url = x && x.getResponseHeader('Content-Location');
                        var etag = x && x.getResponseHeader('ETag');
                        if(url && etag) localStorage.setItem('versions:'+getUID(url), etag);
                        ok(url,obj,s,x);
                    },
                    error: function(x,s,e){
                        me.isGetting(false,url,isCN);
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
            pendingCN[cn]=true;
            for(var u in getting) if(u.indexOf('/c-n-')== -1) return;
            for(var url in pendingCN) me.getJSON(url,creds,ok,err);
            pendingCN={};
        },
        getCacheNotify: function(){
            if(cacheNotify) return cacheNotify;
            cacheNotify=localStorage.getItem('Cache-Notify');
            if(cacheNotify) return cacheNotify;
            cacheNotify=generateUID('c-n');
            localStorage.setItem('Cache-Notify', cacheNotify);
            return cacheNotify;
        },
        isGetting: function(get,url,isCN){
            var pending=getting[url];
            if(get) getting[url] = true;
            else delete getting[url];
            $('#progress').width(5*sizeof(getting));
            // console.log((get?'get':'got')+' '+url+' '+(isCN?'cn':'')+' '+(pending?'pending':'')+' '+(getting[url]?'getting':'not getting')+' | '+sizeof(getting)+' '+toString(getting));
            return pending;
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
        getHTML: function(url,json,closed,raw){
            if(!json) return '<div><div>No object!</div><div>'+'<a href="'+url+'">'+url+'</a></div></div>';
            if(json.constructor===String){
                if(url.endethWith('.cyr'))      return this.getCyrusTextHTML(url,json,closed);
                return '<div><div>Not Cyrus text!</div><div>'+'<a href="'+url+'">'+url+'</a></div><div>'+json+'</div></div>';
            }
            if(json.constructor!==Object) return '<div><div>Not an object!</div><div>'+'<a href="'+url+'">'+url+'</a></div><div>'+json+'</div></div>';
            if(raw)                             return this.getCyrusTextHTML(url,json,closed);
            if(this.isA('gui',     json))       return this.getGUIHTML(url,json,closed);
            if(this.isA('contact', json))       return this.getContactHTML(url,json,closed);
            if(this.isA('land',    json) &&
               this.isA('template',json)    )   return this.getLandTemplateHTML(url,json,closed);
            if(this.isA('land',    json))       return this.getLandHTML(url,json,closed);
            if(this.isA('event',   json))       return this.getEventHTML(url,json,closed);
            if(this.isA('article', json))       return this.getArticleHTML(url,json,closed);
            if(this.isA('chapter', json))       return this.getArticleHTML(url,json,closed);
            if(this.isA('list',    json))       return this.getPlainListHTML(url,json,closed);
            if(this.isA('contact', json, true)) return this.getContactListHTML(url,json,closed);
            if(this.isA('article', json, true)) return this.getDocumentListHTML(url,json,closed);
            if(this.isA('document',json, true)) return this.getDocumentListHTML(url,json,closed);
            if(this.isA('media',   json, true)) return this.getMediaListHTML(url,json,closed);
            return this.getCyrusTextHTML(url,json,closed);
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
                        this.cyrusForm(url,JSON.stringify(json))+'</div>';
            else return this.getObjectHeadHTML(this.getTitle(json,title),url,false,closed)+
                        '<pre class="cyrus">\n'+JSON.stringify(json)+'\n</pre></div>';
        },
        getCyrusTextHTML: function(url,item,closed){
            if(item.constructor!==String) return this.getCyrusTextHTML(url,this.toCyrusHash(item),closed);
            return this.getObjectHeadHTML('Cyrus Code',url,false,closed,null,true)+
                   '<input class="cyrus-target" type="hidden" value="'+url+'" />\n'+
                   '<pre class="cyrus-readonly">\n'+this.createLinks(item)+'\n</pre></div>';
        },
        cyrusForm: function(url,item,type,action,rows){
            return '<form class="cyrus-form">\n'+
            (type? '<input class="cyrus-type"   type="hidden" value="'+type+'" />\n':'')+
                   '<input class="cyrus-target" type="hidden" value="'+url+'" />\n'+
                   '<textarea class="cyrus-raw" rows="'+(rows? rows: 24)+'">\n'+item+'\n</textarea>\n'+
                   '<input class="submit" type="submit" value="'+(action? action: 'Update')+'" />\n'+
                   '</form>';
        },
        getListHTML: function(l){
            var that = this;
            var rows = [];
            $.each(l, function(key,val){ rows.push(that.getAnyHTML(val)); });
            return '<div class="list"><p class="list">'+rows.join('</p>\n<p class="list">')+'</p></div>\n';
        },
        getObjectListHTML: function(header,itemclass,list,closed){
            var rows=[];
            if(header) rows.push('<h3>'+header+'</h3>');
            var that = this;
            if(list.constructor===String) list = [ list ];
            if(list.constructor!==Array)  list = [ list ];
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
            return this.markupString2HTML(s);
        },
        // ------------------------------------------------
        getContactHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML('Contact: '+this.getTitle(json), url, false, closed));
            rows.push('<div class="vcard">');
            if(json['full-name'] !== undefined) rows.push('<h2 class="fn">'+this.getAnyHTML(json['full-name'])+'</h2>');
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
            for(var i in addresses){ var address = addresses[i];
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
        getLandHTML: function(url,json,closed){
            var up=this.isA('updatable', json);
            var rows=[];
            rows.push(this.getObjectHeadHTML('Land: '+this.getTitle(json), url, false, closed));
            rows.push('<div class="vcard">');
            if(up){
                rows.push('<form class="land-form">');
                rows.push('<input class="land-target"  type="hidden" value="'+url+'" />');
                rows.push('<input class="land-within"  type="hidden" value="'+json.within+'" />');
                rows.push('<input class="land-request" type="hidden" value="'+json.request+'" />');
            }
            rows.push('<table class="grid">');
            this.addIfPresent(json, 'title', { 'input': 'textfield' }, rows, false);
            this.addIfPresent(json, 'area',  { 'input': 'textfield', 'label': 'Area (ha):' }, rows, false);
            this.createGUI(this.getViaLinksRefactorMe(json,['within','update-template']),rows,json);
            rows.push('</table>');
            if(up){
                rows.push('<input class="submit" type="submit" value="Update" />');
                rows.push('</form>');
                rows.push(this.getObjectHeadHTML('Create new land parcel', null, false, true));
                rows.push('<form class="new-land-form">');
                rows.push('<input class="land-within" type="hidden" value="'+url+'" />');
                rows.push('<table class="grid">');
                this.objectGUI('title',{ 'input': 'textfield', 'label': 'Title:'     },rows,false);
                this.objectGUI('area', { 'input': 'textfield', 'label': 'Area (ha):' },rows,false);
                this.createGUI(json['update-template'],rows);
                rows.push('</table>');
                rows.push('<input class="submit" type="submit" value="Create" />');
                rows.push('</form>');
                rows.push('</div>');
            }
            if(json.list !== undefined) rows.push(this.getObjectListHTML('Land Parcels', 'land-parcel', json.list, true));
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        getLandTemplateHTML: function(url,json,closed){
            var rows=[];
            rows.push('<table class="grid">');
            this.createGUI(json,rows);
            rows.push('</table>');
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
                var submittable=this.createGUI(json['review-template'],rows);
                rows.push('</table>');
                if(submittable)
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
            var submittable=json.view? this.createGUI(json.view,rows): false;
            rows.push('</table>');
            if(submittable)
            rows.push('<input class="submit" type="submit" value="Submit" />');
            rows.push('</form>');
            rows.push('</div>');
            return rows.join('\n')+'\n';
        },
        createGUI: function(guilist,rows,json){
            if(!guilist) return false;
            if(this.isONLink(guilist)){
                rows.push('<tr>');
                rows.push('<td colspan="2" class="grid-col">'+this.getObjectHeadHTML(null, guilist, true, true)+'</td>');
                rows.push('</tr>');
                return false;
            }
            else
            if(guilist.constructor===String){
                rows.push('<tr><td>'+guilist+'</td></tr>');
                return false;
            }
            var horizontal=false;
            for(var i in guilist){ var item=guilist[i];
                if(item.constructor===Object && item.is=='style') horizontal=(item.direction=='horizontal');
            }
            var tagged=(guilist.constructor===Object);
            var submittable=false;
            if(horizontal) rows.push('<tr>');
            for(var i in guilist){
                var tag=tagged? i: null;
                if(/^[A-Z]/.test(tag)) continue;
                if(tag=='is' || tag=='title' || tag=='update-template') continue;
                var item=guilist[i];
                if(item.constructor===Object) submittable=this.addIfPresent(json,tag,item,rows,horizontal) || submittable;
                else
                if(this.isONLink(item)){
                    if(!horizontal) rows.push('<tr>');
                    rows.push('<td class="grid-col">'+this.getObjectHeadHTML(null, item, true, true)+'</td>');
                    if(!horizontal) rows.push('</tr>');
                }
                else
                if(item.constructor===String){
                    if(!horizontal) rows.push('<tr>');
                    rows.push('<td class="grid-col">'+this.getStringHTML(item)+'</td>');
                    if(!horizontal) rows.push('</tr>');
                }
                else
                if(item.constructor===Array){
                    if(!horizontal) rows.push('<tr>');
                    rows.push('<td class="grid-col">');
                    rows.push('<table class="grid">');
                    submittable=this.createGUI(item,rows) || submittable;
                    rows.push('</table>');
                    rows.push('</td>');
                    if(!horizontal) rows.push('</tr>');
                }
                else{
                    if(!horizontal) rows.push('<tr>');
                    rows.push('<td class="grid-col">'+item+'</td>');
                    if(!horizontal) rows.push('</tr>');
                }
            }
            if(horizontal) rows.push('</tr>');
            return submittable;
        },
        addIfPresent: function(json,tag,widget,rows,horizontal){
            if(!json) return this.objectGUI(tag,widget,rows,horizontal);
            var value=json[tag];
            if(value===undefined) return false;
            return this.objectGUI(tag,widget,rows,horizontal,value);
        },
        objectGUI: function(tag,guilist,rows,horizontal,value){
            var val=(value!==undefined);
            var submittable=false;
            if(guilist.input){
                var input=guilist.input;
                var label=guilist.label;
                var range=guilist.range;
                if(!horizontal) rows.push('<tr>');
                if(input=='checkbox'){
                    rows.push('<td class="label"><label for="'+tag+'">'+label+'</label></td>');
                    rows.push('<td><input type="checkbox" id="'+tag+'" class="checkbox form-field" value="'+tag+(value? '" checked="true':'')+'"/></td>');
                    submittable=true;
                }
                else
                if(input=='chooser'){
                    rows.push('<td class="label"><label for="'+tag+'">'+label+'</label></td>');
                    rows.push('<td><select id="'+tag+'" class="chooser form-field">');
                    rows.push('<option value="none">Select..</option>');
                    for(var o in range){
                        if(o==value) rows.push('<option value="'+o+'" selected="true">'+range[o]+'</option>');
                        else         rows.push('<option value="'+o+'" >'               +range[o]+'</option>');
                    }
                    rows.push('</select></td>');
                    submittable=true;
                }
                else
                if(input=='textfield'){
                    if(label)           rows.push('<td class="label"><label for="'+tag+'">'+label+'</label></td>');
                    if(val && !label)   rows.push('<td colspan="2"><input type="text" id="'+tag+'" class="textfield form-field" value="'+value+'" /></td>');
                    else
                    if(val)             rows.push('<td>            <input type="text" id="'+tag+'" class="textfield form-field" value="'+value+'" /></td>');
                    else                rows.push('<td>            <input type="text" id="'+tag+'" class="textfield form-field"                   /></td>');
                    submittable=true;
                }
                else
                if(input=='rating'){
                    rows.push('<td class="label"><label for="'+tag+'">'+label+'</label></td>');
                    rows.push('<td><input type="radio" name="'+tag+'" class="rating form-field" value="0">0');
                    rows.push(    '<input type="radio" name="'+tag+'" class="rating form-field" value="1">1');
                    rows.push(    '<input type="radio" name="'+tag+'" class="rating form-field" value="2">2');
                    rows.push(    '<input type="radio" name="'+tag+'" class="rating form-field" value="3">3');
                    rows.push(    '<input type="radio" name="'+tag+'" class="rating form-field" value="4">4');
                    rows.push(    '<input type="radio" name="'+tag+'" class="rating form-field" value="5">5</td>');
                    submittable=true;
                }
                else
                if(input=='button'){
                    rows.push('<td><input id="'+tag+'" class="button form-field" type="submit" value="'+label+'" />');
                }
                if(!horizontal) rows.push('</tr>');
            }
            else
            if(guilist.view){
                var open=this.isOrContains(guilist.view,'open');
                var raw =this.isOrContains(guilist.view,'raw');
                if(!horizontal) rows.push('<tr>');
                rows.push('<td class="grid-col">'+this.getObjectHeadHTML(null, guilist.item, true, !open, null, raw)+'</td>');
                if(!horizontal) rows.push('</tr>');
            }
            return submittable;
        },
        // ------------------------------------------------
        getArticleHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML(this.getTitle(json), url, false, closed));
            rows.push('<div class="document left">');
            if(json.title        !== undefined) rows.push('<h2 class="summary">'+this.getAnyHTML(json.title)+'</h2>');
            if(json.publisher    !== undefined) rows.push('<div class="info-item">Publisher: '+this.getAnyHTML(json.publisher)+'</div>');
            if(json['journal-title'] !== undefined) rows.push('<div class="info-item">Journal: '+this.getAnyHTML(json['journal-title'])+'</div>');
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
            if(json['content-count'] !== undefined) rows.push('<div class="info-item">'+this.getObjectHTML(null,json['content-count'],false,'Documents Available')+'</div>');
            if(json.list         !== undefined) rows.push(this.getObjectListHTML(null, 'document', json.list, true));
            if(json.collection   !== undefined) rows.push('<div class="info-item">'+this.getObjectHeadHTML(null, json.collection, true, false)+'</div>');
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        getContactListHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML(this.getTitle(json,'Contacts'), url, false, closed));
            rows.push('<div class="contact-list">');
            if(this.isA('queryable', json, true)){
            var query='  full-name: *\n  email: *\n';
            rows.push(this.cyrusForm(url,query,'contact query','Query',4));
            }
            if(json.list !== undefined) rows.push(this.getObjectListHTML(null, 'contact', json.list, true));
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        getDocumentListHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML(this.getTitle(json,'Documents'), url, false, closed, json.icon));
            rows.push('<div class="document-list">');
            if(json.logo         !== undefined) rows.push('<div class="logo">'+this.getAnyHTML(json.logo)+'</div>');
            if(json['web-view']  !== undefined) rows.push('<div class="info-item">Website: '+this.getAnyHTML(json['web-view'])+'</div>');
            if(json['content-count'] !== undefined) rows.push('<div class="info-item">'+this.getObjectHTML(null,json['content-count'],false,'Documents Available')+'</div>');
            if(this.isA('queryable', json, true)){
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
            if(json.list         !== undefined) rows.push(this.getObjectListHTML(null, 'document', json.list, true));
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
                   '  <div class="media-text"><p>\n'+this.markupString2HTML(json.text)+'</p>\n</div>\n'+
                   ' </div>\n';
        },
        // ------------------------------------------------
        markupString2HTML: function(text){
            if(!text) return '';
            text=text.replace(/&#39;/g,'\'');
            text=text.replace(/&quot;/g,'"');
            text=text.htmlEscape();
            text=text.replace(/\|\[/g,'</p><pre>');
            text=text.replace(/\]\|/g,'</pre><p>');
            text=text.replace(linkre, '<a href="$2">$1</a>');
            text=text.replace(boldre, '<b>$1</b>');
            text=text.replace(italre, '<i>$1</i>');
            text=text.replace(codere, '<code>$1</code>');
            return text;
        },
        // ---------------------------------------------------
        getTitleString: function(json){
            if(!json || json.constructor!==Object) return '';
            if(json['full-name'] !== undefined) return simpleStringFrom(json['full-name']);
            if(json.title        !== undefined) return simpleStringFrom(json.title);
            return deCameliseList(json.is);
        },
        getTitle: function(json,elsedefault){
            if(!json || json.constructor!==Object) return '';
            if(json['full-name'] !== undefined) return this.getAnyHTML(json['full-name']);
            if(json.title        !== undefined) return this.getAnyHTML(json.title);
            return elsedefault? elsedefault: deCameliseList(json.is);
        },
        getDateSpan: function(clss, date){
            return '<span class="'+clss+'" title="'+makeISODate(date)+'">'+makeNiceDate(date)+'</span>';
        },
        getObjectHeadHTML: function(title, url, place, closed, icon, raw){  // don't forget to close that hanging div if !place
            if(place && !this.isObjectURL(url)) return this.getAnyHTML(url);
            return '<div class="object-head'+(closed? '':' open')+(raw? ' raw':'')+'">'+
                                                    this.getAnyHTML(url)+
                                                  ' <span class="open-close">+/-</span>'+
                                             (url?' <a href="'+url+'" class="object'+(place? '-place': '')+'">{..}</a>':'')+
                                            (icon? '<span class="icon">'+this.getAnyHTML(icon)+'</span>':'')+
                                                   '<span class="object-title">'+(title? title: '...')+'&nbsp;</span>'+
                   '</div>'+(!place? '<div class="object-body" '+(closed? 'style="display: none"':'')+'>': '');
        },
        getViaLinksRefactorMe: function(o1,l){
            var s1=l[0];
            var url1=o1[s1];
            if(!url1) return null;
            var str1 = localStorage.getItem('objects:'+getUID(url1));
            if(!str1) return null;
            var o2=JSON.parse(str1);
            if(!o2) return null;

            var s2=l[1];
            var url2=o2[s2];
            if(!url2) return null;
            var str2 = localStorage.getItem('objects:'+getUID(url2));
            if(!str2) return null;
            return JSON.parse(str2);
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
        },
        createLinks: function(s){
            return s.replace(/(http:\/\/[^ ]*\/uid-[-0-9a-zA-Z]*.json)/g, '<a class="replace-up" href="#$1">$1</a>')
                    .replace(/([^\/]uid-[-0-9a-zA-Z]*)/g,                 '<a class="replace-up" href="#$1.json">$1</a>');
        },
        toCyrusObject: function(o,i,tagdelim){
            if(o===undefined || o===null) return '';
            if(o.constructor===String) return o.indexOf(' ')== -1? o: '"'+o+'"';
            if(o.constructor===Object) return this.toCyrusHash(o,i);
            if(o.constructor===Array)  return this.toCyrusList(o,i,tagdelim);
            return ''+o;
        },
        toCyrusHash: function(o,i){
            if(!o || o.constructor!==Object) return '??';
            if(!i) i=2; else i+=2;
            var r='{\n';
            for(var tag in o){ r+=this.indent(i)+tag+': '+this.toCyrusObject(o[tag],i,true)+'\n'; }
            r+=this.indent(i-2)+'}';
            return r;
        },
        toCyrusList: function(o,i,tagdelim){
            if(!o || o.constructor!==Array) return '??';
            var nobrackets=tagdelim && (o.length!=1 || (o[0].constructor===Array && o[0].length!=1));
            var r=nobrackets? '': '(';
            for(var x in o){ r+=this.toCyrusObject(o[x],i)+' '; }
            r=r.trim()+(nobrackets? '': ')');
            return r;
        },
        indent: function(i){
            return '                                                            '.substring(0,i);
        }
    };
};

// }-------------- Viewer Application ----------------------{

function Cyrus(){

    var network = new Network();
    var json2html;
    var topObjectURL = null;
    var windowWidth = $(window).width();
    var windowHeight = $(window).height();
    var moreOf = {};
    var retryDelay=100;

    var me = {
        init: function(){
            me.getTopObject(''+window.location);
        },
        topObjectIn: function(url,obj,s,x){
            if(url && url!=topObjectURL){
                topObjectURL = url;
                history.pushState(null,null,getMashURL(url));
                json2html = new JSON2HTML(topObjectURL.substring(0,topObjectURL.lastIndexOf('/')+1));
            }
            if(false && s!='mored'){
                var objMore=obj.More;
                if(objMore){
                    if(objMore.constructor===String) moreOf[objMore]=obj;
                    else for(var i in objMore) moreOf[objMore[i]]=obj;
                }
            }
            document.title = json2html.getTitleString(obj).htmlUnEscape();
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
            $('a.object-place, a.object').each(function(n,ae){ var a=$(ae);
                if(a.attr('href')!=url) return;
                var open=a.parent().hasClass('open');
                var raw =a.parent().hasClass('raw');
                var objhead = a.parent();
                objhead.next().remove();
                objhead.replaceWith(json2html.getHTML(url, obj, !open, raw));
            });
            me.setUpHTMLEvents();
            setTimeout(function(){
                me.ensureVisibleAndReflow($('#content'));
                var cn = x && x.getResponseHeader('Cache-Notify');
                if(cn) network.longGetJSON(cn,me.getCreds(cn),me.someObjectIn,me.objectFail);
            }, 50);
            retryDelay=100;
        },
        objectFail: function(url,err,s,x){
            console.log('objectFail '+url+' '+err+' '+(x && x.status)+' '+s+' '+(x && x.getResponseHeader('Cache-Notify')));
            var isCN=url && url.indexOf('/c-n-')!= -1;
            retryDelay*=2;
            if(isCN) setTimeout(function(){ network.longGetJSON(url,me.getCreds(url),me.someObjectIn,me.objectFail); }, retryDelay);
        },
        someObjectIn: function(url,obj,s,x){
            if(obj){
                var notify=obj.Notify; delete obj.Notify;
                var formForCurrentView=me.getUIDOnly(topObjectURL);
                if(formForCurrentView) for(var i in notify){ var u=notify[i];
                    if(getUID(u)==formForCurrentView){ me.topObjectIn(url,obj,s,x); return; }
                }
            }
            if(url==topObjectURL) me.topObjectIn(url,obj,s,x);
            else                     me.objectIn(url,obj,s,x);
        },
        setUpHTMLEvents: function(){
            $(window).resize(function(e){
                if($(window).width() != windowWidth){
                    windowWidth = $(window).width();
                    me.reflowIfWidthChanged($('#content'));
                }
                if($(window).height() != windowHeight){
                    windowHeight = $(window).height();
                    me.reflowIfHeightChanged($('#content'));
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
            $('.cyrus-readonly').unbind().click(function(e){
            //   if(item.indexOf('editable')!= -1)
                var url =$(this).parent().find('.cyrus-target').val();
                var item=$(this).text();
                var h=json2html.cyrusForm(url,item);
                $(this).replaceWith(h);
                me.setUpHTMLEvents();
            });
            $('.cyrus-form').unbind().submit(function(e){
                var cyrus=true;
                var cytext=$(this).find('.cyrus-raw').val();
                var cy; try{ cy=cyrus? cytext: JSON.parse(cytext); } catch(e){ alert('Syntax: '+e); }
                if(!cy){ e.preventDefault(); return; }
                if(!cyrus){
                    if(cy.is.constructor==String) cy.is=[ cy.is, 'editable' ];
                    if(cy.is.constructor==Array && cy.is.indexOf('editable')== -1) cy.is.push('editable');
                }
                var targetURL=$(this).find('.cyrus-target').val();
                var ver=localStorage.getItem('versions:'+getUID(targetURL));
                if(ver) ver=JSON.parse('{ "ver": '+ver.substring(1,ver.length-1)+' }').ver;
                var uidver=me.getUIDandVer(targetURL,null,cyrus);
                if(cyrus){
                    var type=$(this).find('.cyrus-type').val();
                    if(!type){
                        var cyr = '{ '+uidver+'\n  is: editable rule\n  when: edited\n  editable: '+targetURL+'\n  user: ".."\n  '+
                                    ': { Version: '+ver+' } => as-is\n'+cy+'\n}';
                        network.postJSON(targetURL, cyr, true, me.getCreds(targetURL), null, null);
                    }else{
                        var cyr = '{ '+uidver+'\n  is: '+type+'\n  target: '+targetURL+'\n  user: ".."\n  match: {\n'+cy+'\n  }\n}';
                        network.postJSON(targetURL, cyr, true, me.getCreds(targetURL), null, null);
                    }
                }else{
                    var json = '{ '+uidver+', "is": [ "editable", "rule" ],\n  "when": "edited",\n  "editable": "'+targetURL+'",\n  "user": "" }';
                    network.postJSON(targetURL, me.makeJSONEditRule(json,ver,cy), false, me.getCreds(targetURL), null, null);
                }
                e.preventDefault();
            });
            $('.gui-form').unbind().submit(function(e){
                var targetURL=$(this).find('.form-target').val();
                var uidver=me.getUIDandVer(targetURL);
                var json = '{ '+uidver+',\n  "is": "form",\n  "gui": "'+targetURL+'",\n  "user": "",\n  "form": {\n   ';
                var fields = [];
                me.getFormFields($(this),fields);
                json+=fields.join(',\n   ');
                json+='\n }\n}';
                network.postJSON(targetURL, json, false, me.getCreds(targetURL), null, null);
                e.preventDefault();
            });
            $('.new-land-form').unbind().submit(function(e){
                var targetURL=$(this).find('.land-within').val();
                var uidver=me.getUIDandVer();
                var json = '{ '+uidver+',\n  "is": [ "land", "request" ],\n  "within": "'+targetURL+'",\n  "user": "",\n  ';
                var fields = [];
                me.getFormFields($(this),fields);
                json+=fields.join(',\n  ');
                json+='\n}\n';
                network.postJSON(targetURL, json, false, me.getCreds(targetURL), null, null);
                e.preventDefault();
            });
            $('.land-form').unbind().submit(function(e){
                var targetURL =$(this).find('.land-target').val();
                var withinURL =$(this).find('.land-within').val();
                var requestURL=$(this).find('.land-request').val();
                var uidver=me.getUIDandVer(targetURL,requestURL);
                var json = '{ '+uidver+',\n  "is": [ "land", "request" ],\n  "within": "'+withinURL+'",\n  "land": "'+targetURL+'",\n  "user": "",\n  ';
                var fields = [];
                me.getFormFields($(this),fields);
                json+=fields.join(',\n  ');
                json+='\n}\n';
                network.postJSON(targetURL, json, false, me.getCreds(targetURL), null, null);
                e.preventDefault();
            });
            $('.rsvp-form').unbind().submit(function(e){
                if($(this).find('.rsvp-type').val()=='attendable'){
                    var targetURL=$(this).find('.rsvp-target').val();
                    var uidver=me.getUIDandVer(targetURL);
                    var q=$(this).find('.rsvp-attending').is(':checked');
                    var json = '{ '+uidver+', "is": "rsvp",\n  "event": "'+targetURL+'",\n  "user": "",\n  "attending": "'+(q? 'yes': 'no')+'"\n}';
                    network.postJSON(targetURL, json, false, me.getCreds(targetURL), null, null);
                    e.preventDefault();
                }
                if($(this).find('.rsvp-type').val()=='reviewable'){
                    var withinURL=$(this).find('.rsvp-within').val();
                    var within=me.getUIDOnly(withinURL);
                    if(!within){ e.preventDefault(); alert('please mark your attendance before reviewing'); return; }
                    var targetURL=$(this).find('.rsvp-target').val();
                    var uidver=me.getUIDandVer(targetURL);
                    var json = '{ '+uidver+',\n  "is": "rsvp",\n  "event": "'+targetURL+'",\n  "user": "",\n  "within": "'+within+'",\n   ';
                    var fields = [];
                    me.getFormFields($(this),fields);
                    json+=fields.join(',\n  ');
                    json+=' }';
                    network.postJSON(targetURL, json, false, me.getCreds(targetURL), null, null);
                    e.preventDefault();
                }
            });
            $('#query').focus();
            $('#query-form').unbind().submit(function(e){
                var q=$('#query').val();
                var json = '{ "is": [ "document", "query" ],\n  "text": [ "has-words", "'+q.jsonEscape()+'" ] }';
                network.postJSON(topObjectURL, json, false, me.getCreds(topObjectURL), me.topObjectIn, me.topObjectFail);
                e.preventDefault();
            });
            $('#login-form').unbind().submit(function(e){
                var creds = { 'username': $('#username').val(), 'userpass': $('#userpass').val() };
                me.setCreds(topObjectURL, creds);
                e.preventDefault();
            });
            $('.replace-up').unbind().click(function(e){
                var url = $(this).attr('href').substring(1);
                me.replaceUp(url,$(this));
                e.preventDefault();
                return false;
            });
            $('.new-state').unbind().click(function(e){
                var mashURL = $(this).attr('href');
                url=mashURL.substring(mashURL.indexOf('#')+1);
                if(!me.replaceUp(url,$(this))){
                    me.getTopObject(mashURL);
                    history.pushState(null,null,mashURL);
                }
                e.preventDefault();
                return false;
            });
            $(window).bind('popstate', function() {
                me.getTopObject(''+window.location);
            });
        },
        replaceUp: function(url, a){
            var objbody=me.getObjectBodyAbove(a);
            if(!objbody) return false;
            var objhead=objbody.prev();
            var open=objhead.hasClass('open');
            var raw =objhead.hasClass('raw');
            objhead.replaceWith(json2html.getObjectHeadHTML(null, url, true, !open, null, raw));
            objhead=objbody.prev();
            objbody.remove();
            me.ensureVisibleObjectsIn(objhead);
            return true;
        },
        getObjectBodyAbove: function(el){
            for(var i=0; i<20; i++){
                el=el.parent();
                if(!el || el.hasClass('object-body')) return el;
            }
            return null;
        },
        getFormFields: function(form,fields){
            form.find('.form-field').each(function(n,i){
                var idOrName=i.getAttribute('id') || i.getAttribute('name');
                var intype=$(i).attr('type');
                if(intype=='checkbox') fields.push('"'+idOrName+'": '+$(i).is(':checked'));
                else
                if(!(intype=='radio' && !$(i).is(':checked'))) fields.push('"'+idOrName+'": "'+$(i).val()+'"');
            });
        },
        getTopObject: function(url){
            var previousObjectURL = topObjectURL;
            topObjectURL = me.getFullObjectURL(url);
            if(!topObjectURL || topObjectURL==previousObjectURL) return;
            json2html = new JSON2HTML(topObjectURL.substring(0,topObjectURL.lastIndexOf('/')+1));
            network.getJSON(topObjectURL, me.getCreds(topObjectURL), me.topObjectIn, me.topObjectFail);
        },
        getUIDandVer: function(url,useuid,cyrus){
            var uidver=url? localStorage.getItem('responses:'+url): null;
            if(!uidver){
                var uid=useuid? getUID(useuid): generateUID('uid');
                if(cyrus) uidver= 'UID: '  +uid+' Version: ' +1;
                else      uidver='"UID": "'+uid+'", "Version": '+1;
            }else{
                var re;
                if(cyrus) re=RegExp('(.*Version: )(.*)').exec(uidver);
                else      re=RegExp('(.*"Version": )(.*)').exec(uidver);
                uidver=re[1]+(parseInt(re[2])+1);
            }
            if(url) localStorage.setItem('responses:'+url, uidver);
            return uidver;
        },
        getUIDOnly: function(url){
            var uidver=localStorage.getItem('responses:'+url);
            if(!uidver) return null;
            var     re=RegExp('"UID": "([^"]*)"').exec(uidver);
            if(!re) re=RegExp('UID: ([^ ]*) ').exec(uidver);
            if(!re) return null;
            return re[1];
        },
        ensureVisibleAndReflow: function(panel){
            me.ensureVisibleObjectsIn(panel);
            me.reflowIfWidthChanged(panel);
            me.reflowIfHeightChanged(panel);
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
                if($(r).parent().width() > 1000) $(r).addClass('wide');
                else                             $(r).removeClass('wide');
            });
        },
        reflowIfHeightChanged: function(panel){
            var outer=$('#content .object-body');
            var outerheight=outer.height();
            $(panel).find('.object-body .object-body').each(function(n,r){
                if(!$(r).is(':visible')) return;
                $(r).height(outerheight-70);
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
            localStorage.setItem('credsOfSite:'+domain, JSON.stringify(creds));
        },
        getCreds: function(requestURL){
            var domain = getDomain(requestURL);
            return JSON.parse(localStorage.getItem('credsOfSite:'+domain));
        },
        makeJSONEditRule: function(json,ver,val){
            var j=JSON.parse(json);
            var v={ 'Version': ver };
            j['']=[ v, '=>', 'as-is', val ];
            return JSON.stringify(j);
        },
        mergeHashes: function(a, b){
            for(var x in b){

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
            for(var x in b){
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
  for(var i in this){
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

function simpleStringFrom(o){
    if(!o) return '';
    if(o.constructor===String) return o;
    if(o.constructor!==Array) return ''+o;
    r=''; $.each(o, function(k,s){ r+=s+' '; });
    return r;
}

function deCameliseList(ll){
    if(!ll) return '';
    if(ll.constructor===String) return deCamelise(ll);
    if(ll.constructor!==Array) return deCamelise(''+ll);
    r=''; $.each(ll, function(k,s){ r+=deCameliseList(s)+' '; });
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

function toString(o){
    var r='';
    if(o.constructor===Array)  for(var i in o) r+=o[i]+', ';
    else
    if(o.constructor===Object) for(var i in o) r+=i+': '+o[i]+', ';
    else r=o;
    return r;
}

function sizeof(o){
    var i=0;
    for(var x in o) i++;
    return i;
}

// }--------------------------------------------------------{



// }-------------- Networking ------------------------------{

function Network(){

    var useLocalStorage = typeof(localStorage)!=='undefined';
    var localCache = {};
    var outstandingRequests = 0;

    var me = {
        getJSON: function(url,creds,ok,err){
            me.updateProgress(1);
            var obj=null;
            if(useLocalStorage){
                var objstr = localStorage.getItem(url);
                if(objstr) obj=JSON.parse(objstr);
            }
            else obj=localCache[url];
            if(obj){
                me.updateProgress(-1);
                ok(obj,'from-cache',null);
            }else{
                var headers = creds? { 'Authorization': me.buildAuth(creds,'GET',url) }: {}
                $.ajax({
                    url: url,
                    headers: headers,
                    dataType: 'json',
                    success: function(obj, s, x){
                        if(useLocalStorage){ try{ localStorage.setItem(url, JSON.stringify(obj));
                                            }catch(e){ if(e==QUOTA_EXCEEDED_ERR){ console.log('Local Storage quota exceeded'); } }
                        } else localCache[url]=obj;
                        me.updateProgress(-1);
                        ok(obj,s,x);
                    },
                    error: err
                });
            }
        },
        postJSON: function(url,json,creds,ok,err){
            var headers = creds? { 'Authorization': me.buildAuth(creds,'POST',url,json) }: {}
            $.ajax({
                type: 'POST',
                url: url,
                headers: headers,
                data: 'o='+escape(json),
             // contentType: 'application/json', // crappy CORS
                dataType: 'json',
                success: ok,
                error: err
            });
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
            if(!json || json.constructor!==Object) return '<div><div>Not an object!</div><div>'+'<a href="'+url+'">'+url+'</a></div><div>'+json+'</div></div>';
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
            if(a.constructor===Object) return this.getHTML(a['%url']||a['%more'],a,closed);
            return a!=null? ''+a: '-';
        },
        getObjectHTML: function(url,json,closed,title){
            var that = this;
            var rows = [];
            $.each(json, function(key,val){ rows.push('<tr><td>'+deCamelise(key)+'</td><td>'+that.getAnyHTML(val)+ '</td></tr>'); });
            return this.getObjectHeadHTML(this.getTitle(json,title),url,false,closed)+
                   '<table class="json">\n'+rows.join('\n')+'\n</table></div>';
        },
        getListHTML: function(l){
            var that = this;
            var rows = [];
            $.each(l, function(key,val){ rows.push(that.getAnyHTML(val)); });
            if(rows.length >5) return '<div class="list"><p class="list">'+rows.join('</p>\n<p class="list">')+'</p></div>\n';
            return rows.join(', ');
        },
        getObjectListHTML: function(header,itemclass,list){
            var rows=[];
            if(header) rows.push('<h3>'+header+'</h3>');
            var that = this;
            if(list.constructor===String) list = [ list ];
            if(list.constructor!==Array) return this.getAnyHTML(list);
            if(list.length){
            rows.push('<ul>');
            $.each(list, function(key,item){
                rows.push('<li class="'+itemclass+'">');
                if(that.isONLink(item)) rows.push(that.getObjectHeadHTML(null, item, true));
                else                    rows.push(that.getAnyHTML(item));
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
            if(json.webView      !== undefined) rows.push('<div class="info-item">Website: '+this.getAnyHTML(json.webView)+'</div>');
            if(json.publications !== undefined) rows.push(this.getObjectListHTML('Publications', 'publication', json.publications));
            if(json.bio          !== undefined) rows.push('<div class="info-item">Bio: '+this.getAnyHTML(json.bio)+'</div>');
            if(json.photo        !== undefined) rows.push('<div class="photo">'+this.getAnyHTML(json.photo)+'</div>');
            if(json.parents      !== undefined) rows.push(this.getObjectListHTML('Parents', 'parent', json.parents));
            if(json.inspirations !== undefined) rows.push(this.getObjectListHTML('Inspired by', 'inspirations', json.inspirations));
            if(json.following    !== undefined) rows.push(this.getObjectListHTML('Following', 'following', json.following));
            if(json['%more']     !== undefined) rows.push(this.getObjectListHTML('More', 'more', json['%more']));
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
            if(json.content   !== undefined) rows.push('<p class="description">'+this.getAnyHTML(json.content)+'</p>');
            if(json.start     !== undefined) rows.push('<div class="info-item">From: ' +this.getDateSpan("dtstart", json.start)+'</div>');
            if(json.end       !== undefined) rows.push('<div class="info-item">Until: '+this.getDateSpan("dtend",   json.end)  +'</div>');
            if(json.location  !== undefined) rows.push(this.getEventLocationHTML(json.location));
            if(json.attendees !== undefined) rows.push(this.getObjectListHTML('Attendees:', 'attendee', json.attendees));
            if(json['%more']  !== undefined) rows.push(this.getObjectListHTML('More', 'more', json['%more']));
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        getEventLocationHTML: function(locurl){
            var rows=[];
            rows.push('<h3>Location:</h3>');
            rows.push('<div class="location">');
            rows.push(this.getObjectHeadHTML(null, locurl, true));
            rows.push('</div>');
            return rows.join('\n')+'\n';
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
            if(json.published    !== undefined) rows.push('<div class="info-item">Published: '+this.getDateSpan("published", json.published)+'</div>');
            if(json.webView      !== undefined) rows.push('<div class="info-item">Website: '+this.getAnyHTML(json.webView)+'</div>');
            if(json.collection   !== undefined) rows.push('<div class="info-item">'+this.getObjectHeadHTML(null, json.collection, true)+'</div>');
            if(json.authors      !== undefined) rows.push(this.getObjectListHTML('Authors:', 'author', json.authors));
            rows.push('</div><div class="document right">');
            if(json.content      !== undefined) rows.push('<div class="content">'+this.getAnyHTML(json.content)+'</div>');
            if(json['%more']     !== undefined) rows.push(this.getObjectListHTML('More', 'more', json['%more']));
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        // ------------------------------------------------
        getPlainListHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML(this.getTitle(json,'Documents'), url, false, closed, json.icon));
            rows.push('<div class="plain-list">');
            if(json.logo         !== undefined) rows.push('<div class="logo">'+this.getAnyHTML(json.logo)+'</div>');
            if(json.webView      !== undefined) rows.push('<div class="info-item">Website: '+this.getAnyHTML(json.webView)+'</div>');
            if(json.contentCount !== undefined) rows.push('<div class="info-item">'+this.getObjectHTML(null,json.contentCount,false,'Documents Available')+'</div>');
            if(json.list         !== undefined) rows.push(this.getObjectListHTML(null, 'document', json.list));
            if(json.collection   !== undefined) rows.push('<div class="info-item">'+this.getObjectHeadHTML(null, json.collection, true)+'</div>');
            rows.push('</div></div>');
            return rows.join('\n')+'\n';
        },
        getDocumentListHTML: function(url,json,closed){
            var rows=[];
            rows.push(this.getObjectHeadHTML(this.getTitle(json,'Documents'), url, false, closed, json.icon));
            rows.push('<div class="document-list">');
            if(json.logo         !== undefined) rows.push('<div class="logo">'+this.getAnyHTML(json.logo)+'</div>');
            if(json.webView      !== undefined) rows.push('<div class="info-item">Website: '+this.getAnyHTML(json.webView)+'</div>');
            if(json.contentCount !== undefined) rows.push('<div class="info-item">'+this.getObjectHTML(null,json.contentCount,false,'Documents Available')+'</div>');
            if(this.isA('searchable', json, true)){
            rows.push('<form id="query-form">');
            rows.push('<label for="query">Search these documents:</label>');
            rows.push('<input id="query" class="text" type="text" />');
            rows.push('<input class="submit" type="submit" value="&gt;" />');
            rows.push('</form>');
            }
            if(this.isA('personalisable', json, true)){
            rows.push('<form id="login-form">');
            rows.push('<label for="username" class="login-label">Name</label>');
            rows.push('<input id="username" class="text" type="text" />');
            rows.push('<label for="userpass" class="login-label">Password</label>');
            rows.push('<input id="userpass" class="text" type="text" />');
            rows.push('<input class="submit" type="submit" value="&gt;" />');
            rows.push('</form>');
            }
            if(json.list         !== undefined) rows.push(this.getObjectListHTML(null, 'document', json.list));
            if(json.collection   !== undefined) rows.push('<div class="info-item">'+this.getObjectHeadHTML(null, json.collection, true)+'</div>');
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
            if(!json) return 'No object';
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
            return s && (s.constructor===String) && ((this.isLink(s) && s.endethWith('.json')) || s.startethWith('uid-'));
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
            if(!s.endethWith('.json')) return false;
            return true;
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
    var credsOfSite = {};
    var moreOf = {};

    var me = {
        init: function(){
            me.getTopObject(window.location);
            if(!useHistory) setInterval(function(){ me.getTopObject(window.location); }, 200);
        },
        topObjectIn: function(obj, s, x){
            var newURL = x && x.getResponseHeader('Content-Location');
            if(newURL && newURL!=topObjectURL){
                topObjectURL = newURL;
                json2html = new JSON2HTML(topObjectURL.substring(0,topObjectURL.lastIndexOf('/')+1));
                var mashURL = getMashURL(topObjectURL);
                if(useHistory) history.pushState(null,null,mashURL);
                else { /* $('#content').html('Reloading  '+mashURL); window.location = mashURL; return; */ }
            }
            if(s!='mored'){
                var objMore=obj["%more"];
                if(objMore){
                    if(objMore.constructor===String) moreOf[objMore]=obj;
                    else for(i in objMore) moreOf[objMore[i]]=obj;
                }
            }
            document.title = json2html.getTitle(obj).htmlUnEscape();
            window.scrollTo(0,0);
            $('#content').html(json2html.getHTML(topObjectURL, obj));
            me.setUpHTMLEvents();
            setTimeout(function(){ me.ensureVisibleAndReflow($('#content')); }, 50);
        },
        topObjectFail: function(x,s,e){
            $('#content').html('<div>topObjectFail: <a href="'+topObjectURL+'">'+topObjectURL+'</a></div><div>'+s+'; '+e+'</div>');
        },
        objectIn: function(url,obj,s){
            if(!obj){ this.objectFail(url,null,'object empty; status='+s,null); return; }
            var moreofobj=moreOf[url];
            if(moreofobj){
                this.mergeHashes(moreofobj,obj);
                this.topObjectIn(moreofobj,'mored',null);
                moreOf[url]=undefined;
                return;
            }
            var html = json2html.getHTML(url, obj, true);
            $('a.object-place').each(function(n,ae){ var a=$(ae);
                if(a.attr('href')!=url) return;
                a.parent().replaceWith(html);
            });
            me.setUpHTMLEvents();
        },
        objectFail: function(url,x,s,e){
            console.log(s+' '+url);
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
            $('#query').focus();
            $('#query-form').unbind().submit(function(e){
                var q=$('#query').val();
                var json = '{ "is": [ "document", "query" ], "content": "<hasWords('+q.jsonEscape()+')>" }';
                network.postJSON(topObjectURL, json, me.getCreds(topObjectURL), me.topObjectIn, me.topObjectFail);
                e.preventDefault();
            });
            $('#login-form').unbind().submit(function(e){
                var creds = { "username": $('#username').val(), "userpass": $('#userpass').val() };
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
        ensureVisibleAndReflow: function(panel){
            me.ensureVisibleObjectsIn(panel);
            me.reflowIfWidthChanged(panel);
        },
        ensureVisibleObjectsIn: function(panel){
            $(panel).find('a.object-place').each(function(n,a){
                if(!$(a).is(':visible')) return;
                var url = a.getAttribute('href');
                network.getJSON(url, me.getCreds(url), function(obj,s){ me.objectIn(url,obj,s); }, function(x,s,e){ me.objectFail(url,x,s,e);});
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
            if(!url.endethWith('.json'))     url = url+'.json';
            return url;
        },
        setCreds: function(siteURL, creds){
            var domain = getDomain(siteURL);
            if(useLocalStorage) localStorage.setItem('credsOfSite:'+domain, JSON.stringify(creds));
            else                credsOfSite[domain] = creds;
        },
        getCreds: function(requestURL){
            var domain = getDomain(requestURL);
            if(useLocalStorage) return JSON.parse(localStorage.getItem('credsOfSite:'+domain));
            else                return credsOfSite[domain];
        },
        mergeHashes: function(a, b){
            for(x in b){

                if(!b[x]) continue;
                typebx=b[x].constructor;
                var bx;

                if(typebx===String) bx=b[x]; else
                if(typebx===Array)  bx=b[x].clone(); else
                if(typebx===Object) bx=b[x].clone();

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
                if(typebx===Array)  if(a.indexOf(b[x])== -1) a.push(b[x].clone()); else
                if(typebx===Object) if(a.indexOf(b[x])== -1) a.push(b[x].clone());
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

Object.prototype.clone = function() {
  var o = (this instanceof Array)? []: {};
  for(i in this){
    if(!(this[i] && this.hasOwnProperty(i))) continue;
    if(typeof this[i]=="object") o[i]=this[i].clone();
    else o[i]=this[i];
  }
  return o;
};

// --------------------

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

// }--------------------------------------------------------{


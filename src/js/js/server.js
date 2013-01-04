#!/usr/bin/node

var PORT=8083;

var http = require('http'),
    url = require('url'),
    path = require('path'),
    fs = require('fs');

var mimeTypes={
    '.html': 'text/html',
    '.js':   'application/javascript',
    '.json': 'application/json',
    '.css':  'text/css',
    '.appcache': 'text/cache-manifest',

    '.jpeg': 'image/jpeg',
    '.jpg':  'image/jpeg',
    '.gif':  'image/gif',
    '.ico':  'image/x-icon',
    '.png':  'image/png'
};

var charType={
    '.html': true,
    '.js':   true,
    '.json': true,
    '.css':  true,
    '.manifest': true,

    '.jpeg': false,
    '.jpg':  false,
    '.gif':  false,
    '.ico':  false,
    '.png':  false
};

http.createServer(function(req, res) {
    if(req.method !== 'GET'){
        res.writeHead(400);
        res.end();
        console.log('400 '+req.method);
        return;
    }
    var filename = path.join(process.cwd(), url.parse(req.url).pathname);
    var q = filename.indexOf('?');
    if(q>=0) filename = filename.substring(0,q);
    if(filename.endethWith('/')) filename += 'index.html';

    path.exists(filename, function(exists){
        if(!exists) {
            res.writeHead(404);
            res.end();
            console.log('404 '+filename);
            return;
        }
        var ext = path.extname(filename);
        var mimeType = mimeTypes[ext];
        if(!mimeType) mimeType='text/plain';
        res.writeHead(200, mimeType=='text/cache-manifest'? { 'Content-Type': mimeType, 'Cache-Control': 'no-cache' }: { 'Content-Type': mimeType });

        var fileStream = fs.createReadStream(filename);
        if(charType[ext]) fileStream.setEncoding('utf-8');
        fileStream.on('error', function(){
            res.writeHead(500);
            res.end();
            console.log('500 '+filename);
        });
        fileStream.pipe(res);
        console.log('200 '+filename);
    });

}).listen(PORT);

String.prototype.startethWith = function(str){ return this.slice(0, str.length)==str; };
String.prototype.endethWith   = function(str){ return this.slice(  -str.length)==str; };


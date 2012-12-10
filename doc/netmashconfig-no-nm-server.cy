{ name: Cyrus
  kernel: { threadpool: 3 }
  persist: { db: cyrus.db
             syncrate: 100
             preload: ( ) }
  network: { pathprefix: /o/
             log: true }
  cyrus: { links: http://the-object.net/123-456.json }
}


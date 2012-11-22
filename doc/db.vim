syn match tag1 "UID:\|Rules:\|Notifying:\|Alerted:"
syn match tag2 "is:\|title:\|when:\|list:\|view:\|user:\|form:\|vertices:\|texturepoints:\|normals:\|faces:\|textures:\|sub-objects:\|vertex-shader:\|fragment-shader:\|rotation:\|scale:\|light:\|text:\|status:\|value:\|input:\|label:\|object:\|coords:\|direction:\|location:\|address:\|colours:\|proportions:\|range:\|logo:\|template:"
syn match uid1 "uid-[a-z0-9-]\+\|http://[^ ]*"
syn region  str1 contained display oneline start='"' end='"'
syn match key1 " < \| > \| = \| + \| - \| * \| × \| / \|\<[0-9]\+"
syn keyword key2 form gui rule editable 3d chooser textfield checkbox button true false clamp has if then else new number boolean format select integer count place mesh cuboid notice random horizontal vertical style updatable land 
syn match symbol "{\|}\|(\|)\|=>\|\!=>\|:\|\$\|\.\|#"

hi def link tag1 Comment
hi def link tag2 Special
hi def link uid1 Type
hi def link key1 Type
hi def link key2 Type
hi def link str1 String
hi def link symbol Comment


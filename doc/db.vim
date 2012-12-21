syn match tag1 "[A-Z][a-zA-Z0-9-]*:"
syn match tag2 "is:\|title:\|when:\|list:\|view:\|user:\|form:\|vertices:\|texturepoints:\|normals:\|faces:\|textures:\|sub-items:\|vertex-shader:\|fragment-shader:\|rotation:\|scale:\|light:\|text:\|status:\|value:\|input:\|label:\|object:\|coords:\|direction:\|location:\|address:\|colours:\|proportions:\|range:\|logo:\|update-template:\|review-template:\|full-name:\|extended:\|street:\|locality:\|region:\|postal-code:\|country\|phone:\|work:\|home:\|web-view:\|email:\|attendees:\|attending:\|rating:\|sub-title:\|author:\|atom:\|updated:\|published:\|icon:\|start:\|end:\|watching:\|place:"
syn match key1 " < \| > \| = \| + \| - \| * \| × \| / \|[0-9-]\+"
syn keyword key2 form gui rule editable 3d chooser textfield checkbox button true false clamp has if then else new number boolean format select integer count mesh cuboid notice random horizontal vertical style updatable land 
syn match uid1 "uid-[a-z0-9-]\+\|http://[^ ]*"
syn region str1 contained display oneline start='"' end='"'
syn match symbol "{\|}\|(\|)\|=>\|\!=>\|:\|@\|\.\|#"

hi def link tag1 Comment
hi def link tag2 Special
hi def link key1 Type
hi def link key2 Type
hi def link uid1 Type
hi def link str1 String
hi def link symbol Comment


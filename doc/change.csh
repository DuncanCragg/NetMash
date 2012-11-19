#!/bin/csh

foreach f (`find src res doc -type f`)
 egrep -l $1 $f && sed -i "s/$1/$2/g" $f
end

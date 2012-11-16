#!/bin/csh

foreach f (`find . -type f`)
 egrep -l $1 $f && sed -i "s/$1/$2/g" $f
end

var tkmlibSICreateHttpRequest=function(){if(window.ActiveXObject){try{return new ActiveXObject("Msxml2.XMLHTTP");}catch(e){try{return new ActiveXObject("Microsoft.XMLHTTP");}catch(e2){return null;}}}else if(window.XMLHttpRequest){return new XMLHttpRequest();}else{return null;}}
var simpleget=function(file,afterfunc){var httpoj=tkmlibSICreateHttpRequest();httpoj.open("GET",file,"true");httpoj.onreadystatechange=function(){if(httpoj.readyState==4){if(afterfunc!=undefined){afterfunc(httpoj.responseText, httpoj);}}};httpoj.send("");}
var simpleimport=function(targetI,fileI,afterfuncI){simpleget(fileI,function(response){document.getElementById(targetI).innerHTML=response;if(afterfuncI!=undefined){afterfuncI(httpoj);}})}

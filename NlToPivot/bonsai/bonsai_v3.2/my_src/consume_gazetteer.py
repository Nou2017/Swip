#!/usr/bin/env python

import fileinput
import unicodedata
#import sys
from suds.client import Client

#sys.setdefaultencoding('utf-8')
url = 'http://localhost:8080/NlToPivotGatePipeline/NlToPivotGatePipelineWS?wsdl'
client = Client(url)
result = ''
for line in fileinput.input():
	#line = line.encode('utf8')
	#line = unicode(line, 'utf8')
	#line2 = unicodedata.normalize('NFKD', line).encode('ascii','ignore')
	d = dict(adaptedNlQuery=line)
	result += client.service.getQueryWithGatheredNamedEntities(**d)

print result

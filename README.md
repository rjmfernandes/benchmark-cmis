benchmark-cmis
==============

Load test using vanilla CMIS and the Alfresco Benchmark Framework

* <a href="https://www.youtube.com/watch?v=_8w5TxjBgh4&list=PLktNOqTikHe8wXFvWnV8s7TbTlV4K2flf">Videos from Alfresco Summit 2014</a> showing the test being used in AWS.
* The <a href="https://github.com/derekhulley/alfresco-benchmark">Alfresco Benchmark Framework Project</a> required to execute load tests.
* The <a href="https://wiki.alfresco.com/wiki/Benchmark_Testing_with_Alfresco">Benchmark Testing with Alfresco Wiki page</a> has links to the artifacts and source code for all related products.
* The <a href="https://wiki.alfresco.com/wiki/Benchmark_Framework_2.0">Architecture and setup</a> of the tests 

This project produces a WAR file that acts as a headless load driver application designed to run in Tomcat7.  It is used in conjuction with the <a href="https://github.com/derekhulley/alfresco-benchmark">Alfresco Benchmark Framework</a> application, which is a separate application containing a browser-based client that configures and controls test execution.


* Follow instructions for the <a href="https://wiki.alfresco.com/wiki/Benchmark_Framework_2.0#Benchmark_Server_Setup">Benchmark Server set up</a>.
* Follow the  <a href="https://wiki.alfresco.com/wiki/Benchmark_Framework_2.0#Benchmark_Load_Driver_Setup">set up instructions for the drivers</a> but using this project rather than the sample.
* Create users in the target server.  Alfresco provides a <a href="https://wiki.alfresco.com/wiki/Running_Benchmark_Applications:_Alfresco_Sign_Up">benchmark application called Sign Up</a> to help do this.  If you are not using Alfresco, you need to create users in the target server and then import the user data into the MongoDB instance:
<pre>
  mongoimport -d bm20-data -c mirrors.${serverIP}.users < users.json
</pre>
where <b>${serverIp}</b> is the IP address of the server to test.  The JSON data could look like this:
<pre>
  {
    "randomizer" : 797943,
    "username" : "admin",
    "password" : "admin",
    "creationState" : "Created",
    "firstName" : "Administrator",
    "lastName" : "Tiger",
    "email" : "admin@example.com",
    "domain" : "example.com"
  }
</pre>
